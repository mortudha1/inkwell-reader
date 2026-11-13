// Client-side word-lookup helper with a session memory cache.

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

const cache = new Map<string, DefineResponse>();

export async function lookupWord(
  word: string,
  context = "",
  mode: "plain" | "ai" = "plain"
): Promise<DefineResponse> {
  const key = `${word.toLowerCase()}|${mode}`;
  const hit = cache.get(key);
  if (hit) return hit;

  const params = new URLSearchParams({ word });
  if (context) params.set("context", context);
  if (mode === "ai") params.set("mode", "ai");

  const res = await fetch(`/api/define?${params.toString()}`);
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { error?: string } | null;
    throw new Error(body?.error ?? "Lookup failed");
  }
  const data = (await res.json()) as DefineResponse;
  cache.set(key, data);
  return data;
}
