# Inkwell — eReader

A calm, Kindle-style e-reader that runs in any modern browser and installs to
your home screen like a native app. Import your EPUBs and TXT files, keep an
entire offline library on your device, customize every detail of the page, and
tap any word for an instant dictionary definition.

## Features

### Reading, the Kindle way
- **Real pagination** — text flows through a horizontally-paged column layout,
  exactly like an e-ink device. Tap the edges or swipe to turn pages; the
  reading position is tracked as a character offset, so it survives font,
  theme, margin, and orientation changes without losing your place.
- **Immersive chrome** — UI appears only when you tap the middle of the page.
  A thin progress line, locations (Loc), and time-left-in-chapter estimates
  (calibrated to your actual reading speed) keep you oriented.
- **Table of contents** with anchor-accurate jumps, plus a scrubber slider for
  jumping anywhere in the book.

### Full text customization
- 5 typefaces (Literata, Merriweather, Lora, Atkinson Hyperlegible, Inter)
- Font size 14–30 px, line spacing 1.2–2.2, four margin levels
- Justified text and automatic hyphenation toggles
- 5 reading themes: Light, Paper, Sage, Dark, and true Black (OLED/e-ink)

### Word definitions built in
- **Tap any word** for a dictionary card with phonetics, parts of speech,
  definitions, and examples.
- Dictionary lookups hit a free dictionary API first; anything it can't
  handle — inflections, archaic words, proper nouns, or selected phrases —
  falls back to an AI explanation, aware of the sentence you're reading.
- Every lookup is cached in SQLite: repeat taps are instant, and the
  **Vocabulary** sheet in the library lets you review and manage every word
  you've ever looked up.

### A real library, offline-first
- Import `.epub` and `.txt` files — parsed entirely on your device (spine,
  TOC, embedded images and covers included).
- Books, chapters, covers, and reading progress live in IndexedDB +
  localStorage: no account, no server round-trips, works offline after the
  first load.
- Three public-domain classics (Alice's Adventures in Wonderland, The Art of
  War, Meditations) are bundled as samples.

### Performance
- Pagination is pure CSS columns + a GPU `translateX` — no layout thrash on
  page turns.
- Chapters load lazily with next-chapter prefetch and an LRU cache.
- Definitions are cached in memory (LRU), in SQLite, and in the browser
  session — the hot path never leaves the device after the first lookup.
- Web-font-aware re-measurement, debounced resize handling, and a stable
  `__html` reference so unrelated re-renders never touch the chapter DOM.

## Tech stack

- [Next.js 16](https://nextjs.org) (App Router) + TypeScript
- Tailwind CSS 4 + shadcn/ui, Lucide icons, Framer Motion, Vaul drawers
- Zustand (persisted settings + progress), IndexedDB (books), Prisma + SQLite
  (dictionary cache)
- `z-ai-web-dev-sdk` for AI-powered definitions and contextual explanations
- PWA manifest + icons — installable on Android/iOS/desktop

## Getting started

```bash
bun install
bun run db:push   # create the SQLite dictionary cache
bun run dev       # http://localhost:3000
```

> Definitions need the dev server's API routes online. Everything else
> (library, reading, progress, settings) keeps working fully offline.

## Install it as an app

**Android** — open the site in Chrome, then *Add to Home screen*. It launches
fullscreen in the Paper theme, stores books on-device, and behaves like a
native e-reader. To ship a store-ready APK, wrap the deployed PWA with
[Bubblewrap](https://github.com/GoogleChromeLabs/bubblewrap) (TWA):

```bash
npm i -g @bubblewrap/cli
bubblewrap init --manifest https://your-domain/manifest.webmanifest
bubblewrap build   # produces app-release-signed.apk / .aab
```

**iOS** — Safari → Share → *Add to Home Screen*.

## Project structure

```
src/
  app/                 # layout, page, PWA manifest, API routes (define, dictionary)
  components/library/  # library grid, covers, vocabulary sheet
  components/reader/   # reader view, settings sheet, TOC sheet, definition popover
  lib/reader/          # pagination engine, EPUB/TXT parsers, IndexedDB, stores
  data/                # sample-book seeding
prisma/                # SQLite schema (dictionary cache)
public/                # icons, sample-book covers and texts
```
