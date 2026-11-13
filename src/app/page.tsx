import type { Metadata } from "next";
import Image from "next/image";
import {
  Bot,
  BookMarked,
  BookOpen,
  Bookmark,
  ChevronDown,
  ChevronUp,
  FileText,
  Keyboard,
  Moon,
  Smartphone,
  SunMoon,
  Type,
  Zap,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";

export const metadata: Metadata = {
  title: "InkBook — a classic e-ink reader for Android",
  description:
    "Download InkBook: a no-frills Android e-book reader that looks and feels like a real 6-inch e-ink device. True page turns, paper-gray screen, zero distractions.",
};

const FEATURES = [
  {
    icon: BookOpen,
    title: "True pagination",
    desc: "Page-turn layout, not endless scroll. Just like paper.",
  },
  {
    icon: Smartphone,
    title: "Tap zones + volume keys",
    desc: "Turn pages from either bezel or the volume rocker.",
  },
  {
    icon: BookMarked,
    title: "Long-press dictionary",
    desc: "Press any word for an instant Wiktionary definition.",
  },
  {
    icon: FileText,
    title: "TXT & EPUB",
    desc: "Imports any file, even heavy ones, fully offline.",
  },
  {
    icon: SunMoon,
    title: "Gray / White / Night themes",
    desc: "Real e-ink gray by default.",
  },
  {
    icon: Type,
    title: "Font & spacing controls",
    desc: "Size, typeface, line spacing, justification.",
  },
  {
    icon: Zap,
    title: "E-ink refresh flash",
    desc: "Optional flash on every page turn, for authenticity.",
  },
  {
    icon: Bookmark,
    title: "Remembers your place",
    desc: "Opens exactly where you stopped.",
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
    desc: "Open InkBook, press the “+” and pick any .txt or .epub.",
  },
];

const REPLICA_PARAGRAPHS = [
  "The Villa Rose kept its lights low on card nights, and by midnight the gaming room had gone the color of ash. The dealer in the gray vest swept the felt once more and waited, patient as a lamp.",
  "Mara sat with her back to the lake window and counted the room the way her father taught her: two exits, four cameras, one man who had not touched his drink all evening.",
  "He wore his stillness like a coat. When the pit boss drifted past, he turned a single chip between his fingers, and the chip made no sound at all, which was how she knew it was not a chip.",
  "A woman in the far chair laughed at something the roulette wheel had said. No one else laughed. Somewhere behind the panelled doors a telephone rang twice and stopped, as if it had been answered by a hand that knew exactly when to stop it.",
  "At two the house closed its tables without a word. The players filed out past the fountain, where the water had been shut off for the season, and the bronze boy in the middle held his empty urn toward the dark.",
  "Mara stayed. She had learned at the Villa Rose that the night never really ends there — it only changes tables. She ordered nothing, folded her hands, and watched the gray room count itself to sleep.",
];

function BatteryGlyph({ className }: { className?: string }) {
  return (
    <span aria-hidden="true" className={className ?? "flex items-center"}>
      <span className="flex h-3 w-6 items-center border-2 border-[#1c1c1c] bg-[#d8d8d5] p-px">
        <span className="h-full w-3/4 bg-[#1c1c1c]" />
      </span>
      <span className="h-2 w-0.5 bg-[#1c1c1c]" />
    </span>
  );
}

function MiniBatteryGlyph() {
  return (
    <span aria-hidden="true" className="flex items-center">
      <span className="flex h-3 w-5 items-center border border-[#1c1c1c] bg-[#d6d6d3] p-px">
        <span className="h-full w-2/3 bg-[#1c1c1c]" />
      </span>
      <span className="h-1.5 w-px bg-[#1c1c1c]" />
    </span>
  );
}

export default function Home() {
  return (
    <div className="flex min-h-screen flex-col bg-[#d8d8d5] text-[#1c1c1c]">
      {/* 1. Device-style status bar strip */}
      <header className="w-full bg-[#c7c7c4]">
        <div className="mx-auto flex w-full max-w-5xl items-end justify-between px-4 py-2 sm:px-6">
          <div className="flex items-end gap-3">
            <span className="rounded-t-sm bg-neutral-900 px-3 py-1.5 text-xs font-bold leading-none text-neutral-50">
              InkBook
            </span>
            <span aria-hidden="true" className="text-sm font-bold leading-none">
              »
            </span>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-xs font-medium leading-none">
              1.0.0 · Android 5.0+
            </span>
            <BatteryGlyph />
          </div>
        </div>
        <Separator className="bg-[#8a8a8a]" />
      </header>

      <main className="flex w-full flex-1 flex-col">
        {/* 2. Hero */}
        <section className="mx-auto w-full max-w-5xl px-4 py-16 sm:px-6">
          <div className="grid items-center gap-10 md:grid-cols-2 md:gap-12">
            <div>
              <p className="text-xs font-bold uppercase tracking-[0.3em] text-[#555552]">
                Classic e-ink, reborn
              </p>
              <h1 className="mt-4 text-6xl font-bold tracking-tighter sm:text-7xl">
                InkBook
              </h1>
              <p className="mt-5 max-w-md text-base leading-relaxed text-[#2a2a28]">
                A no-frills e-book reader for Android that looks and feels like
                a real 6-inch e-ink reader. True page turns, paper-gray screen,
                and zero distractions.
              </p>
              <div className="mt-8 flex flex-wrap gap-3">
                <Button
                  asChild
                  size="lg"
                  className="rounded-none border-2 border-[#1c1c1c] bg-neutral-900 text-neutral-50 shadow-none hover:bg-neutral-800 hover:text-neutral-50"
                >
                  <a href="/InkBook.apk" download>
                    <Bot aria-hidden="true" />
                    Download APK
                  </a>
                </Button>
                <Button
                  asChild
                  size="lg"
                  variant="outline"
                  className="rounded-none border-2 border-[#3a3a3a] bg-transparent text-[#1c1c1c] shadow-none hover:bg-[#c7c7c4] hover:text-[#1c1c1c] dark:border-[#3a3a3a] dark:bg-transparent dark:text-[#1c1c1c] dark:hover:bg-[#c7c7c4] dark:hover:text-[#1c1c1c]"
                >
                  <a href="/inkbook-source.zip" download>
                    Source code (.zip)
                  </a>
                </Button>
              </div>
              <p className="mt-4 text-xs font-medium text-[#555552]">
                Signed · 87 KB · works offline · no ads, no tracking
              </p>
            </div>
            <Card className="rounded-none border-2 border-[#3a3a3a] bg-[#e3e3e0] p-4 text-[#1c1c1c] shadow-none">
              <Image
                src="/device.jpg"
                alt="Classic six-inch e-ink e-reader displaying a page of a book"
                width={1000}
                height={1000}
                sizes="(min-width: 768px) 480px, 100vw"
                className="h-auto w-full border border-[#8a8a8a]"
              />
              <p className="mt-3 text-center text-xs font-bold uppercase tracking-[0.2em] text-[#555552]">
                The device that inspired it
              </p>
            </Card>
          </div>
        </section>

        {/* 3. On-screen replica */}
        <section className="mx-auto w-full max-w-5xl px-4 pb-16 sm:px-6">
          <div className="text-center">
            <p className="text-xs font-bold uppercase tracking-[0.3em] text-[#555552]">
              The screen
            </p>
            <h2 className="mt-3 text-3xl font-bold tracking-tight sm:text-4xl">
              Paper-gray, down to the pixels
            </h2>
          </div>
          <div className="mx-auto mt-10 w-full max-w-[640px]">
            <div className="flex aspect-[3/4] w-full flex-col border-2 border-[#3a3a3a] bg-[#d6d6d3]">
              {/* Mini status bar */}
              <div className="flex items-center justify-between gap-2 border-b border-[#3a3a3a] bg-[#c7c7c4] px-3 py-1.5">
                <span className="truncate bg-neutral-900 px-2 py-1 text-[10px] font-bold leading-none text-neutral-50">
                  Althe Villa Rose.txt
                </span>
                <span className="flex shrink-0 items-center gap-2">
                  <span className="text-[10px] font-bold leading-none">
                    7/353 · 04:09
                  </span>
                  <MiniBatteryGlyph />
                </span>
              </div>
              {/* Reading area with bezel tap zones */}
              <div className="flex min-h-0 flex-1">
                <div className="flex w-9 flex-col items-center justify-center gap-2 border-r border-[#8a8a8a] bg-[#cfcfcc] px-1 py-3 sm:w-11">
                  <span className="flex h-9 w-7 items-center justify-center border border-[#3a3a3a] bg-[#d6d6d3]">
                    <ChevronUp
                      aria-hidden="true"
                      className="h-4 w-4"
                      strokeWidth={2.5}
                    />
                  </span>
                  <span className="flex h-9 w-7 items-center justify-center border border-[#3a3a3a] bg-[#d6d6d3]">
                    <ChevronDown
                      aria-hidden="true"
                      className="h-4 w-4"
                      strokeWidth={2.5}
                    />
                  </span>
                </div>
                <div className="min-w-0 flex-1 overflow-hidden px-3 py-4 sm:px-4">
                  <div className="space-y-2 font-serif text-justify text-[11px] leading-snug text-[#2a2a28] sm:space-y-3 sm:text-[13px] sm:leading-relaxed">
                    {REPLICA_PARAGRAPHS.map((paragraph, index) => (
                      <p
                        key={paragraph.slice(0, 24)}
                        className={index === 0 ? undefined : "indent-8"}
                      >
                        {paragraph}
                      </p>
                    ))}
                  </div>
                </div>
                <div className="flex w-9 flex-col items-center justify-center gap-2 border-l border-[#8a8a8a] bg-[#cfcfcc] px-1 py-3 sm:w-11">
                  <span className="flex h-9 w-7 items-center justify-center border border-[#3a3a3a] bg-[#d6d6d3]">
                    <ChevronUp
                      aria-hidden="true"
                      className="h-4 w-4"
                      strokeWidth={2.5}
                    />
                  </span>
                  <span className="flex h-9 w-7 items-center justify-center border border-[#3a3a3a] bg-[#d6d6d3]">
                    <ChevronDown
                      aria-hidden="true"
                      className="h-4 w-4"
                      strokeWidth={2.5}
                    />
                  </span>
                </div>
              </div>
              {/* Bottom control bar */}
              <div className="flex items-center justify-between gap-2 border-t-2 border-[#3a3a3a] bg-[#c7c7c4] px-3 py-2">
                <div className="flex items-center gap-2">
                  <span className="border border-[#3a3a3a] bg-[#d6d6d3] px-2 py-1.5 text-[10px] font-bold leading-none">
                    Q|Q
                  </span>
                  <span className="border border-[#3a3a3a] bg-[#d6d6d3] px-2 py-1.5 text-[10px] font-bold leading-none">
                    M
                  </span>
                </div>
                <span className="flex h-9 w-9 items-center justify-center rounded-lg border-[3px] border-[#3a3a3a] bg-[#d6d6d3]">
                  <span
                    aria-hidden="true"
                    className="h-4 w-4 rounded-[3px] bg-[#3a3a3a]"
                  />
                </span>
                <div className="flex items-center gap-2">
                  <span className="flex h-7 w-7 items-center justify-center border border-[#3a3a3a] bg-[#d6d6d3]">
                    <Moon aria-hidden="true" className="h-3.5 w-3.5" />
                  </span>
                  <span className="flex h-7 w-7 items-center justify-center border border-[#3a3a3a] bg-[#d6d6d3]">
                    <Keyboard aria-hidden="true" className="h-3.5 w-3.5" />
                  </span>
                </div>
              </div>
            </div>
            <p className="mt-4 text-center text-xs font-bold uppercase tracking-[0.2em] text-[#555552]">
              Exactly what you get on your phone.
            </p>
          </div>
        </section>

        {/* 4. Features grid */}
        <section className="mx-auto w-full max-w-5xl px-4 pb-16 sm:px-6">
          <p className="text-xs font-bold uppercase tracking-[0.3em] text-[#555552]">
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
                  className="gap-3 rounded-none border-2 border-[#3a3a3a] bg-[#e3e3e0] p-5 text-[#1c1c1c] shadow-none"
                >
                  <span className="flex h-9 w-9 items-center justify-center border border-[#3a3a3a] bg-[#d8d8d5]">
                    <Icon aria-hidden="true" className="h-4 w-4" />
                  </span>
                  <CardTitle className="text-sm font-bold leading-snug">
                    {feature.title}
                  </CardTitle>
                  <p className="text-xs leading-relaxed text-[#555552]">
                    {feature.desc}
                  </p>
                </Card>
              );
            })}
          </div>
        </section>

        {/* 5. Install steps */}
        <section className="mx-auto w-full max-w-5xl px-4 pb-16 sm:px-6">
          <p className="text-xs font-bold uppercase tracking-[0.3em] text-[#555552]">
            Install
          </p>
          <h2 className="mt-3 text-3xl font-bold tracking-tight sm:text-4xl">
            Install on your Android device
          </h2>
          <ol className="mt-10 flex flex-col gap-4">
            {INSTALL_STEPS.map((step, index) => (
              <li
                key={step.title}
                className="flex items-start gap-5 border-2 border-[#3a3a3a] bg-[#e3e3e0] px-5 py-4 sm:items-center sm:gap-6 sm:px-6"
              >
                <span className="w-10 shrink-0 font-mono text-3xl font-bold leading-none tracking-tighter sm:text-4xl">
                  {index + 1}
                </span>
                <div className="min-w-0">
                  <p className="text-sm font-bold">{step.title}</p>
                  <p className="mt-1 text-sm leading-relaxed text-[#555552]">
                    {step.desc}
                  </p>
                </div>
              </li>
            ))}
          </ol>
        </section>
      </main>

      {/* 6. Sticky footer */}
      <footer className="mt-auto w-full border-t-2 border-[#3a3a3a] bg-[#c7c7c4]">
        <div className="mx-auto flex w-full max-w-5xl flex-col gap-2 px-4 py-6 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <p className="text-xs font-bold">
            InkBook — inspired by the classic e-readers
          </p>
          <p className="text-xs font-medium text-[#555552]">
            v1.0.0 · signed build
          </p>
        </div>
      </footer>
    </div>
  );
}
