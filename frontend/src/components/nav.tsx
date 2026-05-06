import Link from "next/link";
import { getSession } from "@/lib/auth";
import { LogoutButton } from "./logout-button";

/**
 * Top-of-page navigation. Server-rendered so the role check runs against
 * the cookie session, not client state, so there's no flicker between guest/signed-in.
 *
 * For customers we show their *display name* (from the customer profile,
 * not the username); that's what shows up on invoices, orders, etc.
 * Operators don't have a linked customer profile, so we just show the
 * username for them.
 */
export async function Nav() {
  const session = await getSession();
  const displayName =
    session?.user.customerName ?? session?.user.username ?? null;

  return (
    <nav className="border-b border-gray-200 bg-white">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-3">
        <Link href="/" className="text-lg font-semibold">
          Order Management
        </Link>
        <ul className="flex items-center gap-6 text-sm">
          <li>
            <Link href="/products" className="hover:text-blue-600">
              Products
            </Link>
          </li>
          {session?.user.role === "CUSTOMER" && (
            <li>
              <Link href="/orders" className="hover:text-blue-600">
                My orders
              </Link>
            </li>
          )}
          {session?.user.role === "OPERATOR" && (
            <li>
              <Link href="/operator" className="hover:text-blue-600">
                Operator
              </Link>
            </li>
          )}
          {session ? (
            <li className="flex items-center gap-3">
              <span className="text-gray-700">
                <span className="font-medium">{displayName}</span>
                <span
                  className={`ml-2 rounded px-1.5 py-0.5 text-xs ${
                    session.user.role === "OPERATOR"
                      ? "bg-purple-100 text-purple-800"
                      : "bg-blue-100 text-blue-800"
                  }`}
                >
                  {session.user.role}
                </span>
              </span>
              <LogoutButton />
            </li>
          ) : (
            <>
              <li>
                <Link href="/register" className="hover:text-blue-600">
                  Register
                </Link>
              </li>
              <li>
                <Link
                  href="/login"
                  className="rounded bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700"
                >
                  Sign in
                </Link>
              </li>
            </>
          )}
        </ul>
      </div>
    </nav>
  );
}
