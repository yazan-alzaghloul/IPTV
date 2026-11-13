"use client";
import { useState } from "react";
import Link from "next/link";
import { Heart, Play, Film, Radio, Star } from "lucide-react";
import { keyOf, type Item } from "@rayan/shared-types";
import { useLibrary } from "@/stores/library";
import { useText } from "@/hooks/use-text";
export function itemLink(i: Item) {
  return i.kind === "live"
    ? `/live?channel=${encodeURIComponent(i.id)}`
    : `/details/${i.kind}/${encodeURIComponent(i.parent ?? i.id)}`;
}
export function watchLink(i: Item) {
  return `/watch/${i.kind}/${encodeURIComponent(i.parent ?? i.id)}${i.parent ? `?episode=${encodeURIComponent(i.id)}` : ""}`;
}
export function Artwork({
  src,
  alt = "",
  className,
  priority = false,
  width = 384,
}: {
  src: string;
  alt?: string;
  className?: string;
  priority?: boolean;
  width?: number;
}) {
  const [failed, set] = useState(false);
  return src && !failed ? (
    <img
      src={
        src.startsWith("/api/media/")
          ? `${src}?width=${className?.includes("hero-art") ? 1440 : width}`
          : src
      }
      width={width}
      height={Math.round(width * 1.5)}
      alt={alt}
      className={className}
      loading={priority ? "eager" : "lazy"}
      decoding="async"
      onError={() => set(true)}
    />
  ) : (
    <div
      className={`poster-placeholder ${className ?? ""}`}
      aria-label={alt || undefined}
    >
      <Film />
    </div>
  );
}
export function ItemCard({ item: i }: { item: Item }) {
  const t = useText(),
    favorite = useLibrary((s) => !!s.library.favorites[keyOf(i)]),
    position = useLibrary((s) => s.library.positions[keyOf(i)]),
    watched = useLibrary((s) => s.library.watched.includes(keyOf(i)));
  return (
    <article className={`card ${i.kind === "live" ? "live" : ""}`}>
      <Link className="poster" href={itemLink(i)} aria-label={i.name}>
        <Artwork src={i.image} />
        {i.kind === "live" ? (
          <span className="badge">
            <Radio size={10} />
            LIVE
          </span>
        ) : watched ? (
          <span className="badge">WATCHED</span>
        ) : null}
        <span className="card-hover-play">
          <Play fill="currentColor" />
        </span>
        {position && position.duration > 0 && position.position > 0 && (
          <div className="progress-track">
            <span
              style={{
                width: `${Math.min(100, (position.position / position.duration) * 100)}%`,
              }}
            />
          </div>
        )}
      </Link>
      <div>
        <Link href={itemLink(i)} className="card-title">
          {i.name}
        </Link>
        <div className="card-subtitle">
          {i.year && <span>{i.year}</span>}
          {i.rating > 0 && (
            <span className="rating">
              <Star size={10} />
              {i.rating.toFixed(1)}
            </span>
          )}
          <span>
            {i.kind === "live"
              ? "Live channel"
              : i.genre.split(/[,/|]/)[0] ||
                (i.kind === "series" ? "Series" : "")}
          </span>
        </div>
      </div>
      <button
        className="card-favorite"
        aria-label={`${t(favorite ? "Remove favorite" : "Add favorite")}: ${i.name}`}
        aria-pressed={favorite}
        onClick={() => useLibrary.getState().favorite(i)}
      >
        <Heart fill={favorite ? "currentColor" : "none"} />
      </button>
    </article>
  );
}
