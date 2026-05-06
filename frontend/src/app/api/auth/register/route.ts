import { NextResponse } from "next/server";
import { apiFetch, ApiError } from "@/lib/api";
import { ACCESS_COOKIE, REFRESH_COOKIE, USER_COOKIE } from "@/lib/auth";
import type { AuthResponse } from "@/lib/types";

/**
 * Proxies the registration form to Spring's /auth/register, then sets the
 * same three cookies as the login route so the user is immediately signed in.
 * The browser never sees the raw tokens; they travel Next.js → Spring only.
 */
export async function POST(req: Request) {
  const body = await req.json();

  try {
    const auth = await apiFetch<AuthResponse>("/auth/register", {
      method: "POST",
      body: JSON.stringify(body),
    });

    const res = NextResponse.json({
      username: auth.username,
      role: auth.role,
      customerId: auth.customerId,
      customerName: auth.customerName,
    });

    res.cookies.set(ACCESS_COOKIE, auth.accessToken, {
      httpOnly: false,
      sameSite: "lax",
      path: "/",
      maxAge: auth.accessTokenExpiresInSeconds,
    });
    res.cookies.set(REFRESH_COOKIE, auth.refreshToken, {
      httpOnly: true,
      sameSite: "lax",
      path: "/",
      maxAge: 60 * 60 * 24 * 7,
    });
    res.cookies.set(
      USER_COOKIE,
      JSON.stringify({
        username: auth.username,
        role: auth.role,
        customerId: auth.customerId,
        customerName: auth.customerName,
      }),
      {
        httpOnly: false,
        sameSite: "lax",
        path: "/",
        maxAge: auth.accessTokenExpiresInSeconds,
      },
    );

    return res;
  } catch (e) {
    if (e instanceof ApiError) {
      return NextResponse.json(e.problem, { status: e.status });
    }
    return NextResponse.json({ title: "Network error" }, { status: 502 });
  }
}
