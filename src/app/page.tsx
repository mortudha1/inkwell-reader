"use client";

import { useState, useSyncExternalStore } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { LibraryView } from "@/components/library/LibraryView";
import { ReaderView } from "@/components/reader/ReaderView";

const emptySubscribe = () => () => {};

function Splash() {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-3 bg-background">
      <p className="reader-serif-aa text-3xl font-semibold tracking-tight text-foreground">
        Inkwell
      </p>
      <p className="text-xs uppercase tracking-[0.3em] text-muted-foreground">eReader</p>
    </div>
  );
}

export default function Page() {
  // hydration-safe mounted flag without setState-in-effect
  const mounted = useSyncExternalStore(
    emptySubscribe,
    () => true,
    () => false
  );
  const [bookId, setBookId] = useState<string | null>(null);

  if (!mounted) return <Splash />;

  return (
    <AnimatePresence mode="wait" initial={false}>
      {bookId == null ? (
        <motion.div
          key="library"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0, scale: 0.985 }}
          transition={{ duration: 0.18, ease: "easeOut" }}
        >
          <LibraryView onOpen={(id) => setBookId(id)} />
        </motion.div>
      ) : (
        <motion.div
          key={`reader-${bookId}`}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.18, ease: "easeOut" }}
        >
          <ReaderView bookId={bookId} onExit={() => setBookId(null)} />
        </motion.div>
      )}
    </AnimatePresence>
  );
}
