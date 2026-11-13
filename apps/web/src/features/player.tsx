"use client";
import { useRef, useState, useEffect } from "react";
import type Hls from "hls.js";
import {
  Play,
  Pause,
  Volume2,
  VolumeX,
  Maximize,
  PictureInPicture,
  Lock,
  Unlock,
  SkipForward,
  SkipBack,
  RotateCcw,
} from "lucide-react";
import { keyOf, type Item } from "@rayan/shared-types";
import { useLibrary } from "@/stores/library";
import { usePreferences } from "@/stores/preferences";
import { Button } from "@/components/ui/button";
interface Track {
  id: number;
  name: string;
}
type NativeAudioTrack = { label: string; language: string; enabled: boolean };
function nativeAudio(el: HTMLVideoElement) {
  const tracks = (
    el as HTMLVideoElement & { audioTracks?: ArrayLike<NativeAudioTrack> }
  ).audioTracks;
  return tracks ? Array.from(tracks) : [];
}
const time = (n: number) =>
  Number.isFinite(n)
    ? `${Math.floor(n / 3600) ? Math.floor(n / 3600) + ":" : ""}${String(Math.floor((n % 3600) / 60)).padStart(2, "0")}:${String(Math.floor(n % 60)).padStart(2, "0")}`
    : "LIVE";
export function Player({
  url,
  format,
  item,
  preview = false,
  restart = false,
  onEnded,
  onPrevious,
  onNext,
}: {
  url: string;
  format: string;
  item: Item;
  preview?: boolean;
  restart?: boolean;
  onEnded?: () => void;
  onPrevious?: () => void;
  onNext?: () => void;
}) {
  const video = useRef<HTMLVideoElement>(null),
    container = useRef<HTMLDivElement>(null),
    hls = useRef<Hls | null>(null),
    [playing, setPlaying] = useState(false),
    [current, setCurrent] = useState(0),
    [duration, setDuration] = useState(0),
    [error, setError] = useState(""),
    [attempt, setAttempt] = useState(0),
    [locked, setLocked] = useState(false),
    [muted, setMuted] = useState(preview),
    [pip, setPip] = useState(false),
    [aspect, setAspect] = useState("fit"),
    [speed, setSpeed] = useState("1"),
    [quality, setQuality] = useState("-1"),
    [audio, setAudio] = useState("0"),
    [subtitle, setSubtitle] = useState("-1"),
    [qualities, setQualities] = useState<Track[]>([]),
    [audios, setAudios] = useState<Track[]>([]),
    [subtitles, setSubtitles] = useState<Track[]>([]),
    [diagnostic, setDiagnostic] = useState("Connecting…"),
    [full, setFull] = useState(false),
    [volume, setVolume] = useState(1);
  const latest = useRef({ onEnded, onNext, onPrevious, item });
  useEffect(() => {
    latest.current = { onEnded, onNext, onPrevious, item };
  });
  useEffect(() => {
    setPip(!!document.pictureInPictureEnabled);
  }, []);
  useEffect(() => {
    const el = video.current;
    if (!el) return;
    let disposed = false,
      retries = 0,
      resumed = false,
      lastSave = 0,
      timer: ReturnType<typeof setTimeout> | undefined;
    setError("");
    setQualities([]);
    setAudios([]);
    setSubtitles([]);
    setCurrent(0);
    setDuration(0);
    el.muted = preview;
    const save = () => {
      if (!preview && el.currentTime >= 0) {
        const state = usePreferences.getState();
        useLibrary
          .getState()
          .progress(
            item,
            el.currentTime,
            Number.isFinite(el.duration) ? el.duration : 0,
            state.completion,
          );
      }
    };
    const metadata = () => {
      setDuration(el.duration);
      setDiagnostic(
        `${el.videoWidth} × ${el.videoHeight} · ${format.toUpperCase()}`,
      );
      if (!resumed) {
        resumed = true;
        const p = restart
          ? 0
          : (useLibrary.getState().library.positions[keyOf(item)]?.position ??
            0);
        if (item.kind !== "live" && p > 0 && Number.isFinite(el.duration))
          el.currentTime = Math.min(p, Math.max(0, el.duration - 1));
      }
      if (hls.current) return;
      const nativeTracks = nativeAudio(el);
      setAudios(
        nativeTracks.map((a, id) => ({
          id,
          name: a.label || a.language || `Audio ${id + 1}`,
        })),
      );
      const enabled = nativeTracks.findIndex((a) => a.enabled);
      if (enabled >= 0) setAudio(String(enabled));
      const tracks = el.textTracks;
      setSubtitles(
        Array.from({ length: tracks.length }, (_, n) => ({
          id: n,
          name: tracks[n].label || tracks[n].language || `Subtitle ${n + 1}`,
        })),
      );
    };
    const update = () => {
      setCurrent(el.currentTime);
      if (Date.now() - lastSave > 5000) {
        lastSave = Date.now();
        save();
      }
    };
    const ended = () => {
      save();
      setPlaying(false);
      latest.current.onEnded?.();
    };
    const fatal = () =>
      setError(
        "This stream could not be decoded. Ask your provider for browser-compatible HLS (H.264/AAC) or MP4. Encrypted streams require a supported license configuration.",
      );
    const network = () => {
      if (retries >= 2) {
        setError(
          "The stream connection failed after two retries. Check your connection and retry.",
        );
        return;
      }
      const delay = 1000 * 2 ** retries++;
      setDiagnostic(`Reconnecting (${retries}/2)…`);
      timer = setTimeout(() => {
        if (disposed) return;
        if (hls.current) hls.current.startLoad();
        else {
          el.load();
          void el.play().catch(() => {});
        }
      }, delay);
    };
    const nativeError = () => {
      if (el.error?.code === 2) network();
      else fatal();
    };
    const play = () => setPlaying(true),
      pause = () => {
        setPlaying(false);
        save();
      };
    el.addEventListener("loadedmetadata", metadata);
    el.addEventListener("durationchange", metadata);
    el.addEventListener("timeupdate", update);
    el.addEventListener("ended", ended);
    el.addEventListener("error", nativeError);
    el.addEventListener("play", play);
    el.addEventListener("pause", pause);
    const start = async () => {
      if (
        format === "m3u8" &&
        !el.canPlayType("application/vnd.apple.mpegurl")
      ) {
        const { default: H } = await import("hls.js");
        if (disposed) return;
        if (!H.isSupported()) {
          setError(
            "This browser does not support HLS. Try a current browser with Media Source Extensions.",
          );
          return;
        }
        const instance = new H({
          enableWorker: true,
          maxBufferLength: 30,
          backBufferLength: 30,
        });
        hls.current = instance;
        instance.on(H.Events.MANIFEST_PARSED, () => {
          setQualities(
            instance.levels.map((l, n) => ({
              id: n,
              name: l.height
                ? `${l.height}p${l.bitrate ? ` · ${Math.round(l.bitrate / 1000)} kbps` : ""}`
                : `${Math.round(l.bitrate / 1000)} kbps`,
            })),
          );
          setAudios(
            instance.audioTracks.map((a, n) => ({
              id: n,
              name: a.name || a.lang || `Audio ${n + 1}`,
            })),
          );
          setSubtitles(
            instance.subtitleTracks.map((s, n) => ({
              id: n,
              name: s.name || s.lang || `Subtitle ${n + 1}`,
            })),
          );
          void el.play().catch(() => setDiagnostic("Press Play to start."));
        });
        instance.on(H.Events.ERROR, (_event, data) => {
          if (!data.fatal) return;
          if (data.type === H.ErrorTypes.NETWORK_ERROR) network();
          else if (data.type === H.ErrorTypes.MEDIA_ERROR && retries < 2) {
            retries++;
            instance.recoverMediaError();
          } else fatal();
        });
        instance.loadSource(url);
        instance.attachMedia(el);
      } else {
        el.src = url;
        void el.play().catch(() => setDiagnostic("Press Play to start."));
      }
    };
    void start().catch(() =>
      setError("Unable to initialize playback. Retry the stream."),
    );
    return () => {
      disposed = true;
      clearTimeout(timer);
      save();
      el.removeEventListener("loadedmetadata", metadata);
      el.removeEventListener("durationchange", metadata);
      el.removeEventListener("timeupdate", update);
      el.removeEventListener("ended", ended);
      el.removeEventListener("error", nativeError);
      el.removeEventListener("play", play);
      el.removeEventListener("pause", pause);
      if (document.pictureInPictureElement === el)
        void document.exitPictureInPicture().catch(() => {});
      el.pause();
      hls.current?.destroy();
      hls.current = null;
      el.removeAttribute("src");
      el.load();
    };
  }, [url, format, item, attempt, preview, restart]);
  const toggle = () => {
    const el = video.current;
    if (el) {
      if (el.paused)
        void el.play().catch(() => setDiagnostic("Press Play to start."));
      else el.pause();
    }
  };
  async function fullscreen() {
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
      else if (container.current?.requestFullscreen)
        await container.current.requestFullscreen();
      else setFull((v) => !v);
    } catch {
      setFull((v) => !v);
    }
  }
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (
        locked ||
        preview ||
        (e.target instanceof HTMLElement &&
          ["INPUT", "SELECT", "BUTTON", "TEXTAREA"].includes(e.target.tagName))
      )
        return;
      const el = video.current;
      if (!el) return;
      switch (e.key.toLowerCase()) {
        case " ":
        case "k":
          e.preventDefault();
          if (el.paused) void el.play().catch(() => {});
          else el.pause();
          break;
        case "arrowright":
          e.preventDefault();
          el.currentTime += 10;
          break;
        case "arrowleft":
          e.preventDefault();
          el.currentTime = Math.max(0, el.currentTime - 10);
          break;
        case "m":
          el.muted = !el.muted;
          setMuted(el.muted);
          break;
        case "f":
          e.preventDefault();
          if (document.fullscreenElement) void document.exitFullscreen();
          else if (container.current?.requestFullscreen)
            void container.current
              .requestFullscreen()
              .catch(() => setFull(true));
          else setFull((v) => !v);
          break;
        case "n":
          latest.current.onNext?.();
          break;
        case "p":
          latest.current.onPrevious?.();
          break;
        case "escape":
          setFull(false);
      }
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [locked, preview]);
  return (
    <>
      <div
        className={`player-screen ${full ? "full-window" : ""}`}
        ref={container}
        data-testid="video-player"
      >
        <video
          ref={video}
          className={aspect}
          playsInline
          preload="metadata"
          aria-label={`Video: ${item.name}`}
          tabIndex={0}
        />
        {locked ? (
          <Button
            variant="secondary"
            className="unlock"
            aria-label="Unlock controls"
            onClick={() => setLocked(false)}
          >
            <Unlock />
            Unlock
          </Button>
        ) : (
          <div className="player-overlay">
            <div className="player-top">
              <h2>{item.name}</h2>
              {item.kind === "live" && <span className="badge">LIVE</span>}
              {!preview && (
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label="Lock controls"
                  onClick={() => setLocked(true)}
                >
                  <Lock />
                </Button>
              )}
            </div>
            <div className="player-bottom">
              <Button
                variant="ghost"
                size="icon"
                aria-label={playing ? "Pause" : "Play"}
                onClick={toggle}
              >
                {playing ? <Pause /> : <Play />}
              </Button>
              {onPrevious && (
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label="Previous title"
                  onClick={onPrevious}
                >
                  <SkipBack />
                </Button>
              )}
              {onNext && (
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label="Next title"
                  onClick={onNext}
                >
                  <SkipForward />
                </Button>
              )}
              {item.kind !== "live" && (
                <>
                  <span className="player-time">{time(current)}</span>
                  <input
                    type="range"
                    aria-label="Playback position"
                    min={0}
                    max={Number.isFinite(duration) ? duration : 0}
                    step={0.1}
                    value={Math.min(
                      current,
                      Number.isFinite(duration) ? duration : 0,
                    )}
                    onChange={(e) => {
                      if (video.current)
                        video.current.currentTime = +e.target.value;
                    }}
                  />
                  <span className="player-time">{time(duration)}</span>
                </>
              )}
              <Button
                variant="ghost"
                size="icon"
                aria-label={muted ? "Unmute" : "Mute"}
                onClick={() => {
                  if (video.current) {
                    video.current.muted = !muted;
                    setMuted(!muted);
                  }
                }}
              >
                {muted ? <VolumeX /> : <Volume2 />}
              </Button>
              {!preview && (
                <>
                  <input
                    aria-label="Volume"
                    type="range"
                    min={0}
                    max={1}
                    step={0.05}
                    value={volume}
                    style={{ maxWidth: 65, minWidth: 40 }}
                    onChange={(e) => {
                      const v = +e.target.value;
                      setVolume(v);
                      if (video.current) video.current.volume = v;
                    }}
                  />
                  <select
                    aria-label="Playback speed"
                    value={speed}
                    onChange={(e) => {
                      setSpeed(e.target.value);
                      if (video.current)
                        video.current.playbackRate = +e.target.value;
                    }}
                  >
                    {[0.5, 0.75, 1, 1.25, 1.5, 2].map((n) => (
                      <option key={n} value={n}>
                        {n}×
                      </option>
                    ))}
                  </select>
                  {qualities.length > 0 && (
                    <select
                      aria-label="Video quality"
                      value={quality}
                      onChange={(e) => {
                        setQuality(e.target.value);
                        if (hls.current)
                          hls.current.currentLevel = +e.target.value;
                      }}
                    >
                      <option value="-1">Auto quality</option>
                      {qualities.map((q) => (
                        <option key={q.id} value={q.id}>
                          {q.name}
                        </option>
                      ))}
                    </select>
                  )}
                  {audios.length > 0 && (
                    <select
                      aria-label="Audio track"
                      value={audio}
                      onChange={(e) => {
                        setAudio(e.target.value);
                        if (hls.current)
                          hls.current.audioTrack = +e.target.value;
                        else if (video.current)
                          nativeAudio(video.current).forEach(
                            (t, n) => (t.enabled = n === +e.target.value),
                          );
                      }}
                    >
                      {audios.map((a) => (
                        <option key={a.id} value={a.id}>
                          {a.name}
                        </option>
                      ))}
                    </select>
                  )}
                  {subtitles.length > 0 && (
                    <select
                      aria-label="Subtitles"
                      value={subtitle}
                      onChange={(e) => {
                        const n = +e.target.value;
                        setSubtitle(e.target.value);
                        if (hls.current) hls.current.subtitleTrack = n;
                        else if (video.current)
                          Array.from(video.current.textTracks).forEach(
                            (t, i) =>
                              (t.mode = i === n ? "showing" : "disabled"),
                          );
                      }}
                    >
                      <option value="-1">Subtitles off</option>
                      {subtitles.map((s) => (
                        <option key={s.id} value={s.id}>
                          {s.name}
                        </option>
                      ))}
                    </select>
                  )}
                  <select
                    aria-label="Aspect ratio"
                    value={aspect}
                    onChange={(e) => setAspect(e.target.value)}
                  >
                    <option value="fit">Fit</option>
                    <option value="fill">Fill</option>
                    <option value="stretch">Stretch</option>
                  </select>
                  {pip && (
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label="Picture in Picture"
                      onClick={async () => {
                        try {
                          if (document.pictureInPictureElement)
                            await document.exitPictureInPicture();
                          else await video.current?.requestPictureInPicture();
                        } catch {
                          setDiagnostic(
                            "Picture in Picture is unavailable for this stream.",
                          );
                        }
                      }}
                    >
                      <PictureInPicture />
                    </Button>
                  )}
                </>
              )}
              <Button
                variant="ghost"
                size="icon"
                aria-label="Fullscreen"
                onClick={fullscreen}
              >
                <Maximize />
              </Button>
            </div>
          </div>
        )}
        {error && (
          <div className="player-error" role="alert">
            <h2>Playback unavailable</h2>
            <p>{error}</p>
            <Button
              onClick={() => {
                setError("");
                setAttempt((n) => n + 1);
              }}
            >
              <RotateCcw />
              Retry playback
            </Button>
          </div>
        )}
      </div>
      {!preview && (
        <div className="player-diagnostics" role="status">
          {diagnostic}
          <span className="muted">
            {" "}
            · Space: play/pause · ← →: seek · F: fullscreen · M: mute · N/P:
            next/previous
          </span>
        </div>
      )}
    </>
  );
}
