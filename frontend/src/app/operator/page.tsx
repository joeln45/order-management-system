import { redirect } from "next/navigation";
import { apiFetch, ApiError } from "@/lib/api";
import { getSession } from "@/lib/auth";
import type {
  CustomerSummary,
  HateoasCollection,
  OrderResponse,
  Product,
} from "@/lib/types";
import { OperatorDashboard } from "./operator-dashboard";

export const dynamic = "force-dynamic";

/**
 * Operator landing page. Server-side we fetch the full order + product lists
 * (bearer-authenticated), then hand off to a client component for the
 * interactive bits: status updates, price edits, revenue lookup.
 *
 * Spring's HATEOAS CollectionModel wraps entity models under `_embedded`.
 * For operator orders the inner collection key is `entityModelList` and each
 * entity model wraps the OrderResponse under `.content`.
 */
export default async function OperatorPage() {
  const session = await getSession();
  if (!session) redirect("/login");
  if (session.user.role !== "OPERATOR") {
    return (
      <main className="mx-auto max-w-5xl px-6 py-10">
        <p className="rounded bg-amber-50 px-4 py-3 text-sm text-amber-800">
          Operator accounts only.
        </p>
      </main>
    );
  }

  let orders: OrderResponse[] = [];
  let products: Product[] = [];
  let customers: CustomerSummary[] = [];
  let errorMessage: string | null = null;

  try {
    // Spring HATEOAS picks the embedded key from the wrapped type, not the
    // wrapper. CollectionModel<EntityModel<OrderResponse>> serialises with
    // key `orderResponseList`; each entry has the OrderResponse fields at
    // the top level alongside `_links` (no `.content` nesting).
    const ordersBody = await apiFetch<
      HateoasCollection<"orderResponseList", OrderResponse>
    >("/operator/orders", { bearer: session.accessToken });
    orders = ordersBody._embedded?.orderResponseList ?? [];

    const productsBody = await apiFetch<
      HateoasCollection<"productList", Product>
    >("/products");
    products = productsBody._embedded?.productList ?? [];

    const customersBody = await apiFetch<
      HateoasCollection<"customerSummaryList", CustomerSummary>
    >("/operator/customers", { bearer: session.accessToken });
    customers = customersBody._embedded?.customerSummaryList ?? [];
  } catch (e) {
    errorMessage =
      e instanceof ApiError
        ? `${e.problem.title ?? "Error"}: ${e.problem.detail ?? ""}`
        : "Could not reach the backend.";
  }

  return (
    <main className="mx-auto max-w-6xl px-6 py-10">
      <h1 className="text-2xl font-semibold">Operator dashboard</h1>
      <p className="mt-1 text-sm text-gray-600">
        Manage orders and product pricing. Changes hit the API immediately.
      </p>

      {errorMessage && (
        <p
          role="alert"
          className="mt-6 rounded bg-red-50 px-4 py-3 text-sm text-red-700"
        >
          {errorMessage}
        </p>
      )}

      {!errorMessage && (
        <OperatorDashboard
          orders={orders}
          products={products}
          customers={customers}
        />
      )}
    </main>
  );
}
