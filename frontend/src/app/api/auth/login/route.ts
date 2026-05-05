import { NextResponse } from "next/server";
import { apiFetch, ApiError } from "@/lib/api";
import { ACCESS_COOKIE, REFRESH_COOKIE, USER_COOKIE } from "@/lib/auth";
import type { AuthResponse } from "@/lib/types";

/**
 * Proxies the browser's login form to Spring's /auth/login, then translates
 * the JSON token response into cookies:
 *   - oms_access  (readable by server code, 15 min)
 *   - oms_refresh (HttpOnly, 7 days)
 *   - oms_user    (readable — so the nav can show username/role without decoding the JWT)
 *
 * Proxying like this keeps the backend URL out of the browser's history and
 * lets us mark the refresh token HttpOnly, which JS fetch on the client cannot do.
 */
export async function POST(req: Request) {
  const body = (await req.json()) as { username: string; password: string };

  try {
    const auth = await apiFetch<AuthResponse>("/auth/login", {
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
      httpOnly: false, // readable so server components can forward it
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
