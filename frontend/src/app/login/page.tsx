"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

export default function LoginPage() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const res = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        setError(body.detail ?? body.title ?? "Login failed");
        return;
      }
      const { role } = (await res.json()) as { role: "CUSTOMER" | "OPERATOR" };
      router.push(role === "OPERATOR" ? "/operator" : "/products");
      router.refresh();
    } catch {
      setError("Network error — is the backend running on :8080?");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="mx-auto max-w-sm p-8">
      <h1 className="mb-1 text-2xl font-semibold">Sign in</h1>
      <p className="mb-6 text-sm text-gray-600">
        New customer?{" "}
        <a href="/register" className="text-blue-600 hover:underline">
          Create an account
        </a>
      </p>
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <label className="flex flex-col gap-1">
          <span className="text-sm font-medium">Username</span>
          <input
            type="text"
            autoComplete="username"
            required
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className="rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none"
          />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-sm font-medium">Password</span>
          <input
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none"
          />
        </label>
        {error && (
          <p role="alert" className="rounded bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </p>
        )}
        <button
          type="submit"
          disabled={submitting}
          className="rounded bg-blue-600 px-4 py-2 font-medium text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {submitting ? "Signing in…" : "Sign in"}
        </button>
      </form>
      <div className="mt-6 space-y-3 text-sm text-gray-600">
        <p>
          Dev credentials: <code>operator</code> / <code>password123</code>{" "}
          or <code>customer</code> / <code>password123</code>
        </p>
        <p className="rounded bg-blue-50 px-3 py-2 text-xs text-blue-800">
          <strong>Tip:</strong> to demo operator + customer side-by-side,
          open one window normally and the other in <em>incognito / private</em>.
          Cookies are scoped per-browser-profile, so two regular tabs share
          the same session.
        </p>
      </div>
    </main>
  );
}
