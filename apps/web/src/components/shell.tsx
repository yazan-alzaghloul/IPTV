"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Home,
  Radio,
  Film,
  Clapperboard,
  Heart,
  History,
  Search,
  Settings,
  LogOut,
  TvMinimal,
  ChevronRight,
} from "lucide-react";
import { api, APIError } from "@/services/api";
import { useLibrary } from "@/stores/library";
import { useText } from "@/hooks/use-text";
import { usePreferences } from "@/stores/preferences";
import { Button } from "./ui/button";
import { Failure, Loading } from "./status";
const navigation = [
  ["/", "Home", Home],
  ["/live", "Live TV", Radio],
  ["/movies", "Movies", Film],
  ["/series", "Series", Clapperboard],
  ["/favorites", "Favorites", Heart],
  ["/history", "History", History],
  ["/search", "Search", Search],
  ["/settings", "Settings", Settings],
] as const;
export function Shell({ children }: { children: React.ReactNode }) {
  const path = usePathname(),
    router = useRouter(),
    client = useQueryClient(),
    t = useText();
  const s = useQuery({
    queryKey: ["session"],
    queryFn: () =>
      api<{ scope: string; provider: string; expires: number }>("session"),
    retry: false,
    staleTime: 300000,
  });
  const hydrate = useLibrary((v) => v.hydrate),
    error = useLibrary((v) => v.error),
    ready = useLibrary((v) => v.ready),
    libraryScope = useLibrary((v) => v.scope);
  useEffect(() => {
    if (s.data) void hydrate(s.data.scope);
    if (s.error instanceof APIError && s.error.status === 401)
      router.replace("/login");
  }, [s.data, s.error, hydrate, router]);
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (
        e.key !== "/" ||
        path.startsWith("/watch") ||
        e.ctrlKey ||
        e.metaKey ||
        e.altKey
      )
        return;
      if (
        e.target instanceof HTMLElement &&
        ["INPUT", "TEXTAREA", "SELECT"].includes(e.target.tagName)
      )
        return;
      e.preventDefault();
      router.push("/search");
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [router, path]);
  async function logout() {
    try {
      await api("logout", { method: "POST" });
      await useLibrary.getState().logout();
      client.clear();
      router.replace("/login");
    } catch (e) {
      window.alert(e instanceof Error ? e.message : "Sign out failed.");
    }
  }
  if (s.isPending || ((!ready || libraryScope !== s.data?.scope) && !s.error))
    return (
      <main className="page">
        <Loading />
      </main>
    );
  if (s.error)
    return (
      <main className="page">
        <Failure error={s.error} retry={() => s.refetch()} />
      </main>
    );
  return (
    <>
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <aside className="sidebar">
        <Link href="/" className="brand">
          <span className="brand-symbol">
            <TvMinimal />
          </span>
          <span>
            RAYAN<span className="brand-tv">TV</span>
            <small>ENTERTAINMENT, YOUR WAY</small>
          </span>
        </Link>
        <span className="nav-caption">YOUR WORLD</span>
        <nav aria-label="Main navigation">
          {navigation.map(([href, label, Icon]) => (
            <Link
              key={href}
              href={href}
              className={
                path === href || (href !== "/" && path.startsWith(href))
                  ? "nav-link active"
                  : "nav-link"
              }
              aria-current={path === href ? "page" : undefined}
            >
              <Icon size={19} />
              <span>{t(label)}</span>
              {href === "/live" && <span className="live-dot" />}
            </Link>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="provider-badge">
            <span className="avatar">R</span>
            <div>
              <strong>Your connection</strong>
              <small>{s.data?.provider}</small>
            </div>
          </div>
          <Button variant="ghost" onClick={logout}>
            <LogOut size={17} />
            {t("Sign out")}
          </Button>
        </div>
      </aside>
      <div className="app-content">
        <header className="topbar">
          <div className="breadcrumb">
            Rayan TV <ChevronRight size={13} />
            <strong>
              {t(
                navigation.find(([href]) => href === path)?.[1] ??
                  "Your cinema",
              )}
            </strong>
          </div>
          <Link className="top-search" href="/search" aria-label={t("Search")}>
            <Search size={17} />
            <span>{t("Search")} movies, series, channels</span>
            <kbd>/</kbd>
          </Link>
          <button
            className="avatar"
            aria-label="Open settings"
            onClick={() => router.push("/settings")}
          >
            R
          </button>
        </header>
        {error && (
          <p role="alert" className="notice">
            {error}
          </p>
        )}
        <main id="main" className="page" key={path}>
          {children}
        </main>
        <footer className="app-footer">
          <span>RAYAN TV</span>
          <span>Your provider. Your personal cinema.</span>
          <button
            onClick={() =>
              usePreferences.getState().set({
                language:
                  usePreferences.getState().language === "en" ? "ar" : "en",
              })
            }
          >
            English / العربية
          </button>
        </footer>
      </div>
    </>
  );
}
