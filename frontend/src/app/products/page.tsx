import { apiFetch, ApiError } from "@/lib/api";
import type { HateoasCollection, Product } from "@/lib/types";

export const dynamic = "force-dynamic";

export default async function ProductsPage() {
  let products: Product[] = [];
  let errorMessage: string | null = null;

  try {
    const body = await apiFetch<HateoasCollection<"productList", Product>>(
      "/products",
    );
    products = body._embedded?.productList ?? [];
  } catch (e) {
    errorMessage =
      e instanceof ApiError
        ? `${e.problem.title ?? "Error"}: ${e.problem.detail ?? ""}`
        : "Could not reach the backend — is it running on :8080?";
  }

  return (
    <main className="mx-auto max-w-5xl px-6 py-10">
      <h1 className="text-2xl font-semibold">Products</h1>
      <p className="mt-1 text-sm text-gray-600">
        Live catalogue — synced from the wholesaler at application startup.
      </p>

      {errorMessage && (
        <p
          role="alert"
          className="mt-6 rounded bg-red-50 px-4 py-3 text-sm text-red-700"
        >
          {errorMessage}
        </p>
      )}

      {!errorMessage && products.length === 0 && (
        <p className="mt-6 text-gray-600">
          No products loaded yet. The wholesaler sync runs once at startup — if
          you just booted the backend, give it a few seconds and refresh.
        </p>
      )}

      {products.length > 0 && (
        <ul className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {products.map((p) => (
            <li
              key={p.id}
              className="rounded-lg border border-gray-200 bg-white p-5"
            >
              <h2 className="font-medium">{p.description}</h2>
              <p className="mt-1 text-sm text-gray-500">
                Wholesaler id: <code>{p.wholesalerId}</code>
              </p>
              <p className="mt-3 text-lg font-semibold">
                £{p.retailPrice.toFixed(2)}
              </p>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
