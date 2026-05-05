"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type {
  CustomerSummary,
  OrderResponse,
  OrderStatus,
  Product,
} from "@/lib/types";

/**
 * Interactive slab of the operator page. Handles three concerns:
 *   1. Change an order's status via PUT /operator/orders/{id}/status
 *   2. Edit a product's retail price via PUT /operator/products/{id}/price
 *   3. Look up a customer's lifetime revenue via GET /operator/customers/{id}/revenue
 *
 * All three go through `/api/proxy/...` so the browser never sees the token.
 *
 * Customers are passed in from the server component so the revenue lookup
 * can render a dropdown instead of a free-text field — no typos, no
 * "customer not found" guessing.
 */
const STATUSES: OrderStatus[] = [
  "PENDING",
  "SHIPPED",
  "OUT_OF_STOCK",
  "CANCELLED",
];

const STATUS_STYLES: Record<OrderStatus, string> = {
  PENDING: "bg-amber-100 text-amber-800",
  SHIPPED: "bg-green-100 text-green-800",
  OUT_OF_STOCK: "bg-red-100 text-red-800",
  CANCELLED: "bg-gray-200 text-gray-700",
};

export function OperatorDashboard({
  orders,
  products,
  customers,
}: {
  orders: OrderResponse[];
  products: Product[];
  customers: CustomerSummary[];
}) {
  const router = useRouter();
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Lookup customer name by id so order rows can show "Demo Customer (CUST001)"
  // instead of just "CUST001".
  const customerNameById = new Map(customers.map((c) => [c.id, c.name]));

  async function call(
    path: string,
    method: "PUT" | "GET",
    body?: unknown,
  ): Promise<Response> {
    setError(null);
    setMessage(null);
    return fetch(`/api/proxy${path}`, {
      method,
      headers: body ? { "Content-Type": "application/json" } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  async function updateStatus(orderId: string, status: OrderStatus) {
    const res = await call(`/operator/orders/${orderId}/status`, "PUT", {
      status,
    });
    if (!res.ok) {
      const b = await res.json().catch(() => ({}));
      setError(b.detail ?? b.title ?? "Status update failed");
    } else {
      setMessage(`Order ${orderId.substring(0, 8)} → ${status}`);
      router.refresh();
    }
  }

  async function updatePrice(productId: string, retailPrice: number) {
    const res = await call(`/operator/products/${productId}/price`, "PUT", {
      retailPrice,
    });
    if (!res.ok) {
      const b = await res.json().catch(() => ({}));
      setError(b.detail ?? b.title ?? "Price update failed");
    } else {
      setMessage(`Price updated to £${retailPrice.toFixed(2)}`);
      router.refresh();
    }
  }

  return (
    <div className="mt-6 space-y-10">
      {message && (
        <p className="rounded bg-green-50 px-4 py-3 text-sm text-green-800">
          {message}
        </p>
      )}
      {error && (
        <p
          role="alert"
          className="rounded bg-red-50 px-4 py-3 text-sm text-red-700"
        >
          {error}
        </p>
      )}

      {/* Orders */}
      <section>
        <div className="flex items-baseline justify-between">
          <h2 className="text-lg font-semibold">All orders</h2>
          <span className="text-sm text-gray-500">
            {orders.length} total
          </span>
        </div>
        {orders.length === 0 ? (
          <p className="mt-2 text-sm text-gray-600">No orders yet.</p>
        ) : (
          <ul className="mt-3 divide-y divide-gray-200 overflow-hidden rounded-lg border border-gray-200 bg-white">
            {orders.map((o) => {
              const customerName =
                customerNameById.get(o.customerId) ?? "Unknown customer";
              return (
                <li
                  key={o.id}
                  className="flex flex-col gap-3 p-4 sm:flex-row sm:items-start sm:justify-between"
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-medium">
                        Order {o.id.substring(0, 8)}
                      </p>
                      <span
                        className={`rounded px-2 py-0.5 text-xs font-medium ${STATUS_STYLES[o.status]}`}
                      >
                        {o.status}
                      </span>
                    </div>
                    <p className="mt-1 text-sm text-gray-600">
                      <span className="font-medium text-gray-800">
                        {customerName}
                      </span>{" "}
                      <span className="text-gray-400">({o.customerId})</span>{" "}
                      — {o.items.length} item
                      {o.items.length === 1 ? "" : "s"} — Total £
                      {o.total.toFixed(2)}
                    </p>
                    {o.orderDate && (
                      <p className="mt-0.5 text-xs text-gray-500">
                        Placed{" "}
                        {new Date(o.orderDate).toLocaleString(undefined, {
                          dateStyle: "medium",
                          timeStyle: "short",
                        })}
                      </p>
                    )}
                    <ul className="mt-2 text-xs text-gray-500">
                      {o.items.map((it, idx) => (
                        <li key={idx}>
                          {it.quantity} × {it.productDescription} @ £
                          {it.priceAtPurchase.toFixed(2)}
                        </li>
                      ))}
                    </ul>
                  </div>
                  <div className="flex flex-col items-end gap-1">
                    <label className="text-xs text-gray-500">
                      Update status
                    </label>
                    <select
                      defaultValue={o.status}
                      onChange={(e) =>
                        updateStatus(o.id, e.target.value as OrderStatus)
                      }
                      className="rounded border border-gray-300 px-2 py-1 text-sm"
                    >
                      {STATUSES.map((s) => (
                        <option key={s} value={s}>
                          {s}
                        </option>
                      ))}
                    </select>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      {/* Products */}
      <section>
        <div className="flex items-baseline justify-between">
          <h2 className="text-lg font-semibold">Product pricing</h2>
          <span className="text-sm text-gray-500">
            {products.length} product{products.length === 1 ? "" : "s"}
          </span>
        </div>
        {products.length === 0 ? (
          <p className="mt-2 text-sm text-gray-600">
            No products in catalogue.
          </p>
        ) : (
          <ul className="mt-3 divide-y divide-gray-200 overflow-hidden rounded-lg border border-gray-200 bg-white">
            {products.map((p) => (
              <PriceRow key={p.id} product={p} onSave={updatePrice} />
            ))}
          </ul>
        )}
      </section>

      {/* Revenue lookup — dropdown of seeded customers, no free-text. */}
      <section>
        <h2 className="text-lg font-semibold">Customer revenue</h2>
        <p className="mt-1 text-xs text-gray-500">
          Lifetime total across all non-cancelled orders.
        </p>
        <RevenueLookup customers={customers} />
      </section>
    </div>
  );
}

function PriceRow({
  product,
  onSave,
}: {
  product: Product;
  onSave: (id: string, price: number) => Promise<void>;
}) {
  const [price, setPrice] = useState(product.retailPrice.toFixed(2));
  const dirty = Number(price) !== product.retailPrice;

  return (
    <li className="flex items-center justify-between gap-3 p-4">
      <div className="min-w-0 flex-1">
        <p className="font-medium">{product.description}</p>
        <p className="text-xs text-gray-500">
          {product.id} · wholesaler {product.wholesalerId}
        </p>
      </div>
      <div className="flex items-center gap-2">
        <span className="text-sm text-gray-600">£</span>
        <input
          type="number"
          step="0.01"
          min="0"
          value={price}
          onChange={(e) => setPrice(e.target.value)}
          className="w-24 rounded border border-gray-300 px-2 py-1 text-sm"
        />
        <button
          disabled={!dirty || !price}
          onClick={() => onSave(product.id, Number(price))}
          className="rounded bg-blue-600 px-3 py-1 text-xs font-medium text-white hover:bg-blue-700 disabled:opacity-40"
        >
          Save
        </button>
      </div>
    </li>
  );
}

function RevenueLookup({ customers }: { customers: CustomerSummary[] }) {
  const [customerId, setCustomerId] = useState(customers[0]?.id ?? "");
  const [result, setResult] = useState<{
    name: string;
    total: number;
  } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function lookup(e: React.FormEvent) {
    e.preventDefault();
    if (!customerId) return;
    setBusy(true);
    setResult(null);
    setError(null);
    try {
      const res = await fetch(
        `/api/proxy/operator/customers/${encodeURIComponent(customerId)}/revenue`,
      );
      if (!res.ok) {
        const b = await res.json().catch(() => ({}));
        setError(b.detail ?? b.title ?? "Lookup failed");
      } else {
        const body = (await res.json()) as {
          customerName: string;
          totalRevenue: number;
        };
        setResult({
          name: body.customerName,
          total: Number(body.totalRevenue),
        });
      }
    } finally {
      setBusy(false);
    }
  }

  if (customers.length === 0) {
    return (
      <p className="mt-3 text-sm text-gray-600">
        No customers in the system yet.
      </p>
    );
  }

  return (
    <form onSubmit={lookup} className="mt-3 flex flex-wrap items-end gap-3">
      <label className="text-sm">
        <span className="mb-1 block text-gray-700">Customer</span>
        <select
          value={customerId}
          onChange={(e) => setCustomerId(e.target.value)}
          className="min-w-64 rounded border border-gray-300 px-2 py-1.5"
        >
          {customers.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name} ({c.id})
            </option>
          ))}
        </select>
      </label>
      <button
        type="submit"
        disabled={busy}
        className="rounded bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
      >
        {busy ? "Looking up…" : "Look up"}
      </button>
      {result && (
        <p className="text-sm">
          <span className="text-gray-700">{result.name}</span>{" "}
          <span className="text-gray-400">→</span>{" "}
          <span className="font-semibold text-green-700">
            £{result.total.toFixed(2)}
          </span>
        </p>
      )}
      {error && <p className="text-sm text-red-700">{error}</p>}
    </form>
  );
}
