package org.morningbrief.app;

import android.text.Html;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FeedReader {
    private FeedReader() {}

    private static final Pattern IMG = Pattern.compile("<img[^>]+src\\s*=\\s*['\"]([^'\"]+)", Pattern.CASE_INSENSITIVE);

    static List<Story> read(SourceConfig source) throws Exception {
        byte[] bytes = Net.getBytes(source.feedUrl, 1_600_000);
        XmlPullParser p = Xml.newPullParser();
        p.setInput(new ByteArrayInputStream(bytes), "UTF-8");

        List<Story> out = new ArrayList<>();
        Draft d = null;
        String textTag = "";
        int event = p.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            String name = p.getName() == null ? "" : p.getName();
            String prefix = p.getPrefix() == null ? "" : p.getPrefix();

            if (event == XmlPullParser.START_TAG) {
                if ("item".equalsIgnoreCase(name) || "entry".equalsIgnoreCase(name)) {
                    d = new Draft();
                } else if (d != null) {
                    textTag = name.toLowerCase(Locale.US);
                    if ("link".equalsIgnoreCase(name)) {
                        String href = attr(p, "href");
                        if (!href.trim().isEmpty()) d.url = href;
                    }
                    if ("enclosure".equalsIgnoreCase(name)) {
                        String type = attr(p, "type");
                        String url = attr(p, "url");
                        if (type.toLowerCase(Locale.US).startsWith("image/") && !url.trim().isEmpty()) d.image = url;
                    }
                    if (("media".equalsIgnoreCase(prefix) && ("content".equalsIgnoreCase(name) || "thumbnail".equalsIgnoreCase(name)))
                            || "thumbnail".equalsIgnoreCase(name)) {
                        String url = attr(p, "url");
                        if (!url.trim().isEmpty()) d.image = url;
                    }
                }
            } else if (event == XmlPullParser.TEXT && d != null) {
                String txt = p.getText() == null ? "" : p.getText();
                switch (textTag) {
                    case "title" -> d.title += txt;
                    case "link" -> { if (d.url.trim().isEmpty()) d.url += txt; }
                    case "description", "summary", "content", "encoded" -> d.summary += txt;
                    case "pubdate", "published", "updated", "date" -> d.date += txt;
                    case "source" -> d.sourceText += txt;
                }
            } else if (event == XmlPullParser.END_TAG) {
                if (("item".equalsIgnoreCase(name) || "entry".equalsIgnoreCase(name)) && d != null) {
                    Story s = d.toStory(source);
                    if (s != null && googleSourceMatches(source, d.sourceText)) out.add(s);
                    d = null;
                }
                textTag = "";
            }
            event = p.next();
        }
        return out;
    }

    private static boolean googleSourceMatches(SourceConfig source, String actual) {
        if (source.mode == null || !source.mode.contains("google")) return true;
        String expected = source.name.replace(" (via Google News)", "").trim().toLowerCase(Locale.US);
        String got = actual == null ? "" : actual.trim().toLowerCase(Locale.US);
        return got.trim().isEmpty() || got.equals(expected) || got.contains(expected) || expected.contains(got);
    }

    private static String attr(XmlPullParser p, String name) {
        for (int i = 0; i < p.getAttributeCount(); i++) {
            if (name.equalsIgnoreCase(p.getAttributeName(i))) return p.getAttributeValue(i);
        }
        return "";
    }

    static String plain(String html) {
        if (html == null || html.trim().isEmpty()) return "";
        try {
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("\\s+", " ").trim();
        } catch (Exception e) {
            return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        }
    }

    static long parseDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0L;
        String s = raw.trim();
        try { return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli(); }
        catch (DateTimeParseException ignored) {}
        try { return Instant.parse(s).toEpochMilli(); }
        catch (DateTimeParseException ignored) {}
        DateTimeFormatter[] formats = {
                DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        };
        for (DateTimeFormatter f : formats) {
            try { return ZonedDateTime.parse(s, f).toInstant().toEpochMilli(); }
            catch (Exception ignored) {}
        }
        return 0L;
    }

    private static final class Draft {
        String title = "";
        String url = "";
        String summary = "";
        String image = "";
        String date = "";
        String sourceText = "";

        Story toStory(SourceConfig source) {
            String cleanTitle = plain(title);
            String cleanUrl = url.trim();
            if (cleanTitle.trim().isEmpty() || cleanUrl.trim().isEmpty()) return null;
            Story s = new Story();
            s.source = source.cleanName();
            s.sourceMode = source.mode;
            s.title = cleanTitle;
            s.url = cleanUrl;
            s.summary = plain(summary);
            s.imageUrl = image;
            if (s.imageUrl.trim().isEmpty()) {
                Matcher m = IMG.matcher(summary == null ? "" : summary);
                if (m.find()) s.imageUrl = m.group(1);
            }
            s.publishedEpoch = parseDate(date);
            return s;
        }
    }
}
