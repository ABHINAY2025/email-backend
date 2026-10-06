package com.applyflow.mail.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** HTML to text conversion, HTML sanitizing and quoted-reply stripping. */
@Component
public class HtmlNormalizer {

    private static final Pattern MANY_BLANK_LINES = Pattern.compile("\\n{3,}");
    private static final Pattern TRAILING_SPACES = Pattern.compile("[ \\t\\x0B\\f\\r]+\\n");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\x0B\\f\\u00A0]+");
    private static final Pattern REPLY_HEADER = Pattern.compile(
            "(?m)^(?:On .{4,200}wrote:\\s*$|-{2,}\\s*Original Message\\s*-{2,}|_{10,}\\s*$|From: .+\\n(?:Sent|Date): .+)");

    /** Converts HTML into readable plain text, keeping paragraph and line breaks. */
    public String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document doc = Jsoup.parse(html);
        doc.outputSettings(new Document.OutputSettings().prettyPrint(false));
        doc.select("script, style, head, noscript, title, meta").remove();
        // Hidden preheader blocks often contain marketing filler.
        doc.select("[style~=(?i)display\\s*:\\s*none]").remove();
        for (Element br : doc.select("br")) {
            br.before("\\n");
        }
        for (Element block : doc.select("p, div, tr, li, h1, h2, h3, h4, h5, h6, table, blockquote, section")) {
            block.before("\\n");
            block.append("\\n");
        }
        for (Element td : doc.select("td, th")) {
            td.append(" ");
        }
        // Keep link targets visible for job URL extraction.
        for (Element a : doc.select("a[href]")) {
            String href = a.attr("href");
            if (href.startsWith("http") && !a.text().contains(href)) {
                a.append(" <" + href + ">");
            }
        }
        String withMarkers = doc.body() == null ? doc.text() : doc.body().html();
        String cleaned = Jsoup.clean(withMarkers, "", Safelist.none(),
                new Document.OutputSettings().prettyPrint(false));
        String text = Parser.unescapeEntities(cleaned.replace("\\n", "\n"), false);
        return normalizeText(text);
    }

    /** Sanitizes HTML for storage/rendering (Safelist.relaxed: no scripts, no event handlers, no styles). */
    public String sanitizeHtml(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Safelist safelist = Safelist.relaxed()
                .addAttributes("a", "target", "rel")
                .preserveRelativeLinks(false);
        return Jsoup.clean(html, "", safelist, new Document.OutputSettings().prettyPrint(false));
    }

    public String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replace("\r\n", "\n").replace('\r', '\n');
        t = SPACES.matcher(t).replaceAll(" ");
        t = TRAILING_SPACES.matcher(t).replaceAll("\n");
        t = t.lines().map(String::strip).reduce(new StringBuilder(), (sb, l) -> sb.append(l).append('\n'),
                StringBuilder::append).toString();
        t = MANY_BLANK_LINES.matcher(t).replaceAll("\n\n");
        return t.strip();
    }

    /** Removes quoted reply history ("On ... wrote:", "&gt; " lines, forwarded originals) where safely possible. */
    public String stripQuotedReply(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String t = text;
        var m = REPLY_HEADER.matcher(t);
        if (m.find() && m.start() > 40) {
            t = t.substring(0, m.start());
        }
        StringBuilder sb = new StringBuilder();
        for (String line : t.split("\n", -1)) {
            if (line.startsWith(">")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        String result = sb.toString().strip();
        return result.isEmpty() ? text.strip() : result;
    }

    public String snippet(String text, int max) {
        if (text == null) {
            return null;
        }
        String s = text.replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max - 1).trim() + "…";
    }
}
