"use client";
import { useRouter } from "next/navigation";
import Link from "next/link";
import dynamic from "next/dynamic";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import type { Item, Kind, Details } from "@rayan/shared-types";
import { api } from "@/services/api";
import { usePreferences } from "@/stores/preferences";
import { watchLink } from "@/components/item-card";
import { Button } from "@/components/ui/button";
import { Loading, Failure } from "@/components/status";
const Player = dynamic(() => import("./player").then((m) => m.Player), {
  ssr: false,
  loading: () => <div className="live-preview">Loading player…</div>,
});
export function Watch({
  kind,
  id,
  episode,
  restart,
}: {
  kind: Kind;
  id: string;
  episode?: string;
  restart: boolean;
}) {
  const router = useRouter(),
    autoplay = usePreferences((s) => s.autoplay);
  const q = useQuery({
    queryKey: ["stream", kind, id, episode],
    queryFn: () =>
      api<{ url: string; format: string; item: Item }>(
        `stream?kind=${kind}&id=${encodeURIComponent(id)}${episode ? `&episode=${encodeURIComponent(episode)}` : ""}`,
      ),
    staleTime: 60000,
  });
  const detail = useQuery({
    queryKey: ["details", kind, id],
    queryFn: () =>
      api<{ item: Item; details: Details }>(
        `details?kind=${kind}&id=${encodeURIComponent(id)}`,
      ),
    enabled: kind === "series",
  });
  const channels = useQuery({
    queryKey: ["neighbors", kind, id],
    queryFn: () =>
      api<{ items: Item[] }>(
        `neighbors?kind=${kind}&id=${encodeURIComponent(id)}`,
      ),
    enabled: kind !== "series",
  });
  if (q.isPending) return <Loading />;
  if (q.error) return <Failure error={q.error} retry={() => q.refetch()} />;
  if (!q.data) return null;
  const { item, url, format } = q.data,
    queue =
      kind === "series"
        ? (detail.data?.details.episodes ?? [])
        : (channels.data?.items ?? []),
    at = queue.findIndex((i) => i.id === item.id),
    next = queue[at + 1],
    previous = queue[at - 1];
  return (
    <>
      <Button variant="ghost" asChild>
        <Link
          href={
            kind === "live"
              ? `/live?channel=${encodeURIComponent(id)}`
              : `/details/${kind}/${encodeURIComponent(id)}`
          }
        >
          <ArrowLeft />
          Back
        </Link>
      </Button>
      <Player
        key={`${kind}:${item.id}`}
        url={url}
        format={format}
        item={item}
        restart={restart}
        onNext={next ? () => router.push(watchLink(next)) : undefined}
        onPrevious={
          previous ? () => router.push(watchLink(previous)) : undefined
        }
        onEnded={() => {
          if (autoplay && next) router.replace(watchLink(next));
        }}
      />
      <div className="page-heading">
        <div>
          <h1>{item.name}</h1>
          <p>
            {item.parentName
              ? `${item.parentName} · Season ${item.season}, Episode ${item.episode}`
              : item.kind === "live"
                ? "Live television"
                : item.genre}
          </p>
        </div>
      </div>
      {queue.length > 1 && (
        <section className="section">
          <h2>
            {kind === "live"
              ? "Channels"
              : kind === "series"
                ? "Episodes"
                : "Up next"}
          </h2>
          <div className="queue">
            {queue.map((i) => (
              <Button
                key={i.id}
                variant={i.id === item.id ? "default" : "secondary"}
                asChild
              >
                <Link href={watchLink(i)}>
                  {i.episode ? `${i.episode}. ` : ""}
                  {i.name}
                </Link>
              </Button>
            ))}
          </div>
        </section>
      )}
    </>
  );
}
