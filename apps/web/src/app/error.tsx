"use client";
import { Button } from "@/components/ui/button";
export default function ErrorPage({ reset }: { reset: () => void }) {
  return (
    <main className="center-state">
      <h1>Something interrupted your session</h1>
      <p>Try again. Your saved library is kept on this browser.</p>
      <Button onClick={reset}>Try again</Button>
    </main>
  );
}
