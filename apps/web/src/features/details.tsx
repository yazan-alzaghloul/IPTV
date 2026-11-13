"use client";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import {
  Play,
  Heart,
  Check,
  ExternalLink,
  ArrowLeft,
  RotateCcw,
} from "lucide-react";
import {
  keyOf,
  continuation,
  type Item,
  type Kind,
  type Details,
  type Page,
} from "@rayan/shared-types";
import { api } from "@/services/api";
import { useLibrary } from "@/stores/library";
import { Artwork, ItemCard, watchLink } from "@/components/item-card";
import { Button } from "@/components/ui/button";
import { Loading, Failure } from "@/components/status";
import { useText } from "@/hooks/use-text";
export function DetailsScreen({ kind, id }: { kind: Kind; id: string }) {
  const t = useText(),
    l = useLibrary((s) => s.library);
  const q = useQuery({
    queryKey: ["details", kind, id],
    queryFn: () =>
      api<{ item: Item; details: Details }>(
        `details?kind=${kind}&id=${encodeURIComponent(id)}`,
      ),
  });
  const related = useQuery({
    queryKey: ["related", kind, q.data?.item.category],
    queryFn: () =>
      api<Page>(
        `catalog?kind=${kind}&category=${encodeURIComponent(q.data!.item.category)}`,
      ),
    enabled: !!q.data,
  });
  if (q.isPending) return <Loading />;
  if (q.error) return <Failure error={q.error} retry={() => q.refetch()} />;
  if (!q.data) return null;
  const { item: i, details: d } = q.data,
    k = keyOf(i),
    favorite = !!l.favorites[k],
    watched = l.watched.includes(k),
    next = kind === "series" ? continuation(d.episodes, l, id) : i;
  const seasons = [...new Set(d.episodes.map((e) => e.season ?? 0))],
    season = l.seasons[id] ?? seasons[0],
    episodes = d.episodes.filter((e) => e.season === season),
    resume = next ? (l.positions[keyOf(next)]?.position ?? 0) : 0;
  return (
    <>
      <Button variant="ghost" asChild>
        <Link href={kind === "movie" ? "/movies" : "/series"}>
          <ArrowLeft />
          {t("Back")}
        </Link>
      </Button>
      <section className="detail-hero">
        <Artwork src={d.backdrop || i.backdrop} className="hero-art" priority />
        <Artwork
          src={d.image || i.image}
          alt={i.name}
          className="detail-poster"
          priority
        />
        <div className="detail-info">
          <span className="eyebrow">
            {kind === "series" ? "SERIES" : "MOVIE"} · RAYAN TV
          </span>
          <h1>{i.name}</h1>
          <div className="hero-meta">
            <span>{d.year || i.year}</span>
            <span className="rating">{d.rating || i.rating || ""}</span>
            <span>{d.genre || i.genre}</span>
            <span>{d.duration}</span>
            <span>{d.classification}</span>
          </div>
          <p>{d.overview || "No description supplied by your provider."}</p>
          <div className="hero-actions">
            {next && (
              <>
                <Button asChild>
                  <Link href={watchLink(next)}>
                    <Play fill="currentColor" />
                    {t(resume > 0 ? "Resume" : "Play")}
                    {next.episode ? ` S${next.season} · E${next.episode}` : ""}
                  </Link>
                </Button>
                {resume > 0 && (
                  <Button variant="secondary" asChild>
                    <Link
                      href={`${watchLink(next)}${next.parent ? "&" : "?"}restart=1`}
                    >
                      <RotateCcw />
                      {t("Restart")}
                    </Link>
                  </Button>
                )}
              </>
            )}
            <Button
              variant="secondary"
              aria-pressed={favorite}
              onClick={() => useLibrary.getState().favorite(i)}
            >
              <Heart fill={favorite ? "currentColor" : "none"} />
              {t(favorite ? "Remove favorite" : "Add favorite")}
            </Button>
            {kind === "movie" && (
              <Button
                variant="ghost"
                onClick={() => useLibrary.getState().watch([i], !watched)}
              >
                <Check />
                {t(watched ? "Mark unwatched" : "Mark watched")}
              </Button>
            )}
            {d.trailer && (
              <Button variant="secondary" asChild>
                <a href={d.trailer} target="_blank" rel="noopener noreferrer">
                  <ExternalLink />
                  {t("Trailer")}
                </a>
              </Button>
            )}
          </div>
        </div>
      </section>
      <dl className="detail-metadata">
        {[
          ["Cast", d.cast],
          ["Director", d.director],
          ["Language", d.language],
          ["Subtitles", d.subtitles],
          ["Video", d.technical],
          ["Format", d.container.toUpperCase()],
        ]
          .filter(([, value]) => value)
          .map(([label, value]) => (
            <div key={label}>
              <dt>{label}</dt>
              <dd>{value}</dd>
            </div>
          ))}
      </dl>
      {kind === "series" && (
        <section className="section">
          <div className="section-heading">
            <h2>{t("Episodes")}</h2>
            <select
              aria-label="Season"
              value={season ?? ""}
              onChange={(e) =>
                useLibrary.getState().season(id, +e.target.value)
              }
            >
              {seasons.map((s) => (
                <option key={s} value={s}>
                  Season {s}
                </option>
              ))}
            </select>
            <Button
              variant="ghost"
              disabled={!episodes.length}
              onClick={() =>
                useLibrary
                  .getState()
                  .watch(
                    episodes,
                    !episodes.every((e) => l.watched.includes(keyOf(e))),
                  )
              }
            >
              {episodes.every((e) => l.watched.includes(keyOf(e)))
                ? "Mark season unwatched"
                : "Mark season watched"}
            </Button>
          </div>
          {!d.episodes.length && (
            <p className="notice">
              Your provider has not supplied episodes for this series.
            </p>
          )}
          <div className="episodes">
            {episodes.map((e) => {
              const w = l.watched.includes(keyOf(e)),
                p = l.positions[keyOf(e)];
              return (
                <article className="episode" key={e.id}>
                  <span className="episode-number">
                    {String(e.episode).padStart(2, "0")}
                  </span>
                  <div className="episode-copy">
                    <h3>{e.name}</h3>
                    <p>
                      {e.overview ||
                        `${e.duration ? Math.round(e.duration / 60) + " min · " : ""}Season ${e.season}, episode ${e.episode}`}
                    </p>
                    {p && p.duration > 0 && (
                      <div className="episode-progress">
                        <span
                          style={{
                            width: `${Math.min(100, (p.position / p.duration) * 100)}%`,
                          }}
                        />
                      </div>
                    )}
                  </div>
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label={`${w ? "Mark unwatched" : "Mark watched"}: ${e.name}`}
                    aria-pressed={w}
                    onClick={() => useLibrary.getState().watch([e], !w)}
                  >
                    <Check />
                  </Button>
                  <Button variant="secondary" size="icon" asChild>
                    <Link href={watchLink(e)} aria-label={`Play ${e.name}`}>
                      <Play />
                    </Link>
                  </Button>
                </article>
              );
            })}
          </div>
        </section>
      )}
      {related.data &&
        related.data.items.filter((r) => r.id !== id).length > 0 && (
          <section className="section">
            <h2>{t("Related titles")}</h2>
            <div className="rail">
              {related.data.items
                .filter((r) => r.id !== id)
                .slice(0, 12)
                .map((r) => (
                  <ItemCard key={r.id} item={r} />
                ))}
            </div>
          </section>
        )}
    </>
  );
}
