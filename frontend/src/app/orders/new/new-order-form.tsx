"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type { Product } from "@/lib/types";

/**
 * Lets a customer build an order line-by-line. Each row picks a product
 * and a quantity; submitting POSTs to /api/proxy/orders, which forwards
 * to Spring. The backend has the final say on stock and profitability;
 * any rejection comes back as a ProblemDetail and is surfaced here.
 */
type Line = { productId: string; quantity: number };

export function NewOrderForm({
  customerId,
  products,
}: {
  customerId: string;
  products: Product[];
}) {
  const router = useRouter();
  const [lines, setLines] = useState<Line[]>([
    { productId: products[0]?.id ?? "", quantity: 1 },
  ]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function updateLine(idx: number, patch: Partial<Line>) {
    setLines((curr) =>
      curr.map((l, i) => (i === idx ? { ...l, ...patch } : l)),
    );
  }

  function addLine() {
    setLines((curr) => [
      ...curr,
      { productId: products[0]?.id ?? "", quantity: 1 },
    ]);
  }

  function removeLine(idx: number) {
    setLines((curr) => curr.filter((_, i) => i !== idx));
  }

  const estimatedTotal = lines.reduce((sum, l) => {
    const p = products.find((x) => x.id === l.productId);
    return sum + (p ? p.retailPrice * l.quantity : 0);
  }, 0);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    const cleaned = lines.filter((l) => l.productId && l.quantity > 0);
    if (cleaned.length === 0) {
      setError("Add at least one line item.");
      return;
    }

    setSubmitting(true);
    try {
      const res = await fetch("/api/proxy/orders", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          customerId,
          items: cleaned.map((l) => ({
            productId: l.productId,
            quantity: l.quantity,
          })),
        }),
      });

      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        // Backend returns RFC 7807; prefer detail, fall back to title.
        setError(body.detail ?? body.title ?? `Order failed (${res.status})`);
        return;
      }

      router.push("/orders");
      router.refresh();
    } catch {
      setError("Could not reach the server.");
    } finally {
      setSubmitting(false);
    }
  }

  if (products.length === 0) {
    return (
      <p className="rounded bg-amber-50 px-4 py-3 text-sm text-amber-800">
        No products in the catalogue yet. Ask an operator to seed data.
      </p>
    );
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      <ul className="space-y-3">
        {lines.map((line, idx) => (
          <li
            key={idx}
            className="flex items-end gap-3 rounded border border-gray-200 bg-white p-3"
          >
            <label className="flex-1 text-sm">
              <span className="mb-1 block text-gray-700">Product</span>
              <select
                value={line.productId}
                onChange={(e) =>
                  updateLine(idx, { productId: e.target.value })
                }
                className="w-full rounded border border-gray-300 px-2 py-1.5"
              >
                {products.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.description} · £{p.retailPrice.toFixed(2)}
                  </option>
                ))}
              </select>
            </label>
            <label className="w-28 text-sm">
              <span className="mb-1 block text-gray-700">Quantity</span>
              <input
                type="number"
                min={1}
                value={line.quantity}
                onChange={(e) =>
                  updateLine(idx, {
                    quantity: Math.max(1, Number(e.target.value) || 1),
                  })
                }
                className="w-full rounded border border-gray-300 px-2 py-1.5"
              />
            </label>
            {lines.length > 1 && (
              <button
                type="button"
                onClick={() => removeLine(idx)}
                className="rounded border border-gray-300 px-2 py-1.5 text-xs text-gray-700 hover:bg-gray-50"
              >
                Remove
              </button>
            )}
          </li>
        ))}
      </ul>

      <div className="flex items-center justify-between">
        <button
          type="button"
          onClick={addLine}
          className="rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
        >
          + Add line
        </button>
        <p className="text-sm text-gray-600">
          Estimated total:{" "}
          <span className="font-medium text-gray-900">
            £{estimatedTotal.toFixed(2)}
          </span>
        </p>
      </div>

      {error && (
        <p
          role="alert"
          className="rounded bg-red-50 px-4 py-3 text-sm text-red-700"
        >
          {error}
        </p>
      )}

      <button
        type="submit"
        disabled={submitting}
        className="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
      >
        {submitting ? "Placing order…" : "Place order"}
      </button>
    </form>
  );
}
