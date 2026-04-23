import Link from "next/link";
import { getSession } from "@/lib/auth";
import { LogoutButton } from "./logout-button";

export async function Nav() {
  const session = await getSession();

  return (
    <nav className="border-b border-gray-200 bg-white">
      <div className="mx-auto flex max-w-5xl items-center justify-between px-6 py-3">
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
              <span className="text-gray-600">
                {session.user.username}{" "}
                <span className="rounded bg-gray-100 px-1.5 py-0.5 text-xs">
                  {session.user.role}
                </span>
              </span>
              <LogoutButton />
            </li>
          ) : (
            <li>
              <Link href="/login" className="hover:text-blue-600">
                Sign in
              </Link>
            </li>
          )}
        </ul>
      </div>
    </nav>
  );
}
