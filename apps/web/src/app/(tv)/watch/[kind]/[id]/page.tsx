import { notFound } from "next/navigation";
import { Watch } from "@/features/watch";
export default async function Page({
  params,
  searchParams,
}: {
  params: Promise<{ kind: string; id: string }>;
  searchParams: Promise<{ episode?: string; restart?: string }>;
}) {
  const { kind, id } = await params,
    q = await searchParams;
  if (!["live", "movie", "series"].includes(kind)) notFound();
  return (
    <Watch
      kind={kind as "live" | "movie" | "series"}
      id={id}
      episode={q.episode}
      restart={q.restart === "1"}
    />
  );
}
