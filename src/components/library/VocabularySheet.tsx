"use client";

// Vocabulary sheet: browsing every word the reader has looked up.
// Backed by the server-side SQLite dictionary cache (/api/dictionary).

import { useCallback, useEffect, useState } from "react";
import {
  Drawer,
  DrawerContent,
  DrawerDescription,
  DrawerHeader,
  DrawerTitle,
} from "@/components/ui/drawer";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { BookMarked, ChevronDown, Trash2 } from "lucide-react";
import { cn } from "@/lib/utils";

interface Sense {
  partOfSpeech: string;
  definition: string;
  example?: string | null;
}

interface VocabEntry {
  word: string;
  phonetic: string | null;
  senses: Sense[];
  source: string;
  hits: number;
  updatedAt: string;
}

function timeAgo(iso: string): string {
  const then = new Date(iso).getTime();
  if (!isFinite(then)) return "";
  const s = Math.max(1, Math.floor((Date.now() - then) / 1000));
  if (s < 60) return "just now";
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  const d = Math.floor(h / 24);
  if (d < 30) return `${d}d ago`;
  return new Date(iso).toLocaleDateString();
}

interface VocabularySheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function VocabularySheet({ open, onOpenChange }: VocabularySheetProps) {
  const [entries, setEntries] = useState<VocabEntry[] | null>(null);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [confirmClear, setConfirmClear] = useState(false);

  // load history whenever the sheet opens (state updates happen after the
  // await, not synchronously in the effect body)
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    (async () => {
      try {
        const res = await fetch("/api/dictionary");
        const data = (await res.json()) as { entries?: VocabEntry[] };
        if (!cancelled) setEntries(data.entries ?? []);
      } catch {
        if (!cancelled) setEntries([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [open]);

  const removeWord = useCallback((word: string) => {
    setEntries((prev) => prev?.filter((e) => e.word !== word) ?? null);
    fetch(`/api/dictionary?word=${encodeURIComponent(word)}`, {
      method: "DELETE",
    }).catch(() => {});
  }, []);

  const clearAll = useCallback(() => {
    setConfirmClear(false);
    setEntries([]);
    fetch("/api/dictionary", { method: "DELETE" }).catch(() => {});
  }, []);

  return (
    <Drawer open={open} onOpenChange={onOpenChange}>
      <DrawerContent>
        <div className="mx-auto w-full max-w-xl">
          <DrawerHeader className="pb-2">
            <DrawerTitle className="flex items-center gap-2 text-base">
              <BookMarked className="size-4 text-amber-700 dark:text-amber-500" />
              Vocabulary
            </DrawerTitle>
            <DrawerDescription>
              {entries == null
                ? "Loading your words…"
                : entries.length > 0
                  ? `Every word you've looked up — ${entries.length} saved`
                  : "Words you look up while reading are saved here for review."}
            </DrawerDescription>
          </DrawerHeader>

          <div className="lib-scroll max-h-[58vh] overflow-y-auto px-3 pb-4">
            {entries == null ? (
              <div className="space-y-2 px-1" aria-hidden>
                {Array.from({ length: 5 }).map((_, i) => (
                  <div key={i} className="space-y-1.5 rounded-lg px-2 py-2">
                    <div className="h-4 w-1/3 animate-pulse rounded bg-muted" />
                    <div className="h-3 w-3/4 animate-pulse rounded bg-muted" />
                  </div>
                ))}
              </div>
            ) : entries.length === 0 ? (
              <div className="flex flex-col items-center gap-2 px-6 py-12 text-center">
                <BookMarked className="size-8 text-muted-foreground/40" />
                <p className="text-sm font-medium">Nothing here yet</p>
                <p className="text-xs text-muted-foreground">
                  Open a book and tap any word to see its definition — every
                  lookup is saved to your vocabulary.
                </p>
              </div>
            ) : (
              <ul>
                {entries.map((e) => {
                  const isOpen = expanded === e.word;
                  return (
                    <li key={e.word} className="rounded-lg">
                      <button
                        onClick={() => setExpanded(isOpen ? null : e.word)}
                        aria-expanded={isOpen}
                        className="flex w-full items-center gap-2.5 rounded-lg px-2 py-2.5 text-left transition-colors hover:bg-accent/50 focus-visible:outline-2 focus-visible:outline-ring"
                      >
                        <ChevronDown
                          className={cn(
                            "size-4 shrink-0 text-muted-foreground transition-transform duration-200",
                            isOpen && "rotate-180"
                          )}
                        />
                        <span className="min-w-0 flex-1">
                          <span className="reader-serif block truncate text-sm font-semibold capitalize">
                            {e.word}
                          </span>
                          <span className="block truncate text-xs text-muted-foreground">
                            {e.phonetic ? `${e.phonetic} · ` : ""}
                            {e.senses[0]?.definition}
                          </span>
                        </span>
                        {e.hits > 1 ? (
                          <Badge
                            variant="secondary"
                            className="shrink-0 text-[10px] tabular-nums"
                          >
                            ×{e.hits}
                          </Badge>
                        ) : null}
                        <span className="shrink-0 text-[10px] text-muted-foreground">
                          {timeAgo(e.updatedAt)}
                        </span>
                      </button>

                      {isOpen ? (
                        <div className="px-2 pb-3 pl-8">
                          <ol className="space-y-2">
                            {e.senses.map((s, i) => (
                              <li key={i} className="text-sm leading-relaxed">
                                {s.partOfSpeech ? (
                                  <span className="text-xs font-medium italic text-amber-700 dark:text-amber-500">
                                    {s.partOfSpeech}
                                  </span>
                                ) : null}
                                <p>{s.definition}</p>
                                {s.example ? (
                                  <p className="mt-0.5 text-xs italic text-muted-foreground">
                                    &ldquo;{s.example}&rdquo;
                                  </p>
                                ) : null}
                              </li>
                            ))}
                          </ol>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="mt-2 h-7 gap-1.5 px-2 text-xs text-muted-foreground hover:text-destructive"
                            onClick={() => removeWord(e.word)}
                          >
                            <Trash2 className="size-3.5" />
                            Remove
                          </Button>
                        </div>
                      ) : null}
                    </li>
                  );
                })}
              </ul>
            )}
          </div>

          {entries && entries.length > 0 ? (
            <div className="border-t px-5 py-3 pb-[calc(0.75rem+env(safe-area-inset-bottom,0px))]">
              <Button
                variant="outline"
                size="sm"
                className="text-destructive hover:border-destructive/40 hover:bg-destructive/10"
                onClick={() => setConfirmClear(true)}
              >
                <Trash2 className="size-4" />
                Clear vocabulary
              </Button>
            </div>
          ) : null}
        </div>

        <AlertDialog open={confirmClear} onOpenChange={setConfirmClear}>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Clear your vocabulary?</AlertDialogTitle>
              <AlertDialogDescription>
                All {entries?.length ?? 0} saved words and their definitions
                will be removed. This can't be undone.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <AlertDialogAction
                className="bg-destructive text-white hover:bg-destructive/90"
                onClick={clearAll}
              >
                Clear all
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      </DrawerContent>
    </Drawer>
  );
}
