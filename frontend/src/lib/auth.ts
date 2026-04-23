import { cookies } from "next/headers";
import type { Role } from "./types";

/**
 * Cookie names. The access token lives in a readable cookie (short-lived,
 * 15 min) so middleware/server components can attach it to backend calls.
 * The refresh token is HttpOnly — browser JS cannot read or steal it.
 */
export const ACCESS_COOKIE = "oms_access";
export const REFRESH_COOKIE = "oms_refresh";
export const USER_COOKIE = "oms_user";

export interface SessionUser {
  username: string;
  role: Role;
  customerId: string | null;
}

/** Server-side: read the current session from cookies, or null if not signed in. */
export async function getSession(): Promise<{
  accessToken: string;
  user: SessionUser;
} | null> {
  const jar = await cookies();
  const access = jar.get(ACCESS_COOKIE)?.value;
  const userRaw = jar.get(USER_COOKIE)?.value;
  if (!access || !userRaw) return null;
  try {
    return { accessToken: access, user: JSON.parse(userRaw) as SessionUser };
  } catch {
    return null;
  }
}
