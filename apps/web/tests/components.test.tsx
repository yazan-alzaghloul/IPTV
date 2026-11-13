// @vitest-environment jsdom
import React from "react";
import { afterEach, it, expect, vi } from "vitest";
import { render, screen, fireEvent, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { emptyLibrary, type Item } from "@rayan/shared-types";
vi.mock("@/services/persistence", () => ({
  loadLibrary: async () => emptyLibrary(),
  saveLibrary: async () => {},
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn() }),
  usePathname: () => "/login",
}));
import { ItemCard } from "@/components/item-card";
import { useLibrary } from "@/stores/library";
import { appearanceSchema } from "@/stores/preferences";
import { Login } from "@/features/login";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
afterEach(cleanup);
const item: Item = {
  id: "1",
  name: "A real title",
  kind: "movie",
  category: "",
  image: "",
  backdrop: "",
  container: "mp4",
  added: 0,
  rating: 8,
  year: "2026",
  genre: "Drama",
  language: "",
  overview: "",
  releaseDate: "",
};
it("favorite control is accessible and updates actual account state", () => {
  useLibrary.setState({ scope: "test", ready: true, library: emptyLibrary() });
  render(<ItemCard item={item} />);
  const button = screen.getByRole("button", {
    name: "Add favorite: A real title",
  });
  fireEvent.click(button);
  expect(
    screen.getByRole("button", { name: "Remove favorite: A real title" }),
  ).toHaveAttribute("aria-pressed", "true");
  expect(useLibrary.getState().library.favorites["MOVIE:1"].name).toBe(
    item.name,
  );
});
it("validates login before sending a request and preserves secure password entry", async () => {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <Login />
    </QueryClientProvider>,
  );
  expect(screen.getByLabelText("Password")).toHaveAttribute("type", "password");
  fireEvent.click(screen.getByRole("button", { name: "Show password" }));
  expect(screen.getByLabelText("Password")).toHaveAttribute("type", "text");
  fireEvent.click(screen.getByRole("button", { name: "Connect" }));
  expect(
    await screen.findByText("Enter your provider address."),
  ).toBeInTheDocument();
});
it("rejects appearance imports with credentials or invalid scales", () => {
  const a = {
    theme: "dark",
    accent: "violet",
    fontScale: 1,
    highContrast: false,
    motion: false,
    heroRotation: false,
    landing: "HOME",
    hiddenRows: [],
  };
  expect(appearanceSchema.safeParse(a).success).toBe(true);
  expect(appearanceSchema.safeParse({ ...a, password: "secret" }).success).toBe(
    false,
  );
  expect(appearanceSchema.safeParse({ ...a, fontScale: 4 }).success).toBe(
    false,
  );
});

import { appearance as contract } from "@rayan/api-contracts";
it("keeps appearance fields and limits aligned with the canonical shared contract", () => {
  const valid = {
    theme: "dark",
    accent: "violet",
    fontScale: 1,
    highContrast: false,
    motion: false,
    heroRotation: false,
    landing: "HOME",
    hiddenRows: [],
  };
  expect(Object.keys(appearanceSchema.parse(valid)).sort()).toEqual(
    [...contract.required].sort(),
  );
  expect(
    appearanceSchema.safeParse({
      ...valid,
      fontScale: contract.properties.fontScale.maximum + 0.1,
    }).success,
  ).toBe(false);
  expect(
    appearanceSchema.safeParse({
      ...valid,
      hiddenRows: Array.from(
        { length: contract.properties.hiddenRows.maxItems + 1 },
        (_, n) => String(n),
      ),
    }).success,
  ).toBe(false);
});
