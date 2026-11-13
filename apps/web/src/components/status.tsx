"use client";
import { Film, RefreshCw } from "lucide-react";
import { Button } from "./ui/button";
import { useText } from "@/hooks/use-text";
export function Loading() {
  return (
    <div aria-label="Loading catalog" role="status" className="skeleton-grid">
      {Array.from({ length: 8 }, (_, i) => (
        <div className="skeleton" key={i} />
      ))}
    </div>
  );
}
export function Empty() {
  const t = useText();
  return (
    <div className="center-state">
      <Film size={36} />
      <h2>{t("No titles here yet")}</h2>
      <p>{t("Try another filter or refresh your provider catalog.")}</p>
    </div>
  );
}
export function Failure({
  error,
  retry,
}: {
  error: unknown;
  retry: () => void;
}) {
  const t = useText();
  return (
    <div role="alert" className="center-state">
      <h2>Unable to load</h2>
      <p>{error instanceof Error ? error.message : "Try again later."}</p>
      <Button onClick={retry}>
        <RefreshCw />
        {t("Retry")}
      </Button>
    </div>
  );
}
