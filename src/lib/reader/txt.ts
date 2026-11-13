// Plain-text book parsing: detects chapter headings, reflows hard-wrapped
// paragraphs into clean HTML.

export interface ParsedChapter {
  title: string;
  html: string;
  chars: number;
}

export interface ParsedBook {
  chapters: ParsedChapter[];
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

/** Count characters the way the live DOM will (textContent length). */
export function htmlCharCount(html: string): number {
  if (typeof document === "undefined") return html.replace(/<[^>]+>/g, " ").length;
  const div = document.createElement("div");
  div.innerHTML = html;
  return div.textContent?.length ?? 0;
}

const HEADING_RE =
  /^(chapter|part|book|section|prologue|epilogue|preface|foreword|introduction|interlude|afterword|appendix|letter|conclusion)\b.{0,64}$/i;

function isAllCaps(s: string): boolean {
  const letters = s.replace(/[^A-Za-z]/g, "");
  if (letters.length < 3) return false;
  const upper = s.replace(/[^A-Z]/g, "").length;
  return upper / letters.length > 0.7;
}

function toTitle(raw: string): string {
  const line = raw.trim();
  if (isAllCaps(line)) {
    return line
      .toLowerCase()
      .replace(/(^|[\s—–-])([a-z])/g, (_, p, c) => p + c.toUpperCase())
      .replace(/\b(I|V|x|l|c|m)\b/g, (m) => m.toUpperCase());
  }
  return line.replace(/^([a-z])/, (c) => c.toUpperCase());
}

interface RawChapter {
  title: string | null;
  lines: string[];
}

/** Split into blocks on blank lines; a block may be a hard-wrapped paragraph. */
function blocksToHtml(lines: string[]): string {
  const blocks: string[][] = [];
  let current: string[] = [];
  for (const line of lines) {
    if (line.trim() === "") {
      if (current.length) blocks.push(current);
      current = [];
    } else {
      current.push(line.trim());
    }
  }
  if (current.length) blocks.push(current);

  const out: string[] = [];
  for (const block of blocks) {
    // Numbered lists inside a block (e.g. "1. Sun Tzu said: ...") become paragraphs
    const numbered = block.filter((l) => /^\d+[.)]\s+\S/.test(l));
    if (numbered.length >= 2 && numbered.length >= block.length - 1) {
      for (const line of block) out.push(`<p>${escapeHtml(line)}</p>`);
      continue;
    }
    const text = block.join(" ").replace(/\s{2,}/g, " ").trim();
    if (text) out.push(`<p>${escapeHtml(text)}</p>`);
  }
  return out.join("\n");
}

/** Group raw chapters from heading lines. */
function splitByHeadings(lines: string[]): RawChapter[] {
  const chapters: RawChapter[] = [];
  let current: RawChapter = { title: null, lines: [] };
  let headings = 0;
  let started = false;

  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed && trimmed.length <= 70 && HEADING_RE.test(trimmed)) {
      if (started || current.lines.some((l) => l.trim())) {
        chapters.push(current);
      }
      current = { title: toTitle(trimmed), lines: [] };
      headings++;
      started = true;
      continue;
    }
    current.lines.push(line);
  }
  chapters.push(current);
  return headings >= 2 ? chapters : [];
}

/** Fallback: chunk a heading-less text into readable parts. */
function chunkText(lines: string[], target = 16000): RawChapter[] {
  const chapters: RawChapter[] = [];
  let current: RawChapter = { title: "Part 1", lines: [] };
  let size = 0;
  let index = 1;

  for (const line of lines) {
    current.lines.push(line);
    size += line.length;
    if (size >= target && line.trim() === "") {
      chapters.push(current);
      index++;
      current = { title: `Part ${index}`, lines: [] };
      size = 0;
    }
  }
  if (current.lines.some((l) => l.trim())) chapters.push(current);
  return chapters;
}

export function parseTxt(text: string): ParsedBook {
  const normalized = text
    .replace(/\r\n?/g, "\n")
    .replace(/\u00A0/g, " ")
    .replace(/\t/g, "  ")
    // strip Gutenberg-style header/footer markers if present
    .replace(/^\s*\*{3}\s*START OF (THE|THIS) PROJECT GUTENBERG EBOOK[^\n]*\*{3}\s*/im, "")
    .replace(/\s*\*{3}\s*END OF (THE|THIS) PROJECT GUTENBERG EBOOK[^\n]*\*{3}\s*$/im, "")
    .trim();

  const lines = normalized.split("\n");
  let raw = splitByHeadings(lines);
  if (raw.length < 2) raw = chunkText(lines);

  const chapters: ParsedChapter[] = [];
  for (let i = 0; i < raw.length; i++) {
    const body = blocksToHtml(raw[i].lines);
    // Merge tiny sections (e.g. a "Book One" divider) into the next chapter title
    if (body.length < 220 && i < raw.length - 1 && raw[i].title) {
      if (raw[i + 1].title) {
        raw[i + 1].title = `${raw[i].title} · ${raw[i + 1].title}`;
      } else {
        raw[i + 1].title = raw[i].title;
      }
      continue;
    }
    if (!body) continue;
    const html = raw[i].title
      ? `<h2>${escapeHtml(raw[i].title)}</h2>\n${body}`
      : body;
    chapters.push({
      title: raw[i].title ?? `Section ${chapters.length + 1}`,
      html,
      chars: 0,
    });
  }

  for (const ch of chapters) ch.chars = htmlCharCount(ch.html);
  return { chapters: chapters.filter((c) => c.chars > 40) };
}
