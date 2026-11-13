"use client";
import Link from "next/link";
import { Trash2, Play } from "lucide-react";
import { keyOf } from "@rayan/shared-types";
import { useLibrary } from "@/stores/library";
import { VirtualCatalog } from "./catalog";
import { Empty } from "@/components/status";
import { Button } from "@/components/ui/button";
import { itemLink, watchLink } from "@/components/item-card";
import { useText } from "@/hooks/use-text";
export function Library({ history = false }: { history?: boolean }) {
  const t = useText(),
    l = useLibrary((s) => s.library);
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="section-kicker">YOUR COLLECTION</span>
          <h1>{t(history ? "History" : "Favorites")}</h1>
          <p>
            {history
              ? "Pick up a story where you left it."
              : "The titles you love, always within reach."}
          </p>
        </div>
        {history && l.history.length > 0 && (
          <Button
            variant="ghost"
            onClick={() => {
              if (window.confirm("Clear watch history and resume positions?"))
                useLibrary.getState().clear();
            }}
          >
            <Trash2 />
            Clear history
          </Button>
        )}
      </div>
      {history ? (
        l.history.length ? (
          l.history.map((e) => (
            <div className="history-row" key={keyOf(e.item)}>
              <Link href={itemLink(e.item)}>
                <strong>{e.item.name}</strong>
                <small>
                  {new Date(e.updated).toLocaleDateString()} ·{" "}
                  {e.item.kind === "live"
                    ? "Live channel"
                    : `${Math.floor(e.position / 60)} / ${Math.floor(e.duration / 60)} min`}
                </small>
              </Link>
              <Button variant="secondary" asChild>
                <Link
                  href={watchLink(e.item)}
                  aria-label={`Play ${e.item.name}`}
                >
                  <Play />
                </Link>
              </Button>
              <Button
                variant="ghost"
                size="icon"
                aria-label={`Remove ${e.item.name} from history`}
                onClick={() => useLibrary.getState().remove(e.item)}
              >
                <Trash2 />
              </Button>
            </div>
          ))
        ) : (
          <Empty />
        )
      ) : Object.keys(l.favorites).length ? (
        <VirtualCatalog items={Object.values(l.favorites)} />
      ) : (
        <Empty />
      )}
    </>
  );
}
