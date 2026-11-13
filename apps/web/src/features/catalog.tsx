"use client";
import { useState, useRef, useEffect } from "react";
import { useQuery, keepPreviousData } from "@tanstack/react-query";
import { useVirtualizer } from "@tanstack/react-virtual";
import {
  Search,
  Grid2X2,
  List,
  ChevronLeft,
  ChevronRight,
  RefreshCw,
  Mic,
} from "lucide-react";
import type { Kind, Item, Page } from "@rayan/shared-types";
import { api } from "@/services/api";
import { useDebounce } from "@/hooks/use-debounce";
import { useText } from "@/hooks/use-text";
import { useLibrary } from "@/stores/library";
import { ItemCard } from "@/components/item-card";
import { Loading, Failure, Empty } from "@/components/status";
import { Button } from "@/components/ui/button";
export function VirtualCatalog({
  items,
  list = false,
}: {
  items: Item[];
  list?: boolean;
}) {
  const parent = useRef<HTMLDivElement>(null),
    [columns, setColumns] = useState(2);
  useEffect(() => {
    const e = parent.current;
    if (!e) return;
    const o = new ResizeObserver(([entry]) => {
      const w = entry.contentRect.width;
      setColumns(
        list ? (w < 550 ? 1 : 2) : w > 1500 ? 8 : w > 900 ? 6 : w > 550 ? 4 : 2,
      );
    });
    o.observe(e);
    return () => o.disconnect();
  }, [list, items.length]);
  const rows = Math.ceil(items.length / columns);
  const v = useVirtualizer({
    count: rows,
    getScrollElement: () => parent.current,
    estimateSize: () =>
      list
        ? 140
        : (((parent.current?.clientWidth ?? 600) - (columns - 1) * 16) /
            columns) *
            1.5 +
          80,
    overscan: 2,
  });
  if (items.length <= 24)
    return (
      <div className={`catalog-grid ${list ? "list" : ""}`}>
        {items.map((i) => (
          <ItemCard item={i} key={`${i.kind}:${i.id}`} />
        ))}
      </div>
    );
  return (
    <div
      ref={parent}
      style={{ height: "70vh", overflow: "auto" }}
      aria-label="Catalog results"
    >
      <div style={{ height: v.getTotalSize(), position: "relative" }}>
        {v.getVirtualItems().map((row) => (
          <div
            key={row.key}
            ref={v.measureElement}
            data-index={row.index}
            className={`virtual-row catalog-grid ${list ? "list" : ""}`}
            style={{
              transform: `translateY(${row.start}px)`,
              gridTemplateColumns: `repeat(${columns},minmax(0,1fr))`,
              paddingBottom: 24,
            }}
          >
            {items
              .slice(row.index * columns, (row.index + 1) * columns)
              .map((i) => (
                <ItemCard key={i.id} item={i} />
              ))}
          </div>
        ))}
      </div>
    </div>
  );
}
type SpeechResult = {
  results: { [n: number]: { [n: number]: { transcript: string } } };
};
type BrowserSpeech = {
  lang: string;
  start: () => void;
  abort: () => void;
  onresult: ((e: SpeechResult) => void) | null;
  onerror: (() => void) | null;
  onend: (() => void) | null;
};
const getSpeech = () => {
  const w = window as unknown as {
    SpeechRecognition?: new () => BrowserSpeech;
    webkitSpeechRecognition?: new () => BrowserSpeech;
  };
  return w.SpeechRecognition ?? w.webkitSpeechRecognition;
};
export function VoiceSearch({ onResult }: { onResult: (s: string) => void }) {
  const [supported, setSupported] = useState(false),
    [status, setStatus] = useState("");
  const recognition = useRef<{ abort: () => void } | null>(null);
  useEffect(() => {
    setSupported(!!getSpeech());
    return () => recognition.current?.abort();
  }, []);
  return (
    <>
      <Button
        variant="ghost"
        size="icon"
        disabled={!supported}
        title={
          supported
            ? "Voice search (browser speech service)"
            : "Voice search unavailable; type your search"
        }
        aria-label="Voice search"
        onClick={() => {
          const R = getSpeech();
          if (!R) return;
          recognition.current?.abort();
          const r = new R();
          recognition.current = r;
          r.lang = document.documentElement.lang === "ar" ? "ar-SA" : "en-US";
          r.onresult = (e) => onResult(e.results[0][0].transcript);
          r.onerror = () =>
            setStatus("Microphone unavailable. Type your search.");
          r.onend = () => setStatus("");
          setStatus("Listening…");
          try {
            r.start();
          } catch {
            setStatus("Voice search unavailable.");
          }
        }}
      >
        <Mic size={17} />
      </Button>
      <span className="sr-only" role="status">
        {status}
      </span>
    </>
  );
}
type SavedFilters = {
  query: string;
  category: string;
  genre: string;
  year: string;
  language: string;
  rating: string;
  sort: string;
  index: number;
  list: boolean;
  searchKind: Kind;
};
const browserState = new Map<string, SavedFilters>();
export function Catalog({
  kind,
  universal = false,
}: {
  kind: Kind;
  universal?: boolean;
}) {
  const scope = useLibrary((s) => s.scope),
    stateKey = scope + ":" + (universal ? "search" : kind),
    saved = browserState.get(stateKey);
  const t = useText(),
    [query, setQuery] = useState(saved?.query ?? ""),
    [category, setCategory] = useState(saved?.category ?? ""),
    [genre, setGenre] = useState(saved?.genre ?? ""),
    [year, setYear] = useState(saved?.year ?? ""),
    [language, setLanguage] = useState(saved?.language ?? ""),
    [rating, setRating] = useState(saved?.rating ?? ""),
    [sort, setSort] = useState(saved?.sort ?? "provider"),
    [index, setIndex] = useState(saved?.index ?? 0),
    [list, setList] = useState(saved?.list ?? false),
    [searchKind, setKind] = useState<Kind>(saved?.searchKind ?? kind);
  const current = universal ? searchKind : kind,
    q = useDebounce(query);
  useEffect(() => {
    browserState.set(stateKey, {
      query,
      category,
      genre,
      year,
      language,
      rating,
      sort,
      index,
      list,
      searchKind,
    });
    if (browserState.size > 24)
      browserState.delete(browserState.keys().next().value!);
  }, [
    stateKey,
    query,
    category,
    genre,
    year,
    language,
    rating,
    sort,
    index,
    list,
    searchKind,
  ]);
  const params = new URLSearchParams({
    kind: current,
    q,
    category,
    genre,
    year,
    language,
    rating,
    sort,
    page: String(index),
  });
  const data = useQuery({
    queryKey: ["catalog", params.toString()],
    queryFn: () => api<Page>(`catalog?${params}`),
    placeholderData: keepPreviousData,
  });
  useEffect(() => {
    setIndex(0);
  }, [q, category, genre, year, language, rating, sort, current]);
  const searches = useLibrary((s) => s.library.searches);
  useEffect(() => {
    if (universal && q.trim()) useLibrary.getState().search(q);
  }, [q, universal]);
  const title = universal
    ? "Search"
    : kind === "movie"
      ? "Movies"
      : kind === "series"
        ? "Series"
        : "Live TV";
  const select = (
    label: string,
    value: string,
    set: (s: string) => void,
    options: { id: string; name: string }[],
  ) => (
    <select
      aria-label={label}
      value={value}
      onChange={(e) => set(e.target.value)}
    >
      <option value="">{t(label)}</option>
      {options.map((o) => (
        <option key={o.id} value={o.id}>
          {o.name}
        </option>
      ))}
    </select>
  );
  const options = (values: string[] = []) =>
    values.map((s) => ({ id: s, name: s }));
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="section-kicker">EXPLORE YOUR LIBRARY</span>
          <h1>{t(title)}</h1>
          <p>
            {data.data
              ? `${data.data.total.toLocaleString()} titles from your provider`
              : "Find your next great watch."}
          </p>
        </div>
        <Button
          variant="ghost"
          size="icon"
          aria-label="Refresh catalog"
          onClick={async () => {
            await api("refresh", { method: "POST" });
            await data.refetch();
          }}
        >
          <RefreshCw size={18} />
        </Button>
      </div>
      <div className="filters">
        <div className="search-wrap">
          <Search size={16} />
          <input
            aria-label="Search titles"
            placeholder="Search titles…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        <VoiceSearch onResult={setQuery} />
        {universal && (
          <select
            aria-label="Content type"
            value={current}
            onChange={(e) => {
              setKind(e.target.value as Kind);
              setCategory("");
            }}
          >
            <option value="movie">{t("Movies")}</option>
            <option value="series">{t("Series")}</option>
            <option value="live">{t("Live TV")}</option>
          </select>
        )}
        {select(
          "All categories",
          category,
          setCategory,
          data.data?.categories ?? [],
        )}
        <div className="filter-secondary">
          {select("All genres", genre, setGenre, options(data.data?.genres))}
          {select("All years", year, setYear, options(data.data?.years))}
          {select(
            "All languages",
            language,
            setLanguage,
            options(data.data?.languages),
          )}
          <select
            aria-label="Minimum rating"
            value={rating}
            onChange={(e) => setRating(e.target.value)}
          >
            <option value="">Any rating</option>
            {[5, 6, 7, 8, 9].map((n) => (
              <option key={n} value={n}>
                {n}+ rating
              </option>
            ))}
          </select>
          <select
            aria-label="Sort titles"
            value={sort}
            onChange={(e) => setSort(e.target.value)}
          >
            {[
              ["provider", "Provider order"],
              ["title", "Title"],
              ["added", "Recently added"],
              ["release", "Release date"],
              ["rating", "Rating"],
            ].map(([v, l]) => (
              <option key={v} value={v}>
                {t(l)}
              </option>
            ))}
          </select>
          <div className="layout-switch">
            <Button
              variant="ghost"
              size="icon"
              aria-label="Grid view"
              aria-pressed={!list}
              onClick={() => setList(false)}
            >
              <Grid2X2 />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              aria-label="List view"
              aria-pressed={list}
              onClick={() => setList(true)}
            >
              <List />
            </Button>
          </div>
        </div>
      </div>
      {universal && searches.length > 0 && (
        <div className="search-chips" aria-label="Recent searches">
          {searches.map((s) => (
            <button key={s} onClick={() => setQuery(s)}>
              {s}
            </button>
          ))}
        </div>
      )}
      {data.isPending ? (
        <Loading />
      ) : data.error ? (
        <Failure error={data.error} retry={() => data.refetch()} />
      ) : !data.data?.items.length ? (
        <Empty />
      ) : (
        <div aria-busy={data.isFetching}>
          <VirtualCatalog items={data.data.items} list={list} />
          <div className="pagination">
            <Button
              variant="secondary"
              disabled={index === 0 || data.isPlaceholderData}
              onClick={() => setIndex((i) => i - 1)}
            >
              <ChevronLeft />
              {t("Previous")}
            </Button>
            <span>
              {index + 1} / {Math.max(1, Math.ceil(data.data.total / 48))}
            </span>
            <Button
              variant="secondary"
              disabled={
                (index + 1) * 48 >= data.data.total || data.isPlaceholderData
              }
              onClick={() => setIndex((i) => i + 1)}
            >
              {t("Next")}
              <ChevronRight />
            </Button>
          </div>
        </div>
      )}
    </>
  );
}
