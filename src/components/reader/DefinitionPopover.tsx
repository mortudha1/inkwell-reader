"use client";

import { Sparkles, X, RefreshCw } from "lucide-react";
import type { DefineResponse } from "@/lib/reader/lookup";
import { cn } from "@/lib/utils";

export interface DefinitionState {
  word: string;
  /** anchor: center x, top y of the tapped word (viewport coords) */
  x: number;
  y: number;
  /** true → show below the anchor line */
  below: boolean;
  context: string;
  loading: boolean;
  data: DefineResponse | null;
  error: string | null;
  aiMode: boolean;
}

interface DefinitionPopoverProps {
  state: DefinitionState;
  onClose: () => void;
  onExplainInContext: () => void;
  onRetry: () => void;
}

const SOURCE_LABEL: Record<DefineResponse["source"], string> = {
  dictionary: "Dictionary",
  ai: "AI",
  cache: "Dictionary · offline",
};

export function DefinitionPopover({
  state,
  onClose,
  onExplainInContext,
  onRetry,
}: DefinitionPopoverProps) {
  const w = Math.min(400, window.innerWidth - 24);
  const left = Math.max(12, Math.min(state.x - w / 2, window.innerWidth - w - 12));
  const top = state.below
    ? state.y + 14
    : Math.max(70, state.y - 14);

  return (
    <div
      role="dialog"
      aria-label={`Definition of ${state.word}`}
      className="reader-pop fixed z-50 w-[min(400px,calc(100vw-24px))] rounded-xl border shadow-2xl"
      style={{
        left,
        top,
        transform: state.below ? "none" : "translateY(-100%)",
        background: "var(--reader-pop-bg)",
        borderColor: "var(--reader-line)",
        color: "var(--reader-fg)",
      }}
      onClick={(e) => e.stopPropagation()}
    >
      {/* word header */}
      <div className="flex items-start gap-2 px-4 pt-3 pb-2">
        <div className="min-w-0 flex-1">
          <h3 className="reader-serif text-lg font-semibold leading-tight break-words">
            {state.word}
          </h3>
          {state.data?.phonetic ? (
            <p className="text-xs" style={{ color: "var(--reader-muted)" }}>
              {state.data.phonetic}
            </p>
          ) : null}
        </div>
        <button
          onClick={onClose}
          aria-label="Close definition"
          className="rounded-md p-1 transition-colors hover:bg-black/5 dark:hover:bg-white/10"
          style={{ color: "var(--reader-muted)" }}
        >
          <X className="size-4" />
        </button>
      </div>

      {/* body */}
      <div className="reader-scroll max-h-[44vh] overflow-y-auto px-4 pb-3">
        {state.loading ? (
          <div className="space-y-2 py-1" aria-live="polite">
            <div className="reader-skeleton h-4 w-3/4 rounded" />
            <div className="reader-skeleton h-4 w-full rounded" />
            <div className="reader-skeleton h-4 w-2/3 rounded" />
          </div>
        ) : state.error ? (
          <div className="py-2">
            <p className="text-sm" style={{ color: "var(--reader-muted)" }}>
              {state.error}
            </p>
            <button
              onClick={onRetry}
              className="mt-2 inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium transition-colors hover:bg-black/5"
              style={{ color: "var(--reader-accent)" }}
            >
              <RefreshCw className="size-3.5" /> Try again
            </button>
          </div>
        ) : state.data ? (
          <ol className="space-y-2.5">
            {state.data.senses.map((sense, i) => (
              <li key={i} className="text-sm leading-relaxed">
                {sense.partOfSpeech ? (
                  <span
                    className="italic text-xs font-medium"
                    style={{ color: "var(--reader-accent)" }}
                  >
                    {sense.partOfSpeech}
                  </span>
                ) : null}
                <p>{sense.definition}</p>
                {sense.example ? (
                  <p
                    className="mt-0.5 text-xs italic"
                    style={{ color: "var(--reader-muted)" }}
                  >
                    “{sense.example}”
                  </p>
                ) : null}
              </li>
            ))}
          </ol>
        ) : null}
      </div>

      {/* footer */}
      <div
        className="flex items-center justify-between gap-2 border-t px-4 py-2"
        style={{ borderColor: "var(--reader-line)" }}
      >
        <span
          className="text-[10px] uppercase tracking-wider"
          style={{ color: "var(--reader-muted)" }}
        >
          {state.data ? (state.aiMode ? "AI · in context" : SOURCE_LABEL[state.data.source]) : "Inkwell dictionary"}
        </span>
        <div className="flex items-center gap-1">
          {!state.aiMode && !state.loading && !state.error ? (
            <button
              onClick={onExplainInContext}
              className="inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium transition-colors hover:bg-black/5"
              style={{ color: "var(--reader-accent)" }}
            >
              <Sparkles className="size-3.5" />
              Explain in context
            </button>
          ) : null}
          <button
            onClick={onClose}
            className={cn(
              "rounded-md px-2 py-1 text-xs font-medium transition-colors hover:bg-black/5"
            )}
            style={{ color: "var(--reader-muted)" }}
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
