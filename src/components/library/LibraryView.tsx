"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useTheme } from "next-themes";
import {
  BookMarked,
  BookOpen,
  Loader2,
  Moon,
  MoreVertical,
  Sun,
  Trash2,
  Upload,
} from "lucide-react";

import { BookCover } from "@/components/library/BookCover";
import { VocabularySheet } from "@/components/library/VocabularySheet";
import {
  idbDeleteBook,
  idbGetCover,
  idbListBooks,
  idbPutBook,
  idbPutChapters,
  idbPutCover,
} from "@/lib/reader/idb";
import { parseTxt } from "@/lib/reader/txt";
import { parseEpub } from "@/lib/reader/epub";
import { useProgressStore } from "@/lib/reader/store";
import type { BookMeta } from "@/lib/reader/types";
import { seedSampleBooks } from "@/data/sample-books";
import { toast } from "@/hooks/use-toast";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
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
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

interface LibraryViewProps {
  onOpen: (bookId: string) => void;
}

export function LibraryView({ onOpen }: LibraryViewProps) {
  const [books, setBooks] = useState<BookMeta[] | null>(null);
  const [coverUrls, setCoverUrls] = useState<Record<string, string>>({});
  const [importing, setImporting] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<BookMeta | null>(null);
  const [vocabOpen, setVocabOpen] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { theme, setTheme } = useTheme();
  const progress = useProgressStore((s) => s.progress);
  const clearProgress = useProgressStore((s) => s.clearProgress);

  const loadLibrary = useCallback(async () => {
    try {
      const list = await idbListBooks();
      setBooks(list);
      // load cover blobs
      const withBlobs = list.filter((b) => b.hasCoverBlob);
      const entries = await Promise.all(
        withBlobs.map(async (b) => {
          try {
            const blob = await idbGetCover(b.id);
            if (blob) return [b.id, URL.createObjectURL(blob)] as const;
          } catch {
            /* ignore */
          }
          return null;
        })
      );
      setCoverUrls((prev) => {
        const next = { ...prev };
        for (const e of entries) {
          if (e) next[e[0]] = e[1];
        }
        return next;
      });
    } catch {
      setBooks([]);
      toast({
        title: "Storage unavailable",
        description: "Your browser blocked local storage — books can't be saved.",
        variant: "destructive",
      });
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      await seedSampleBooks();
      if (!cancelled) loadLibrary();
    })();
    return () => {
      cancelled = true;
    };
  }, [loadLibrary]);

  // continue-reading: most recently opened book with progress
  const continueBook = useMemo(() => {
    if (!books || !books.length) return null;
    let best: { book: BookMeta; updatedAt: number; percent: number } | null = null;
    for (const b of books) {
      const p = progress[b.id];
      if (!p || p.percent >= 0.995) continue;
      if (!best || p.updatedAt > best.updatedAt) {
        best = { book: b, updatedAt: p.updatedAt, percent: p.percent };
      }
    }
    return best;
  }, [books, progress]);

  // -------------------------------------------------------------------------
  // import
  // -------------------------------------------------------------------------
  const handleFiles = useCallback(
    async (files: FileList | null) => {
      if (!files || !files.length) return;
      setImporting(true);
      const added: string[] = [];
      const failures: string[] = [];

      for (const file of Array.from(files)) {
        const nameLower = file.name.toLowerCase();
        try {
          if (nameLower.endsWith(".epub")) {
            const result = await parseEpub(file);
            const id = `bk-${crypto.randomUUID()}`;
            const totalChars = result.chapters.reduce((a, c) => a + c.chars, 0);
            const meta: BookMeta = {
              id,
              title: result.title,
              author: result.author,
              format: "epub",
              source: "upload",
              lang: result.lang,
              coverPath: null,
              hasCoverBlob: !!result.cover,
              createdAt: Date.now(),
              totalChars,
              chapters: result.chapters.map((c, i) => ({
                index: i,
                title: c.title,
                chars: c.chars,
              })),
              toc: result.toc,
            };
            await idbPutBook(meta);
            await idbPutChapters(
              result.chapters.map((c, i) => ({ bookId: id, index: i, html: c.html }))
            );
            if (result.cover) await idbPutCover(id, result.cover);
            added.push(result.title);
          } else if (nameLower.endsWith(".txt")) {
            const text = await file.text();
            const parsed = parseTxt(text);
            if (!parsed.chapters.length) throw new Error("No readable text");
            const id = `bk-${crypto.randomUUID()}`;
            const totalChars = parsed.chapters.reduce((a, c) => a + c.chars, 0);
            const meta: BookMeta = {
              id,
              title: file.name.replace(/\.txt$/i, ""),
              author: "Unknown author",
              format: "txt",
              source: "upload",
              lang: "en",
              coverPath: null,
              hasCoverBlob: false,
              createdAt: Date.now(),
              totalChars,
              chapters: parsed.chapters.map((c, i) => ({
                index: i,
                title: c.title,
                chars: c.chars,
              })),
              toc: parsed.chapters.map((c, i) => ({
                title: c.title,
                chapterIndex: i,
                anchor: null,
              })),
            };
            await idbPutBook(meta);
            await idbPutChapters(
              parsed.chapters.map((c, i) => ({ bookId: id, index: i, html: c.html }))
            );
            added.push(meta.title);
          } else {
            failures.push(`${file.name} — unsupported format`);
          }
        } catch (err) {
          failures.push(`${file.name} — ${err instanceof Error ? err.message : "could not be read"}`);
        }
      }

      await loadLibrary();
      setImporting(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
      if (added.length) {
        toast({
          title: added.length === 1 ? `Added “${added[0]}”` : `Added ${added.length} books`,
          description: "Saved on this device — read offline anytime.",
        });
      }
      for (const f of failures) {
        toast({ title: "Import failed", description: f, variant: "destructive" });
      }
    },
    [loadLibrary]
  );

  const handleDelete = useCallback(async () => {
    const target = deleteTarget;
    setDeleteTarget(null);
    if (!target) return;
    try {
      await idbDeleteBook(target.id);
      clearProgress(target.id);
      setCoverUrls((prev) => {
        const next = { ...prev };
        const url = next[target.id];
        if (url) {
          URL.revokeObjectURL(url);
          delete next[target.id];
        }
        return next;
      });
      setBooks((prev) => prev?.filter((b) => b.id !== target.id) ?? null);
      toast({ title: `Removed “${target.title}”` });
    } catch {
      toast({ title: "Could not remove this book", variant: "destructive" });
    }
  }, [deleteTarget, clearProgress]);

  // -------------------------------------------------------------------------
  // render
  // -------------------------------------------------------------------------
  return (
    <div className="flex min-h-dvh flex-col bg-background text-foreground">
      {/* header */}
      <header className="sticky top-0 z-20 border-b bg-background/85 backdrop-blur-md">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3 pt-[calc(0.75rem+env(safe-area-inset-top,0px))]">
          <div className="flex items-baseline gap-2">
            <h1 className="reader-serif-aa text-xl font-semibold tracking-tight">Inkwell</h1>
            <span className="hidden text-xs text-muted-foreground sm:inline">
              your books, on this device
            </span>
          </div>
          <div className="flex items-center gap-0.5">
            <Button
              variant="ghost"
              size="icon"
              aria-label="Vocabulary"
              aria-haspopup="dialog"
              onClick={() => setVocabOpen(true)}
            >
              <BookMarked className="size-[18px]" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              aria-label="Toggle theme"
              onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
            >
              <Sun className="size-[18px] scale-100 rotate-0 transition-transform dark:scale-0 dark:-rotate-90" />
              <Moon className="absolute size-[18px] scale-0 rotate-90 transition-transform dark:scale-100 dark:rotate-0" />
            </Button>
          </div>
        </div>
      </header>

      {/* main */}
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 pb-36 pt-5">
        {books == null ? (
          <div
            className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5"
            aria-hidden
          >
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="space-y-2">
                <div className="aspect-2/3 animate-pulse rounded-lg bg-muted" />
                <div className="h-3.5 w-4/5 animate-pulse rounded bg-muted" />
                <div className="h-3 w-2/5 animate-pulse rounded bg-muted" />
              </div>
            ))}
          </div>
        ) : books.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-4 py-24 text-center">
            <BookOpen className="size-12 text-muted-foreground/50" />
            <div>
              <p className="font-medium">Your library is empty</p>
              <p className="mt-1 text-sm text-muted-foreground">
                Add an EPUB or TXT file to start reading.
              </p>
            </div>
            <Button onClick={() => fileInputRef.current?.click()}>Import a book</Button>
          </div>
        ) : (
          <>
            {/* continue reading */}
            {continueBook ? (
              <Link
                href="#"
                onClick={(e) => {
                  e.preventDefault();
                  onOpen(continueBook.book.id);
                }}
                className="group mb-7 flex items-center gap-4 rounded-2xl border bg-card p-3 pr-4 shadow-sm transition-all hover:shadow-md sm:gap-5 sm:p-4"
              >
                <div className="w-16 shrink-0 sm:w-20">
                  <BookCover
                    title={continueBook.book.title}
                    author={continueBook.book.author}
                    coverUrl={continueBook.book.coverPath ?? coverUrls[continueBook.book.id]}
                    compact
                  />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-[11px] font-medium uppercase tracking-wider text-amber-700 dark:text-amber-500">
                    {continueBook.percent > 0.005 ? "Continue reading" : "Start reading"}
                  </p>
                  <p className="reader-serif-aa mt-0.5 truncate font-semibold leading-snug">
                    {continueBook.book.title}
                  </p>
                  <p className="truncate text-xs text-muted-foreground">
                    {continueBook.book.author}
                  </p>
                  {continueBook.percent > 0.005 ? (
                    <div className="mt-2 flex items-center gap-2">
                      <Progress
                        value={continueBook.percent * 100}
                        className="h-1.5 flex-1"
                        aria-label={`Progress ${Math.round(continueBook.percent * 100)}%`}
                      />
                      <span className="text-[11px] tabular-nums text-muted-foreground">
                        {Math.round(continueBook.percent * 100)}%
                      </span>
                    </div>
                  ) : null}
                </div>
              </Link>
            ) : null}

            {/* grid */}
            <section aria-label="Library">
              <div className="grid grid-cols-2 gap-x-4 gap-y-6 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
                {books.map((b) => {
                  const p = progress[b.id];
                  return (
                    <div key={b.id} className="group/book relative">
                      <button
                        onClick={() => onOpen(b.id)}
                        className="block w-full text-left focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-ring"
                        aria-label={`Open ${b.title} by ${b.author}`}
                      >
                        <div className="relative transition-transform duration-200 group-hover/book:-translate-y-1">
                          <BookCover
                            title={b.title}
                            author={b.author}
                            coverUrl={b.coverPath ?? coverUrls[b.id]}
                          />
                          {b.source === "sample" ? (
                            <Badge
                              variant="secondary"
                              className="absolute bottom-1.5 left-1.5 h-5 border border-border/60 bg-background/85 px-1.5 text-[9px] font-medium uppercase tracking-wider text-foreground/80 shadow-sm backdrop-blur"
                            >
                              Sample
                            </Badge>
                          ) : null}
                        </div>
                        <p className="reader-serif-aa mt-2 line-clamp-2 text-sm font-medium leading-snug">
                          {b.title}
                        </p>
                        <p className="line-clamp-1 text-xs text-muted-foreground">{b.author}</p>
                        {p && p.percent > 0.001 ? (
                          <div className="mt-1.5 flex items-center gap-1.5">
                            <div
                              className="h-1 flex-1 overflow-hidden rounded-full bg-muted"
                              aria-hidden
                            >
                              <div
                                className="h-full rounded-full bg-amber-600 dark:bg-amber-500"
                                style={{ width: `${Math.min(100, p.percent * 100)}%` }}
                              />
                            </div>
                            <span className="text-[10px] tabular-nums text-muted-foreground">
                              {Math.round(p.percent * 100)}%
                            </span>
                          </div>
                        ) : null}
                      </button>

                      <div className="absolute right-1.5 top-1.5 opacity-0 transition-opacity focus-within:opacity-100 group-hover/book:opacity-100">
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <button
                              aria-label={`Options for ${b.title}`}
                              className="rounded-full bg-background/85 p-1.5 shadow-sm backdrop-blur transition-colors hover:bg-background"
                            >
                              <MoreVertical className="size-4" />
                            </button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem
                              variant="destructive"
                              onClick={() => setDeleteTarget(b)}
                            >
                              <Trash2 className="size-4" />
                              Remove from library
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </div>
                    </div>
                  );
                })}
              </div>
            </section>
          </>
        )}
      </main>

      {/* sticky footer bar */}
      <footer className="fixed inset-x-0 bottom-0 z-20 border-t bg-background/85 backdrop-blur-md">
        <div className="mx-auto flex max-w-6xl items-center gap-3 px-4 py-3 pb-[calc(0.75rem+env(safe-area-inset-bottom,0px))]">
          <Button
            className="flex-1 bg-amber-700 font-medium text-amber-50 hover:bg-amber-800 dark:bg-amber-600 dark:text-amber-950 dark:hover:bg-amber-500 sm:flex-none sm:px-6"
            onClick={() => fileInputRef.current?.click()}
            disabled={importing}
          >
            {importing ? (
              <Loader2 className="size-4 animate-spin" />
            ) : (
              <Upload className="size-4" />
            )}
            {importing ? "Importing…" : "Import books"}
          </Button>
          <p className="hidden text-xs text-muted-foreground sm:block">
            EPUB &amp; TXT · stored privately on this device
          </p>
        </div>
      </footer>

      <input
        ref={fileInputRef}
        type="file"
        accept=".epub,.txt,application/epub+zip,text/plain"
        multiple
        className="hidden"
        onChange={(e) => handleFiles(e.target.files)}
        aria-label="Import EPUB or TXT books"
      />

      {/* vocabulary sheet */}
      <VocabularySheet open={vocabOpen} onOpenChange={setVocabOpen} />

      {/* delete confirmation */}
      <AlertDialog open={!!deleteTarget} onOpenChange={(v) => !v && setDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Remove this book?</AlertDialogTitle>
            <AlertDialogDescription>
              “{deleteTarget?.title}” will be deleted from this device, along with your
              reading progress. This can't be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-white hover:bg-destructive/90"
              onClick={handleDelete}
            >
              Remove
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
