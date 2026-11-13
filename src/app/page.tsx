"use client";

import { useEffect, useMemo, useState, type CSSProperties, type ReactNode } from "react";
import Image from "next/image";
import {
  ALargeSmall,
  AlignJustify,
  AlignLeft,
  BookMarked,
  BookOpen,
  Bookmark,
  ChevronDown,
  ChevronLeft,
  Contrast,
  Highlighter,
  List,
  PencilLine,
  Smartphone,
  StickyNote,
  SunMedium,
  Tablet,
  Type,
  Zap,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";

const MARGIN_FACTORS = [82, 75, 67, 58]; // % of screen width, landscape

/* -------------------------------------------------------------- data */

const FEATURES = [
  {
    icon: BookOpen,
    title: "True pagination",
    desc: "Real page-turn layout with chapter-aware breaks — not endless scroll.",
  },
  {
    icon: Type,
    title: "Book typography",
    desc: "Serif first, 20sp default, adjustable size, line height and paragraph gap.",
  },
  {
    icon: Tablet,
    title: "Tablet-first layout",
    desc: "A centered 70–80% reading column, tuned for 10–13\u2033 screens in any orientation.",
  },
  {
    icon: Bookmark,
    title: "Bookmarks, highlights, notes",
    desc: "Long-press any word to look it up, highlight the passage or attach a note.",
  },
  {
    icon: Contrast,
    title: "Warm paper themes",
    desc: "Warm paper, white and dark — flat monochrome palettes, no color noise.",
  },
  {
    icon: SunMedium,
    title: "Display controls",
    desc: "Brightness, optional full-refresh blink (off by default) and haptics.",
  },
  {
    icon: BookMarked,
    title: "Per-book settings",
    desc: "Every book remembers its own typography, margins and reading position.",
  },
  {
    icon: Smartphone,
    title: "Ergonomic navigation",
    desc: "Tap zones, swipes, volume keys and spacebar. Distraction-free in one tap.",
  },
] as const;

const INSTALL_STEPS = [
  {
    title: "Download the APK",
    desc: "Use the dark “Download APK” button at the top of this page.",
  },
  {
    title: "Allow unknown apps",
    desc: "Settings > Apps > Special access > Install unknown apps.",
  },
  {
    title: "Open the downloaded file",
    desc: "Tap Install when Android asks for confirmation.",
  },
  {
    title: "Add your first book",
    desc: "Open InkBook, press “+” and pick any .txt, .epub or .pdf.",
  },
];

const CHAPTERS = [
  { title: "Chapter One — The Villa Rose", page: 12 },
  { title: "Chapter Two — The Gray Vest", page: 31 },
  { title: "Chapter Three — The Keyed Elevator", page: 57 },
  { title: "Chapter Four — The Shore Road", page: 78 },
  { title: "Chapter Five — Morning", page: 94 },
];

const PAGE_SETS: { heading: string | null; paras: string[] }[] = [
  {
    heading: "Chapter One",
    paras: [
      "The Villa Rose kept its lights low on card nights, and by midnight the gaming room had gone the color of ash. The dealer in the gray vest swept the felt once more and waited, patient as a lamp.",
      "Mara sat with her back to the lake window and counted the room the way her father taught her: two exits, four cameras, one man who had not touched his drink all evening.",
      "He wore his stillness like a coat. When the pit boss drifted past, he turned a single chip between his fingers, and the chip made no sound at all, which was how she knew it was not a chip.",
      "A woman in the far chair laughed at something the roulette wheel had said. No one else laughed. Somewhere behind the panelled doors a telephone rang twice and stopped.",
    ],
  },
  {
    heading: null,
    paras: [
      "At two the house closed its tables without a word. The players filed out past the fountain, where the water had been shut off for the season, and the bronze boy in the middle held his empty urn toward the dark.",
      "Mara stayed. She had learned at the Villa Rose that the night never really ends there — it only changes tables. She ordered nothing, folded her hands, and watched the gray room count itself to sleep.",
      "Outside, the lake moved in its sleep. A lamp in a first-floor window went dark, then another, until the house was a hull resting on black water.",
      "The dealer in the gray vest locked the doors at half past two and walked home along the shore road, whistling a tune that no one had ever heard him hum indoors.",
    ],
  },
  {
    heading: null,
    paras: [
      "In the morning the tables were wiped and re-clothed, the chips were counted into their trays, and the whole villa smelled of coffee and cut lemons. It was as if the night had never happened, or had happened to someone else.",
      "Mara came down at nine and asked for the room at the end of the corridor, the one with the keyed elevator. The clerk did not ask why. At the Villa Rose, questions were the one currency nobody carried.",
      "The man who had not touched his drink came down at ten, exactly, and asked for the same room. The clerk pretended to consult the ledger, which was blank, and gave him the room next to hers.",
      "By eleven they were both standing at the keyed elevator, waiting, with the polite patience of people who already knew how the day would end.",
    ],
  },
];

/* ------------------------------------------------------- theme tokens */

const THEMES = [
  {
    name: "Paper",
    bg: "#E9E8E2",
    ink: "#222222",
    soft: "#6E6B63",
    line: "#CFCDC5",
    btn: "#F2F1EB",
    border: "#B7B4AB",
    band: "#DBD9D0",
    scrim: "rgba(0,0,0,0.18)",
  },
  {
    name: "White",
    bg: "#F7F5F0",
    ink: "#1C1C1B",
    soft: "#6F6D67",
    line: "#D6D3CB",
    btn: "#FDFCF9",
    border: "#C2BFB6",
    band: "#EAE7DE",
    scrim: "rgba(0,0,0,0.18)",
  },
  {
    name: "Dark",
    bg: "#151514",
    ink: "#BAB8B1",
    soft: "#7C7A74",
    line: "#2B2A27",
    btn: "#1D1D1B",
    border: "#43423E",
    band: "#2C2B27",
    scrim: "rgba(0,0,0,0.45)",
  },
] as const;


/* --------------------------------------------------------- replica UI */

function ToolButton({
  label,
  active,
  onClick,
  children,
  ink,
  border,
  btn,
}: {
  label: string;
  active?: boolean;
  onClick?: () => void;
  children: ReactNode;
  ink: string;
  border: string;
  btn: string;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      className="flex h-10 flex-1 items-center justify-center rounded-[3px] border transition-colors"
      style={{
        background: active ? ink : btn,
        borderColor: active ? ink : border,
        color: active ? btn : ink,
        maxWidth: 56,
      }}
    >
      {children}
    </button>
  );
}

function ReaderReplica() {
  const [themeIdx, setThemeIdx] = useState(0);
  const [chrome, setChrome] = useState(true);
  const [page, setPage] = useState(12);
  const [bookmarked, setBookmarked] = useState(false);
  const [tocOpen, setTocOpen] = useState(false);
  const [panel, setPanel] = useState<null | "type" | "display">(null);
  const [font, setFont] = useState<0 | 1 | 2>(0);
  const [size, setSize] = useState(20);
  const [lineH, setLineH] = useState(1.5);
  const [paraGap, setParaGap] = useState(35);
  const [margin, setMargin] = useState(1);
  const [justify, setJustify] = useState(true);
  const [brightness, setBrightness] = useState(100);
  const [flash, setFlash] = useState(false);
  const [flashOn, setFlashOn] = useState(false);
  const [hint, setHint] = useState(true);

  const th = THEMES[themeIdx];
  const total = 100;
  const pct = Math.max(1, Math.min(100, Math.round((page / total) * 100)));

  useEffect(() => {
    const t = setTimeout(() => setHint(false), 4200);
    return () => clearTimeout(t);
  }, []);

  const turn = (dir: 1 | -1) => {
    setPage((p) => Math.max(1, Math.min(total, p + dir)));
    if (flash) {
      setFlashOn(true);
      setTimeout(() => setFlashOn(false), 130);
    }
  };

  const set = useMemo(() => PAGE_SETS[page % PAGE_SETS.length], [page]);
  const fontStack =
    font === 1
      ? "ui-sans-serif, system-ui, sans-serif"
      : font === 2
        ? "ui-monospace, Menlo, monospace"
        : "var(--font-literata), Georgia, 'Literata', 'Times New Roman', serif";

  const segStyle = (active: boolean): CSSProperties => ({
    background: active ? th.ink : th.btn,
    color: active ? th.bg : th.ink,
    borderColor: active ? th.ink : th.border,
  });

  return (
    <div
      className="relative w-full overflow-hidden border-2 transition-colors duration-200"
      style={{
        aspectRatio: "16 / 10",
        background: th.bg,
        borderColor: "#3a3a38",
      }}
    >
      {/* brightness dim */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 z-40 transition-opacity"
        style={{ background: "#000", opacity: (100 - brightness) / 130 }}
      />
      {/* full-refresh blink */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 z-40 transition-opacity duration-100"
        style={{ background: th.name === "Dark" ? "#000" : "#191917", opacity: flashOn ? 0.92 : 0 }}
      />

      {/* ---------------------------------------------- top bar */}
      {chrome && (
        <div className="relative z-10 flex h-11 items-center border-b px-3" style={{ borderColor: th.line }}>
          <span aria-hidden="true" style={{ color: th.ink }}>
            <ChevronLeft className="h-4 w-4" strokeWidth={2} />
          </span>
          <span
            className="ml-1 truncate text-xs font-bold uppercase"
            style={{ color: th.ink, letterSpacing: "0.06em" }}
          >
            The Villa Rose
          </span>
          <button
            type="button"
            aria-label="Contents"
            onClick={() => {
              setTocOpen((v) => !v);
              setPanel(null);
            }}
            className="ml-2 p-1"
            style={{ color: th.soft }}
          >
            <ChevronDown className="h-3.5 w-3.5" strokeWidth={2.2} />
          </button>
          <span className="ml-auto text-xs" style={{ color: th.soft }}>
            {page} / {total} &nbsp;·&nbsp; {pct}%
          </span>
        </div>
      )}

      {/* ---------------------------------------------- contents dropdown */}
      {chrome && tocOpen && (
        <div
          className="absolute left-2 top-[52px] z-30 w-72 border py-1 shadow-none"
          style={{ background: th.bg, borderColor: th.border }}
        >
          <p
            className="px-4 pb-1 pt-2 text-[10px] font-bold uppercase"
            style={{ color: th.soft, letterSpacing: "0.16em" }}
          >
            Contents
          </p>
          {CHAPTERS.map((c, i) => (
            <button
              key={c.title}
              type="button"
              onClick={() => {
                setPage(c.page);
                setTocOpen(false);
              }}
              className="flex w-full items-center justify-between px-4 py-2.5 text-left text-[13px] hover:opacity-80"
              style={{
                color: page >= c.page ? th.ink : th.soft,
                borderTop: i === 0 ? "none" : `1px solid ${th.line}`,
                fontWeight: page >= c.page ? 600 : 400,
              }}
            >
              <span className="truncate pr-3">{c.title}</span>
              <span className="shrink-0 text-xs" style={{ color: th.soft }}>
                p. {c.page}
              </span>
            </button>
          ))}
        </div>
      )}

      {/* ---------------------------------------------- reading column */}
      <div className="absolute inset-x-0 bottom-0 top-0 flex" style={{ top: chrome ? 44 : 0 }}>
        {/* tap zones */}
        <button
          type="button"
          aria-label="Previous page"
          onClick={() => turn(-1)}
          className="h-full w-[32%] cursor-pointer outline-none"
        />
        <button
          type="button"
          aria-label="Toggle controls"
          onClick={() => setChrome((v) => !v)}
          className="h-full flex-1 cursor-pointer outline-none"
        />
        <button
          type="button"
          aria-label="Next page"
          onClick={() => turn(1)}
          className="h-full w-[32%] cursor-pointer outline-none"
        />
      </div>
      <div
        className="pointer-events-none absolute inset-x-0 flex justify-center"
        style={{ top: chrome ? 44 : 0, bottom: chrome ? 56 : 0 }}
      >
        <div
          className="pointer-events-none overflow-hidden px-2 transition-all duration-200"
          style={{
            width: `${MARGIN_FACTORS[margin]}%`,
            paddingTop: 26,
            paddingBottom: 24,
            fontFamily: fontStack,
            fontSize: size,
            lineHeight: lineH,
            color: th.ink,
          }}
        >
          {set.heading && (
            <h3
              className="mb-4 text-center font-bold"
              style={{ fontSize: size * 1.28, marginTop: 6 }}
            >
              {set.heading}
            </h3>
          )}
          <div style={{ textAlign: justify ? "justify" : "left" }}>
            {set.paras.map((p, i) => (
              <p
                key={i}
                style={{
                  textIndent: "1.3em",
                  marginBottom: `${((0.12 + 0.85 * (paraGap / 100)) * lineH).toFixed(2)}em`,
                }}
              >
                {p}
              </p>
            ))}
          </div>
        </div>
        {/* bookmark ribbon */}
        {bookmarked && (
          <span
            aria-hidden="true"
            className="pointer-events-none absolute"
            style={{ right: `${(100 - MARGIN_FACTORS[margin]) / 2 + 4}%`, top: (chrome ? 44 : 0) + 16 }}
          >
            <Bookmark className="h-4 w-4 fill-current" style={{ color: th.ink }} />
          </span>
        )}
      </div>

      {/* ---------------------------------------------- hint */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-x-0 z-20 text-center text-[10px] font-bold uppercase transition-opacity duration-700"
        style={{
          bottom: chrome ? 64 : 40,
          color: th.soft,
          letterSpacing: "0.14em",
          opacity: hint ? 0.9 : 0,
        }}
      >
        Tap the center to hide the controls
      </div>

      {/* ---------------------------------------------- bottom toolbar */}
      {chrome && (
        <div className="absolute inset-x-0 bottom-0 z-10 flex h-14 items-center border-t px-4" style={{ borderColor: th.line }}>
          <div className="mx-auto flex w-full items-center justify-between" style={{ maxWidth: 480 }}>
            <ToolButton label="Contents" ink={th.ink} border={th.border} btn={th.btn} onClick={() => { setTocOpen((v) => !v); setPanel(null); }}>
              <List className="h-4 w-4" strokeWidth={2} />
            </ToolButton>
            <ToolButton label="Bookmark" ink={th.ink} border={th.border} btn={th.btn} active={bookmarked} onClick={() => setBookmarked((v) => !v)}>
              <Bookmark className="h-4 w-4" strokeWidth={2} fill={bookmarked ? "currentColor" : "none"} />
            </ToolButton>
            <ToolButton label="Highlights" ink={th.ink} border={th.border} btn={th.btn} onClick={() => { setPanel(null); setTocOpen(false); }}>
              <Highlighter className="h-4 w-4" strokeWidth={2} />
            </ToolButton>
            <ToolButton label="Notes" ink={th.ink} border={th.border} btn={th.btn} onClick={() => { setPanel(null); setTocOpen(false); }}>
              <StickyNote className="h-4 w-4" strokeWidth={2} />
            </ToolButton>
            <ToolButton label="Typography" ink={th.ink} border={th.border} btn={th.btn} active={panel === "type"} onClick={() => { setPanel(panel === "type" ? null : "type"); setTocOpen(false); }}>
              <ALargeSmall className="h-5 w-5" strokeWidth={2} />
            </ToolButton>
            <ToolButton label="Display" ink={th.ink} border={th.border} btn={th.btn} active={panel === "display"} onClick={() => { setPanel(panel === "display" ? null : "display"); setTocOpen(false); }}>
              <SunMedium className="h-4 w-4" strokeWidth={2} />
            </ToolButton>
          </div>
        </div>
      )}

      {/* ---------------------------------------------- progress hairline */}
      <div aria-hidden="true" className="absolute inset-x-0 bottom-0 z-10 h-[3px]" style={{ background: th.line }}>
        <div className="h-full transition-all" style={{ width: `${pct}%`, background: th.soft }} />
      </div>

      {/* ---------------------------------------------- typography panel */}
      {panel === "type" && (
        <div
          className="absolute bottom-2 right-2 top-2 z-30 w-[290px] max-w-[82%] overflow-y-auto border p-4"
          style={{ background: th.bg, borderColor: th.border, color: th.ink }}
        >
          <div className="flex items-center justify-between">
            <p className="text-[11px] font-bold uppercase" style={{ letterSpacing: "0.16em" }}>
              Typography
            </p>
            <button type="button" aria-label="Close" onClick={() => setPanel(null)} style={{ color: th.ink }}>
              <span className="text-sm font-bold">✕</span>
            </button>
          </div>
          <div className="mt-3 h-px w-full" style={{ background: th.line }} />

          <p className="mt-3 text-[13px]">Font</p>
          <div className="mt-1.5 flex gap-1.5">
            {["Serif", "Sans", "Mono"].map((f, i) => (
              <button
                key={f}
                type="button"
                onClick={() => setFont(i as 0 | 1 | 2)}
                className="h-8 flex-1 rounded-[3px] border text-xs font-semibold"
                style={segStyle(font === i)}
              >
                {f}
              </button>
            ))}
          </div>

          <div className="mt-4 flex items-center justify-between text-[13px]">
            <span>Size</span>
            <span className="text-xs font-bold">{size}</span>
          </div>
          <input
            type="range"
            min={14}
            max={30}
            value={size}
            aria-label="Font size"
            onChange={(e) => setSize(Number(e.target.value))}
            className="mt-1 w-full"
            style={{ accentColor: th.ink }}
          />

          <div className="mt-2 flex items-center justify-between text-[13px]">
            <span>Line height</span>
            <span className="text-xs font-bold">{lineH.toFixed(2)}</span>
          </div>
          <input
            type="range"
            min={1.2}
            max={2}
            step={0.05}
            value={lineH}
            aria-label="Line height"
            onChange={(e) => setLineH(Number(e.target.value))}
            className="mt-1 w-full"
            style={{ accentColor: th.ink }}
          />

          <div className="mt-2 flex items-center justify-between text-[13px]">
            <span>Paragraph gap</span>
            <span className="text-xs font-bold">{paraGap}%</span>
          </div>
          <input
            type="range"
            min={0}
            max={100}
            step={5}
            value={paraGap}
            aria-label="Paragraph gap"
            onChange={(e) => setParaGap(Number(e.target.value))}
            className="mt-1 w-full"
            style={{ accentColor: th.ink }}
          />

          <p className="mt-4 text-[13px]">Margins</p>
          <div className="mt-1.5 flex gap-1.5">
            {MARGIN_FACTORS.map((f, i) => (
              <button
                key={f}
                type="button"
                aria-label={`Margins level ${i + 1}`}
                onClick={() => setMargin(i)}
                className="flex h-8 flex-1 items-center justify-center rounded-[3px] border"
                style={segStyle(margin === i)}
              >
                <span
                  className="h-[3px] rounded-full"
                  style={{ width: 18 - i * 4, background: margin === i ? th.bg : th.ink }}
                />
              </button>
            ))}
          </div>

          <p className="mt-4 text-[13px]">Alignment</p>
          <div className="mt-1.5 flex gap-1.5">
            <button
              type="button"
              onClick={() => setJustify(true)}
              className="flex h-8 flex-1 items-center justify-center gap-1 rounded-[3px] border text-xs font-semibold"
              style={segStyle(justify)}
            >
              <AlignJustify className="h-3.5 w-3.5" /> Justified
            </button>
            <button
              type="button"
              onClick={() => setJustify(false)}
              className="flex h-8 flex-1 items-center justify-center gap-1 rounded-[3px] border text-xs font-semibold"
              style={segStyle(!justify)}
            >
              <AlignLeft className="h-3.5 w-3.5" /> Left
            </button>
          </div>

          <p className="mt-4 text-[13px]">Paper tone</p>
          <div className="mt-1.5 flex gap-2">
            {THEMES.map((t, i) => (
              <button
                key={t.name}
                type="button"
                aria-label={`${t.name} theme`}
                onClick={() => setThemeIdx(i)}
                className="flex-1"
              >
                <span
                  className="flex h-8 items-center justify-center rounded-[2px] border-2"
                  style={{
                    background: t.bg,
                    borderColor: themeIdx === i ? th.ink : th.border,
                    borderWidth: themeIdx === i ? 2 : 1,
                  }}
                >
                  <span className="h-[3px] w-8 rounded-full" style={{ background: t.ink }} />
                </span>
                <span className="mt-1 block text-center text-[10px] font-semibold" style={{ color: th.ink }}>
                  {t.name}
                </span>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* ---------------------------------------------- display panel */}
      {panel === "display" && (
        <div
          className="absolute bottom-2 right-2 top-2 z-30 w-[290px] max-w-[82%] border p-4"
          style={{ background: th.bg, borderColor: th.border, color: th.ink }}
        >
          <div className="flex items-center justify-between">
            <p className="text-[11px] font-bold uppercase" style={{ letterSpacing: "0.16em" }}>
              Display
            </p>
            <button type="button" aria-label="Close" onClick={() => setPanel(null)} style={{ color: th.ink }}>
              <span className="text-sm font-bold">✕</span>
            </button>
          </div>
          <div className="mt-3 h-px w-full" style={{ background: th.line }} />

          <div className="mt-3 flex items-center justify-between text-[13px]">
            <span>Brightness</span>
            <span className="text-xs font-bold">{brightness === 100 ? "Auto" : `${brightness}%`}</span>
          </div>
          <input
            type="range"
            min={20}
            max={100}
            step={5}
            value={brightness}
            aria-label="Brightness"
            onChange={(e) => setBrightness(Number(e.target.value))}
            className="mt-1 w-full"
            style={{ accentColor: th.ink }}
          />

          <p className="mt-4 text-[13px]">Full refresh on turn</p>
          <div className="mt-1.5 flex gap-1.5">
            <button
              type="button"
              onClick={() => setFlash(true)}
              className="h-8 flex-1 rounded-[3px] border text-xs font-semibold"
              style={segStyle(flash)}
            >
              On
            </button>
            <button
              type="button"
              onClick={() => setFlash(false)}
              className="h-8 flex-1 rounded-[3px] border text-xs font-semibold"
              style={segStyle(!flash)}
            >
              Off
            </button>
          </div>
          <p className="mt-2 text-[11px] leading-relaxed" style={{ color: th.soft }}>
            Emulates a full e-ink refresh on every page turn. Off by default for speed.
          </p>
        </div>
      )}
    </div>
  );
}

/* ------------------------------------------------------------- page */

export default function Home() {
  return (
    <div className="flex min-h-screen flex-col bg-[#E7E6E0] text-[#222222]">
      {/* header strip */}
      <header className="w-full bg-[#DDDBD3]">
        <div className="mx-auto flex w-full max-w-5xl items-end justify-between px-4 py-2 sm:px-6">
          <div className="flex items-end gap-3">
            <span className="rounded-t-sm bg-neutral-900 px-3 py-1.5 text-xs font-bold leading-none text-neutral-50">
              InkBook
            </span>
            <span className="text-xs font-bold uppercase leading-none tracking-[0.18em] text-[#6E6B63]">
              Premium e-ink reader
            </span>
          </div>
          <span className="text-xs font-medium leading-none text-[#6E6B63]">
            1.1.0 · Android 5.0+ · phones &amp; tablets
          </span>
        </div>
        <Separator className="bg-[#A5A299]" />
      </header>

      <main className="flex w-full flex-1 flex-col">
        {/* hero */}
        <section className="mx-auto w-full max-w-5xl px-4 py-16 sm:px-6">
          <div className="grid items-center gap-10 md:grid-cols-2 md:gap-12">
            <div>
              <p className="text-xs font-bold uppercase tracking-[0.3em] text-[#6E6B63]">
                A printed book, on glass
              </p>
              <h1 className="mt-4 text-6xl font-bold tracking-tighter sm:text-7xl">
                InkBook
              </h1>
              <p className="mt-5 max-w-md text-base leading-relaxed text-[#3a3a37]">
                A calm, tablet-first e-book reader for Android. True pagination,
                warm-paper themes and book-quality typography — like reading a
                beautifully printed novel on a BOOX E-Ink tablet.
              </p>
              <div className="mt-8 flex flex-wrap gap-3">
                <Button
                  asChild
                  size="lg"
                  className="rounded-none border-2 border-[#222222] bg-neutral-900 text-neutral-50 shadow-none hover:bg-neutral-800 hover:text-neutral-50"
                >
                  <a href="/InkBook.apk" download>
                    <Tablet aria-hidden="true" />
                    Download APK
                  </a>
                </Button>
                <Button
                  asChild
                  size="lg"
                  variant="outline"
                  className="rounded-none border-2 border-[#3a3a3a] bg-transparent text-[#222222] shadow-none hover:bg-[#DDDBD3] hover:text-[#222222] dark:border-[#3a3a3a] dark:bg-transparent dark:text-[#222222] dark:hover:bg-[#DDDBD3] dark:hover:text-[#222222]"
                >
                  <a href="/inkbook-source.zip" download>
                    Source code (.zip)
                  </a>
                </Button>
              </div>
              <p className="mt-4 text-xs font-medium text-[#6E6B63]">
                Signed · 120 KB · works offline · no ads, no tracking
              </p>
            </div>
            <Card className="rounded-none border-2 border-[#3a3a3a] bg-[#F0EFE9] p-4 text-[#222222] shadow-none">
              <Image
                src="/device.jpg"
                alt="Classic six-inch e-ink e-reader displaying a page of a book"
                width={1000}
                height={1000}
                sizes="(min-width: 768px) 480px, 100vw"
                className="h-auto w-full border border-[#A5A299]"
              />
              <p className="mt-3 text-center text-xs font-bold uppercase tracking-[0.2em] text-[#6E6B63]">
                The device that inspired it
              </p>
            </Card>
          </div>
        </section>

        {/* interactive replica */}
        <section className="mx-auto w-full max-w-5xl px-4 pb-16 sm:px-6">
          <div className="text-center">
            <p className="text-xs font-bold uppercase tracking-[0.3em] text-[#6E6B63]">
              The reading screen
            </p>
            <h2 className="mt-3 text-3xl font-bold tracking-tight sm:text-4xl">
              Try the reader right here
            </h2>
            <p className="mx-auto mt-3 max-w-xl text-sm leading-relaxed text-[#55534D]">
              This preview mirrors the app: tap the page edges to turn, the
              center to hide the controls. Open “Aa” for typography and paper
              tones, the sun for display settings.
            </p>
          </div>
          <div className="mx-auto mt-10 w-full max-w-[760px]">
            <ReaderReplica />
            <p className="mt-4 text-center text-xs font-bold uppercase tracking-[0.2em] text-[#6E6B63]">
              Centered column · serif body · first-line indents · hairline progress
            </p>
          </div>
        </section>

        {/* features */}
        <section className="mx-auto w-full max-w-5xl px-4 pb-16 sm:px-6">
          <p className="text-xs font-bold uppercase tracking-[0.3em] text-[#6E6B63]">
            Features
          </p>
          <h2 className="mt-3 text-3xl font-bold tracking-tight sm:text-4xl">
            Everything a paper screen needs
          </h2>
          <div className="mt-10 grid grid-cols-2 gap-6 lg:grid-cols-4">
            {FEATURES.map((feature) => {
              const Icon = feature.icon;
              return (
                <Card
                  key={feature.title}
                  className="gap-3 rounded-none border-2 border-[#3a3a3a] bg-[#F0EFE9] p-5 text-[#222222] shadow-none"
                >
                  <span className="flex h-9 w-9 items-center justify-center border border-[#3a3a3a] bg-[#E7E6E0]">
                    <Icon aria-hidden="true" className="h-4 w-4" />
                  </span>
                  <CardTitle className="text-sm font-bold leading-snug">
                    {feature.title}
                  </CardTitle>
                  <p className="text-xs leading-relaxed text-[#55534D]">
                    {feature.desc}
                  </p>
                </Card>
              );
            })}
          </div>
        </section>

        {/* install */}
        <section className="mx-auto w-full max-w-5xl px-4 pb-16 sm:px-6">
          <p className="text-xs font-bold uppercase tracking-[0.3em] text-[#6E6B63]">
            Install
          </p>
          <h2 className="mt-3 text-3xl font-bold tracking-tight sm:text-4xl">
            Install on your Android device
          </h2>
          <ol className="mt-10 flex flex-col gap-4">
            {INSTALL_STEPS.map((step, index) => (
              <li
                key={step.title}
                className="flex items-start gap-5 border-2 border-[#3a3a3a] bg-[#F0EFE9] px-5 py-4 sm:items-center sm:gap-6 sm:px-6"
              >
                <span className="w-10 shrink-0 font-mono text-3xl font-bold leading-none tracking-tighter sm:text-4xl">
                  {index + 1}
                </span>
                <div className="min-w-0">
                  <p className="text-sm font-bold">{step.title}</p>
                  <p className="mt-1 text-sm leading-relaxed text-[#55534D]">
                    {step.desc}
                  </p>
                </div>
              </li>
            ))}
          </ol>
        </section>
      </main>

      {/* sticky footer */}
      <footer className="mt-auto w-full border-t-2 border-[#3a3a3a] bg-[#DDDBD3]">
        <div className="mx-auto flex w-full max-w-5xl flex-col gap-2 px-4 py-6 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <p className="text-xs font-bold">InkBook — calm reading, printed on glass</p>
          <p className="text-xs font-medium text-[#6E6B63]">v1.1.0 · signed build</p>
        </div>
      </footer>
    </div>
  );
}
