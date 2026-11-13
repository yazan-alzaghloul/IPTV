"use client";
import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Download, Upload, RotateCcw, Trash2, RefreshCw } from "lucide-react";
import {
  usePreferences,
  appearanceSchema,
  defaultAppearance,
} from "@/stores/preferences";
import { useLibrary } from "@/stores/library";
import { api } from "@/services/api";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/dialog";
import { useText } from "@/hooks/use-text";
export function Settings() {
  const t = useText(),
    s = usePreferences(),
    a = s.appearance,
    client = useQueryClient(),
    [message, setMessage] = useState(""),
    [confirm, setConfirm] = useState(false),
    [storage, setStorage] = useState("");
  const check = (
    label: string,
    value: boolean,
    onChange: (v: boolean) => void,
  ) => (
    <label className="setting">
      <span>{label}</span>
      <input
        type="checkbox"
        checked={value}
        onChange={(e) => onChange(e.target.checked)}
      />
    </label>
  );
  async function importFile(file?: File) {
    if (!file) return;
    try {
      if (file.size > 32768)
        throw new Error("Appearance file must be 32 KB or smaller.");
      s.update(appearanceSchema.parse(JSON.parse(await file.text())));
      setMessage("Appearance imported.");
    } catch {
      setMessage(
        "Invalid appearance file. Use the shared Rayan TV appearance format (maximum 32 KB).",
      );
    }
  }
  function exportFile() {
    const url = URL.createObjectURL(
        new Blob([JSON.stringify(a, null, 2)], { type: "application/json" }),
      ),
      link = document.createElement("a");
    link.href = url;
    link.download = "Rayan-TV-appearance.json";
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="section-kicker">MAKE IT YOURS</span>
          <h1>{t("Settings")}</h1>
          <p>Your viewing experience, just the way you like it.</p>
        </div>
      </div>
      {message && (
        <p role="status" className="notice">
          {message}
        </p>
      )}
      <div className="settings-grid">
        <section className="settings-panel">
          <h2>{t("Appearance")}</h2>
          <label className="setting">
            <span>{t("Theme")}</span>
            <select
              value={a.theme}
              onChange={(e) =>
                s.update({ theme: e.target.value as "dark" | "light" })
              }
            >
              <option value="dark">{t("Dark")}</option>
              <option value="light">{t("Light")}</option>
            </select>
          </label>
          <label className="setting">
            <span>Accent</span>
            <select
              value={a.accent}
              onChange={(e) =>
                s.update({ accent: e.target.value as "violet" | "cyan" })
              }
            >
              <option value="violet">Violet</option>
              <option value="cyan">Cyan</option>
            </select>
          </label>
          <label className="setting">
            <span>Text size · {Math.round(a.fontScale * 100)}%</span>
            <input
              aria-label="Text size"
              type="range"
              min={0.85}
              max={1.4}
              step={0.05}
              value={a.fontScale}
              onChange={(e) => s.update({ fontScale: +e.target.value })}
            />
          </label>
          {check("High contrast", a.highContrast, (v) =>
            s.update({ highContrast: v }),
          )}
          {check("Enable motion", a.motion, (v) => s.update({ motion: v }))}
          {check("Rotate featured titles", a.heroRotation, (v) =>
            s.update({ heroRotation: v }),
          )}
          <p>System reduced-motion preferences always take priority.</p>
          <label className="setting">
            <span>{t("Language")}</span>
            <select
              value={s.language}
              onChange={(e) =>
                s.set({ language: e.target.value as "en" | "ar" })
              }
            >
              <option value="en">English</option>
              <option value="ar">العربية</option>
            </select>
          </label>
        </section>
        <section className="settings-panel">
          <h2>{t("Playback")}</h2>
          {check("Play next episode automatically", s.autoplay, (v) =>
            s.set({ autoplay: v }),
          )}
          <label className="setting">
            <span>Mark watched at</span>
            <select
              value={s.completion}
              onChange={(e) => s.set({ completion: +e.target.value })}
            >
              {[80, 90, 95, 98, 100].map((n) => (
                <option key={n} value={n}>
                  {n}%
                </option>
              ))}
            </select>
          </label>
          <p>
            Resume positions are saved on this browser for this provider
            account. Live channels always start live.
          </p>
          <h3>Browser compatibility</h3>
          <p>
            HLS and compatible MP4 are supported. Codec and encrypted-media
            support depend on your browser and provider. Ask your provider for
            H.264/AAC HLS if a title cannot play. No DRM license server or
            transcoding is included.
          </p>
          <p>
            Fullscreen, Picture in Picture, native track controls, microphone
            and background playback depend on browser capabilities. Native
            Safari controls offer additional supported audio-track selection.
          </p>
        </section>
        <section className="settings-panel">
          <h2>Home screen</h2>
          <label className="setting">
            <span>Start on</span>
            <select
              value={a.landing}
              onChange={(e) =>
                s.update({ landing: e.target.value as typeof a.landing })
              }
            >
              {["HOME", "LIVE", "MOVIES", "SERIES"].map((v) => (
                <option key={v} value={v}>
                  {v}
                </option>
              ))}
            </select>
          </label>
          <p>Choose the rows that appear on Home.</p>
          {[
            "Continue watching",
            "Recently watched channels",
            "Favorites",
            "Live TV",
            "Movies",
            "Series",
          ].map((row) => (
            <div key={row}>
              {check(t(row), !a.hiddenRows.includes(row), (v) =>
                s.update({
                  hiddenRows: v
                    ? a.hiddenRows.filter((r) => r !== row)
                    : [...a.hiddenRows, row],
                }),
              )}
            </div>
          ))}
        </section>
        <section className="settings-panel">
          <h2>Data & preferences</h2>
          <p>
            Appearance exports contain no credentials, favorites, or watch
            history.
          </p>
          <div className="hero-actions">
            <Button variant="secondary" onClick={exportFile}>
              <Download />
              Export appearance
            </Button>
            <Button variant="secondary" asChild>
              <label style={{ cursor: "pointer" }}>
                <Upload />
                Import appearance
                <input
                  className="sr-only"
                  aria-label="Import appearance file"
                  type="file"
                  accept="application/json,.json"
                  onChange={(e) => {
                    void importFile(e.target.files?.[0]);
                    e.target.value = "";
                  }}
                />
              </label>
            </Button>
            <Button
              variant="ghost"
              onClick={() => {
                s.update(defaultAppearance);
                setMessage("Appearance reset.");
              }}
            >
              <RotateCcw />
              Reset appearance
            </Button>
          </div>
          <hr className="my-6 border-white/10" />
          <div className="hero-actions">
            <Button
              variant="secondary"
              onClick={async () => {
                try {
                  await api("refresh", { method: "POST" });
                  await client.invalidateQueries({
                    predicate: (q) => q.queryKey[0] !== "session",
                  });
                  setMessage("Catalog and artwork links refreshed.");
                } catch (e) {
                  setMessage(
                    e instanceof Error ? e.message : "Refresh failed.",
                  );
                }
              }}
            >
              <RefreshCw />
              Refresh catalog
            </Button>
            <Button variant="ghost" onClick={() => setConfirm(true)}>
              <Trash2 />
              Clear history
            </Button>
          </div>
          <p className="mt-5">Rayan TV Web · 1.0.0</p>
          <Button
            variant="ghost"
            onClick={async () => {
              const v = await navigator.storage?.estimate();
              setStorage(
                v
                  ? `${Math.round((v.usage ?? 0) / 1024)} KB used · ${Math.round((v.quota ?? 0) / 1024 / 1024)} MB quota · ${navigator.onLine ? "online" : "offline"}`
                  : "Storage estimate unavailable.",
              );
            }}
          >
            Browser diagnostics
          </Button>
          {storage && <p role="status">{storage}</p>}
        </section>
      </div>
      <Modal
        open={confirm}
        onOpenChange={setConfirm}
        title="Clear watch history?"
      >
        <p>
          Watch history and saved resume positions will be removed. Favorites
          are kept.
        </p>
        <div className="hero-actions">
          <Button
            variant="destructive"
            onClick={() => {
              useLibrary.getState().clear();
              setConfirm(false);
              setMessage("History cleared.");
            }}
          >
            Clear history
          </Button>
          <Button variant="secondary" onClick={() => setConfirm(false)}>
            Cancel
          </Button>
        </div>
      </Modal>
    </>
  );
}
