import type { Metadata, Viewport } from "next";
import { Providers } from "@/components/providers";
import "@/styles/globals.css";
export const metadata: Metadata = {
  title: { default: "Rayan TV", template: "%s · Rayan TV" },
  description:
    "Your personal cinema. Live television, movies and series from your provider.",
  robots: { index: false, follow: false },
};
export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: "#080B12",
};
export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
