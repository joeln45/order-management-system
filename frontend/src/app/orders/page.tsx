import { redirect } from "next/navigation";
import { apiFetch, ApiError } from "@/lib/api";
import { getSession } from "@/lib/auth";
import type { HateoasCollection, OrderResponse } from "@/lib/types";
import { CancelOrderButton } from "./cancel-button";
import Link from "next/link";

export const dynamic = "force-dynamic";

export default async function MyOrdersPage() {
  const session = await getSession();
  if (!session) redirect("/login");
  if (session.user.role !== "CUSTOMER") {
    return (
      <main className="mx-auto max-w-5xl px-6 py-10">
        <p className="rounded bg-amber-50 px-4 py-3 text-sm text-amber-800">
          Only customer accounts have an order history.
        </p>
      </main>
    );
  }

  const customerId = session.user.customerId;
  let orders: OrderResponse[] = [];
  let errorMessage: string | null = null;

  if (!customerId) {
    errorMessage = "Your account is not linked to a customer record.";
  } else {
    try {
      const body = await apiFetch<
        HateoasCollection<"orderResponseList", OrderResponse>
      >(`/customers/${customerId}/orders`, { bearer: session.accessToken });
      orders = body._embedded?.orderResponseList ?? [];
    } catch (e) {
      errorMessage =
        e instanceof ApiError
          ? `${e.problem.title ?? "Error"}: ${e.problem.detail ?? ""}`
          : "Could not reach the backend.";
    }
  }

  return (
    <main className="mx-auto max-w-5xl px-6 py-10">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">My orders</h1>
        <Link
          href="/orders/new"
          className="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          Place new order
        </Link>
      </div>

      {errorMessage && (
        <p
          role="alert"
          className="mt-6 rounded bg-red-50 px-4 py-3 text-sm text-red-700"
        >
          {errorMessage}
        </p>
      )}

      {!errorMessage && orders.length === 0 && (
        <p className="mt-6 text-gray-600">
          No orders yet. Click &ldquo;Place new order&rdquo; above to get started.
        </p>
      )}

      {orders.length > 0 && (
        <ul className="mt-6 divide-y divide-gray-200 overflow-hidden rounded-lg border border-gray-200 bg-white">
          {orders.map((o) => (
            <li key={o.id} className="flex items-start justify-between p-4">
              <div>
                <p className="font-medium">Order {o.id.substring(0, 8)}</p>
                <p className="mt-1 text-sm text-gray-600">
                  {o.items.length} item{o.items.length === 1 ? "" : "s"} —
                  Total £{o.total.toFixed(2)}
                </p>
                <ul className="mt-2 text-xs text-gray-500">
                  {o.items.map((it, idx) => (
                    <li key={idx}>
                      {it.quantity} × {it.productDescription} @ £
                      {it.priceAtPurchase.toFixed(2)}
                    </li>
                  ))}
                </ul>
              </div>
              <div className="flex items-center gap-3">
                <StatusPill status={o.status} />
                {o.status === "PENDING" && <CancelOrderButton orderId={o.id} />}
              </div>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}

function StatusPill({ status }: { status: OrderResponse["status"] }) {
  const colour = {
    PENDING: "bg-amber-100 text-amber-800",
    SHIPPED: "bg-green-100 text-green-800",
    OUT_OF_STOCK: "bg-red-100 text-red-800",
    CANCELLED: "bg-gray-200 text-gray-700",
  }[status];
  return (
    <span className={`rounded px-2 py-1 text-xs font-medium ${colour}`}>
      {status}
    </span>
  );
}
