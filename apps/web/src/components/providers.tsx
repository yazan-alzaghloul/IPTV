"use client";
import { useState, useEffect } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MotionConfig } from "motion/react";
import tokens from "@rayan/design-tokens";
import { usePreferences } from "@/stores/preferences";
export function Providers({ children }: { children: React.ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { staleTime: 120000, retry: 1, refetchOnWindowFocus: false },
        },
      }),
  );
  const { appearance: a, language } = usePreferences();
  useEffect(() => {
    const root = document.documentElement;
    root.dataset.theme = a.theme;
    root.dataset.accent = a.accent;
    root.dataset.contrast = String(a.highContrast);
    root.dataset.motion = String(a.motion);
    root.lang = language;
    root.dir = language === "ar" ? "rtl" : "ltr";
    root.style.fontSize = `${16 * a.fontScale}px`;
    root.style.setProperty("--brand-violet", tokens.color.dark.accent);
  }, [a, language]);
  return (
    <QueryClientProvider client={client}>
      <MotionConfig reducedMotion={a.motion ? "user" : "always"}>
        {children}
      </MotionConfig>
    </QueryClientProvider>
  );
}
