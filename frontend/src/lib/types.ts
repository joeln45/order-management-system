// Types mirroring the backend DTOs. Keep these in sync with the Java side —
// when the backend adds/renames a field, TypeScript will tell us at compile
// time exactly which components need updating.

export type Role = "CUSTOMER" | "OPERATOR";

export type OrderStatus = "PENDING" | "SHIPPED" | "OUT_OF_STOCK" | "CANCELLED";

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresInSeconds: number;
  username: string;
  role: Role;
}

export interface Product {
  id: string;
  description: string;
  retailPrice: number;
  wholesalerId: string;
}

export interface OrderItem {
  product: Pick<Product, "id" | "description" | "wholesalerId">;
  quantity: number;
  priceAtPurchase: number;
}

export interface OrderResponse {
  id: string;
  status: OrderStatus;
  customer: { id: string; name: string };
  items: OrderItem[];
  total: number;
}

// HATEOAS envelope shape used by Spring's CollectionModel output.
export interface HateoasCollection<Key extends string, T> {
  _embedded?: Record<Key, T[]>;
  _links: Record<string, { href: string }>;
}

// RFC 7807 problem detail — what GlobalExceptionHandler returns on errors.
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  errors?: Record<string, string>;
}
