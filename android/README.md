# InkBook - E-Ink Reader for Android

A native Android e-book reader where **only the reading screen** replicates a
classic 6-inch e-ink e-reader - paper-gray screen, status bar with a book-title
tab, side page buttons, hardware-style bottom controls, and an authentic
e-ink page-turn (quick dark blink + ghosting settle). Everything outside the
reader (library, dialogs, system bars) looks and behaves like a normal,
modern Android app.

## Features

- **Modern library screen** - app bar, white book cards with cover thumbnails,
  progress bars, FAB import, Material-style delete dialog
- **TXT + EPUB** support (SAF import, no storage permissions needed)
- True **pagination engine** (page-turn layout, not scrolling)
- **E-ink page turn transition** - dark blink frame + ghost settle (toggleable)
- **Tap zones** (left/right thirds), swipe, volume keys, and on-screen page buttons
- **Long-press any word** for an instant Wiktionary definition (online)
- **Gray / White / Night** themes (inside the reader)
- Font size, typeface (Sans/Serif/Mono), line spacing, justification settings
- **Chapters** list, **Go to page** keypad, reading position saved per book
- Formats ~10k paragraphs/sec in the background with a real "FORMATTING" screen

## Project layout

```
android/
  AndroidManifest.xml
  build.sh              pure aapt2/javac/d8/zipalign/apksigner pipeline
  assets/sample.txt     pre-seeded sample book
  res/                  launcher icons (mipmap-*)
  src/com/inkbook/reader/
    MainActivity.java   activity glue, SAF import, chrome switching (immersive in reader only)
    LibraryView.java    modern library screen: cards, covers, FAB (canvas-drawn)
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
