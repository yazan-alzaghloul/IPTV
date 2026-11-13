"use client";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import {
  TvMinimal,
  ArrowRight,
  Eye,
  EyeOff,
  ShieldCheck,
  Globe,
  MonitorPlay,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { api } from "@/services/api";
import { usePreferences } from "@/stores/preferences";
import { useText } from "@/hooks/use-text";
const schema = z.object({
  host: z.string().trim().min(1, "Enter your provider address."),
  scheme: z.enum(["https", "http"]),
  port: z.string().regex(/^\d{0,5}$/, "Enter a valid port."),
  username: z.string().trim().min(1, "Enter your username."),
  password: z.string().min(1, "Enter your password."),
});
type Form = z.infer<typeof schema>;
export function Login() {
  const t = useText(),
    router = useRouter(),
    client = useQueryClient(),
    [visible, setVisible] = useState(false),
    [error, setError] = useState("");
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: {
      scheme: "https",
      host: "",
      port: "",
      username: "",
      password: "",
    },
  });
  async function submit(v: Form) {
    setError("");
    try {
      await api("login", { method: "POST", body: JSON.stringify(v) });
      client.clear();
      const landing = usePreferences.getState().appearance.landing;
      router.replace(
        { HOME: "/", LIVE: "/live", MOVIES: "/movies", SERIES: "/series" }[
          landing
        ],
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not connect.");
    }
  }
  return (
    <main className="login-page">
      <section className="login-story">
        <div className="brand">
          <span className="brand-symbol">
            <TvMinimal />
          </span>
          RAYAN<span className="brand-tv">TV</span>
        </div>
        <div className="login-story-copy">
          <span className="eyebrow">A WORLD WORTH WATCHING</span>
          <h1>{t("Your screen. Your world.")}</h1>
          <p>
            Live moments. Unforgettable stories.
            <br />
            Everything you love, in one beautiful place.
          </p>
          <div className="login-features">
            <span>
              <MonitorPlay />
              Live TV & on demand
            </span>
            <span>
              <Globe />
              Across your devices
            </span>
          </div>
        </div>
        <p className="muted">Powered by your provider. Made for your screen.</p>
        <div aria-hidden="true" className="cinema-orbit" />
      </section>
      <section className="login-form-panel">
        <button
          className="language-switch"
          onClick={() =>
            usePreferences.getState().set({
              language:
                usePreferences.getState().language === "en" ? "ar" : "en",
            })
          }
        >
          English / العربية
        </button>
        <div className="login-form-wrap">
          <span className="eyebrow">WELCOME TO RAYAN TV</span>
          <h2>{t("Sign in with your TV provider")}</h2>
          <p className="muted">
            Use the Xtream account details supplied by your provider.
          </p>
          <form onSubmit={handleSubmit(submit)} noValidate>
            {(["host", "username", "password"] as const).map((name) => (
              <div className="field" key={name}>
                <label htmlFor={name}>
                  {t(
                    {
                      host: "Provider address",
                      username: "Username",
                      password: "Password",
                    }[name],
                  )}
                </label>
                <div className="input-wrap">
                  <input
                    id={name}
                    {...register(name)}
                    type={name === "password" && !visible ? "password" : "text"}
                    autoComplete={
                      name === "username"
                        ? "username"
                        : name === "password"
                          ? "current-password"
                          : "url"
                    }
                    placeholder={
                      name === "host" ? "https://your-provider.com" : undefined
                    }
                    aria-invalid={!!errors[name]}
                    aria-describedby={
                      errors[name] ? `${name}-error` : undefined
                    }
                  />
                  {name === "password" && (
                    <button
                      type="button"
                      aria-label={visible ? "Hide password" : "Show password"}
                      onClick={() => setVisible(!visible)}
                    >
                      {visible ? <EyeOff size={18} /> : <Eye size={18} />}
                    </button>
                  )}
                </div>
                {errors[name] && (
                  <small className="field-error" id={`${name}-error`}>
                    {errors[name]?.message}
                  </small>
                )}
              </div>
            ))}
            <details className="advanced">
              <summary>Connection options</summary>
              <div className="inline-fields">
                <label>
                  Protocol
                  <select {...register("scheme")}>
                    <option value="https">HTTPS</option>
                    <option value="http">HTTP</option>
                  </select>
                </label>
                <label>
                  Port (optional)
                  <input {...register("port")} inputMode="numeric" />
                  {errors.port && <small>{errors.port.message}</small>}
                </label>
              </div>
            </details>
            {error && (
              <p className="field-error" role="alert">
                {error}
              </p>
            )}
            <Button className="login-submit" disabled={isSubmitting}>
              {isSubmitting ? "Connecting…" : t("Connect")}
              <ArrowRight size={18} />
            </Button>
          </form>
          <p className="security-note">
            <ShieldCheck size={17} />
            Your credentials are encrypted in a secure session cookie. Rayan TV
            does not provide content or subscriptions.
          </p>
        </div>
      </section>
    </main>
  );
}
