// Kindle-style pagination engine.
//
// Content flows through a horizontally-overflowing sequence of CSS columns;
// each column is exactly one "page". Page turns are GPU translateX moves.
//
// Positions are tracked as char offsets into the chapter's text (via a
// text-node index), so the exact reading position survives font-size, theme,
// margin and viewport changes — just like a Kindle.

export interface TextIndex {
  nodes: Text[];
  starts: number[];
  /** node → its global start offset */
  nodeMap: Map<Text, number>;
  total: number;
}

export function buildTextIndex(root: HTMLElement): TextIndex {
  const nodes: Text[] = [];
  const starts: number[] = [];
  const nodeMap = new Map<Text, number>();
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let total = 0;
  let n = walker.nextNode();
  while (n) {
    const text = n as Text;
    if (text.length > 0) {
      nodeMap.set(text, total);
      nodes.push(text);
      starts.push(total);
      total += text.length;
    }
    n = walker.nextNode();
  }
  return { nodes, starts, nodeMap, total };
}

function locate(index: TextIndex, offset: number): { node: Text; offset: number } | null {
  if (!index.total || offset < 0 || offset >= index.total) return null;
  let lo = 0;
  let hi = index.starts.length - 1;
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1;
    if (index.starts[mid] <= offset) lo = mid;
    else hi = mid - 1;
  }
  return { node: index.nodes[lo], offset: offset - index.starts[lo] };
}

export class Paginator {
  private viewport: HTMLElement;
  private flow: HTMLElement;
  private index: TextIndex = { nodes: [], starts: [], nodeMap: new Map(), total: 0 };

  page = 0;
  pageCount = 1;
  colWidth = 0;
  colHeight = 0;
  gap = 48;

  constructor(viewport: HTMLElement, flow: HTMLElement) {
    this.viewport = viewport;
    this.flow = flow;
  }

  /** (Re)measure the flow. Must be called after content or layout changes. */
  layout(): void {
    const cs = getComputedStyle(this.viewport);
    const padL = parseFloat(cs.paddingLeft) || 0;
    const padT = parseFloat(cs.paddingTop) || 0;
    const padR = parseFloat(cs.paddingRight) || 0;
    const padB = parseFloat(cs.paddingBottom) || 0;
    this.colWidth = Math.max(80, this.viewport.clientWidth - padL - padR);
    this.colHeight = Math.max(80, this.viewport.clientHeight - padT - padB);

    const s = this.flow.style;
    s.width = `${this.colWidth}px`;
    s.height = `${this.colHeight}px`;
    s.columnWidth = `${this.colWidth}px`;
    s.columnGap = `${this.gap}px`;
    s.columnFill = "auto";

    const sw = this.flow.scrollWidth;
    this.pageCount = Math.max(1, Math.round((sw + this.gap) / (this.colWidth + this.gap)));
    this.index = buildTextIndex(this.flow);
    if (this.page >= this.pageCount) this.page = this.pageCount - 1;
  }

  goToPage(page: number, animate = true): void {
    const p = Math.max(0, Math.min(this.pageCount - 1, page));
    this.page = p;
    this.flow.style.transition = animate
      ? "transform 300ms cubic-bezier(0.22, 0.61, 0.36, 1)"
      : "none";
    this.flow.style.transform = `translateX(${-p * (this.colWidth + this.gap)}px)`;
  }

  /**
   * React may re-create the chapter's DOM nodes without our knowledge
   * (dangerouslySetInnerHTML re-commits, external manipulation…). Text nodes
   * then get detached while our index keeps pointing at the old objects —
   * every Range query silently returns empty rects. Detect and rebuild.
   */
  private ensureFreshIndex(): void {
    const nodes = this.index.nodes;
    if (nodes.length === 0) return;
    const first = nodes[0];
    const last = nodes[nodes.length - 1];
    if (!first.isConnected || !last.isConnected) {
      this.index = buildTextIndex(this.flow);
    }
  }

  /** viewport-absolute rect of the character at `offset` (single-char range) */
  private charRect(offset: number): DOMRect | null {
    const loc = locate(this.index, offset);
    if (!loc) return null;
    const range = document.createRange();
    range.setStart(loc.node, loc.offset);
    if (loc.offset + 1 <= loc.node.length) {
      range.setEnd(loc.node, loc.offset + 1);
    } else {
      range.setEnd(loc.node, loc.offset);
    }
    const rects = range.getClientRects();
    for (let i = 0; i < rects.length; i++) {
      const r = rects[i];
      if (r.width > 0 || r.height > 0) return r;
    }
    const br = range.getBoundingClientRect();
    if (br.width > 0 || br.height > 0) return br;
    return null;
  }

  /**
   * Column (page) containing `offset`, computed from the char's position
   * RELATIVE to the flow element. Both the char rect and the flow rect move
   * together with the page-turn transform, so this stays correct even while
   * the 300ms turn animation is mid-flight (progress saves, stats and
   * re-layout anchoring never read a stale column).
   */
  private directPage(offset: number): number {
    this.ensureFreshIndex();
    const flowLeft = this.flow.getBoundingClientRect().left;
    for (let o = offset; o < Math.min(offset + 64, this.index.total); o++) {
      const rect = this.charRect(o);
      if (rect) {
        return Math.floor((rect.left - flowLeft) / (this.colWidth + this.gap));
      }
    }
    return -1;
  }

  /** page that currently contains `offset` — always shows that char on screen */
  pageOfChar(offset: number): number | null {
    const p = this.directPage(offset);
    return p < 0 ? null : p;
  }

  /** first char offset visible on page p (lower-bound binary search) */
  charAtPage(p: number): number {
    const N = this.index.total;
    if (!N || p <= 0) return 0;
    if (p >= this.pageCount) return Math.max(0, N - 1);
    let lo = 0;
    let hi = N - 1;
    while (lo < hi) {
      const mid = (lo + hi) >> 1;
      if (this.directPage(mid) >= p) hi = mid;
      else lo = mid + 1;
    }
    return lo;
  }

  goToChar(offset: number, animate = true): void {
    const max = Math.max(0, this.index.total - 1);
    const clamped = Math.max(0, Math.min(offset, max));
    const p = this.pageOfChar(clamped);
    this.goToPage(p ?? 0, animate);
  }

  /** offset of the first text node inside (or right after) an element */
  offsetOfElement(el: Element): number | null {
    this.ensureFreshIndex();
    const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
    let n = walker.nextNode();
    while (n) {
      const start = this.index.nodeMap.get(n as Text);
      if (start !== undefined) return start;
      n = walker.nextNode();
    }
    return null;
  }

  percentThroughChapter(): number {
    if (!this.index.total) return 0;
    return Math.min(1, this.charAtPage(this.page) / this.index.total);
  }

  /** char offset of the first visible character on the current page */
  currentCharOffset(): number {
    return this.charAtPage(this.page);
  }
}

// ---------------------------------------------------------------------------
// Word-at-point utilities (tap-to-define)
// ---------------------------------------------------------------------------

export interface WordHit {
  word: string;
  rect: DOMRect;
  /** pre-built Range covering the word (for highlighting via Selection) */
  range: Range;
  /** sentence containing the word, for AI context */
  context: string;
}

const WORD_CHAR = /[\p{L}\p{N}'’-]/u;

export function wordAtPoint(x: number, y: number, root: HTMLElement): WordHit | null {
  let node: Node | null = null;
  let offset = 0;

  const docAny = document as Document & {
    caretRangeFromPoint?: (x: number, y: number) => Range | null;
    caretPositionFromPoint?: (x: number, y: number) => { offsetNode: Node; offset: number } | null;
  };
  if (typeof docAny.caretRangeFromPoint === "function") {
    const range = docAny.caretRangeFromPoint(x, y);
    if (range) {
      node = range.startContainer;
      offset = range.startOffset;
    }
  } else if (typeof docAny.caretPositionFromPoint === "function") {
    const pos = docAny.caretPositionFromPoint(x, y);
    if (pos) {
      node = pos.offsetNode;
      offset = pos.offset;
    }
  }

  if (!node || !root.contains(node)) return null;
  if (node.nodeType !== Node.TEXT_NODE) {
    // tap landed between elements — try first text child of the element at point
    const el = document.elementFromPoint(x, y);
    if (!el || !root.contains(el)) return null;
    const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
    const first = walker.nextNode() as Text | null;
    if (!first) return null;
    node = first;
    offset = 0;
  }

  const textNode = node as Text;
  const text = textNode.data;
  if (!text.trim()) return null;

  let s = offset;
  let e = offset;
  while (s > 0 && WORD_CHAR.test(text[s - 1] ?? "")) s--;
  while (e < text.length && WORD_CHAR.test(text[e] ?? "")) e++;
  if (e <= s) return null;

  const word = text.slice(s, e);
  // require at least one letter (skip pure numbers/punctuation)
  if (!/\p{L}/u.test(word)) return null;
  if (word.length > 40) return null;

  const range = document.createRange();
  range.setStart(textNode, s);
  range.setEnd(textNode, e);
  const rects = range.getClientRects();
  const rect = rects.length ? (rects[0] as DOMRect) : range.getBoundingClientRect();
  if (!rect.width && !rect.height) return null;

  // Only define when the tap is actually near the word — taps in margins or
  // padding fall through to page-turn zones (Kindle behavior).
  const vTol = Math.max(6, rect.height * 0.6);
  const hTol = 24;
  const verticalOk = y >= rect.top - vTol && y <= rect.bottom + vTol;
  const horizontalOk = x >= rect.left - hTol && x <= rect.right + hTol;
  if (!verticalOk || !horizontalOk) return null;

  return { word, rect, range, context: sentenceContext(textNode, s, e) };
}

/** Best-effort sentence around a word, for AI disambiguation. */
export function sentenceContext(textNode: Text, start: number, end: number): string {
  let block: Element | null = textNode.parentElement;
  while (block && !/^(P|BLOCKQUOTE|H[1-6]|LI|DIV|SECTION)$/.test(block.tagName)) {
    block = block.parentElement;
  }
  const full = (block ?? textNode.parentElement)?.textContent ?? textNode.data;
  const word = textNode.data.slice(start, end);
  const idx = full.indexOf(word);
  if (idx < 0) return full.slice(0, 200).trim();

  let s = 0;
  const before = full.slice(0, idx);
  const lastTerm = Math.max(
    before.lastIndexOf("."),
    before.lastIndexOf("!"),
    before.lastIndexOf("?"),
    before.lastIndexOf("…")
  );
  if (lastTerm >= 0 && idx - lastTerm < 400) s = lastTerm + 1;

  let e = full.length;
  const after = full.slice(idx + word.length);
  const firstTerm = after.search(/[.!?…]/);
  if (firstTerm >= 0 && firstTerm < 400) e = idx + word.length + firstTerm + 1;

  const sentence = full.slice(s, e).replace(/\s+/g, " ").trim();
  if (sentence.length > 260) {
    const rel = idx - s;
    const ws = Math.max(0, rel - 100);
    return sentence.slice(ws, Math.min(sentence.length, ws + 260)) + "…";
  }
  return sentence;
}
