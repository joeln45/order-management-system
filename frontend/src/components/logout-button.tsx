"use client";

import { useRouter } from "next/navigation";

export function LogoutButton() {
  const router = useRouter();
  async function logout() {
    await fetch("/api/auth/logout", { method: "POST" });
    router.push("/login");
    router.refresh();
  }
  return (
    <button
      onClick={logout}
      className="rounded border border-gray-300 px-3 py-1 text-xs hover:bg-gray-100"
    >
      Sign out
    </button>
  );
}
