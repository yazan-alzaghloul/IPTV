import "server-only";
import sharp from "sharp";
import { createHash } from "node:crypto";
import { boundedBody } from "./security";
const images = new Map<
  string,
  { data: Uint8Array; scope: string; expires: number }
>();
let bytes = 0;
const MAX = 16 * 1024 * 1024;
export const artworkWidth = (raw: string | null) =>
  Math.min(1920, Math.max(96, Math.round((Number(raw) || 384) / 32) * 32));
const key = (scope: string, url: string, width: number) =>
  createHash("sha256").update(`${scope}:${width}:${url}`).digest("hex");
export function cachedArtwork(scope: string, url: string, width: number) {
  const v = images.get(key(scope, url, width));
  if (v && v.expires > Date.now()) return imageResponse(v.data);
}
export function clearArtwork(scope: string) {
  for (const [k, v] of images)
    if (v.scope === scope) {
      bytes -= v.data.byteLength;
      images.delete(k);
    }
}
function imageResponse(data: Uint8Array) {
  return new Response(new Uint8Array(data), {
    headers: {
      "Content-Type": "image/webp",
      "Content-Length": String(data.byteLength),
      "Cache-Control": "private, no-store",
      "X-Content-Type-Options": "nosniff",
    },
  });
}
export async function optimizeArtwork(
  res: Response,
  scope: string,
  url: string,
  width: number,
) {
  const input = await boundedBody(res, 10 * 1024 * 1024);
  const data = new Uint8Array(
    await sharp(input, { limitInputPixels: 20000000, animated: false })
      .rotate()
      .resize({ width, withoutEnlargement: true })
      .webp({ quality: 78 })
      .toBuffer(),
  );
  const k = key(scope, url, width),
    old = images.get(k);
  if (old) {
    bytes -= old.data.byteLength;
    images.delete(k);
  }
  while (images.size && (bytes + data.byteLength > MAX || images.size >= 64)) {
    const first = images.keys().next().value!;
    bytes -= images.get(first)!.data.byteLength;
    images.delete(first);
  }
  if (data.byteLength <= MAX) {
    images.set(k, { data, scope, expires: Date.now() + 120000 });
    bytes += data.byteLength;
  }
  return imageResponse(data);
}
