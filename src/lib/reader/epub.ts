// EPUB parsing with JSZip: spine extraction, sanitization, image inlining,
// TOC (nav + NCX) and cover extraction. Runs fully client-side.

import JSZip from "jszip";
import type { TocEntry } from "./types";
import { htmlCharCount } from "./txt";

export interface EpubChapter {
  title: string;
  html: string;
  chars: number;
}

export interface EpubParseResult {
  title: string;
  author: string;
  lang: string;
  chapters: EpubChapter[];
  toc: TocEntry[];
  cover: Blob | null;
}

const MAX_CHAPTERS = 500;

function parseXml(text: string): Document {
  const doc = new DOMParser().parseFromString(text, "application/xml");
  if (doc.querySelector("parsererror")) {
    return new DOMParser().parseFromString(text, "text/html");
  }
  return doc;
}

function resolveZipPath(base: string, href: string): string {
  let p = href;
  try {
    p = decodeURIComponent(href);
  } catch {
    /* keep raw */
  }
  p = p.split("#")[0];
  if (p.startsWith("/")) return p.slice(1);
  const parts = (base + p).split("/");
  const out: string[] = [];
  for (const part of parts) {
    if (part === "..") out.pop();
    else if (part !== "." && part !== "") out.push(part);
  }
  return out.join("/");
}

const MIME_BY_EXT: Record<string, string> = {
  png: "image/png",
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  gif: "image/gif",
  svg: "image/svg+xml",
  webp: "image/webp",
  bmp: "image/bmp",
};

const STRIP_TAGS =
  "script,style,link,meta,iframe,object,embed,video,audio,form,input,button,select,textarea,nav,base";

export async function parseEpub(file: File): Promise<EpubParseResult> {
  const zip = await JSZip.loadAsync(file);

  const containerFile = zip.file("META-INF/container.xml");
  if (!containerFile) throw new Error("Not a valid EPUB file (missing container).");
  const containerDoc = parseXml(await containerFile.async("text"));
  const opfPath =
    containerDoc.querySelector("rootfile")?.getAttribute("full-path") ?? "";
  if (!opfPath) throw new Error("Not a valid EPUB file (missing package document).");
  const opfDir = opfPath.includes("/") ? opfPath.slice(0, opfPath.lastIndexOf("/") + 1) : "";

  const opfFile = zip.file(opfPath);
  if (!opfFile) throw new Error("Not a valid EPUB file (package document unreadable).");
  const opf = parseXml(await opfFile.async("text"));

  // ----- metadata -----
  const title =
    opf.getElementsByTagName("dc:title")[0]?.textContent?.trim() ||
    file.name.replace(/\.(epub|EPUB)$/, "");
  const author =
    opf.getElementsByTagName("dc:creator")[0]?.textContent?.trim() || "Unknown author";
  const lang = opf.getElementsByTagName("dc:language")[0]?.textContent?.trim() || "en";

  // ----- manifest + spine -----
  interface ManifestItem {
    href: string;
    zipPath: string;
    type: string;
    properties: string;
  }
  const manifest = new Map<string, ManifestItem>();
  for (const item of Array.from(opf.querySelectorAll("manifest > item"))) {
    const id = item.getAttribute("id") ?? "";
    const href = item.getAttribute("href") ?? "";
    if (!id || !href) continue;
    manifest.set(id, {
      href,
      zipPath: resolveZipPath(opfDir, href),
      type: item.getAttribute("media-type") ?? "",
      properties: item.getAttribute("properties") ?? "",
    });
  }

  const spineEl = opf.querySelector("spine");
  const spineIds = Array.from(spineEl?.querySelectorAll("itemref") ?? [])
    .map((el) => el.getAttribute("idref") ?? "")
    .filter((id) => manifest.has(id));

  if (!spineIds.length) throw new Error("This EPUB has no readable content (empty spine).");

  // path → spine index
  const spinePathToIndex = new Map<string, number>();
  const spineItems: ManifestItem[] = [];
  for (const id of spineIds) {
    const item = manifest.get(id)!;
    const isDoc =
      /html|xml/.test(item.type) || /\.x?html?$/i.test(item.zipPath);
    if (!isDoc) continue;
    if (!spinePathToIndex.has(item.zipPath)) {
      spinePathToIndex.set(item.zipPath, spineItems.length);
      spineItems.push(item);
    }
  }

  // ----- images → data URLs (lazy, shared cache) -----
  const imageCache = new Map<string, string>();
  async function imageDataUrl(zipPath: string): Promise<string | null> {
    if (imageCache.has(zipPath)) return imageCache.get(zipPath)!;
    const entry = zip.file(zipPath) ?? zip.file(zipPath.toLowerCase());
    if (!entry) return null;
    const ext = zipPath.split(".").pop()?.toLowerCase() ?? "";
    const mime = MIME_BY_EXT[ext] ?? "application/octet-stream";
    const base64 = await entry.async("base64");
    const url = `data:${mime};base64,${base64}`;
    imageCache.set(zipPath, url);
    return url;
  }

  // ----- chapter sanitization -----
  async function sanitize(text: string, baseDir: string): Promise<string> {
    let doc: Document;
    try {
      doc = new DOMParser().parseFromString(text, "application/xhtml+xml");
      if (doc.querySelector("parsererror")) throw new Error("xhtml parse error");
    } catch {
      doc = new DOMParser().parseFromString(text, "text/html");
    }
    const body = doc.body;
    if (!body) return "";

    for (const tag of STRIP_TAGS.split(",")) {
      body.querySelectorAll(tag).forEach((el) => el.remove());
    }

    for (const img of Array.from(body.querySelectorAll("img, image"))) {
      const src =
        img.getAttribute("src") ??
        img.getAttribute("xlink:href") ??
        img.getAttribute("href");
      if (!src || /^(https?:|data:)/i.test(src)) {
        if (src && src.startsWith("data:")) continue;
        img.remove();
        continue;
      }
      const url = await imageDataUrl(resolveZipPath(baseDir, src));
      if (!url) {
        img.remove();
        continue;
      }
      img.removeAttribute("xlink:href");
      img.removeAttribute("href");
      img.setAttribute("src", url);
      if (!img.getAttribute("alt")) img.setAttribute("alt", "");
    }

    for (const el of Array.from(body.querySelectorAll("*"))) {
      el.removeAttribute("class");
      el.removeAttribute("style");
      for (const attr of Array.from(el.attributes)) {
        const n = attr.name.toLowerCase();
        if (n.startsWith("on") || n.startsWith("data-") || n === "srcset") {
          el.removeAttribute(attr.name);
        }
      }
    }

    // links → plain spans (this is a reading app, not a browser)
    for (const a of Array.from(body.querySelectorAll("a"))) {
      const span = doc.createElement("span");
      span.setAttribute("data-xref", a.getAttribute("href") ?? "");
      while (a.firstChild) span.appendChild(a.firstChild);
      a.replaceWith(span);
    }

    return body.innerHTML.trim();
  }

  // ----- process chapters -----
  const chapters: EpubChapter[] = [];
  const spineTitles: (string | null)[] = new Array(spineItems.length).fill(null);
  /** spine index → final chapter index (some spine items get skipped) */
  const spineToChapter = new Map<number, number>();

  // ----- TOC (nav doc preferred, NCX fallback) -----
  const toc: TocEntry[] = [];
  const navItem = [...manifest.values()].find((m) => m.properties.includes("nav"));
  if (navItem) {
    try {
      const navFile = zip.file(navItem.zipPath);
      if (navFile) {
        const navDoc = new DOMParser().parseFromString(
          await navFile.async("text"),
          "text/html"
        );
        const navEl =
          navDoc.querySelector('nav[epub\\:type="toc"]') ?? navDoc.querySelector("nav");
        for (const a of Array.from(navEl?.querySelectorAll("a") ?? [])) {
          const href = a.getAttribute("href") ?? "";
          const label = a.textContent?.trim();
          if (!href || !label || /^(https?:|#)/.test(href)) continue;
          const [pathPart, hash] = href.split("#");
          const zipPath = resolveZipPath(navItem.zipPath.includes("/")
            ? navItem.zipPath.slice(0, navItem.zipPath.lastIndexOf("/") + 1)
            : "", pathPart);
          const idx = spinePathToIndex.get(zipPath);
          if (idx === undefined) continue;
          toc.push({ title: label, chapterIndex: idx, anchor: hash || null });
        }
      }
    } catch {
      /* fall through to NCX */
    }
  }
  if (!toc.length) {
    const ncxId = spineEl?.getAttribute("toc");
    const ncxItem = ncxId
      ? manifest.get(ncxId)
      : [...manifest.values()].find((m) => m.type.includes("dtbncx"));
    if (ncxItem) {
      try {
        const ncxFile = zip.file(ncxItem.zipPath);
        if (ncxFile) {
          const ncx = parseXml(await ncxFile.async("text"));
          const baseDir = ncxItem.zipPath.includes("/")
            ? ncxItem.zipPath.slice(0, ncxItem.zipPath.lastIndexOf("/") + 1)
            : "";
          for (const point of Array.from(ncx.querySelectorAll("navPoint"))) {
            const label = point.querySelector("navLabel > text")?.textContent?.trim();
            const src = point.querySelector("content")?.getAttribute("src") ?? "";
            if (!label || !src || /^(https?:|#)/.test(src)) continue;
            const [pathPart, hash] = src.split("#");
            const idx = spinePathToIndex.get(resolveZipPath(baseDir, pathPart));
            if (idx === undefined) continue;
            toc.push({ title: label, chapterIndex: idx, anchor: hash || null });
          }
        }
      } catch {
        /* ignore — fallback TOC below */
      }
    }
  }

  // Pre-fill spine titles from the TOC
  for (const entry of toc) {
    if (spineTitles[entry.chapterIndex] === null) {
      spineTitles[entry.chapterIndex] = entry.title;
    }
  }

  for (let i = 0; i < spineItems.length && chapters.length < MAX_CHAPTERS; i++) {
    const item = spineItems[i];
    const file = zip.file(item.zipPath);
    if (!file) continue;
    const text = await file.async("text");
    const html = await sanitize(text, item.zipPath.includes("/")
      ? item.zipPath.slice(0, item.zipPath.lastIndexOf("/") + 1)
      : "");
    const chars = htmlCharCount(html);
    if (chars < 20) continue;

    // title: TOC entry → first heading → fallback
    let title = spineTitles[i];
    if (!title) {
      const probe = document.createElement("div");
      probe.innerHTML = html;
      const heading = probe.querySelector("h1, h2, h3, h4, h5, h6");
      title = heading?.textContent?.trim().slice(0, 80) || `Chapter ${chapters.length + 1}`;
      spineTitles[i] = title;
    }

    chapters.push({ title, html, chars });
    spineToChapter.set(i, chapters.length - 1);
  }

  if (!chapters.length) throw new Error("No readable chapters found in this EPUB.");

  // re-map toc chapter indices (chapters may have been dropped)
  const tocMapped: TocEntry[] = [];
  for (const entry of toc) {
    const idx = spineToChapter.get(entry.chapterIndex);
    if (idx === undefined) continue;
    tocMapped.push({ ...entry, chapterIndex: idx });
  }

  // ----- cover -----
  let cover: Blob | null = null;
  try {
    const coverMeta = opf.querySelector('metadata > meta[name="cover"]')?.getAttribute("content");
    const coverItem =
      (coverMeta && manifest.get(coverMeta)) ||
      [...manifest.values()].find((m) => m.properties.includes("cover-image")) ||
      [...manifest.values()].find((m) => /cover.*\.(jpe?g|png|webp)/i.test(m.zipPath));
    if (coverItem) {
      const entry = zip.file(coverItem.zipPath);
      if (entry) cover = await entry.async("blob");
    }
  } catch {
    /* typographic cover fallback */
  }

  // build a TOC from chapter titles if the book had none
  const finalToc: TocEntry[] = tocMapped.length
    ? tocMapped
    : chapters.map((c, i) => ({ title: c.title, chapterIndex: i, anchor: null }));

  return { title, author, lang, chapters, toc: finalToc, cover };
}
