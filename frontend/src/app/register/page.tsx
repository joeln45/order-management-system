"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

/**
 * Customer self-registration form.
 * Submits to /api/auth/register which proxies to Spring's POST /auth/register,
 * sets the auth cookies, and returns the new user info. On success we redirect
 * straight to /orders; the user is immediately signed in.
 *
 * Field validation runs on the backend (Spring Bean Validation); the frontend
 * surfaces the `errors` map from the RFC 7807 ProblemDetail response.
 */
type FieldErrors = Record<string, string>;

export default function RegisterPage() {
  const router = useRouter();
  const [fields, setFields] = useState({
    username: "",
    password: "",
    name: "",
    email: "",
    postalAddress: "",
  });
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [globalError, setGlobalError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function set(key: keyof typeof fields) {
    return (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      setFields((f) => ({ ...f, [key]: e.target.value }));
      // Clear the per-field error as the user types.
      setFieldErrors((fe) => {
        const next = { ...fe };
        delete next[key];
        return next;
      });
    };
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setGlobalError(null);
    setFieldErrors({});
    setSubmitting(true);

    try {
      const res = await fetch("/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(fields),
      });

      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        // Spring Bean Validation returns `errors: { fieldName: message }`.
        if (body.errors && typeof body.errors === "object") {
          setFieldErrors(body.errors as FieldErrors);
        } else {
          setGlobalError(body.detail ?? body.title ?? "Registration failed");
        }
        return;
      }

      // Cookies are set by the route handler; we're immediately signed in.
      router.push("/orders");
      router.refresh();
    } catch {
      setGlobalError("Network error: is the backend running?");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="mx-auto max-w-md px-6 py-10">
      <h1 className="mb-1 text-2xl font-semibold">Create an account</h1>
      <p className="mb-6 text-sm text-gray-600">
        Already have one?{" "}
        <Link href="/login" className="text-blue-600 hover:underline">
          Sign in
        </Link>
      </p>

      <form onSubmit={onSubmit} className="space-y-4">
        {/* Name */}
        <Field
          label="Full name"
          type="text"
          autoComplete="name"
          value={fields.name}
          onChange={set("name")}
          error={fieldErrors.name}
          placeholder="Alice Example"
        />

        {/* Email */}
        <Field
          label="Email"
          type="email"
          autoComplete="email"
          value={fields.email}
          onChange={set("email")}
          error={fieldErrors.email}
          placeholder="alice@example.com"
        />

        {/* Postal address */}
        <div className="flex flex-col gap-1">
          <label className="text-sm font-medium text-gray-700">
            Postal address
          </label>
          <textarea
            required
            rows={2}
            value={fields.postalAddress}
            onChange={set("postalAddress")}
            placeholder="10 Downing Street, London, SW1A 2AA"
            className={`rounded border px-3 py-2 text-sm focus:border-blue-500 focus:outline-none ${
              fieldErrors.postalAddress
                ? "border-red-400 bg-red-50"
                : "border-gray-300"
            }`}
          />
          {fieldErrors.postalAddress && (
            <p className="text-xs text-red-600">{fieldErrors.postalAddress}</p>
          )}
        </div>

        <hr className="my-2 border-gray-200" />

        {/* Username */}
        <Field
          label="Username"
          type="text"
          autoComplete="username"
          value={fields.username}
          onChange={set("username")}
          error={fieldErrors.username}
          placeholder="alice42"
          hint="Letters, digits, _ . - only. 3–50 characters."
        />

        {/* Password */}
        <Field
          label="Password"
          type="password"
          autoComplete="new-password"
          value={fields.password}
          onChange={set("password")}
          error={fieldErrors.password}
          hint="Minimum 8 characters."
        />

        {globalError && (
          <p
            role="alert"
            className="rounded bg-red-50 px-3 py-2 text-sm text-red-700"
          >
            {globalError}
          </p>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded bg-blue-600 px-4 py-2 font-medium text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {submitting ? "Creating account…" : "Create account"}
        </button>
      </form>
    </main>
  );
}

function Field({
  label,
  type,
  autoComplete,
  value,
  onChange,
  error,
  placeholder,
  hint,
}: {
  label: string;
  type: string;
  autoComplete?: string;
  value: string;
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  error?: string;
  placeholder?: string;
  hint?: string;
}) {
  return (
    <div className="flex flex-col gap-1">
      <label className="text-sm font-medium text-gray-700">{label}</label>
      <input
        type={type}
        autoComplete={autoComplete}
        required
        value={value}
        onChange={onChange}
        placeholder={placeholder}
        className={`rounded border px-3 py-2 text-sm focus:border-blue-500 focus:outline-none ${
          error ? "border-red-400 bg-red-50" : "border-gray-300"
        }`}
      />
      {hint && !error && <p className="text-xs text-gray-500">{hint}</p>}
      {error && <p className="text-xs text-red-600">{error}</p>}
    </div>
  );
}
