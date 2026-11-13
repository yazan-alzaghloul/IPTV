import { beforeEach, describe, it, expect, vi } from "vitest";
vi.mock("next/headers", () => ({ cookies: vi.fn(), headers: vi.fn() }));
import {
  normalize,
  seal,
  unseal,
  checkURL,
  privateIP,
  rewriteHLS,
  resource,
  resourceURL,
  scopeFor,
  type Session,
} from "@/services/server/security";
import {
  parseItem,
  page,
  safeTrailer,
  streamURL,
  extension,
} from "@/services/server/provider";
const credentials = {
  base: "https://provider.example/base",
  username: "a+b/x",
  password: "p?+&",
};
const s: Session = {
  ...credentials,
  scope: "scope-a",
  expires: Date.now() + 86400000,
};
beforeEach(() => {
  process.env.PROVIDER_ORIGINS = "https://provider.example";
  process.env.MEDIA_ORIGINS = "https://cdn.example";
  process.env.SESSION_SECRET = "test-only-32-character-secret-value";
});
describe("session and request security", () => {
  it("normalizes scheme, explicit port and base path", () => {
    expect(
      normalize({
        host: "https://provider.example:8080/base/",
        scheme: "http",
        port: "443",
        username: " a ",
        password: " p ",
      }),
    ).toEqual({
      base: "https://provider.example/base",
      username: "a",
      password: " p ",
    });
  });
  it.each([
    "https://a.example/player_api.php",
    "https://u:p@a.example",
    "https://a.example?password=x",
    "ftp://a.example",
    "https://a.example/list.m3u8",
  ])("rejects unsafe login address %s", (host) => {
    expect(() => normalize({ host, username: "a", password: "b" })).toThrow();
  });
  it.each(["0", "65536", "wrong"])("rejects invalid port %s", (port) =>
    expect(() =>
      normalize({ host: "a.example", port, username: "a", password: "b" }),
    ).toThrow(),
  );
  it("encrypts, authenticates, scopes and purpose-binds secrets", () => {
    const t = seal(credentials, "session");
    expect(t).not.toContain("provider");
    expect(unseal(t, "session")).toEqual(credentials);
    expect(() => unseal(t, "media")).toThrow();
    expect(() => unseal(t.slice(0, -8) + "AAAAAAAA", "session")).toThrow();
    expect(scopeFor(credentials)).toHaveLength(32);
    expect(scopeFor({ ...credentials, password: "different" })).not.toBe(
      scopeFor(credentials),
    );
  });
  it("approves only exact configured origins", () => {
    expect(checkURL("https://cdn.example/a", s.base).origin).toBe(
      "https://cdn.example",
    );
    expect(() =>
      checkURL("https://provider.example.evil.test/a", s.base),
    ).toThrow();
    expect(() => checkURL("http://provider.example", s.base)).toThrow();
    expect(() => checkURL("file:///etc/passwd", s.base)).toThrow();
  });
  it.each([
    "127.0.0.1",
    "10.0.1.2",
    "169.254.169.254",
    "172.31.1.2",
    "192.168.1.1",
    "100.64.0.1",
    "::1",
    "::ffff:127.0.0.1",
    "fd00::1",
    "fe80::1",
  ])("blocks private address %s", (ip) => expect(privateIP(ip)).toBe(true));
  it("permits public addresses", () =>
    expect(privateIP("8.8.8.8")).toBe(false));
  it("rewrites HLS segment, variant, map and key addresses without leaking credentials", () => {
    const h =
      '#EXTM3U\n#EXT-X-MAP:URI="init.mp4"\n#EXT-X-KEY:METHOD=AES-128,URI="key.bin"\n../a+b/seg.m4s\nhttps://cdn.example/variant.m3u8';
    const rewritten = rewriteHLS(
      h,
      "https://provider.example/live/a/password/index.m3u8",
      s,
    );
    expect(rewritten).not.toContain("password");
    expect(rewritten).not.toContain("cdn.example");
    expect(rewritten.match(/\/api\/media\//g)).toHaveLength(4);
    expect(() =>
      rewriteHLS("#EXTM3U\nhttps://evil.example/a", s.base, s),
    ).toThrow();
  });
  it("binds opaque media resources to an unexpired session", () => {
    const r = resource("https://provider.example/a", s);
    expect(resourceURL(r.split("/").at(-1)!, s)).toBe(
      "https://provider.example/a",
    );
    expect(() =>
      resourceURL(r.split("/").at(-1)!, { ...s, scope: "other" }),
    ).toThrow();
    const expired = resource("https://provider.example/a", {
      ...s,
      expires: 1,
    });
    expect(() => resourceURL(expired.split("/").at(-1)!, s)).toThrow();
  });
});
describe("real provider parsing and filtering", () => {
  it("handles heterogeneous fields, missing metadata and container sanitization", () => {
    const i = parseItem(
      {
        stream_id: 1,
        name: "Drama",
        rating: "8.2",
        year: 2026,
        container_extension: "../../x",
        cover: null,
      },
      "movie",
    );
    expect(i.id).toBe("1");
    expect(i.rating).toBe(8.2);
    expect(i.image).toBe("");
    expect(i.container).toBe("mp4");
  });
  it("filters, sorts and pages catalogs deterministically", () => {
    const items = Array.from({ length: 105 }, (_, n) =>
      parseItem(
        {
          stream_id: n + 1,
          name: `Title ${String(n).padStart(3, "0")}`,
          category_id: n % 2 ? "a" : "b",
          rating: n % 10,
          genre: n % 2 ? "Drama" : "Comedy",
          year: "2026",
        },
        "movie",
      ),
    );
    const p = page(
      { items, categories: [] },
      new URLSearchParams({
        category: "a",
        rating: "5",
        sort: "title",
        page: "0",
      }),
    );
    expect(p.total).toBe(30);
    expect(p.items.every((i) => i.category === "a" && i.rating >= 5)).toBe(
      true,
    );
    expect(
      page({ items, categories: [] }, new URLSearchParams({ page: "2" })).items,
    ).toHaveLength(9);
    expect(
      page({ items, categories: [] }, new URLSearchParams({ q: "%_" })).total,
    ).toBe(0);
  });
  it("encodes plus signs and slashes in stream paths", () => {
    const i = parseItem({ stream_id: "1/2", name: "A" }, "movie");
    expect(streamURL(s, i)).toBe(
      "https://provider.example/base/movie/a%2Bb%2Fx/p%3F%2B%26/1%2F2.mp4",
    );
    expect(extension("M3U8")).toBe("m3u8");
  });
  it("rejects unsafe trailer schemes and credentials", () => {
    expect(safeTrailer("javascript:alert(1)")).toBe("");
    expect(safeTrailer("https://u:p@example.com")).toBe("");
    expect(safeTrailer("abcdefghijk")).toBe(
      "https://www.youtube.com/watch?v=abcdefghijk",
    );
  });
});

import sharp from "sharp";
import {
  artworkWidth,
  optimizeArtwork,
  cachedArtwork,
  clearArtwork,
} from "@/services/server/artwork";
import { boundedBody } from "@/services/server/security";
describe("bounded optimized artwork", () => {
  it("resizes real provider images and isolates the account cache", async () => {
    const input = await sharp({
      create: { width: 600, height: 900, channels: 3, background: "#7c3aed" },
    })
      .png()
      .toBuffer();
    const response = await optimizeArtwork(
      new Response(input),
      "account-a",
      "https://provider.example/poster.jpg",
      192,
    );
    expect(response.headers.get("content-type")).toBe("image/webp");
    const metadata = await sharp(
      Buffer.from(await response.arrayBuffer()),
    ).metadata();
    expect(metadata.width).toBe(192);
    expect(metadata.height).toBe(288);
    expect(
      cachedArtwork("account-a", "https://provider.example/poster.jpg", 192),
    ).toBeDefined();
    expect(
      cachedArtwork("account-b", "https://provider.example/poster.jpg", 192),
    ).toBeUndefined();
    clearArtwork("account-a");
    expect(
      cachedArtwork("account-a", "https://provider.example/poster.jpg", 192),
    ).toBeUndefined();
  });
  it("bounds width and response size", async () => {
    expect(artworkWidth("999999")).toBe(1920);
    expect(artworkWidth("-1")).toBe(96);
    expect(artworkWidth("NaN")).toBe(384);
    await expect(
      boundedBody(new Response(new Uint8Array(20)), 10),
    ).rejects.toThrow("safety limit");
  });
});
