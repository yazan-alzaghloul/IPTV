import "server-only";
import type {
  Catalog,
  Item,
  Kind,
  Details,
  Programme,
  Page,
} from "@rayan/shared-types";
import {
  type Session,
  upstream,
  boundedBody,
  PublicError,
  resource,
} from "./security";
type Obj = Record<string, unknown>;
const obj = (x: unknown): Obj =>
  x && typeof x === "object" && !Array.isArray(x) ? (x as Obj) : {};
const arr = (x: unknown): Obj[] =>
  Array.isArray(x) ? x.filter((v) => v && typeof v === "object").map(obj) : [];
const text = (o: Obj, k: string, f = "") =>
  o[k] === null || o[k] === undefined || o[k] === "null" ? f : String(o[k]);
const first = (x: unknown) =>
  Array.isArray(x) && typeof x[0] === "string" ? x[0] : "";
const apiKind = (k: Kind) => (k === "movie" ? "vod" : k);
export async function request(
  s: Session,
  action?: string,
  params: Record<string, string> = {},
) {
  const u = new URL(`${s.base}/player_api.php`);
  u.search = new URLSearchParams({
    username: s.username,
    password: s.password,
    ...(action ? { action } : {}),
    ...params,
  }).toString();
  const r = await upstream(u.href, s.base);
  if (!r.ok) {
    await r.body?.cancel();
    throw new PublicError(
      r.status === 401 || r.status === 403
        ? "Access denied. Check the subscription."
        : `Provider returned HTTP ${r.status}.`,
      502,
    );
  }
  try {
    return JSON.parse((await boundedBody(r)).toString()) as unknown;
  } catch (e) {
    if (e instanceof PublicError) throw e;
    throw new PublicError("The provider returned an unreadable response.", 502);
  }
}
export async function validate(s: Session) {
  const u = obj(obj(await request(s)).user_info);
  if (text(u, "auth") !== "1")
    throw new PublicError(
      "Login failed. Check your username and password.",
      401,
    );
  if (text(u, "status").toLowerCase() !== "active")
    throw new PublicError("Your subscription is not active.", 403);
  const expiry = Number(u.exp_date);
  if (expiry > 0 && expiry * 1000 <= Date.now())
    throw new PublicError("Your subscription has expired.", 403);
}
export function parseItem(o: Obj, kind: Kind, s?: Session): Item {
  const image = text(o, "stream_icon", text(o, "cover")),
    backdrop = first(o.backdrop_path);
  return {
    id: text(o, kind === "series" ? "series_id" : "stream_id"),
    name: text(o, "name", "Untitled"),
    kind,
    category: text(o, "category_id"),
    image: s && image ? resource(image, s) : image,
    backdrop: s && backdrop ? resource(backdrop, s) : backdrop,
    container: extension(text(o, "container_extension", "mp4")),
    added: Number(o.added ?? o.last_modified) || 0,
    rating: Number(o.rating) || 0,
    year: text(o, "year", text(o, "releaseDate").slice(0, 4)),
    genre: text(o, "genre"),
    language: text(o, "language"),
    overview: text(o, "plot", text(o, "description")),
    releaseDate: text(o, "releaseDate", text(o, "releasedate")),
  };
}
export const extension = (s: string) =>
  /^[a-z0-9]{1,8}$/i.test(s) ? s.toLowerCase() : "mp4";
const cache = new Map<
  string,
  { expires: number; value: Catalog; size: number }
>();
let cacheBytes = 0;
const MAX = 80 * 1024 * 1024;
export function clearCache(scope: string) {
  for (const [k, v] of cache)
    if (k.startsWith(scope + ":")) {
      cacheBytes -= v.size;
      cache.delete(k);
    }
}
export async function catalog(
  s: Session,
  k: Kind,
  refresh = false,
): Promise<Catalog> {
  const key = s.scope + ":" + k,
    c = cache.get(key);
  if (c && c.expires > Date.now() && !refresh) return c.value;
  const [raw, cat] = await Promise.all([
    request(s, k === "series" ? "get_series" : `get_${apiKind(k)}_streams`),
    request(s, `get_${apiKind(k)}_categories`),
  ]);
  if (!Array.isArray(raw) || !Array.isArray(cat))
    throw new PublicError("The provider could not load this catalog.", 502);
  const seen = new Set<string>(),
    items = arr(raw)
      .map((o) => parseItem(o, k, s))
      .filter((i) => i.id && !seen.has(i.id) && !!seen.add(i.id));
  const categories = arr(cat).map((o) => ({
    id: text(o, "category_id"),
    name: text(o, "category_name", "Other"),
  }));
  const value = { items, categories },
    size = Buffer.byteLength(JSON.stringify(value));
  if (c) {
    cacheBytes -= c.size;
    cache.delete(key);
  }
  while (cache.size && (cacheBytes + size > MAX || cache.size >= 24)) {
    const first = cache.keys().next().value!;
    cacheBytes -= cache.get(first)!.size;
    cache.delete(first);
  }
  if (size <= MAX) {
    cache.set(key, { value, size, expires: Date.now() + 120000 });
    cacheBytes += size;
  }
  return value;
}
export function page(c: Catalog, p: URLSearchParams): Page {
  const q = (p.get("q") ?? "").trim().toLocaleLowerCase(),
    category = p.get("category"),
    genre = p.get("genre"),
    year = p.get("year"),
    language = p.get("language"),
    rating = Number(p.get("rating") ?? 0);
  let items = c.items.filter(
    (i) =>
      (!q || i.name.toLocaleLowerCase().includes(q)) &&
      (!category || i.category === category) &&
      (!genre ||
        i.genre
          .split(/[,/|]/)
          .map((g) => g.trim())
          .includes(genre)) &&
      (!year || i.year === year) &&
      (!language || i.language === language) &&
      i.rating >= rating,
  );
  switch (p.get("sort")) {
    case "title":
      items.sort((a, b) => a.name.localeCompare(b.name));
      break;
    case "added":
      items.sort((a, b) => b.added - a.added);
      break;
    case "rating":
      items.sort((a, b) => b.rating - a.rating);
      break;
    case "release":
      items.sort(
        (a, b) =>
          b.releaseDate.localeCompare(a.releaseDate) ||
          b.year.localeCompare(a.year),
      );
  }
  const total = items.length,
    index = Math.max(0, Math.min(1000000, Number(p.get("page")) || 0));
  items = items.slice(index * 48, (index + 1) * 48);
  const unique = (f: (i: Item) => string[]) =>
    [...new Set(c.items.flatMap(f).filter(Boolean))].sort();
  return {
    items,
    total,
    categories: c.categories,
    genres: unique((i) => i.genre.split(/[,/|]/).map((g) => g.trim())),
    years: unique((i) => [i.year]).reverse(),
    languages: unique((i) => [i.language]),
  };
}
export function safeTrailer(raw: string) {
  if (/^[\w-]{11}$/.test(raw)) return `https://www.youtube.com/watch?v=${raw}`;
  try {
    const u = new URL(raw);
    return ["http:", "https:"].includes(u.protocol) &&
      !u.username &&
      !u.password
      ? u.href
      : "";
  } catch {
    return "";
  }
}
export async function find(s: Session, kind: Kind, id: string) {
  const i = (await catalog(s, kind)).items.find((i) => i.id === id);
  if (!i)
    throw new PublicError(
      "Title is no longer available. Refresh the catalog.",
      404,
    );
  return i;
}
export async function details(s: Session, i: Item): Promise<Details> {
  const raw = obj(
      await request(
        s,
        i.kind === "series" ? "get_series_info" : "get_vod_info",
        { [i.kind === "series" ? "series_id" : "vod_id"]: i.id },
      ),
    ),
    o = obj(raw.info);
  if (text(obj(raw.user_info), "auth") === "0")
    throw new PublicError("Provider access was denied.", 403);
  const episodes = Object.entries(obj(raw.episodes))
    .flatMap(([season, list]) =>
      arr(list).map((e) => {
        const n = obj(e.info),
          image = text(n, "movie_image");
        return {
          ...i,
          id: text(e, "id"),
          name: text(e, "title", `Episode ${text(e, "episode_num")}`),
          image: image ? resource(image, s) : i.image,
          container: extension(text(e, "container_extension", "mp4")),
          parent: i.id,
          parentName: i.name,
          season: Number(e.season ?? season) || 0,
          episode: Number(e.episode_num) || 0,
          overview: text(n, "plot", text(n, "description")),
          duration: Number(n.duration_secs) || undefined,
        };
      }),
    )
    .filter(
      (e, index, a) => e.id && a.findIndex((v) => v.id === e.id) === index,
    )
    .sort((a, b) => a.season - b.season || a.episode - b.episode);
  const video = obj(o.video);
  return {
    overview: text(o, "plot", text(o, "description", i.overview)),
    cast: text(o, "cast", text(o, "actors")),
    director: text(o, "director"),
    duration: text(o, "duration"),
    rating: text(o, "rating"),
    genre: text(o, "genre", i.genre),
    year: text(
      o,
      "year",
      text(o, "releasedate", text(o, "releaseDate", i.year)).slice(0, 4),
    ),
    language: text(o, "language"),
    subtitles: text(o, "subtitles"),
    classification: text(o, "mpaa", text(o, "age")),
    technical: [
      text(video, "codec_name"),
      Number(video.width) > 0 ? `${video.width} × ${video.height}` : "",
    ]
      .filter(Boolean)
      .join(" · "),
    trailer: safeTrailer(text(o, "youtube_trailer", text(o, "trailer"))),
    image: resource(text(o, "movie_image", text(o, "cover")), s) || i.image,
    backdrop: resource(first(o.backdrop_path), s) || i.backdrop,
    container: extension(
      text(obj(raw.movie_data), "container_extension", i.container),
    ),
    episodes,
  };
}
function decoded(v: string) {
  try {
    const s = Buffer.from(v, "base64").toString("utf8");
    return Buffer.from(s).toString("base64").replace(/=+$/, "") ===
      v.replace(/=+$/, "") && !s.includes("\ufffd")
      ? s
      : v;
  } catch {
    return v;
  }
}
export async function guide(s: Session, id: string): Promise<Programme[]> {
  const r = obj(
    await request(s, "get_short_epg", { stream_id: id, limit: "8" }),
  );
  return arr(r.epg_listings)
    .map((o) => ({
      title: decoded(text(o, "title", "Untitled")),
      overview: decoded(text(o, "description")),
      start: Number(o.start_timestamp) * 1000,
      end: Number(o.stop_timestamp ?? o.end_timestamp) * 1000,
    }))
    .filter((p) => Number.isFinite(p.start) && p.end > p.start)
    .sort((a, b) => a.start - b.start);
}
export function streamURL(s: Session, i: Item, container?: string) {
  return `${s.base}/${i.kind === "live" ? "live" : i.kind === "movie" ? "movie" : "series"}/${encodeURIComponent(s.username)}/${encodeURIComponent(s.password)}/${encodeURIComponent(i.id)}.${i.kind === "live" ? "m3u8" : extension(container ?? i.container)}`;
}
