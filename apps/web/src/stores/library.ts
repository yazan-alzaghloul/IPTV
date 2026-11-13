"use client";
import { create } from "zustand";
import {
  emptyLibrary,
  keyOf,
  recordProgress,
  type Library,
  type Item,
} from "@rayan/shared-types";
import { loadLibrary, saveLibrary } from "@/services/persistence";
interface State {
  scope: string;
  ready: boolean;
  error: string;
  library: Library;
  hydrate: (s: string) => Promise<void>;
  logout: () => Promise<void>;
  favorite: (i: Item) => void;
  watch: (items: Item[], watched: boolean) => void;
  progress: (i: Item, p: number, d: number, t?: number) => void;
  remove: (i: Item) => void;
  clear: () => void;
  season: (id: string, n: number) => void;
  search: (q: string) => void;
}
let writes = Promise.resolve();
function persist(scope: string, library: Library | null) {
  writes = writes.catch(() => {}).then(() => saveLibrary(scope, library));
  writes.catch(() =>
    useLibrary.setState({
      error: "Changes could not be saved. Check browser storage permissions.",
    }),
  );
  return writes;
}
const change = (fn: (l: Library) => Library) => {
  const s = useLibrary.getState();
  if (!s.ready) return;
  const library = fn(s.library);
  useLibrary.setState({ library });
  void persist(s.scope, library);
};
export const useLibrary = create<State>((set, get) => ({
  scope: "",
  ready: false,
  error: "",
  library: emptyLibrary(),
  async hydrate(scope) {
    if (get().scope === scope && get().ready) return;
    set({ scope, ready: false, library: emptyLibrary(), error: "" });
    try {
      const library = await loadLibrary(scope);
      if (get().scope === scope) set({ library, ready: true });
    } catch {
      if (get().scope === scope)
        set({
          ready: true,
          error:
            "Browser storage is unavailable; this session will not persist.",
        });
    }
  },
  async logout() {
    const scope = get().scope;
    set({ scope: "", ready: false, library: emptyLibrary() });
    if (scope) await persist(scope, null);
  },
  favorite(i) {
    change((l) => {
      const favorites = { ...l.favorites },
        k = keyOf(i);
      if (favorites[k]) delete favorites[k];
      else favorites[k] = i;
      return { ...l, favorites };
    });
  },
  watch(items, watched) {
    change((l) => {
      const keys = items.map(keyOf),
        positions = { ...l.positions };
      keys.forEach((k) => delete positions[k]);
      return {
        ...l,
        positions,
        watched: watched
          ? [...new Set([...l.watched, ...keys])]
          : l.watched.filter((k) => !keys.includes(k)),
      };
    });
  },
  progress(i, p, d, t) {
    change((l) => recordProgress(l, i, p, d, t));
  },
  remove(i) {
    change((l) => {
      const k = keyOf(i),
        positions = { ...l.positions };
      delete positions[k];
      return {
        ...l,
        positions,
        history: l.history.filter((e) => keyOf(e.item) !== k),
      };
    });
  },
  clear() {
    change((l) => ({ ...l, history: [], positions: {} }));
  },
  season(id, n) {
    change((l) => ({ ...l, seasons: { ...l.seasons, [id]: n } }));
  },
  search(q) {
    q = q.trim().slice(0, 120);
    if (q)
      change((l) => ({
        ...l,
        searches: [
          q,
          ...l.searches.filter((s) => s.toLowerCase() !== q.toLowerCase()),
        ].slice(0, 10),
      }));
  },
}));
