import "server-only";
import {
  createCipheriv,
  createDecipheriv,
  randomBytes,
  createHash,
  createHmac,
} from "node:crypto";
import { isIP } from "node:net";
import { lookup } from "node:dns/promises";
import http from "node:http";
import https from "node:https";
import { Readable } from "node:stream";
import { cookies, headers } from "next/headers";
import { z } from "zod";
export class PublicError extends Error {
  constructor(
    message: string,
    public status = 400,
  ) {
    super(message);
  }
}
export interface Session {
  base: string;
  username: string;
  password: string;
  scope: string;
  expires: number;
}
const cookieName = "rayan-session";
const globalKey = globalThis as typeof globalThis & { rayanDevKey?: Buffer };
function secret() {
  const value = process.env.SESSION_SECRET;
  if (value && value.length >= 32)
    return createHash("sha256").update(value).digest();
  if (process.env.NODE_ENV === "production")
    throw new PublicError("Server session secret is not configured.", 503);
  return (globalKey.rayanDevKey ??= randomBytes(32));
}
export function seal(data: unknown, purpose: string) {
  const iv = randomBytes(12),
    c = createCipheriv("aes-256-gcm", secret(), iv);
  c.setAAD(Buffer.from(purpose));
  return Buffer.concat([
    iv,
    c.update(JSON.stringify(data)),
    c.final(),
    c.getAuthTag(),
  ]).toString("base64url");
}
export function unseal<T>(token: string, purpose: string): T {
  try {
    if (token.length > 16000) throw 0;
    const b = Buffer.from(token, "base64url"),
      d = createDecipheriv("aes-256-gcm", secret(), b.subarray(0, 12));
    d.setAAD(Buffer.from(purpose));
    d.setAuthTag(b.subarray(-16));
    return JSON.parse(
      Buffer.concat([d.update(b.subarray(12, -16)), d.final()]).toString(),
    ) as T;
  } catch {
    throw new PublicError("Session expired. Sign in again.", 401);
  }
}
export const loginSchema = z.object({
  host: z.string().trim().min(1).max(1024),
  scheme: z.enum(["https", "http"]).default("https"),
  port: z.string().max(5).default(""),
  username: z.string().trim().min(1).max(256),
  password: z.string().min(1).max(512),
});
export function normalize(input: z.input<typeof loginSchema>) {
  const v = loginSchema.parse(input);
  let u: URL;
  try {
    u = new URL(
      /^[a-z]+:\/\//i.test(v.host) ? v.host : `${v.scheme}://${v.host}`,
    );
  } catch {
    throw new PublicError("Enter a valid provider address.");
  }
  if (
    !["http:", "https:"].includes(u.protocol) ||
    u.username ||
    u.password ||
    u.search ||
    u.hash ||
    /\.(php|m3u8?)\/?$/i.test(u.pathname)
  )
    throw new PublicError(
      "Enter the provider base address, without credentials, query or playlist path.",
    );
  if (v.port) {
    if (!/^\d+$/.test(v.port) || +v.port < 1 || +v.port > 65535)
      throw new PublicError("Port must be between 1 and 65535.");
    u.port = v.port;
  }
  u.pathname = u.pathname.replace(/\/+$/, "");
  return {
    base: u.toString().replace(/\/$/, ""),
    username: v.username,
    password: v.password,
  };
}
export function scopeFor(c: {
  base: string;
  username: string;
  password: string;
}) {
  return createHmac("sha256", secret())
    .update(JSON.stringify(c))
    .digest("hex")
    .slice(0, 32);
}
export async function session() {
  const c = (await cookies()).get(cookieName)?.value;
  if (!c) throw new PublicError("Sign in to your provider.", 401);
  const s = unseal<Session>(c, "session");
  if (s.expires < Date.now())
    throw new PublicError("Session expired. Sign in again.", 401);
  return s;
}
export async function saveSession(s: Session) {
  (await cookies()).set(cookieName, seal(s, "session"), {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: 60 * 60 * 24 * 7,
  });
}
export async function removeSession() {
  (await cookies()).delete(cookieName);
}
export async function sameOrigin() {
  const h = await headers(),
    origin = h.get("origin"),
    expected =
      process.env.APP_ORIGIN ??
      `${h.get("x-forwarded-proto") ?? "http"}://${h.get("host")}`;
  if (!origin || origin !== new URL(expected).origin)
    throw new PublicError("Request origin was rejected.", 403);
}
const origins = (name: string) =>
  (process.env[name] ?? "")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
export function checkURL(raw: string, base?: string) {
  let u: URL;
  try {
    u = new URL(raw);
  } catch {
    throw new PublicError("Invalid resource address.");
  }
  if (
    !["https:", "http:"].includes(u.protocol) ||
    u.username ||
    u.password ||
    u.hash
  )
    throw new PublicError("Unsupported resource address.");
  const allowed = [
    ...origins("PROVIDER_ORIGINS"),
    ...(base ? [new URL(base).origin, ...origins("MEDIA_ORIGINS")] : []),
  ];
  if (!allowed.includes(u.origin))
    throw new PublicError(
      "This provider or media origin is not approved by the server operator.",
      403,
    );
  return u;
}
export function privateIP(address: string) {
  if (address.includes(":")) {
    const a = address.toLowerCase();
    return (
      a === "::" ||
      a === "::1" ||
      a.startsWith("fc") ||
      a.startsWith("fd") ||
      a.startsWith("fe8") ||
      a.startsWith("fe9") ||
      a.startsWith("fea") ||
      a.startsWith("feb") ||
      a.startsWith("::ffff:") ||
      !isIP(a)
    );
  }
  const p = address.split(".").map(Number);
  return (
    !isIP(address) ||
    p[0] === 0 ||
    p[0] === 10 ||
    p[0] === 127 ||
    p[0] >= 224 ||
    (p[0] === 169 && p[1] === 254) ||
    (p[0] === 172 && p[1] >= 16 && p[1] <= 31) ||
    (p[0] === 192 && (p[1] === 168 || p[1] === 0)) ||
    (p[0] === 100 && p[1] >= 64 && p[1] <= 127) ||
    (p[0] === 198 && (p[1] === 18 || p[1] === 19))
  );
}
// DNS is checked inside the actual socket lookup, preventing DNS-rebinding between validation and connection.
export async function upstream(
  raw: string,
  base?: string,
  range?: string,
  signal?: AbortSignal,
  redirects = 0,
): Promise<Response> {
  const u = checkURL(raw, base),
    devPrivate =
      process.env.ALLOW_PRIVATE_PROVIDERS === "true" &&
      process.env.NODE_ENV !== "production";
  const addresses = await lookup(u.hostname.replace(/^\[|\]$/g, ""), {
    all: true,
  });
  if (
    !addresses.length ||
    (!devPrivate && addresses.some((a) => privateIP(a.address)))
  )
    throw new PublicError("Private network providers are disabled.", 403);
  const chosen = addresses.find((a) => a.family === 4) ?? addresses[0];
  const res = await new Promise<http.IncomingMessage>((resolve, reject) => {
    const request = (u.protocol === "https:" ? https : http).request(
      u,
      {
        method: "GET",
        headers: {
          Accept: "*/*",
          "Accept-Encoding": "identity",
          ...(range && /^bytes=\d*-\d*$/.test(range) ? { Range: range } : {}),
        },
        lookup: (_host, options, callback) => {
          if (options.all) {
            const all = callback as unknown as (
              error: Error | null,
              addresses: { address: string; family: number }[],
            ) => void;
            all(null, [chosen]);
          } else callback(null, chosen.address, chosen.family);
        },
        signal,
        timeout: 30000,
      },
      resolve,
    );
    request.on("timeout", () => request.destroy(new Error("timeout")));
    request.on("error", () =>
      reject(
        new PublicError(
          "Cannot reach the provider. Check the address and connection.",
          502,
        ),
      ),
    );
    request.end();
  });
  if (res.statusCode && [301, 302, 303, 307, 308].includes(res.statusCode)) {
    res.resume();
    if (redirects >= 4 || !res.headers.location)
      throw new PublicError("Provider redirect limit reached.", 502);
    return upstream(
      new URL(res.headers.location, u).href,
      base,
      range,
      signal,
      redirects + 1,
    );
  }
  const h = new Headers({ "x-rayan-upstream": u.href });
  for (const k of [
    "content-type",
    "content-length",
    "content-range",
    "accept-ranges",
  ]) {
    const v = res.headers[k];
    if (typeof v === "string") h.set(k, v);
  }
  return new Response(Readable.toWeb(res) as ReadableStream<Uint8Array>, {
    status: res.statusCode ?? 502,
    headers: h,
  });
}
export async function boundedBody(
  response: Response,
  limit = 25 * 1024 * 1024,
) {
  const reader = response.body?.getReader();
  if (!reader) return Buffer.alloc(0);
  const chunks: Uint8Array[] = [];
  let size = 0;
  try {
    while (true) {
      const r = await reader.read();
      if (r.done) break;
      size += r.value.length;
      if (size > limit)
        throw new PublicError(
          "Provider response exceeds the configured safety limit.",
          502,
        );
      chunks.push(r.value);
    }
  } finally {
    await reader.cancel();
  }
  return Buffer.concat(chunks);
}
export function resource(raw: string, s: Session) {
  try {
    checkURL(raw, s.base);
    return `/api/media/${seal({ url: raw, scope: s.scope, expires: Math.min(s.expires, Date.now() + 86400000) }, "media")}`;
  } catch {
    return "";
  }
}
export function resourceURL(token: string, s: Session) {
  const p = unseal<{ url: string; scope: string; expires: number }>(
    token,
    "media",
  );
  if (p.scope !== s.scope || p.expires < Date.now())
    throw new PublicError("Media link expired. Reload the title.", 401);
  checkURL(p.url, s.base);
  return p.url;
}
export function rewriteHLS(text: string, url: string, s: Session) {
  return text
    .split(/\r?\n/)
    .map((line) => {
      if (line.startsWith("#"))
        return line.replace(/URI="([^"]+)"/g, (_m, u: string) => {
          const r = resource(new URL(u, url).href, s);
          if (!r)
            throw new PublicError(
              "The stream uses an unapproved media origin.",
              403,
            );
          return `URI="${r}"`;
        });
      if (!line.trim()) return line;
      const r = resource(new URL(line.trim(), url).href, s);
      if (!r)
        throw new PublicError(
          "The stream uses an unapproved media origin.",
          403,
        );
      return r;
    })
    .join("\n");
}
export function apiError(error: unknown) {
  const e =
    error instanceof PublicError
      ? error
      : error instanceof z.ZodError
        ? new PublicError("Check the form values.")
        : new PublicError("Unable to complete the request. Try again.", 502);
  return Response.json(
    { error: e.message },
    { status: e.status, headers: { "Cache-Control": "no-store" } },
  );
}
