"use client";
import { create } from "zustand";
import { persist } from "zustand/middleware";
import { z } from "zod";
import type { Appearance } from "@rayan/shared-types";
export const appearanceSchema = z
  .object({
    theme: z.enum(["dark", "light"]),
    accent: z.enum(["violet", "cyan"]),
    fontScale: z.number().min(0.85).max(1.4),
    highContrast: z.boolean(),
    motion: z.boolean(),
    heroRotation: z.boolean(),
    landing: z.enum(["HOME", "LIVE", "MOVIES", "SERIES"]),
    hiddenRows: z
      .array(z.string())
      .max(6)
      .refine((v) => new Set(v).size === v.length),
  })
  .strict();
export const defaultAppearance: Appearance = {
  theme: "dark",
  accent: "violet",
  fontScale: 1,
  highContrast: false,
  motion: false,
  heroRotation: false,
  landing: "HOME",
  hiddenRows: [],
};
interface Preferences {
  appearance: Appearance;
  autoplay: boolean;
  completion: number;
  language: "en" | "ar";
  update: (a: Partial<Appearance>) => void;
  set: (
    a: Partial<Pick<Preferences, "autoplay" | "completion" | "language">>,
  ) => void;
}
export const usePreferences = create<Preferences>()(
  persist(
    (set) => ({
      appearance: defaultAppearance,
      autoplay: false,
      completion: 95,
      language: "en",
      update: (a) => set((s) => ({ appearance: { ...s.appearance, ...a } })),
      set: (a) => set(a),
    }),
    {
      name: "rayan-preferences",
      version: 1,
      partialize: (s) => ({
        appearance: s.appearance,
        autoplay: s.autoplay,
        completion: s.completion,
        language: s.language,
      }),
    },
  ),
);
