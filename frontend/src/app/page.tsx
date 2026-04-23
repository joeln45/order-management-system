import Link from "next/link";
import { getSession } from "@/lib/auth";

export default async function Home() {
  const session = await getSession();
  return (
    <main className="mx-auto max-w-3xl px-6 py-12">
      <h1 className="text-3xl font-semibold tracking-tight">
        Order Management System
      </h1>
      <p className="mt-3 text-gray-600">
        Drop-shipping retailer storefront and operator console. Products sync
        nightly from the wholesaler; orders flow through{" "}
        <code>PENDING → SHIPPED</code> with stock + profitability checks on
        every placement.
      </p>

      <div className="mt-8 grid gap-4 sm:grid-cols-2">
        <Link
          href="/products"
          className="rounded-lg border border-gray-200 bg-white p-6 hover:border-blue-500"
        >
          <h2 className="font-medium">Browse products</h2>
          <p className="mt-1 text-sm text-gray-600">
            Public catalogue — no sign-in required.
          </p>
        </Link>
        {session ? (
          <Link
            href={session.user.role === "OPERATOR" ? "/operator" : "/orders"}
            className="rounded-lg border border-gray-200 bg-white p-6 hover:border-blue-500"
          >
            <h2 className="font-medium">
              {session.user.role === "OPERATOR"
                ? "Operator dashboard"
                : "My orders"}
            </h2>
            <p className="mt-1 text-sm text-gray-600">
              Signed in as {session.user.username}.
            </p>
          </Link>
        ) : (
          <Link
            href="/login"
            className="rounded-lg border border-gray-200 bg-white p-6 hover:border-blue-500"
          >
            <h2 className="font-medium">Sign in</h2>
            <p className="mt-1 text-sm text-gray-600">
              Use <code>operator</code> / <code>password123</code> or{" "}
              <code>customer</code> / <code>password123</code>.
            </p>
          </Link>
        )}
      </div>
    </main>
  );
}
