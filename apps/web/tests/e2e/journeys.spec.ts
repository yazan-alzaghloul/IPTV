import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
async function login(page: import("@playwright/test").Page) {
  await page.goto("/login");
  await page.getByLabel("Provider address").fill("http://localhost:18765");
  await page.getByLabel("Username", { exact: true }).fill("fixture");
  await page.getByLabel("Password", { exact: true }).fill("fixture");
  await page.getByRole("button", { name: "Connect", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Home", exact: true }),
  ).toBeVisible();
}
test("real provider, catalogs, library, guide, series and logout", async ({
  page,
  context,
}) => {
  await login(page);
  const image = page.locator("article img").first();
  const source = await image.getAttribute("src");
  expect(source).toContain("/api/media/");
  const imageResponse = await page.request.get(source!);
  expect(imageResponse.headers()["content-type"]).toBe("image/webp");
  expect(imageResponse.headers()["x-rayan-upstream"]).toBeUndefined();
  const cookie = (await context.cookies()).find(
    (c) => c.name === "rayan-session",
  );
  expect(cookie?.httpOnly).toBe(true);
  expect(cookie?.sameSite).toBe("Lax");
  expect(cookie?.value).not.toContain("fixture");
  await page.getByRole("link", { name: "Movies", exact: true }).click();
  await expect(page).toHaveURL(/\/movies$/);
  await expect(page.getByText("Fixture Movie", { exact: true })).toBeVisible();
  await page
    .getByRole("button", { name: "Add favorite: Fixture Movie" })
    .click();
  const results = page.getByLabel("Catalog results");
  await expect(results).toBeVisible();
  expect(await results.locator("article").count()).toBeLessThan(48);
  await results.evaluate((el) => (el.scrollTop = el.scrollHeight));
  await expect(
    results.getByText("Catalog Movie 047", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Next", exact: true }).click();
  await expect(page.getByText("2 / 3", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Previous", exact: true }).click();
  await page.getByRole("link", { name: "Favorites", exact: true }).click();
  await expect(page).toHaveURL(/\/favorites$/);
  await expect(page.getByText("Fixture Movie", { exact: true })).toBeVisible();
  await page
    .getByRole("link", { name: "Fixture Movie", exact: true })
    .first()
    .click();
  await expect(
    page.getByText("Fixture movie plot", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Mark watched", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Mark unwatched", exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Series", exact: true }).click();
  await expect(page).toHaveURL(/\/series$/);
  await page
    .getByRole("link", { name: "Fixture Series", exact: true })
    .first()
    .click();
  await expect(
    page.getByRole("heading", { name: "Episodes", exact: true }),
  ).toBeVisible();
  await page.getByLabel("Season", { exact: true }).selectOption("2");
  await expect(
    page.getByRole("link", { name: "Play New season" }).first(),
  ).toBeVisible();
  await page.getByRole("link", { name: "Live TV", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "News now", exact: true }),
  ).toBeVisible();
  await expect(page.locator("video")).toHaveCount(0);
  await page.getByRole("link", { name: "Settings", exact: true }).click();
  await page
    .getByRole("combobox", { name: "Theme", exact: true })
    .selectOption("light");
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
  await page
    .getByRole("combobox", { name: "Language", exact: true })
    .selectOption("ar");
  await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
  await expect(
    page.getByRole("heading", { name: "الإعدادات", exact: true }),
  ).toBeVisible();
  await page
    .getByRole("combobox", { name: "اللغة", exact: true })
    .selectOption("en");
  const storage = await page.evaluate(() => JSON.stringify(localStorage));
  expect(storage).not.toContain("fixture");
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect(
    (await context.cookies()).some((c) => c.name === "rayan-session"),
  ).toBe(false);
});
test("real H264 MP4 playback and live HLS through credential-free relay", async ({
  page,
}) => {
  await login(page);
  await page.goto("/watch/movie/2");
  await expect(page.locator("video")).toBeVisible();
  await expect
    .poll(
      () =>
        page.locator("video").evaluate((v: HTMLVideoElement) => v.readyState),
      { timeout: 30000 },
    )
    .toBeGreaterThanOrEqual(2);
  await expect
    .poll(() =>
      page.locator("video").evaluate((v: HTMLVideoElement) => v.videoWidth),
    )
    .toBeGreaterThan(0);
  const src = await page
    .locator("video")
    .evaluate((v: HTMLVideoElement) => v.currentSrc);
  expect(src).not.toContain("fixture");
  await page.goto("/live");
  await page
    .getByRole("button", { name: "Start preview", exact: true })
    .click();
  await expect
    .poll(
      () =>
        page.locator("video").evaluate((v: HTMLVideoElement) => v.readyState),
      { timeout: 30000 },
    )
    .toBeGreaterThanOrEqual(2);
  await expect(page.locator(".player-error")).toHaveCount(0);
  await page.getByRole("button", { name: "Stop preview", exact: true }).click();
  await expect(page.locator("video")).toHaveCount(0);
});
test("responsive keyboard and accessibility checks", async ({ page }) => {
  await login(page);
  for (const size of [
    { width: 1440, height: 1000 },
    { width: 820, height: 1180 },
    { width: 390, height: 844 },
  ]) {
    await page.setViewportSize(size);
    await expect(
      page.getByRole("heading", { name: "Home", exact: true }),
    ).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    await expect(
      page.getByRole("navigation", { name: "Main navigation" }),
    ).toBeVisible();
  }
  await page.screenshot({
    path: "test-results/home-mobile.png",
    fullPage: true,
  });
  const results = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
    .analyze();
  expect(results.violations).toEqual([]);
  await page.goto("/search");
  await page.getByRole("textbox", { name: "Search titles" }).fill("Fixture");
  await expect(page.getByText("Fixture Movie", { exact: true })).toBeVisible();
  await expect(
    page.getByText("1 titles from your provider", { exact: true }),
  ).toBeVisible();
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.screenshot({
    path: "test-results/search-desktop.png",
    fullPage: true,
  });
});
test("API rejects missing auth, unapproved providers and cross-site mutation", async ({
  request,
}) => {
  expect((await request.get("/api/catalog?kind=movie")).status()).toBe(401);
  expect(
    (
      await request.post("/api/login", {
        headers: { Origin: "https://evil.example" },
        data: {
          host: "http://127.0.0.1:18765",
          username: "fixture",
          password: "fixture",
        },
      })
    ).status(),
  ).toBe(403);
  expect(
    (
      await request.post("/api/login", {
        headers: { Origin: "http://127.0.0.1:3100" },
        data: { host: "http://169.254.169.254", username: "a", password: "b" },
      })
    ).status(),
  ).toBe(403);
});
