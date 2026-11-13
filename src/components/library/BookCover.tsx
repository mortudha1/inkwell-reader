"use client";

import { useMemo, useState } from "react";
import { cn } from "@/lib/utils";

// Warm, bookish cover palettes (bg, fg, accent line) — deterministic per title.
const PALETTES: { bg: string; fg: string; rule: string }[] = [
  { bg: "#f0e6d2", fg: "#5d4326", rule: "#b28a52" },
  { bg: "#2f261b", fg: "#ead9ba", rule: "#9a7a4e" },
  { bg: "#a34a2a", fg: "#f7e9d7", rule: "#f0c9a8" },
  { bg: "#5c6b4a", fg: "#eef0dd", rule: "#c3cfa4" },
  { bg: "#6e3546", fg: "#f4dede", rule: "#d9a3a3" },
  { bg: "#37474f", fg: "#e5ecec", rule: "#90a4a7" },
];

function hashString(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) {
    h = (h * 31 + s.charCodeAt(i)) | 0;
  }
  return Math.abs(h);
}

interface BookCoverProps {
  title: string;
  author: string;
  coverUrl?: string | null;
  className?: string;
  /** shrink typography for small thumbnails */
  compact?: boolean;
}

export function BookCover({ title, author, coverUrl, className, compact }: BookCoverProps) {
  const palette = useMemo(() => PALETTES[hashString(title) % PALETTES.length], [title]);
  const [imgFailed, setImgFailed] = useState(false);

  if (coverUrl && !imgFailed) {
    return (
      <img
        src={coverUrl}
        alt={`Cover of ${title}`}
        className={cn("aspect-2/3 w-full rounded-lg object-cover shadow-md", className)}
        loading="lazy"
        decoding="async"
        onError={() => setImgFailed(true)}
      />
    );
  }

  const long = title.length > 48;
  return (
    <div
      aria-hidden
      className={cn(
        "relative flex aspect-2/3 w-full flex-col justify-between overflow-hidden rounded-lg p-3 shadow-md",
        className
      )}
      style={{
        background: `linear-gradient(155deg, ${palette.bg} 0%, ${palette.bg} 82%, rgba(0,0,0,0.12) 100%)`,
      }}
    >
      <div
        className="absolute inset-1.5 rounded-[5px] border"
        style={{ borderColor: palette.rule, opacity: 0.55 }}
      />
      <div className="relative flex flex-1 items-center justify-center py-2 text-center">
        <span
          className={cn(
            "reader-serif-aa font-semibold leading-snug",
            compact ? "text-[11px]" : long ? "text-sm" : "text-base"
          )}
          style={{ color: palette.fg }}
        >
          {title}
        </span>
      </div>
      <div
        className={cn("relative truncate text-center font-medium uppercase", compact ? "text-[7px]" : "text-[9px]")}
        style={{ color: palette.fg, letterSpacing: "0.12em", opacity: 0.85 }}
      >
        {author}
      </div>
    </div>
  );
}
