import { NextResponse } from "next/server";
import { ACCESS_COOKIE, REFRESH_COOKIE, USER_COOKIE } from "@/lib/auth";

export async function POST() {
  const res = NextResponse.json({ ok: true });
  for (const name of [ACCESS_COOKIE, REFRESH_COOKIE, USER_COOKIE]) {
    res.cookies.set(name, "", { path: "/", maxAge: 0 });
  }
  return res;
}
