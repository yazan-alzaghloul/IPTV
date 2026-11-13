"use client";
import { useState, useEffect } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { motion, useReducedMotion } from "motion/react";
import {
  Play,
  Info,
  ChevronLeft,
  ChevronRight,
  Star,
  Radio,
} from "lucide-react";
import { recommendations, type Item, type Details } from "@rayan/shared-types";
import { api } from "@/services/api";
import { useLibrary } from "@/stores/library";
import { usePreferences } from "@/stores/preferences";
import { useText } from "@/hooks/use-text";
import { Artwork, ItemCard, itemLink, watchLink } from "@/components/item-card";
import { Button } from "@/components/ui/button";
import { Loading, Failure, Empty } from "@/components/status";
function Rail({
  title,
  items,
  href,
  kicker,
}: {
  title: string;
  items: Item[];
  href?: string;
  kicker?: string;
}) {
  const t = useText();
  if (!items.length) return null;
  return (
    <section className="section">
      {kicker && <span className="section-kicker">{kicker}</span>}
      <div className="section-heading">
        <h2>{t(title)}</h2>
        {href && (
          <Link href={href}>
            {t("Browse all")}
            <ChevronRight size={13} />
          </Link>
        )}
      </div>
      <div className="rail">
        {items.map((i) => (
          <ItemCard key={`${i.kind}:${i.id}`} item={i} />
        ))}
      </div>
    </section>
  );
}
export function Home() {
  const t = useText(),
    library = useLibrary((s) => s.library),
    a = usePreferences((s) => s.appearance),
    reduce = useReducedMotion(),
    [index, setIndex] = useState(0);
  const q = useQuery({
    queryKey: ["home"],
    queryFn: () => api<{ live: Item[]; movie: Item[]; series: Item[] }>("home"),
  });
  const featured = [...(q.data?.movie ?? []), ...(q.data?.series ?? [])].slice(
      0,
      8,
    ),
    item = featured[index % Math.max(1, featured.length)];
  const d = useQuery({
    queryKey: ["details", item?.kind, item?.id],
    queryFn: () =>
      api<{ item: Item; details: Details }>(
        `details?kind=${item!.kind}&id=${encodeURIComponent(item!.id)}`,
      ),
    enabled: !!item,
  });
  useEffect(() => {
    if (!a.heroRotation || !a.motion || reduce || featured.length < 2) return;
    const id = setInterval(
      () => setIndex((i) => (i + 1) % featured.length),
      12000,
    );
    return () => clearInterval(id);
  }, [a.heroRotation, a.motion, reduce, featured.length]);
  if (q.isPending) return <Loading />;
  if (q.error) return <Failure error={q.error} retry={() => q.refetch()} />;
  if (!q.data) return null;
  const hidden = (row: string) => a.hiddenRows.includes(row),
    recent = library.history
      .filter((e) => e.item.kind === "live")
      .map((e) => e.item),
    continuing = library.history
      .filter(
        (e) =>
          e.item.kind !== "live" &&
          (library.positions[`${e.item.kind.toUpperCase()}:${e.item.id}`]
            ?.position ?? 0) > 0,
      )
      .map((e) => e.item);
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="section-kicker">MAKE YOURSELF AT HOME</span>
          <h1>{t("Home")}</h1>
          <p>Your next great watch is waiting.</p>
        </div>
        <span className="badge">
          <Radio size={10} className="text-red-400" />
          CONNECTED
        </span>
      </div>
      {item && (
        <section className="hero" aria-label="Featured title">
          <Artwork
            src={d.data?.details.backdrop || item.backdrop || item.image}
            className="hero-art"
            priority
          />
          <motion.div
            className="hero-copy"
            key={item.id}
            initial={a.motion && !reduce ? { opacity: 0, y: 8 } : false}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3 }}
          >
            <span className="eyebrow">
              THE SPOTLIGHT · {item.kind === "movie" ? "MOVIE" : "SERIES"}
            </span>
            <h1>{item.name}</h1>
            <div className="hero-meta">
              {(d.data?.details.year || item.year) && (
                <span>{d.data?.details.year || item.year}</span>
              )}
              {item.rating > 0 && (
                <span className="rating">
                  <Star size={13} fill="currentColor" />
                  {item.rating.toFixed(1)}
                </span>
              )}
              <span>{d.data?.details.genre || item.genre}</span>
              {d.data?.details.duration && (
                <span>{d.data.details.duration}</span>
              )}
            </div>
            <p>
              {d.data?.details.overview ||
                item.overview ||
                "Explore this title from your provider library."}
            </p>
            <div className="hero-actions">
              <Button asChild>
                <Link
                  href={
                    item.kind === "series" ? itemLink(item) : watchLink(item)
                  }
                >
                  <Play size={17} fill="currentColor" />
                  {t(item.kind === "series" ? "Episodes" : "Play")}
                </Link>
              </Button>
              <Button variant="secondary" asChild>
                <Link href={itemLink(item)}>
                  <Info size={17} />
                  View details
                </Link>
              </Button>
            </div>
          </motion.div>
          <div className="hero-navigation">
            <small>
              {String((index % featured.length) + 1).padStart(2, "0")} /{" "}
              {String(featured.length).padStart(2, "0")}
            </small>
            <button
              aria-label="Previous featured title"
              onClick={() =>
                setIndex((i) => (i - 1 + featured.length) % featured.length)
              }
            >
              <ChevronLeft size={17} />
            </button>
            <button
              aria-label="Next featured title"
              onClick={() => setIndex((i) => (i + 1) % featured.length)}
            >
              <ChevronRight size={17} />
            </button>
          </div>
        </section>
      )}
      {!hidden("Continue watching") && (
        <Rail title="Continue watching" items={continuing} href="/history" />
      )}
      {!hidden("Recently watched channels") && (
        <Rail title="Recently watched channels" items={recent} href="/live" />
      )}
      {!hidden("Favorites") && (
        <Rail
          title="Favorites"
          items={Object.values(library.favorites)}
          href="/favorites"
        />
      )}
      {!hidden("Movies") && (
        <Rail
          title="Recently added movies"
          kicker="FRESH IN YOUR LIBRARY"
          items={q.data.movie}
          href="/movies"
        />
      )}
      {!hidden("Series") && (
        <Rail
          title="Recently added series"
          items={q.data.series}
          href="/series"
        />
      )}
      {!hidden("Live TV") && (
        <Rail
          title="Live TV"
          kicker="HAPPENING NOW"
          items={q.data.live}
          href="/live"
        />
      )}
      <Rail
        title="For you"
        items={recommendations([...q.data.movie, ...q.data.series], library)}
      />
      {!item && !q.data.live.length && <Empty />}
    </>
  );
}
