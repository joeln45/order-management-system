import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Emit a self-contained server bundle for the Docker runtime stage.
  // See frontend/Dockerfile — we copy `.next/standalone` into a plain
  // node:alpine image and run `node server.js`, no `next` CLI at runtime.
  output: "standalone",
};

export default nextConfig;
