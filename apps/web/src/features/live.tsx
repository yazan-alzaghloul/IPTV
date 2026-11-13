"use client";
import { useRef, useState, useEffect } from "react";
import Link from "next/link";
import dynamic from "next/dynamic";
import { useQuery, keepPreviousData } from "@tanstack/react-query";
import { useVirtualizer } from "@tanstack/react-virtual";
import { Radio, Play, Heart, Maximize } from "lucide-react";
import {
  keyOf,
  type Item,
  type Page,
  type Programme,
} from "@rayan/shared-types";
import { api } from "@/services/api";
import { useLibrary } from "@/stores/library";
import { useDebounce } from "@/hooks/use-debounce";
import { useText } from "@/hooks/use-text";
import { Artwork, watchLink } from "@/components/item-card";
import { Button } from "@/components/ui/button";
import { Loading, Failure } from "@/components/status";
const Player = dynamic(() => import("./player").then((m) => m.Player), {
  ssr: false,
});
export function Live({ initial }: { initial?: string }) {
  const t = useText(),
    [search, setSearch] = useState(""),
    [category, setCategory] = useState(""),
    [page, setPage] = useState(0),
    [selected, setSelected] = useState<Item | null>(null),
    [preview, setPreview] = useState(false),
    [tick, setTick] = useState(Date.now()),
    q = useDebounce(search),
    parent = useRef<HTMLDivElement>(null),
    l = useLibrary((s) => s.library);
  const channels = useQuery({
    queryKey: ["live", q, category, page],
    queryFn: () =>
      api<Page>(
        `catalog?kind=live&q=${encodeURIComponent(q)}&category=${encodeURIComponent(category)}&page=${page}`,
      ),
    placeholderData: keepPreviousData,
  });
  useEffect(() => {
    setPage(0);
  }, [q, category]);
  const initialChannel = useQuery({
    queryKey: ["item", "live", initial],
    queryFn: () =>
      api<Item>(`item?kind=live&id=${encodeURIComponent(initial!)}`),
    enabled: !!initial,
  });
  useEffect(() => {
    if (!channels.data || (initial && initialChannel.isPending)) return;
    const i =
      initialChannel.data ??
      channels.data.items.find((i) => i.id === initial) ??
      channels.data.items[0];
    if (!selected && i) setSelected(i);
  }, [
    channels.data,
    initial,
    selected,
    initialChannel.data,
    initialChannel.isPending,
  ]);
  useEffect(() => {
    const id = setInterval(() => setTick(Date.now()), 30000);
    return () => clearInterval(id);
  }, []);
  const guide = useQuery({
    queryKey: ["guide", selected?.id],
    queryFn: () =>
      api<Programme[]>(`guide?id=${encodeURIComponent(selected!.id)}`),
    enabled: !!selected,
    staleTime: 120000,
    refetchInterval: 120000,
    gcTime: 120000,
  });
  const stream = useQuery({
    queryKey: ["preview", selected?.id],
    queryFn: () =>
      api<{ url: string; format: string; item: Item }>(
        `stream?kind=live&id=${encodeURIComponent(selected!.id)}`,
      ),
    enabled: preview && !!selected,
    staleTime: 60000,
  });
  const v = useVirtualizer({
    count: channels.data?.items.length ?? 0,
    getScrollElement: () => parent.current,
    estimateSize: () => 72,
    overscan: 5,
  });
  const last = l.history.find((e) => e.item.kind === "live")?.item;
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="section-kicker">THE WORLD, LIVE</span>
          <h1>{t("Live TV")}</h1>
          <p>Find your channel. Stay in the moment.</p>
        </div>
        {last && (
          <Button variant="secondary" asChild>
            <Link href={watchLink(last)}>Last channel: {last.name}</Link>
          </Button>
        )}
      </div>
      <div className="filters">
        <input
          aria-label="Search channels"
          placeholder="Search channels…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <select
          aria-label="Channel category"
          value={category}
          onChange={(e) => setCategory(e.target.value)}
        >
          <option value="">{t("All categories")}</option>
          {channels.data?.categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </div>
      {channels.isPending ? (
        <Loading />
      ) : channels.error ? (
        <Failure error={channels.error} retry={() => channels.refetch()} />
      ) : (
        <div className="live-layout">
          <section>
            <div className="channel-list" ref={parent} aria-label="Channels">
              <div style={{ height: v.getTotalSize(), position: "relative" }}>
                {v.getVirtualItems().map((row) => {
                  const i = channels.data!.items[row.index];
                  return (
                    <button
                      key={i.id}
                      className="channel-row virtual-row"
                      style={{ transform: `translateY(${row.start}px)` }}
                      aria-pressed={selected?.id === i.id}
                      onClick={() => {
                        setPreview(false);
                        setSelected(i);
                      }}
                    >
                      <Artwork src={i.image} />
                      <span>
                        {i.name}
                        <small>
                          {channels.data?.categories.find(
                            (c) => c.id === i.category,
                          )?.name || "Live channel"}
                        </small>
                      </span>
                    </button>
                  );
                })}
              </div>
              {!channels.data?.items.length && (
                <p className="notice">No matching channels.</p>
              )}
            </div>
            <div className="pagination">
              <Button
                variant="ghost"
                disabled={!page || channels.isPlaceholderData}
                onClick={() => setPage((p) => p - 1)}
              >
                Previous
              </Button>
              <span>
                {page + 1} /{" "}
                {Math.max(1, Math.ceil((channels.data?.total ?? 0) / 48))}
              </span>
              <Button
                variant="ghost"
                disabled={
                  (page + 1) * 48 >= (channels.data?.total ?? 0) ||
                  channels.isPlaceholderData
                }
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </Button>
            </div>
          </section>
          <section className="live-panel">
            {preview && stream.data ? (
              <Player
                key={selected?.id}
                url={stream.data.url}
                format={stream.data.format}
                item={stream.data.item}
                preview
              />
            ) : (
              <div className="live-preview">
                <Radio />
                <span>{selected?.name ?? "Choose a channel"}</span>
                <Button
                  disabled={!selected || stream.isFetching}
                  onClick={() => setPreview(true)}
                >
                  <Play />
                  {t("Start preview")}
                </Button>
                {stream.error && <p role="alert">{stream.error.message}</p>}
              </div>
            )}
            <div className="live-info">
              {selected && (
                <>
                  <div className="live-title">
                    <h2>{selected.name}</h2>
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label="Favorite channel"
                      aria-pressed={!!l.favorites[keyOf(selected)]}
                      onClick={() => useLibrary.getState().favorite(selected)}
                    >
                      <Heart
                        fill={
                          l.favorites[keyOf(selected)] ? "currentColor" : "none"
                        }
                      />
                    </Button>
                  </div>
                  <div className="hero-actions">
                    <Button asChild>
                      <Link
                        href={watchLink(selected)}
                        onClick={() => setPreview(false)}
                      >
                        <Maximize />
                        {t("Watch fullscreen")}
                      </Link>
                    </Button>
                    {preview && (
                      <Button
                        variant="secondary"
                        onClick={() => setPreview(false)}
                      >
                        Stop preview
                      </Button>
                    )}
                  </div>
                  <div className="guide">
                    <h3>{t("Program guide")}</h3>
                    {guide.isPending ? (
                      <p className="muted">Loading guide…</p>
                    ) : guide.error ? (
                      <Failure
                        error={guide.error}
                        retry={() => guide.refetch()}
                      />
                    ) : !guide.data?.length ? (
                      <p className="muted">{t("No guide available")}</p>
                    ) : (
                      guide.data.map((p, index) => {
                        const current = p.start <= tick && p.end > tick;
                        return (
                          <article
                            className={`programme ${current ? "current" : ""}`}
                            key={`${p.start}:${index}`}
                          >
                            <small>
                              {current
                                ? t("Now")
                                : new Date(p.start).toLocaleTimeString([], {
                                    hour: "2-digit",
                                    minute: "2-digit",
                                  })}{" "}
                              ·{" "}
                              {new Date(p.end).toLocaleTimeString([], {
                                hour: "2-digit",
                                minute: "2-digit",
                              })}
                            </small>
                            <h3>{p.title}</h3>
                            <p>{p.overview}</p>
                            {current && (
                              <div
                                className="timeline"
                                role="progressbar"
                                aria-label="Current programme progress"
                                aria-valuenow={Math.round(
                                  ((tick - p.start) / (p.end - p.start)) * 100,
                                )}
                                aria-valuemin={0}
                                aria-valuemax={100}
                              >
                                <span
                                  style={{
                                    width: `${Math.min(100, ((tick - p.start) / (p.end - p.start)) * 100)}%`,
                                  }}
                                />
                              </div>
                            )}
                          </article>
                        );
                      })
                    )}
                  </div>
                </>
              )}
            </div>
          </section>
        </div>
      )}
    </>
  );
}
