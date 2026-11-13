import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/db";
import ZAI from "z-ai-web-dev-sdk";

export const runtime = "nodejs";

export interface Sense {
  partOfSpeech: string;
  definition: string;
  example?: string | null;
}

export interface DefineResponse {
  word: string;
  phonetic: string | null;
  senses: Sense[];
  source: "cache" | "dictionary" | "ai";
}

// ---------------------------------------------------------------------------
// Small in-memory LRU for hot words (per server instance).
// ---------------------------------------------------------------------------
const MEM_MAX = 240;
const memCache = new Map<string, DefineResponse>();

function memGet(key: string): DefineResponse | undefined {
  const hit = memCache.get(key);
  if (hit) {
    memCache.delete(key);
    memCache.set(key, hit);
  }
  return hit;
}

function memSet(key: string, value: DefineResponse) {
  if (memCache.size >= MEM_MAX) {
    const oldest = memCache.keys().next().value;
    if (oldest !== undefined) memCache.delete(oldest);
  }
  memCache.set(key, value);
}

// ---------------------------------------------------------------------------
// Input normalization
// ---------------------------------------------------------------------------
const WORD_RE = /^[A-Za-z\u00C0-\u024F'’-]+(?: [A-Za-z\u00C0-\u024F'’-]+)*$/;

function normalizeWord(raw: string): { word: string; isPhrase: boolean } | null {
  let w = raw.trim();
  // strip surrounding quotes/punctuation
  w = w.replace(/^[\s"'“”‘’(\[{—–-]+/, "").replace(/[\s"'“”‘’)\]},.;:!?…]+$/, "");
  w = w.replace(/[’]/g, "'");
  if (!w || w.length > 90 || !WORD_RE.test(w)) return null;
  const words = w.split(/\s+/);
  if (words.length > 6) return null;
  return { word: w, isPhrase: words.length > 1 };
}

// ---------------------------------------------------------------------------
// Source 1: dictionaryapi.dev (free, no key)
// ---------------------------------------------------------------------------
async function fetchDictionaryApi(word: string): Promise<DefineResponse | null> {
  try {
    const url = `https://api.dictionaryapi.dev/api/v2/entries/en/${encodeURIComponent(word.toLowerCase())}`;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 4000);
    const res = await fetch(url, {
      signal: controller.signal,
      headers: { accept: "application/json" },
      cache: "no-store",
    });
    clearTimeout(timer);
    if (!res.ok) return null;
    const data = (await res.json()) as Array<{
      word?: string;
      phonetic?: string;
      phonetics?: { text?: string }[];
      meanings?: {
        partOfSpeech?: string;
        definitions?: { definition?: string; example?: string }[];
      }[];
    }>;
    if (!Array.isArray(data) || !data.length) return null;
    const entry = data[0];
    const phonetic =
      entry.phonetic ?? entry.phonetics?.find((p) => p.text)?.text ?? null;
    const senses: Sense[] = [];
    for (const meaning of entry.meanings ?? []) {
      for (const d of meaning.definitions ?? []) {
        if (!d.definition) continue;
        senses.push({
          partOfSpeech: meaning.partOfSpeech ?? "",
          definition: String(d.definition),
          example: d.example ?? null,
        });
        if (senses.length >= 5) break;
      }
      if (senses.length >= 5) break;
    }
    if (!senses.length) return null;
    return { word, phonetic, senses, source: "dictionary" };
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------------------
// Source 2: LLM via z-ai-web-dev-sdk (handles inflections, proper nouns,
// phrases and contextual explanations).
// ---------------------------------------------------------------------------
const WORD_SYSTEM = `You are a precise dictionary engine embedded in an e-reader. Given a word (and optionally the sentence where the reader encountered it), respond with ONLY valid JSON — no markdown, no extra text — in this exact shape:
{"word":string,"phonetic":string|null,"senses":[{"partOfSpeech":string,"definition":string,"example":string|null}]}
Rules:
- Up to 3 senses, most common meaning first.
- Each definition under 25 words, plain language.
- If the word is an inflected form (plural, -ed, -ing, comparative), define the base form and briefly note the form in the partOfSpeech (e.g. "verb, past tense of run").
- Proper nouns: give a concise factual gloss.
- If it is not a real English word, still give your best gloss of what it likely means.
- phonetic in IPA if known, else null.`;

const PHRASE_SYSTEM = `You are a reading assistant inside an e-reader. The reader selected a phrase or short passage and wants to understand it. Respond with ONLY valid JSON — no markdown, no extra text — in this exact shape:
{"word":string,"phonetic":null,"senses":[{"partOfSpeech":"phrase","definition":string,"example":null}]}
Rules:
- The definition explains the meaning of the phrase in context, under 45 words, plain language.
- Mention figurative, idiomatic or archaic senses when relevant.`;

function extractJson(text: string): string {
  const fence = text.match(/```(?:json)?\s*([\s\S]*?)```/i);
  if (fence) return fence[1].trim();
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start !== -1 && end > start) return text.slice(start, end + 1);
  return text.trim();
}

async function fetchAiDefinition(
  word: string,
  context: string,
  isPhrase: boolean
): Promise<DefineResponse> {
  const zai = await ZAI.create();
  const user = isPhrase
    ? `Phrase: "${word}"${context ? `\nSentence where it appears: "${context}"` : ""}`
    : `Word: "${word}"${context ? `\nSentence where it appears: "${context}"` : ""}`;

  const completion = await Promise.race([
    zai.chat.completions.create({
      messages: [
        { role: "assistant", content: isPhrase ? PHRASE_SYSTEM : WORD_SYSTEM },
        { role: "user", content: user },
      ],
      thinking: { type: "disabled" },
    }),
    new Promise<never>((_, reject) =>
      setTimeout(() => reject(new Error("AI timeout")), 25000)
    ),
  ]);

  const content = (completion as { choices?: { message?: { content?: string } }[] })
    .choices?.[0]?.message?.content ?? "";
  if (!content.trim()) throw new Error("Empty AI response");

  const parsed = JSON.parse(extractJson(content)) as {
    word?: string;
    phonetic?: string | null;
    senses?: { partOfSpeech?: string; definition?: string; example?: string | null }[];
  };
  const senses = (parsed.senses ?? [])
    .filter((s) => s && typeof s.definition === "string" && s.definition.trim())
    .slice(0, 4)
    .map((s) => ({
      partOfSpeech: String(s.partOfSpeech ?? ""),
      definition: String(s.definition).trim(),
      example: s.example ? String(s.example) : null,
    }));
  if (!senses.length) throw new Error("No senses in AI response");
  return {
    word,
    phonetic: typeof parsed.phonetic === "string" ? parsed.phonetic : null,
    senses,
    source: "ai",
  };
}

// ---------------------------------------------------------------------------
// SQLite persistence
// ---------------------------------------------------------------------------
function saveToDb(payload: DefineResponse) {
  const key = payload.word.toLowerCase();
  const senses = JSON.stringify(payload.senses);
  db.wordDefinition
    .upsert({
      where: { word: key },
      update: { senses, phonetic: payload.phonetic, source: payload.source },
      create: { word: key, senses, phonetic: payload.phonetic, source: payload.source },
    })
    .catch(() => {});
}

async function loadFromDb(word: string): Promise<DefineResponse | null> {
  try {
    const row = await db.wordDefinition.findUnique({ where: { word } });
    if (!row) return null;
    const senses = JSON.parse(row.senses) as Sense[];
    if (!Array.isArray(senses) || !senses.length) return null;
    db.wordDefinition
      .update({ where: { word: row.word }, data: { hits: { increment: 1 } } })
      .catch(() => {});
    return { word: row.word, phonetic: row.phonetic, senses, source: "cache" };
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------------------
// Route handler
// ---------------------------------------------------------------------------
export async function GET(request: NextRequest) {
  const params = request.nextUrl.searchParams;
  const raw = params.get("word") ?? "";
  const context = (params.get("context") ?? "").slice(0, 300);
  const forceAi = params.get("mode") === "ai";

  const norm = normalizeWord(raw);
  if (!norm) {
    return NextResponse.json({ error: "Invalid word" }, { status: 400 });
  }
  const { word, isPhrase } = norm;
  const key = `${word.toLowerCase()}${forceAi ? "|ctx" : ""}`;

  // 1. memory cache
  const mem = memGet(key);
  if (mem) return NextResponse.json(mem);

  // 2. sqlite cache (plain word lookups only)
  if (!forceAi && !isPhrase) {
    const cached = await loadFromDb(word.toLowerCase());
    if (cached) {
      memSet(key, cached);
      return NextResponse.json(cached);
    }
  }

  // 3. dictionaryapi.dev (single words only)
  if (!forceAi && !isPhrase) {
    const dict = await fetchDictionaryApi(word);
    if (dict) {
      saveToDb(dict);
      memSet(key, dict);
      return NextResponse.json(dict);
    }
  }

  // 4. LLM fallback / contextual explain
  try {
    const ai = await fetchAiDefinition(word, context, isPhrase || forceAi);
    if (!forceAi && !isPhrase) saveToDb(ai);
    memSet(key, ai);
    return NextResponse.json(ai);
  } catch {
    return NextResponse.json(
      { error: "No definition found. Try again." },
      { status: 404 }
    );
  }
}
