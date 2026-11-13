// Vocabulary list: every word the reader has looked up is cached in SQLite
// by /api/define. This route exposes that history (plus delete/clear).

import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/db";

export const runtime = "nodejs";

interface Sense {
  partOfSpeech: string;
  definition: string;
  example?: string | null;
}

export interface VocabEntryPayload {
  word: string;
  phonetic: string | null;
  senses: Sense[];
  source: string;
  hits: number;
  updatedAt: string;
}

export async function GET() {
  try {
    const rows = await db.wordDefinition.findMany({
      orderBy: { updatedAt: "desc" },
      take: 300,
    });
    const entries: VocabEntryPayload[] = [];
    for (const r of rows) {
      let senses: Sense[] = [];
      try {
        senses = JSON.parse(r.senses) as Sense[];
      } catch {
        /* skip malformed */
      }
      if (!Array.isArray(senses) || !senses.length) continue;
      entries.push({
        word: r.word,
        phonetic: r.phonetic,
        senses: senses.slice(0, 5),
        source: r.source,
        hits: r.hits,
        updatedAt: r.updatedAt.toISOString(),
      });
    }
    return NextResponse.json({ entries });
  } catch {
    return NextResponse.json({ entries: [] });
  }
}

export async function DELETE(request: NextRequest) {
  const word = request.nextUrl.searchParams.get("word");
  try {
    if (word) {
      await db.wordDefinition.delete({ where: { word: word.toLowerCase() } });
    } else {
      await db.wordDefinition.deleteMany({});
    }
    return NextResponse.json({ ok: true });
  } catch {
    return NextResponse.json({ ok: false }, { status: 500 });
  }
}
