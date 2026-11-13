import { notFound } from "next/navigation";
import { DetailsScreen } from "@/features/details";
export default async function Page({
  params,
}: {
  params: Promise<{ kind: string; id: string }>;
}) {
  const { kind, id } = await params;
  if (kind !== "movie" && kind !== "series") notFound();
  return <DetailsScreen kind={kind} id={id} />;
}
