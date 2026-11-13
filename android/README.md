# InkBook - E-Ink Reader for Android

A native Android e-book reader with two distinct faces:

- **Library screen** replicates the BOOX Note Air e-reader software: strict
  grayscale palette (#E5E5E5 background), fixed left navigation sidebar
  (Library / Store / Notes / Storage / Apps / Settings) with a black selected
tile, compact toolbar, 4-column document grid with thin-bordered covers,
format chips, favorite stars, and a "Total: Bookshelves(N) / Books(M)" bottom
bar with pagination.
- **Reading screen** replicates a classic e-ink page: paper-gray screen, status
bar with book-title tab, side page buttons, hardware-style bottom controls,
and an authentic e-ink page-turn (dark blink + ghosting settle).

No gradients, no color, no rounded cards - sharp corners and thin borders
throughout the shell, just like the actual BOOX firmware.

## Features

- **BOOX-style library** - sidebar sections, toolbar (recent / shelves / cloud
  / search / sort / view), 4-column grid, grayscale generated covers with
  format chips (PDF / EPUB / TXT), check + favorite icons
- **Search** filters books instantly (title or author)
- **Sorting** by title, author, date added, recently opened
- **Bookshelves** - create collections and organize books (long-press a book)
- **Import TXT / EPUB / PDF** (SAF import, EPUB author metadata extraction)
- **Storage / Apps / Settings** sections in BOOX visual style
- True **pagination engine** for reading (page-turn layout, not scrolling)
- **E-ink page turn transition** - dark blink frame + ghost settle (toggleable)
- **Tap zones**, swipe, volume keys, and on-screen page buttons while reading
- **Long-press any word** for an instant Wiktionary definition (online)
- **Gray / White / Night** themes (inside the reader)

## Project layout

```
android/
  AndroidManifest.xml
  build.sh              pure aapt2/javac/d8/zipalign/apksigner pipeline
  assets/sample.txt     pre-seeded sample book
  res/                  launcher icons (mipmap-*)
  src/com/inkbook/reader/
    MainActivity.java   activity glue, SAF import, chrome switching (immersive in reader only)
    LibraryView.java    BOOX-style library: sidebar, toolbar, grid, sections
    ReaderView.java     reading screen (canvas-drawn) + all dialogs
    Paginator.java      wrap / paginate / render engine
    Book.java           TXT + EPUB parsing
    Dict.java           Wiktionary REST client
    Prefs.java          settings / library / progress persistence
    EinkTheme.java      palettes
    Util.java           helpers (noise texture, clock, battery)
```

## Building

Requirements: JDK 17 (`javac`), Android SDK with `platforms;android-34` and
`build-tools;34.0.0`. No Gradle required.

```bash
SDK=/path/to/android-sdk JDK=/path/to/jdk17 bash build.sh
# -> InkBook.apk  (signed with debug.keystore, pass: inkbook)
```

Defaults match this sandbox: `SDK=/home/z/android-sdk`, `JDK=/home/z/jdk17`.

## Install

1. Copy `InkBook.apk` to your phone and open it.
2. Allow "Install unknown apps" for your file manager if prompted.
3. Tap Install, open InkBook, and press **+ IMPORT BOOK** to load any `.txt`
   or `.epub` file. A sample book is preloaded on first launch.

## Controls

| Action | Gesture |
| --- | --- |
| Next page | tap right third, swipe left, volume down, side down-arrow |
| Previous page | tap left third, swipe right, volume up, side up-arrow |
| Menu | tap center / `M` button |
| Go to page | `Q|Q` button |
| Theme cycle | moon/sun button |
| Settings | keyboard button |
| Library | center rounded-square button |
| Dictionary | long-press any word |
