import { describe, it, expect } from "vitest";
import {
  emptyLibrary,
  recordProgress,
  keyOf,
  continuation,
  recommendations,
  type Item,
} from "@rayan/shared-types";
const item = (id = "1", extra: Partial<Item> = {}): Item => ({
  id,
  name: "Movie " + id,
  kind: "movie",
  category: "drama",
  image: "",
  backdrop: "",
  container: "mp4",
  added: 0,
  rating: 8,
  year: "2026",
  genre: "Drama",
  language: "English",
  overview: "",
  releaseDate: "",
  ...extra,
});
describe("account library domain", () => {
  it("stores resume independently of the bounded history", () => {
    let l = recordProgress(emptyLibrary(), item(), 40, 100);
    for (let n = 2; n < 105; n++)
      l = recordProgress(l, item(String(n)), 10, 100);
    expect(l.history).toHaveLength(100);
    expect(l.positions["MOVIE:1"].position).toBe(40);
  });
  it("marks completion and resets resume at threshold", () => {
    const l = recordProgress(emptyLibrary(), item(), 95, 100);
    expect(l.watched).toEqual(["MOVIE:1"]);
    expect(l.positions["MOVIE:1"].position).toBe(0);
  });
  it("clamps completion and ignores invalid positions", () => {
    const l = recordProgress(emptyLibrary(), item(), 79, 100, 1);
    expect(l.watched).toEqual([]);
    expect(recordProgress(l, item(), NaN, 100)).toBe(l);
  });
  it("never resumes live channels", () => {
    const i = item("1", { kind: "live" }),
      l = recordProgress(emptyLibrary(), i, 50, 100);
    expect(l.positions[keyOf(i)].position).toBe(0);
    expect(l.history[0].position).toBe(0);
    expect(l.watched).toHaveLength(0);
  });
  it("keeps exact episode continuation and advances after completion", () => {
    const episodes = [1, 2, 3].map((n) =>
      item(String(n), { kind: "series", parent: "parent", episode: n }),
    );
    let l = recordProgress(emptyLibrary(), episodes[1], 30, 100);
    expect(continuation(episodes, l, "parent")?.id).toBe("2");
    l = recordProgress(l, episodes[1], 100, 100);
    expect(continuation(episodes, l, "parent")?.id).toBe("3");
  });
  it("uses first unwatched episode when no parent history exists", () => {
    const episodes = [item("1"), item("2")],
      l = emptyLibrary();
    l.watched = ["MOVIE:1"];
    expect(continuation(episodes, l, "new")?.id).toBe("2");
  });
  it("recommends only actual matches and excludes known titles", () => {
    const l = emptyLibrary();
    l.favorites["MOVIE:1"] = item();
    expect(
      recommendations(
        [item(), item("2"), item("3", { genre: "Comedy", category: "other" })],
        l,
      ).map((i) => i.id),
    ).toEqual(["2"]);
    expect(recommendations([item()], emptyLibrary())).toEqual([]);
  });
});
