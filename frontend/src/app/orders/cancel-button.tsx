"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

export function CancelOrderButton({ orderId }: { orderId: string }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onClick() {
    if (!confirm(`Cancel order ${orderId.substring(0, 8)}?`)) return;
    setBusy(true);
    setError(null);
    try {
      const res = await fetch(`/api/proxy/orders/${orderId}`, { method: "DELETE" });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        setError(body.detail ?? "Cancellation failed");
      } else {
        router.refresh();
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <button
        onClick={onClick}
        disabled={busy}
        className="rounded border border-red-300 px-3 py-1 text-xs text-red-700 hover:bg-red-50 disabled:opacity-50"
      >
        {busy ? "Cancelling…" : "Cancel"}
      </button>
      {error && <p className="text-xs text-red-600">{error}</p>}
    </div>
  );
}
