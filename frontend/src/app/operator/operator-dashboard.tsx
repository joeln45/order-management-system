"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type { OrderResponse, OrderStatus, Product } from "@/lib/types";

/**
 * Interactive slab of the operator page. Handles three concerns:
 *   1. Change an order's status via PUT /operator/orders/{id}/status
 *   2. Edit a product's retail price via PUT /operator/products/{id}/price
 *   3. Look up a customer's lifetime revenue via GET /operator/customers/{id}/revenue
 *
 * All three go through `/api/proxy/...` so the browser never sees the token.
 */
const STATUSES: OrderStatus[] = [
  "PENDING",
  "SHIPPED",
  "OUT_OF_STOCK",
  "CANCELLED",
];

export function OperatorDashboard({
  orders,
  products,
}: {
  orders: OrderResponse[];
  products: Product[];
}) {
  const router = useRouter();
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

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
        <h2 className="text-lg font-semibold">All orders</h2>
        {orders.length === 0 ? (
          <p className="mt-2 text-sm text-gray-600">No orders yet.</p>
        ) : (
          <ul className="mt-3 divide-y divide-gray-200 overflow-hidden rounded-lg border border-gray-200 bg-white">
            {orders.map((o) => (
              <li
                key={o.id}
                className="flex flex-col gap-3 p-4 sm:flex-row sm:items-start sm:justify-between"
              >
                <div className="min-w-0 flex-1">
                  <p className="font-medium">Order {o.id.substring(0, 8)}</p>
                  <p className="mt-1 text-sm text-gray-600">
                    Customer {o.customerId} — {o.items.length} item
                    {o.items.length === 1 ? "" : "s"} — Total £
                    {o.total.toFixed(2)}
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
                <div className="flex items-center gap-2">
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
            ))}
          </ul>
        )}
      </section>

      {/* Products */}
      <section>
        <h2 className="text-lg font-semibold">Product pricing</h2>
        {products.length === 0 ? (
          <p className="mt-2 text-sm text-gray-600">No products in catalogue.</p>
        ) : (
          <ul className="mt-3 divide-y divide-gray-200 overflow-hidden rounded-lg border border-gray-200 bg-white">
            {products.map((p) => (
              <PriceRow key={p.id} product={p} onSave={updatePrice} />
            ))}
          </ul>
        )}
      </section>

      {/* Revenue lookup */}
      <section>
        <h2 className="text-lg font-semibold">Customer revenue</h2>
        <RevenueLookup />
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

function RevenueLookup() {
  const [customerId, setCustomerId] = useState("");
  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function lookup(e: React.FormEvent) {
    e.preventDefault();
    if (!customerId.trim()) return;
    setBusy(true);
    setResult(null);
    setError(null);
    try {
      const res = await fetch(
        `/api/proxy/operator/customers/${encodeURIComponent(customerId.trim())}/revenue`,
      );
      if (!res.ok) {
        const b = await res.json().catch(() => ({}));
        setError(b.detail ?? b.title ?? "Lookup failed");
      } else {
        const body = (await res.json()) as {
          customerName: string;
          totalRevenue: number;
        };
        setResult(
          `${body.customerName} → £${Number(body.totalRevenue).toFixed(2)}`,
        );
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={lookup} className="mt-3 flex items-end gap-2">
      <label className="text-sm">
        <span className="mb-1 block text-gray-700">Customer ID</span>
        <input
          value={customerId}
          onChange={(e) => setCustomerId(e.target.value)}
          placeholder="CUST001"
          className="rounded border border-gray-300 px-2 py-1.5"
        />
      </label>
      <button
        type="submit"
        disabled={busy}
        className="rounded bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
      >
        {busy ? "Looking up…" : "Look up"}
      </button>
      {result && <p className="text-sm text-green-700">{result}</p>}
      {error && <p className="text-sm text-red-700">{error}</p>}
    </form>
  );
}
