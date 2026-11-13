"use client";

import {
  Drawer,
  DrawerContent,
  DrawerDescription,
  DrawerHeader,
  DrawerTitle,
} from "@/components/ui/drawer";
import { BookText } from "lucide-react";
import type { BookMeta, TocEntry, ThemeId } from "@/lib/reader/types";
import { themeClass } from "@/lib/reader/types";
import { cn } from "@/lib/utils";

interface TocSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  book: BookMeta;
  currentChapter: number;
  onJump: (entry: TocEntry) => void;
  /** current reader theme — drawers portal to <body>, so theme vars must be re-applied */
  theme: ThemeId;
}

export function TocSheet({ open, onOpenChange, book, currentChapter, onJump, theme }: TocSheetProps) {
  const entries: TocEntry[] =
    book.toc.length > 0
      ? book.toc
      : book.chapters.map((c, i) => ({ title: c.title, chapterIndex: i, anchor: null }));

  return (
    <Drawer open={open} onOpenChange={onOpenChange}>
      <DrawerContent
        className={cn("reader-sheet reader-root", themeClass(theme))}
        style={{
          background: "var(--reader-bg)",
          color: "var(--reader-fg)",
          borderColor: "var(--reader-line)",
        }}
      >
        <div className="mx-auto w-full max-w-xl">
          <DrawerHeader className="pb-2">
            <DrawerTitle className="reader-serif flex items-center gap-2 text-base">
              <BookText className="size-4" style={{ color: "var(--reader-accent)" }} />
              Table of contents
            </DrawerTitle>
            <DrawerDescription className="sr-only">
              Navigate between chapters of {book.title}
            </DrawerDescription>
          </DrawerHeader>
          <div className="reader-scroll max-h-[60vh] overflow-y-auto px-3 pb-8">
            <ul>
              {entries.map((entry, i) => {
                const active = entry.chapterIndex === currentChapter;
                return (
                  <li key={`${entry.chapterIndex}-${entry.anchor ?? ""}-${i}`}>
                    <button
                      onClick={() => onJump(entry)}
                      aria-current={active ? "true" : undefined}
                      className={cn(
                        "flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm transition-colors hover:bg-black/5 dark:hover:bg-white/5",
                        active && "toc-active"
                      )}
                    >
                      <span
                        className="h-5 w-1 shrink-0 rounded-full"
                        style={{
                          background: active ? "var(--reader-accent)" : "transparent",
                        }}
                        aria-hidden
                      />
                      <span className={cn("truncate", active ? "font-semibold" : "font-normal")}>
                        {entry.title}
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          </div>
        </div>
      </DrawerContent>
    </Drawer>
  );
}
