package org.morningbrief.app;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class EditionBuilder {
    private EditionBuilder() {}

    static String build(LocationInfo loc, WeatherData weather, List<Story> stories,
                        boolean weatherFresh, long builtAt, String theme) {
        ZoneId zone;
        try { zone = ZoneId.of(loc.timezone); } catch (Exception e) { zone = ZoneId.systemDefault(); }
        String date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(builtAt), zone)
                .format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US));

        Story lead = stories.isEmpty() ? null : stories.get(0);
        List<Story> remaining = lead == null ? java.util.Collections.emptyList() : stories.subList(1, stories.size());
        String safeTheme = "dark".equals(theme) ? "dark" : ("light".equals(theme) ? "light" : "system");
        String colorScheme = "dark".equals(safeTheme) ? "dark" : ("light".equals(safeTheme) ? "light" : "light dark");

        StringBuilder h = new StringBuilder(18000);
        h.append("<!doctype html><html class='theme-").append(safeTheme).append("'><head>")
         .append("<meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>")
         .append("<meta name='color-scheme' content='").append(colorScheme).append("'>")
         .append("<style>").append(css()).append("</style></head><body><main>")
         .append("<header class='site-header'><h1>THE MORNING BRIEF</h1><div class='date'>")
         .append(esc(date.toUpperCase(Locale.US))).append("</div></header>");

        h.append("<section class='front'>");
        if (lead != null) h.append(leadCard(lead));
        else h.append("<section class='card empty'><b>No stories cleared today’s editorial cutoff.</b><p>Weather will still refresh when possible. The app will check again at the next refresh.</p></section>");
        h.append(weatherCard(weather, weatherFresh));
        h.append("</section>");

        if (!remaining.isEmpty()) {
            h.append("<section class='card contents'><div class='inside-head'><h2>Today’s brief</h2><span>")
             .append(stories.size()).append(stories.size() == 1 ? " story" : " stories").append("</span></div>");

            Map<String, List<Story>> groups = new LinkedHashMap<>();
            groups.put("TOP STORIES", new ArrayList<>());
            groups.put("LOCAL", new ArrayList<>());
            groups.put("MICHIGAN", new ArrayList<>());
            groups.put("MORE TO KNOW", new ArrayList<>());
            for (Story s : remaining) {
                String section = s.section == null || s.section.trim().isEmpty() ? "MORE TO KNOW" : s.section;
                groups.computeIfAbsent(section, k -> new ArrayList<>()).add(s);
            }
            for (Map.Entry<String, List<Story>> e : groups.entrySet()) {
                if (e.getValue().isEmpty()) continue;
                h.append("<div class='section-head'>").append(esc(e.getKey())).append("</div><div class='story-list'>");
                for (Story s : e.getValue()) h.append(storyCard(s));
                h.append("</div>");
            }
            h.append("</section>");
        }

        h.append("<footer>Built on this phone · ")
         .append(esc(loc.label())).append(" · ")
         .append(esc(ZonedDateTime.ofInstant(Instant.ofEpochMilli(builtAt), zone)
                 .format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))))
         .append("</footer></main></body></html>");
        return h.toString();
    }

    private static String weatherCard(WeatherData w, boolean fresh) {
        if (w == null || !w.available) {
            return "<section class='card weather'><div class='weather-title'>WEATHER</div><p>Weather unavailable.</p></section>";
        }
        StringBuilder h = new StringBuilder();
        h.append("<section class='card weather'><div class='weather-title'>")
         .append(esc(w.label)).append(fresh ? "" : " · CACHED").append("</div>");
        if (!w.hours.isEmpty()) {
            h.append("<div class='section-label'>TODAY</div><div class='forecast-grid'>");
            for (WeatherData.Hour x : w.hours) {
                h.append("<div class='forecast'><b>").append(esc(x.label)).append("</b>")
                 .append("<span class='temp'>").append(x.temp).append("&deg;</span>")
                 .append("<span>").append(esc(x.condition)).append("</span>")
                 .append("<small>").append(x.rain).append("% precip</small></div>");
            }
            h.append("</div>");
        }
        if (!w.days.isEmpty()) {
            h.append("<div class='section-label with-rule'>3-DAY FORECAST</div><div class='daily-grid'>");
            for (WeatherData.Day d : w.days) {
                h.append("<div class='forecast'><b>").append(esc(d.label)).append("</b>")
                 .append("<span>").append(esc(d.condition)).append("</span>")
                 .append("<span class='daily-temp'>").append(d.high).append("&deg; / ").append(d.low).append("&deg;</span>")
                 .append("<small>").append(d.rain).append("% precip</small></div>");
            }
            h.append("</div>");
        }
        h.append("</section>");
        return h.toString();
    }

    private static String leadCard(Story s) {
        StringBuilder h = new StringBuilder("<section class='card lead'>");
        if (!s.imageUrl.trim().isEmpty()) {
            h.append("<img class='lead-img' src='").append(attr(s.imageUrl)).append("' alt='' loading='lazy'>");
        }
        h.append("<div class='lead-copy'><div class='source'>").append(esc(s.source)).append(sourceBadge(s)).append("</div>")
         .append("<h2><a href='").append(attr(s.url)).append("'>").append(esc(s.title)).append("</a></h2>");
        if (!s.summary.trim().isEmpty()) h.append("<p>").append(esc(trimSummary(s.summary, 420))).append("</p>");
        h.append("<a class='read' href='").append(attr(s.url)).append("'>Read story →</a></div></section>");
        return h.toString();
    }

    private static String storyCard(Story s) {
        StringBuilder h = new StringBuilder();
        h.append("<a class='story' href='").append(attr(s.url)).append("'><div>")
         .append("<div class='story-source'>").append(esc(s.source)).append(sourceBadge(s)).append("</div>")
         .append("<div class='story-title'>").append(esc(s.title)).append("</div>");
        if (!s.summary.trim().isEmpty()) h.append("<div class='summary'>").append(esc(trimSummary(s.summary, 190))).append("</div>");
        h.append("</div><span class='read-label'>READ</span></a>");
        return h.toString();
    }

    private static String sourceBadge(Story s) {
        if (s.isPreferred()) return " <span class='badge'>PREFERRED</span>";
        if (s.isNonprofit()) return " <span class='badge'>NONPROFIT</span>";
        return "";
    }

    private static String trimSummary(String s, int max) {
        s = s.replaceAll("\\s+", " ").trim();
        if (s.length() <= max) return s;
        return s.substring(0, max - 1).replaceAll("\\s+\\S*$", "").trim() + "…";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String attr(String s) { return esc(s); }

    private static String css() {
        return """
            :root{--page:#e2e1da;--paper:#fafaf6;--ink:#111;--muted:#5b5b55;--rule:#d8d8cf;--card:#fff;--border:#e7e6df;--summary:#444;--shadow:rgba(0,0,0,.08);--badge:#ecebe4}
            html.theme-dark{--page:#0d0e0c;--paper:#10110f;--ink:#f0efe8;--muted:#b9b8b0;--rule:#44453f;--card:#1a1c18;--border:#373932;--summary:#d0cfc7;--shadow:rgba(0,0,0,.35);--badge:#31332d;color-scheme:dark}
            @media(prefers-color-scheme:dark){html.theme-system{--page:#0d0e0c;--paper:#10110f;--ink:#f0efe8;--muted:#b9b8b0;--rule:#44453f;--card:#1a1c18;--border:#373932;--summary:#d0cfc7;--shadow:rgba(0,0,0,.35);--badge:#31332d;color-scheme:dark}}
            *{box-sizing:border-box}html{background:var(--page)}body{margin:0;background:var(--paper);color:var(--ink);font-family:Georgia,'Times New Roman',serif;line-height:1.35;min-height:100vh}
            main{max-width:860px;margin:0 auto;padding:0 12px 34px}.site-header{text-align:center;padding:18px 8px 11px}.site-header h1{font-size:clamp(1.9rem,7vw,2.7rem);letter-spacing:.035em;margin:0}.date{margin-top:5px;font:700 .82rem system-ui,sans-serif;letter-spacing:.08em;color:var(--muted)}
            .front{display:grid;gap:14px}.card{background:var(--card);border:1px solid var(--border);border-radius:12px;overflow:hidden;box-shadow:0 1px 8px var(--shadow)}
            .weather{padding:14px}.weather-title,.source,.section-head,.section-label,.story-source{font:700 .76rem system-ui,sans-serif;letter-spacing:.08em;text-transform:uppercase}.weather-title{margin-bottom:11px}.section-label{color:var(--muted);margin-bottom:7px}.with-rule{border-top:1px solid var(--rule);padding-top:12px;margin-top:13px}
            .forecast-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:7px}.daily-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.forecast{text-align:center;border-top:2px solid var(--rule);padding:7px 2px 0;font:.76rem system-ui,sans-serif;display:flex;flex-direction:column;gap:3px}.forecast b{font-size:.82rem}.forecast .temp{font:700 1.25rem Georgia,serif}.daily-temp{font:700 1.03rem Georgia,serif}.forecast small{font-size:.7rem;color:var(--muted)}
            .lead-img{display:block;width:100%;aspect-ratio:16/9;object-fit:cover;background:#555}.lead-copy{padding:14px 16px 17px}.source,.story-source{color:var(--muted)}.badge{display:inline-block;margin-left:6px;padding:2px 5px;border-radius:4px;background:var(--badge);font-size:.62rem;letter-spacing:.05em}.lead h2{font-size:clamp(1.7rem,6.5vw,2.4rem);line-height:1.05;margin:7px 0 9px}.lead h2 a,.read{color:var(--ink);text-decoration:none}.lead p{font-size:1.08rem;line-height:1.43;margin:0 0 12px}.read{font:700 .94rem system-ui,sans-serif}
            .contents{padding:15px 14px 12px;margin-top:15px}.inside-head{display:flex;align-items:baseline;justify-content:space-between;gap:10px}.inside-head h2{font-size:1.45rem;margin:0}.inside-head span{font:700 .72rem system-ui,sans-serif;color:var(--muted);letter-spacing:.05em}.section-head{border-bottom:2px solid var(--ink);padding:0 2px 7px;margin-top:22px}.section-head:first-of-type{margin-top:16px}.story-list{display:grid;gap:9px;padding-top:10px}.story{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:12px;align-items:center;padding:13px 12px;border:1px solid var(--rule);border-radius:10px;color:var(--ink);text-decoration:none}.story-source{font-size:.66rem;margin-bottom:5px}.story-title{font-size:1.08rem;font-weight:700;line-height:1.18}.summary{font-size:.91rem;color:var(--summary);margin-top:5px}.read-label{font:700 .7rem system-ui,sans-serif;letter-spacing:.06em;color:var(--muted)}
            .empty{padding:18px}.empty p{margin-bottom:0}footer{text-align:center;color:var(--muted);font:.72rem system-ui,sans-serif;padding:20px 5px 0}
            @media(min-width:720px){.front{grid-template-columns:1.4fr .9fr;align-items:start}}
            """;
    }
}
