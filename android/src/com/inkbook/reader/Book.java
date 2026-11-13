package com.inkbook.reader;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * In-memory book: plain paragraphs in reading order, chapter anchors and
 * heading marks. Supports TXT (auto charset) and EPUB (spine order).
 */
public final class Book {

    public String title = "";
    public String author = "";
    public String format = "txt";
    public final List<String> paragraphs = new ArrayList<String>();
    public final List<Integer> chapterParas = new ArrayList<Integer>();
    public final List<String> chapterTitles = new ArrayList<String>();
    public final Set<Integer> headings = new HashSet<Integer>();

    private static final Pattern TXT_HEADING = Pattern.compile(
            "^\\s*((chapter|part|book)\\b.{0,44}|第.{1,9}[章回节])\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG_TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern TAG_HEADING = Pattern.compile("(?is)<h[1-6][^>]*>(.*?)</h[1-6]>");

    public boolean load(File f) {
        String name = f.getName().toLowerCase();
        try {
            if (name.endsWith(".epub")) {
                format = "epub";
                return loadEpub(f);
            }
            format = "txt";
            return loadTxt(f);
        } catch (Throwable t) {
            return paragraphs.size() > 0;
        }
    }

    // ------------------------------------------------------------------ TXT

    private boolean loadTxt(File f) {
        String text = readTextBestEffort(f);
        if (text == null || text.length() == 0) return false;
        StringBuilder cur = new StringBuilder();
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (String raw : lines) {
            String line = raw.trim();
            boolean heading = line.length() > 0 && line.length() < 60 && TXT_HEADING.matcher(line).matches();
            if (line.length() == 0) {
                flushPara(cur);
            } else if (heading) {
                flushPara(cur);
                headings.add(paragraphs.size());
                chapterParas.add(paragraphs.size());
                chapterTitles.add(line);
                paragraphs.add(line);
            } else {
                if (cur.length() > 0) cur.append(' ');
                cur.append(line);
            }
        }
        flushPara(cur);
        if (paragraphs.isEmpty()) return false;
        title = stripExt(f.getName());
        return true;
    }

    private void flushPara(StringBuilder cur) {
        if (cur.length() > 0) {
            paragraphs.add(cur.toString().trim());
            cur.setLength(0);
        }
    }

    private static String readTextBestEffort(File f) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(f);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) > 0) bo.write(buf, 0, n);
            byte[] b = bo.toByteArray();
            if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
                return new String(b, 3, b.length - 3, "UTF-8");
            }
            if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
                return new String(b, 2, b.length - 2, "UTF-16LE");
            }
            try {
                CharsetDecoder dec = Charset.forName("UTF-8").newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
                return dec.decode(ByteBuffer.wrap(b)).toString();
            } catch (Exception ignored) { }
            try {
                return new String(b, "GBK");
            } catch (Exception ignored) { }
            return new String(b, "ISO-8859-1");
        } catch (Exception e) {
            return null;
        } finally {
            try { if (fis != null) fis.close(); } catch (Exception ignored) { }
        }
    }

    // ---------------------------------------------------------------- EPUB

    private boolean loadEpub(File f) throws Exception {
        ZipFile zf = new ZipFile(f);
        try {
            String opfPath = null;
            ZipEntry container = zf.getEntry("META-INF/container.xml");
            if (container != null) {
                try {
                    XmlPullParser p = Xml.newPullParser();
                    p.setInput(zf.getInputStream(container), "UTF-8");
                    int t;
                    while ((t = p.next()) != XmlPullParser.END_DOCUMENT) {
                        if (t == XmlPullParser.START_TAG && "rootfile".equals(p.getName())) {
                            opfPath = p.getAttributeValue(null, "full-path");
                            break;
                        }
                    }
                } catch (Exception ignored) { }
            }
            if (opfPath == null) {
                Enumeration<? extends ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    String nm = en.nextElement().getName();
                    if (nm.toLowerCase().endsWith(".opf")) { opfPath = nm; break; }
                }
            }
            if (opfPath == null) return false;

            String base = "";
            int slash = opfPath.lastIndexOf('/');
            if (slash >= 0) base = opfPath.substring(0, slash + 1);

            ZipEntry opfE = zf.getEntry(opfPath);
            if (opfE == null) opfE = zf.getEntry(urlDecode(opfPath));
            if (opfE == null) return false;

            HashMap<String, String> manifest = new HashMap<String, String>();
            List<String> spine = new ArrayList<String>();
            try {
                XmlPullParser p = Xml.newPullParser();
                p.setInput(zf.getInputStream(opfE), "UTF-8");
                int t;
                String capture = null;
                StringBuilder capBuf = new StringBuilder();
                while ((t = p.next()) != XmlPullParser.END_DOCUMENT) {
                    String tag = p.getName();
                    if (t == XmlPullParser.START_TAG) {
                        if ("title".equals(tag) || "creator".equals(tag)
                                || (tag != null && tag.endsWith(":title"))
                                || (tag != null && tag.endsWith(":creator"))) {
                            capture = tag;
                            capBuf.setLength(0);
                        }
                    } else if (t == XmlPullParser.TEXT) {
                        if (capture != null) capBuf.append(p.getText());
                    } else if (t == XmlPullParser.END_TAG) {
                        if (capture != null && tag != null && tag.equals(capture)) {
                            String v = Util.htmlToText(capBuf.toString()).trim();
                            if (v.length() > 0) {
                                if (capture.endsWith("title")) {
                                    if (title == null || title.length() == 0) title = v;
                                } else {
                                    if (author.length() == 0) author = v;
                                }
                            }
                            capture = null;
                        }
                    }
                }
            } catch (Exception ignored) { }

            try {
                XmlPullParser p = Xml.newPullParser();
                p.setInput(zf.getInputStream(opfE), "UTF-8");
                int t;
                while ((t = p.next()) != XmlPullParser.END_DOCUMENT) {
                    if (t != XmlPullParser.START_TAG) continue;
                    String tag = p.getName();
                    if ("item".equals(tag)) {
                        String id = p.getAttributeValue(null, "id");
                        String href = p.getAttributeValue(null, "href");
                        String mt = p.getAttributeValue(null, "media-type");
                        if (id != null && href != null) {
                            boolean html = (mt != null && mt.contains("html"))
                                    || href.toLowerCase().endsWith(".xhtml")
                                    || href.toLowerCase().endsWith(".htm")
                                    || href.toLowerCase().endsWith(".html");
                            if (html) manifest.put(id, href);
                        }
                    } else if ("itemref".equals(tag)) {
                        String idref = p.getAttributeValue(null, "idref");
                        if (idref != null) spine.add(idref);
                    }
                }
            } catch (Exception ignored) { }

            Set<String> seen = new HashSet<String>();
            for (String idref : spine) {
                String href = manifest.get(idref);
                if (href == null) continue;
                String path = normalize(base + href);
                if (!seen.add(path)) continue;
                ZipEntry e = zf.getEntry(path);
                if (e == null) e = zf.getEntry(urlDecode(path));
                if (e == null) continue;
                String html = readStream(zf.getInputStream(e), 6 << 20);
                addHtmlDoc(html);
            }
            if (paragraphs.isEmpty()) return false;
            if (title == null || title.length() == 0) title = stripExt(f.getName());
            return true;
        } finally {
            try { zf.close(); } catch (Exception ignored) { }
        }
    }

    /**
     * Fast EPUB metadata probe: opens only the OPF and returns {title, author}.
     * Used at import time so the library can show author names without parsing
     * the whole book.
     */
    public static String[] epubMeta(File f) {
        String[] out = new String[]{"", ""};
        ZipFile zf = null;
        try {
            zf = new ZipFile(f);
            String opfPath = null;
            ZipEntry container = zf.getEntry("META-INF/container.xml");
            if (container != null) {
                try {
                    XmlPullParser p = Xml.newPullParser();
                    p.setInput(zf.getInputStream(container), "UTF-8");
                    int t;
                    while ((t = p.next()) != XmlPullParser.END_DOCUMENT) {
                        if (t == XmlPullParser.START_TAG && "rootfile".equals(p.getName())) {
                            opfPath = p.getAttributeValue(null, "full-path");
                            break;
                        }
                    }
                } catch (Exception ignored) { }
            }
            if (opfPath == null) {
                Enumeration<? extends ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    String nm = en.nextElement().getName();
                    if (nm.toLowerCase().endsWith(".opf")) { opfPath = nm; break; }
                }
            }
            if (opfPath == null) return out;
            ZipEntry opfE = zf.getEntry(opfPath);
            if (opfE == null) opfE = zf.getEntry(urlDecode(opfPath));
            if (opfE == null) return out;

            XmlPullParser p = Xml.newPullParser();
            p.setInput(zf.getInputStream(opfE), "UTF-8");
            String capture = null;
            StringBuilder capBuf = new StringBuilder();
            int t;
            while ((t = p.next()) != XmlPullParser.END_DOCUMENT) {
                String tag = p.getName();
                if (t == XmlPullParser.START_TAG) {
                    if ("title".equals(tag) || "creator".equals(tag)
                            || (tag != null && tag.endsWith(":title"))
                            || (tag != null && tag.endsWith(":creator"))) {
                        capture = tag;
                        capBuf.setLength(0);
                    }
                } else if (t == XmlPullParser.TEXT) {
                    if (capture != null) capBuf.append(p.getText());
                } else if (t == XmlPullParser.END_TAG) {
                    if (capture != null && tag != null && tag.equals(capture)) {
                        String v = Util.htmlToText(capBuf.toString()).trim();
                        if (v.length() > 0) {
                            if (capture.endsWith("title")) {
                                if (out[0].length() == 0) out[0] = v;
                            } else if (out[1].length() == 0) {
                                out[1] = v;
                            }
                        }
                        capture = null;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (zf != null) {
                try { zf.close(); } catch (Exception ignored) { }
            }
        }
        return out;
    }

    private void addHtmlDoc(String html) {
        if (html == null || html.length() == 0) return;

        String docTitle = null;
        Matcher mt = TAG_TITLE.matcher(html);
        if (mt.find()) docTitle = plain(mt.group(1));

        String h = bodyOnly(html);
        String chapterTitle = null;
        Matcher mh = TAG_HEADING.matcher(h);
        if (mh.find()) chapterTitle = plain(mh.group(1));
        if (chapterTitle == null || chapterTitle.length() == 0) chapterTitle = docTitle;

        h = h.replaceAll("(?is)<(script|style)[^>]*>.*?</\\s*\\1\\s*>", " ");
        h = h.replaceAll("(?is)<br\\s*/?>", "\n");
        h = h.replaceAll("(?is)</p\\s*>", "\n\n");
        h = h.replaceAll("(?is)</div\\s*>", "\n\n");
        h = h.replaceAll("(?is)</h[1-6]\\s*>", "\n\n");
        h = h.replaceAll("(?is)<[^>]+>", "");
        h = Util.htmlToText(h);
        h = h.replace('\u00a0', ' ');

        int mark = paragraphs.size();
        boolean any = false;
        String[] blocks = h.split("\n\n+");
        for (String b : blocks) {
            String[] subLines = b.split("\n+");
            StringBuilder sb = new StringBuilder();
            for (String s : subLines) {
                String t = s.trim();
                if (t.length() > 0) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(t);
                }
            }
            String para = sb.toString().trim();
            if (para.length() > 0) {
                paragraphs.add(para);
                any = true;
            }
        }
        if (any) {
            chapterParas.add(mark);
            String t = (chapterTitle == null) ? null : chapterTitle.trim();
            if (t == null || t.length() == 0) t = "Section " + chapterParas.size();
            chapterTitles.add(t);
            if (t.length() > 0 && t.length() < 80) headings.add(mark);
        }
    }

    private static String bodyOnly(String html) {
        String lower = html.toLowerCase();
        int bi = lower.indexOf("<body");
        if (bi >= 0) {
            int bs = html.indexOf('>', bi);
            int be = lower.lastIndexOf("</body");
            if (bs >= 0 && be > bs) return html.substring(bs + 1, be);
        }
        return html.replaceAll("(?is)<head[^>]*>.*?</head>", " ");
    }

    private static String plain(String raw) {
        if (raw == null) return "";
        return Util.htmlToText(raw.replaceAll("(?is)<[^>]+>", " ").replace('\u00a0', ' ')).trim();
    }

    private static String readStream(InputStream in, int limit) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0 && bo.size() < limit) bo.write(buf, 0, n);
            try { in.close(); } catch (Exception ignored) { }
        } catch (Exception ignored) { }
        try {
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return new String(bo.toByteArray());
        }
    }

    private static String normalize(String path) {
        String[] parts = path.split("/");
        ArrayList<String> out = new ArrayList<String>();
        for (String p : parts) {
            if (p.length() == 0 || p.equals(".")) continue;
            if (p.equals("..")) {
                if (!out.isEmpty()) out.remove(out.size() - 1);
                continue;
            }
            out.add(p);
        }
        StringBuilder sb = new StringBuilder();
        for (String p : out) {
            if (sb.length() > 0) sb.append('/');
            sb.append(p);
        }
        return sb.toString();
    }

    private static String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return (i > 0) ? name.substring(0, i) : name;
    }
}
