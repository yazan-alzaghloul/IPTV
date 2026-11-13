import { Live } from "@/features/live";
export default async function Page({
  searchParams,
}: {
  searchParams: Promise<{ channel?: string }>;
}) {
  return <Live initial={(await searchParams).channel} />;
}
