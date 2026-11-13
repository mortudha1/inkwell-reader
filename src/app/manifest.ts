import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Inkwell — eReader",
    short_name: "Inkwell",
    description:
      "A calm, Kindle-style e-reader. Import EPUB & TXT books, customize your page, and tap any word for a definition.",
    start_url: "/",
    display: "standalone",
    orientation: "any",
    background_color: "#f8f2e6",
    theme_color: "#f8f2e6",
    categories: ["books", "education", "lifestyle"],
    icons: [
      {
        src: "/icons/icon-192.png",
        sizes: "192x192",
        type: "image/png",
      },
      {
        src: "/icons/icon-512.png",
        sizes: "512x512",
        type: "image/png",
      },
      {
        src: "/icons/icon-512.png",
        sizes: "512x512",
        type: "image/png",
        purpose: "maskable",
      },
    ],
  };
}
