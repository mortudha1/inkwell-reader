"use client";

import {
  Drawer,
  DrawerContent,
  DrawerHeader,
  DrawerTitle,
  DrawerDescription,
} from "@/components/ui/drawer";
import { Slider } from "@/components/ui/slider";
import { Switch } from "@/components/ui/switch";
import { Minus, Plus } from "lucide-react";
import {
  FONT_OPTIONS,
  MARGIN_STEPS,
  READER_THEMES,
  themeClass,
  type ReaderSettings,
  type ThemeId,
} from "@/lib/reader/types";
import { cn } from "@/lib/utils";

interface SettingsSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  settings: ReaderSettings;
  onChange: (patch: Partial<ReaderSettings>) => void;
  /** current reader theme — drawers portal to <body>, so theme vars must be re-applied */
  theme: ThemeId;
}

export function SettingsSheet({ open, onOpenChange, settings, onChange, theme }: SettingsSheetProps) {
  return (
    <Drawer open={open} onOpenChange={onOpenChange} repositionInputs={false}>
      <DrawerContent
        className={cn("reader-sheet reader-root", themeClass(theme))}
        style={{
          background: "var(--reader-bg)",
          color: "var(--reader-fg)",
          borderColor: "var(--reader-line)",
        }}
      >
        <div className="mx-auto w-full max-w-xl">
          <DrawerHeader className="sr-only">
            <DrawerTitle>Reading settings</DrawerTitle>
            <DrawerDescription>
              Customize the look and feel of your book.
            </DrawerDescription>
          </DrawerHeader>

          <div className="space-y-6 px-5 pb-8 pt-2">
            {/* Theme */}
            <section aria-label="Reading theme">
              <h4 className="settings-label">Theme</h4>
              <div className="flex items-center justify-between gap-2">
                {READER_THEMES.map((t) => (
                  <button
                    key={t.id}
                    onClick={() => onChange({ theme: t.id })}
                    aria-label={`${t.name} theme`}
                    aria-pressed={settings.theme === t.id}
                    className="group flex flex-1 flex-col items-center gap-1.5 rounded-lg py-1"
                  >
                    <span
                      className={cn(
                        "flex size-11 items-center justify-center rounded-full border-2 transition-transform active:scale-95",
                        settings.theme === t.id ? "scale-105" : "group-hover:scale-105"
                      )}
                      style={{
                        background: t.swatch,
                        borderColor: settings.theme === t.id ? "var(--reader-accent)" : "var(--reader-line)",
                      }}
                    >
                      {settings.theme === t.id ? (
                        <span
                          className="size-2 rounded-full"
                          style={{ background: t.id === "light" || t.id === "paper" || t.id === "sage" ? "#7a5a2e" : "#e5a54b" }}
                        />
                      ) : null}
                    </span>
                    <span className="text-[11px]" style={{ color: settings.theme === t.id ? "var(--reader-fg)" : "var(--reader-muted)" }}>
                      {t.name}
                    </span>
                  </button>
                ))}
              </div>
            </section>

            {/* Typeface */}
            <section aria-label="Typeface">
              <h4 className="settings-label">Typeface</h4>
              <div className="grid grid-cols-5 gap-2">
                {FONT_OPTIONS.map((f) => (
                  <button
                    key={f.id}
                    onClick={() => onChange({ font: f.id })}
                    aria-pressed={settings.font === f.id}
                    className={cn(
                      "flex flex-col items-center gap-1 rounded-lg border py-2 transition-colors",
                      settings.font === f.id ? "font-selected" : "font-unselected"
                    )}
                    style={{
                      borderColor: settings.font === f.id ? "var(--reader-accent)" : "var(--reader-line)",
                    }}
                  >
                    <span
                      className="reader-serif-aa text-xl leading-none"
                      style={{ fontFamily: f.stack }}
                    >
                      Aa
                    </span>
                    <span className="text-[10px]" style={{ color: "var(--reader-muted)" }}>
                      {f.name}
                    </span>
                  </button>
                ))}
              </div>
            </section>

            {/* Size */}
            <section aria-label="Text size">
              <div className="flex items-center justify-between">
                <h4 className="settings-label">Size</h4>
                <span className="text-xs" style={{ color: "var(--reader-muted)" }}>
                  {settings.fontSize}px
                </span>
              </div>
              <div className="flex items-center gap-3">
                <button
                  onClick={() => onChange({ fontSize: Math.max(14, settings.fontSize - 1) })}
                  aria-label="Decrease text size"
                  className="rounded-full border p-1.5 transition-colors active:scale-95"
                  style={{ borderColor: "var(--reader-line)" }}
                >
                  <Minus className="size-4" />
                </button>
                <Slider
                  value={[settings.fontSize]}
                  min={14}
                  max={30}
                  step={1}
                  onValueChange={(v) => onChange({ fontSize: v[0] })}
                  aria-label="Text size"
                  className="flex-1"
                  style={{ ["--primary" as string]: "var(--reader-accent)" }}
                />
                <button
                  onClick={() => onChange({ fontSize: Math.min(30, settings.fontSize + 1) })}
                  aria-label="Increase text size"
                  className="rounded-full border p-1.5 transition-colors active:scale-95"
                  style={{ borderColor: "var(--reader-line)" }}
                >
                  <Plus className="size-4" />
                </button>
              </div>
            </section>

            {/* Line spacing */}
            <section aria-label="Line spacing">
              <div className="flex items-center justify-between">
                <h4 className="settings-label">Line spacing</h4>
                <span className="text-xs" style={{ color: "var(--reader-muted)" }}>
                  {settings.lineHeight.toFixed(2)}
                </span>
              </div>
              <Slider
                value={[settings.lineHeight]}
                min={1.2}
                max={2.2}
                step={0.05}
                onValueChange={(v) => onChange({ lineHeight: v[0] })}
                aria-label="Line spacing"
                style={{ ["--primary" as string]: "var(--reader-accent)" }}
              />
            </section>

            {/* Margins */}
            <section aria-label="Page margins">
              <h4 className="settings-label">Margins</h4>
              <div className="grid grid-cols-4 gap-2">
                {MARGIN_STEPS.map((m) => (
                  <button
                    key={m.v}
                    onClick={() => onChange({ margin: m.v as 0 | 1 | 2 | 3 })}
                    aria-pressed={settings.margin === m.v}
                    className={cn(
                      "rounded-lg border py-1.5 text-xs font-medium transition-colors",
                      settings.margin === m.v ? "font-selected" : "font-unselected"
                    )}
                    style={{
                      borderColor: settings.margin === m.v ? "var(--reader-accent)" : "var(--reader-line)",
                    }}
                  >
                    {m.label}
                  </button>
                ))}
              </div>
            </section>

            {/* Typography toggles */}
            <section aria-label="Typography options" className="space-y-3">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium">Justify text</p>
                  <p className="text-xs" style={{ color: "var(--reader-muted)" }}>
                    Even edges like a printed page
                  </p>
                </div>
                <Switch
                  checked={settings.justify}
                  onCheckedChange={(v) => onChange({ justify: v })}
                  aria-label="Justify text"
                  style={{ ["--primary" as string]: "var(--reader-accent)" }}
                />
              </div>
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium">Hyphenation</p>
                  <p className="text-xs" style={{ color: "var(--reader-muted)" }}>
                    Break long words at line ends
                  </p>
                </div>
                <Switch
                  checked={settings.hyphenate}
                  onCheckedChange={(v) => onChange({ hyphenate: v })}
                  aria-label="Hyphenation"
                  style={{ ["--primary" as string]: "var(--reader-accent)" }}
                />
              </div>
            </section>
          </div>
        </div>
      </DrawerContent>
    </Drawer>
  );
}
