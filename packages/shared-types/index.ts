export type Kind = "live" | "movie" | "series";
export interface Item {
  id: string;
  name: string;
  kind: Kind;
  category: string;
  image: string;
  backdrop: string;
  container: string;
  added: number;
  rating: number;
  year: string;
  genre: string;
  language: string;
  overview: string;
  releaseDate: string;
  parent?: string;
  parentName?: string;
  season?: number;
  episode?: number;
}
export interface Category {
  id: string;
  name: string;
}
export interface Episode extends Item {
  duration?: number;
}
export interface Details {
  overview: string;
  cast: string;
  director: string;
  duration: string;
  rating: string;
  genre: string;
  year: string;
  language: string;
  subtitles: string;
  classification: string;
  technical: string;
  trailer: string;
  image: string;
  backdrop: string;
  container: string;
  episodes: Episode[];
}
export interface Programme {
  title: string;
  overview: string;
  start: number;
  end: number;
}
export interface Catalog {
  items: Item[];
  categories: Category[];
}
export interface Page {
  items: Item[];
  total: number;
  categories: Category[];
  genres: string[];
  years: string[];
  languages: string[];
}
export interface Entry {
  item: Item;
  position: number;
  duration: number;
  updated: number;
}
export interface Library {
  favorites: Record<string, Item>;
  history: Entry[];
  positions: Record<string, { position: number; duration: number }>;
  watched: string[];
  seasons: Record<string, number>;
  searches: string[];
}
export interface Appearance {
  theme: "dark" | "light";
  accent: "violet" | "cyan";
  fontScale: number;
  highContrast: boolean;
  motion: boolean;
  heroRotation: boolean;
  landing: "HOME" | "LIVE" | "MOVIES" | "SERIES";
  hiddenRows: string[];
}
export const keyOf = (i: Pick<Item, "kind" | "id">) =>
  `${i.kind.toUpperCase()}:${i.id}`;
export const emptyLibrary = (): Library => ({
  favorites: {},
  history: [],
  positions: {},
  watched: [],
  seasons: {},
  searches: [],
});
export function continuation(
  episodes: Item[],
  library: Library,
  parent: string,
) {
  const recent = library.history.find((e) => e.item.parent === parent);
  if (recent && !library.watched.includes(keyOf(recent.item)))
    return (
      episodes.find((e) => e.id === recent.item.id) ??
      episodes.find((e) => !library.watched.includes(keyOf(e)))
    );
  if (recent) {
    const at = episodes.findIndex((e) => e.id === recent.item.id);
    return (
      episodes.slice(at + 1).find((e) => !library.watched.includes(keyOf(e))) ??
      episodes.find((e) => !library.watched.includes(keyOf(e)))
    );
  }
  return episodes.find((e) => !library.watched.includes(keyOf(e)));
}
export function recordProgress(
  library: Library,
  item: Item,
  position: number,
  duration: number,
  threshold = 95,
): Library {
  if (
    !Number.isFinite(position) ||
    !Number.isFinite(duration) ||
    position < 0 ||
    duration < 0
  )
    return library;
  const key = keyOf(item),
    live = item.kind === "live",
    complete =
      !live &&
      duration > 0 &&
      position / duration >= Math.min(100, Math.max(80, threshold)) / 100;
  return {
    ...library,
    history: [
      { item, position: live ? 0 : position, duration, updated: Date.now() },
      ...library.history.filter((e) => keyOf(e.item) !== key),
    ].slice(0, 100),
    positions: {
      ...library.positions,
      [key]: { position: complete || live ? 0 : position, duration },
    },
    watched: complete
      ? [...new Set([...library.watched, key])]
      : library.watched,
  };
}
export function recommendations(items: Item[], library: Library): Item[] {
  const known = [
      ...Object.values(library.favorites),
      ...library.history.slice(0, 30).map((e) => e.item),
    ],
    keys = new Set(known.map(keyOf));
  const scored = items
    .filter((i) => !keys.has(keyOf(i)))
    .map((i) => ({
      i,
      score: known.reduce(
        (n, k) =>
          n +
          (k.category && k.category === i.category ? 1 : 0) +
          (i.genre &&
          k.genre &&
          i.genre
            .split(/[,/|]/)
            .some((g) => k.genre.toLowerCase().includes(g.trim().toLowerCase()))
            ? 2
            : 0),
        0,
      ),
    }));
  return scored
    .filter((s) => s.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, 12)
    .map((s) => s.i);
}
