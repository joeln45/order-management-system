import { redirect } from "next/navigation";
import { apiFetch } from "@/lib/api";
import { getSession } from "@/lib/auth";
import type { HateoasCollection, Product } from "@/lib/types";
import { NewOrderForm } from "./new-order-form";

export const dynamic = "force-dynamic";

export default async function NewOrderPage() {
  const session = await getSession();
  if (!session) redirect("/login");
  if (session.user.role !== "CUSTOMER" || !session.user.customerId) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-10">
        <p className="rounded bg-amber-50 px-4 py-3 text-sm text-amber-800">
          Only customer accounts can place orders.
        </p>
      </main>
    );
  }

  // Fetch catalogue server-side so the form has real options to pick from.
  const catalogue = await apiFetch<
    HateoasCollection<"productList", Product>
  >("/products");
  const products = catalogue._embedded?.productList ?? [];

  return (
    <main className="mx-auto max-w-3xl px-6 py-10">
      <h1 className="text-2xl font-semibold">Place a new order</h1>
      <p className="mt-1 text-sm text-gray-600">
        Add line items, then submit. The backend checks stock and
        profitability before accepting — any failure surfaces as an error on
        this page.
      </p>
      <div className="mt-6">
        <NewOrderForm
          customerId={session.user.customerId}
          products={products}
        />
      </div>
    </main>
  );
}
