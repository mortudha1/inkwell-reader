// Minimal promise-based IndexedDB wrapper for the Inkwell library.
// Books, chapters and cover blobs live entirely on-device — instant loads,
// fully offline reading.

import type { BookMeta, ChapterContent } from "./types";

const DB_NAME = "inkwell";
const DB_VERSION = 1;
const STORE_BOOKS = "books";
const STORE_CHAPTERS = "chapters";
const STORE_COVERS = "covers";

let dbPromise: Promise<IDBDatabase> | null = null;

function openDb(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE_BOOKS)) {
        db.createObjectStore(STORE_BOOKS, { keyPath: "id" });
      }
      if (!db.objectStoreNames.contains(STORE_CHAPTERS)) {
        db.createObjectStore(STORE_CHAPTERS, { keyPath: ["bookId", "index"] });
      }
      if (!db.objectStoreNames.contains(STORE_COVERS)) {
        db.createObjectStore(STORE_COVERS, { keyPath: "id" });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error("IndexedDB open failed"));
  });
  return dbPromise;
}

function tx<T>(
  stores: string[],
  mode: IDBTransactionMode,
  fn: (tx: IDBTransaction) => IDBRequest<T> | void
): Promise<T | undefined> {
  return openDb().then(
    (db) =>
      new Promise<T | undefined>((resolve, reject) => {
        const transaction = db.transaction(stores, mode);
        let result: T | undefined;
        let request: IDBRequest<T> | void;
        transaction.oncomplete = () => resolve(request instanceof IDBRequest ? request.result : result);
        transaction.onerror = () => reject(transaction.error);
        transaction.onabort = () => reject(transaction.error);
        request = fn(transaction);
        if (request instanceof IDBRequest) {
          request.onsuccess = () => {
            result = request.result;
          };
        }
      })
  );
}

// ---------------------------------------------------------------------------
// Books
// ---------------------------------------------------------------------------
export async function idbListBooks(): Promise<BookMeta[]> {
  const books = (await tx<BookMeta[]>([STORE_BOOKS], "readonly", (t) =>
    t.objectStore(STORE_BOOKS).getAll()
  )) as BookMeta[] | undefined;
  return (books ?? []).sort((a, b) => b.createdAt - a.createdAt);
}

export async function idbGetBook(id: string): Promise<BookMeta | undefined> {
  const book = await tx<BookMeta>([STORE_BOOKS], "readonly", (t) =>
    t.objectStore(STORE_BOOKS).get(id)
  );
  return book;
}

export async function idbPutBook(meta: BookMeta): Promise<void> {
  await tx([STORE_BOOKS], "readwrite", (t) => t.objectStore(STORE_BOOKS).put(meta));
}

// ---------------------------------------------------------------------------
// Chapters
// ---------------------------------------------------------------------------
export async function idbGetChapter(
  bookId: string,
  index: number
): Promise<ChapterContent | undefined> {
  return tx<ChapterContent>([STORE_CHAPTERS], "readonly", (t) =>
    t.objectStore(STORE_CHAPTERS).get([bookId, index])
  );
}

export async function idbPutChapters(chapters: ChapterContent[]): Promise<void> {
  if (!chapters.length) return;
  await tx([STORE_CHAPTERS], "readwrite", (t) => {
    const store = t.objectStore(STORE_CHAPTERS);
    for (const ch of chapters) store.put(ch);
  });
}

// ---------------------------------------------------------------------------
// Covers
// ---------------------------------------------------------------------------
export async function idbPutCover(id: string, blob: Blob): Promise<void> {
  await tx([STORE_COVERS], "readwrite", (t) =>
    t.objectStore(STORE_COVERS).put({ id, blob })
  );
}

export async function idbGetCover(id: string): Promise<Blob | undefined> {
  const row = await tx<{ id: string; blob: Blob }>([STORE_COVERS], "readonly", (t) =>
    t.objectStore(STORE_COVERS).get(id)
  );
  return row?.blob;
}

// ---------------------------------------------------------------------------
// Delete
// ---------------------------------------------------------------------------
export async function idbDeleteBook(id: string): Promise<void> {
  await tx([STORE_BOOKS, STORE_COVERS], "readwrite", (t) => {
    t.objectStore(STORE_BOOKS).delete(id);
    t.objectStore(STORE_COVERS).delete(id);
  });
  // chapters use a composite key [bookId, index] — clear via key range cursor
  await new Promise<void>((resolve, reject) => {
    openDb().then((db) => {
      const transaction = db.transaction(STORE_CHAPTERS, "readwrite");
      const store = transaction.objectStore(STORE_CHAPTERS);
      const range = IDBKeyRange.bound([id, 0], [id, Infinity]);
      const req = store.openCursor(range);
      req.onsuccess = () => {
        const cursor = req.result;
        if (cursor) {
          cursor.delete();
          cursor.continue();
        }
      };
      transaction.oncomplete = () => resolve();
      transaction.onerror = () => reject(transaction.error);
    });
  });
}
