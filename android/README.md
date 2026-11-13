# InkBook - Premium E-Ink Reader for Android

A native Android e-book reader that reads like a beautifully printed novel on
a BOOX E-Ink tablet. Two distinct faces:

- **Reading screen** - a calm, premium, tablet-optimized e-ink page: warm
  paper (#E9E8E2), charcoal ink (#222222), a thin top bar (title + contents
  dropdown, page / progress), a centered reading column (70-80% of width,
  capped at ~88 characters per line), book typography with first-line
  indents and centered chapter headings, a floating monochrome bottom
  toolbar (contents / bookmarks / highlights / notes / typography /
  display), and a subtle 3dp progress hairline that never leaves.
- **Library screen** - BOOX-style document browser: grayscale palette,
  left navigation sidebar, compact toolbar, 4-column cover grid with format
  chips, search, sorting and bookshelves.

Everything is canvas-drawn - sharp corners, hairline borders, flat
monochrome palettes, no gradients, no color noise.

## Reading experience

- **Distraction-free mode** - tap the center of the page to hide every
  control; tap again to bring them back. Back also restores the chrome
  before leaving the book.
- **Typography, remembered per book** - serif (Literata-style system serif)
  by default at 20sp, sliders for font size (14-30), line height (1.2-2.0)
  and paragraph gap (0-100%), margin presets (four column widths), justified
  or left-aligned text.
- **Paper tones** - warm paper, white and dark, flat and authentic.
- **Bookmarks / highlights / notes** - bookmarks anchor to the current page;
  long-press any word to open the Wiktionary dictionary and highlight that
  passage or attach a note from there. All marks persist per book and render
  as soft gray bands in the page.
- **Navigation** - tap zones (32% / 68%), horizontal swipe, volume keys,
  D-pad, page keys, spacebar; optional full-refresh blink on page turns
  (off by default for e-ink performance); "go to page" keypad; chapters
  dropdown from the title; brief chapter-title hint when a turn lands on a
  new chapter.
- **Display panel** - brightness override with auto mode, full-refresh
  toggle, haptic feedback toggle.
- **Tablet-first ergonomics** - landscape uses a centered column capped for
  comfortable line length; portrait opens the column wider; rotation keeps
  the exact reading position; large touch targets kept away from edges.

## Project layout

```
android/
  AndroidManifest.xml   no orientation lock (tablets rotate freely)
  build.sh              pure aapt2/ecj/d8/zipalign/apksigner pipeline
  assets/sample.txt     pre-seeded sample book
  res/                  launcher icons (mipmap-*)
  src/com/inkbook/reader/
    MainActivity.java   activity glue, SAF import, chrome switching (immersive in reader only)
    LibraryView.java    BOOX-style library: sidebar, toolbar, grid, sections
    ReaderView.java     premium reading screen, panels, marks, dictionary
    Paginator.java      wrap / paginate / render engine (centered headings,
                        adjustable paragraph gap)
    Book.java           TXT + EPUB parsing
    Dict.java           Wiktionary REST client
    Prefs.java          settings, per-book typography, marks, positions
    EinkTheme.java      warm paper / white / dark palettes
    Util.java           helpers (paper grain, clock, battery)
```

## Building

Requirements: JDK 17+ and an Android SDK with `platforms;android-34` and
`build-tools;34.0.0`. No Gradle required.

```bash
SDK=/path/to/android-sdk bash build.sh
# -> InkBook.apk  (signed with debug.keystore, pass: inkbook)
```

Defaults match this sandbox: `SDK=/home/z/android-sdk`.

## Install

1. Copy `InkBook.apk` to your phone or tablet and open it.
2. Allow "Install unknown apps" for your file manager if prompted.
3. Tap Install, open InkBook, and press **+ IMPORT BOOK** to load any `.txt`,
   `.epub` or `.pdf` file. A sample book is preloaded on first launch.

## Controls

| Action | Gesture |
| --- | --- |
| Next page | tap right 32%, swipe left, volume down, page down, space |
| Previous page | tap left 32%, swipe right, volume up, page up |
| Distraction-free | tap the center of the page |
| Contents | title chevron, first toolbar button |
| Bookmark this page | ribbon button (fills when bookmarked) |
| Highlights / notes | pen and note buttons; long-press a word for actions |
| Typography | "Aa" button (font, size, spacing, margins, align, tone) |
| Display | sun button (brightness, full refresh, haptics) |
| Go to page | tap the page counter in the top bar |
| Back to library | back chevron in the top bar, or system back |
