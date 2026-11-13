import {
  artworkWidth,
  cachedArtwork,
  optimizeArtwork,
  clearArtwork,
} from "@/services/server/artwork";
import { z } from "zod";
import { type Kind } from "@rayan/shared-types";
import {
  apiError,
  normalize,
  scopeFor,
  saveSession,
  session,
  removeSession,
  sameOrigin,
  resource,
  resourceURL,
  rewriteHLS,
  upstream,
  boundedBody,
  PublicError,
  checkURL,
} from "@/services/server/security";
import {
  validate,
  catalog,
  page,
  details,
  guide,
  find,
  streamURL,
  clearCache,
} from "@/services/server/provider";
export const runtime = "nodejs";
export const dynamic = "force-dynamic";
type Context = { params: Promise<{ path: string[] }> };
const kind = (v: unknown) => z.enum(["live", "movie", "series"]).parse(v);
const rates = new Map<string, { at: number; n: number }>();
export async function POST(r: Request, { params }: Context) {
  try {
    await sameOrigin();
    const [path] = (await params).path;
    if (path === "login") {
      const host =
        process.env.TRUST_PROXY === "true"
          ? (r.headers.get("x-forwarded-for") ?? "shared")
          : "shared";
      if (rates.size > 1000) rates.clear();
      let rate = rates.get(host);
      if (!rate || rate.at < Date.now() - 60000) {
        rate = { at: Date.now(), n: 0 };
        rates.set(host, rate);
      }
      if (++rate.n > 20)
        throw new PublicError("Too many login attempts. Wait a minute.", 429);
      if (Number(r.headers.get("content-length")) > 4096)
        throw new PublicError("Login form is too large.");
      const raw = await r.text();
      if (raw.length > 4096) throw new PublicError("Login form is too large.");
      const c = normalize(JSON.parse(raw));
      checkURL(c.base);
      const s = {
        ...c,
        scope: scopeFor(c),
        expires: Date.now() + 7 * 86400000,
      };
      await validate(s);
      await saveSession(s);
      return Response.json({ scope: s.scope });
    }
    const s = await session();
    if (path === "logout") {
      clearCache(s.scope);
      clearArtwork(s.scope);
      await removeSession();
      return Response.json({ ok: true });
    }
    if (path === "refresh") {
      clearCache(s.scope);
      clearArtwork(s.scope);
      return Response.json({ ok: true });
    }
    throw new PublicError("Unknown request.", 404);
  } catch (e) {
    return apiError(e);
  }
}
export async function GET(r: Request, { params }: Context) {
  try {
    const s = await session(),
      [path, token] = (await params).path,
      u = new URL(r.url);
    const json = (v: unknown) =>
      Response.json(v, { headers: { "Cache-Control": "no-store" } });
    switch (path) {
      case "session":
        await validate(s);
        return json({
          scope: s.scope,
          provider: new URL(s.base).host,
          expires: s.expires,
        });
      case "catalog":
        return json(
          page(
            await catalog(s, kind(u.searchParams.get("kind"))),
            u.searchParams,
          ),
        );
      case "home": {
        const [live, movie, series] = await Promise.all(
          (["live", "movie", "series"] as Kind[]).map((k) => catalog(s, k)),
        );
        return json({
          live: live.items.slice(0, 18),
          movie: [...movie.items]
            .sort((a, b) => b.added - a.added)
            .slice(0, 24),
          series: [...series.items]
            .sort((a, b) => b.added - a.added)
            .slice(0, 24),
        });
      }
      case "item":
        return json(
          await find(
            s,
            kind(u.searchParams.get("kind")),
            u.searchParams.get("id") ?? "",
          ),
        );
      case "neighbors": {
        const c = await catalog(s, kind(u.searchParams.get("kind"))),
          index = c.items.findIndex((i) => i.id === u.searchParams.get("id"));
        if (index < 0) throw new PublicError("Title is unavailable.", 404);
        return json({
          items: c.items.slice(Math.max(0, index - 12), index + 13),
        });
      }
      case "details": {
        const i = await find(
          s,
          kind(u.searchParams.get("kind")),
          u.searchParams.get("id") ?? "",
        );
        return json({ item: i, details: await details(s, i) });
      }
      case "guide": {
        const id = u.searchParams.get("id") ?? "";
        await find(s, "live", id);
        return json(await guide(s, id));
      }
      case "stream": {
        let i = await find(
          s,
          kind(u.searchParams.get("kind")),
          u.searchParams.get("id") ?? "",
        );
        let container = i.container;
        const episode = u.searchParams.get("episode");
        if (i.kind === "series") {
          if (!episode) throw new PublicError("Select an episode.");
          const e = (await details(s, i)).episodes.find(
            (e) => e.id === episode,
          );
          if (!e) throw new PublicError("Episode unavailable.", 404);
          i = e;
        } else if (i.kind === "movie") {
          container = (await details(s, i)).container;
        }
        const ext =
          i.kind === "live"
            ? "m3u8"
            : i.kind === "series"
              ? i.container
              : container;
        if (!["mp4", "m4v", "webm", "m3u8", "mov"].includes(ext))
          throw new PublicError(
            `The provider uses ${ext.toUpperCase()}. Ask for a browser-compatible HLS or MP4 stream.`,
            415,
          );
        return json({
          url: resource(streamURL(s, i, ext), s),
          format: ext,
          item: i,
        });
      }
      case "media": {
        if (!token) throw new PublicError("Missing media token.");
        const raw = resourceURL(token, s);
        const width = artworkWidth(u.searchParams.get("width")),
          cached = cachedArtwork(s.scope, raw, width);
        if (cached) return cached;
        const res = await upstream(
          raw,
          s.base,
          r.headers.get("range") ?? undefined,
          r.signal,
        );
        const type =
          res.headers.get("content-type") ?? "application/octet-stream";
        if (!res.ok) {
          await res.body?.cancel();
          throw new PublicError(
            `Provider media returned HTTP ${res.status}.`,
            res.status === 404 ? 404 : 502,
          );
        }
        if (/^image\/(jpeg|png|webp|avif|gif)(;|$)/.test(type))
          return optimizeArtwork(res, s.scope, raw, width);
        if (type.includes("mpegurl") || new URL(raw).pathname.endsWith(".m3u8"))
          return new Response(
            rewriteHLS(
              (await boundedBody(res, 2 * 1024 * 1024)).toString(),
              res.headers.get("x-rayan-upstream") ?? raw,
              s,
            ),
            {
              headers: {
                "Content-Type": "application/vnd.apple.mpegurl",
                "Cache-Control": "no-store",
              },
            },
          );
        if (
          !/^(video\/|audio\/|image\/(jpeg|png|webp|avif|gif)|application\/(octet-stream|mp4))/.test(
            type,
          )
        ) {
          await res.body?.cancel();
          throw new PublicError("Unsupported provider media type.", 415);
        }
        const h = new Headers(res.headers);
        h.delete("x-rayan-upstream");
        h.set("Cache-Control", "private, no-store");
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Content-Security-Policy", "default-src 'none'; sandbox");
        return new Response(res.body, { status: res.status, headers: h });
      }
      default:
        throw new PublicError("Unknown request.", 404);
    }
  } catch (e) {
    return apiError(e);
  }
}
