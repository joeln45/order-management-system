import { NextRequest, NextResponse } from "next/server";
import { cookies } from "next/headers";
import { API_BASE } from "@/lib/api";
import { ACCESS_COOKIE } from "@/lib/auth";

/**
 * Server-side proxy. The browser hits /api/proxy/<backend-path>; this
 * handler reads the access-token cookie, slaps it on as a Bearer header,
 * and forwards to Spring. Client components never have to handle the
 * token, and the browser only ever talks to Next, which sidesteps CORS.
 *
 * GET/POST/PUT/DELETE all just delegate to forward().
 */
async function forward(req: NextRequest, path: string[]) {
  const jar = await cookies();
  const token = jar.get(ACCESS_COOKIE)?.value;

  const suffix = "/" + path.join("/");
  const search = req.nextUrl.search;
  const url = `${API_BASE}${suffix}${search}`;

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const hasBody = req.method !== "GET" && req.method !== "DELETE";
  const body = hasBody ? await req.text() : undefined;

  const res = await fetch(url, { method: req.method, headers, body });

  const respBody = await res.text();
  // Preserve status + body. NextResponse strips hop-by-hop headers for us.
  return new NextResponse(respBody || null, {
    status: res.status,
    headers: {
      "Content-Type": res.headers.get("Content-Type") ?? "application/json",
    },
  });
}

export async function GET(req: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  return forward(req, (await ctx.params).path);
}
export async function POST(req: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  return forward(req, (await ctx.params).path);
}
export async function PUT(req: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  return forward(req, (await ctx.params).path);
}
export async function DELETE(req: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  return forward(req, (await ctx.params).path);
}
