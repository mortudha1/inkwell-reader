import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  /* config options here */
  typescript: {
    ignoreBuildErrors: true,
  },
  reactStrictMode: false,
  devIndicators: false,
  allowedDevOrigins: [
    "https://*.space-z.ai",
    "https://preview-chat-5aaf32ae-8233-46f5-a898-1d43cd765c34.space-z.ai",
  ],
};

export default nextConfig;
