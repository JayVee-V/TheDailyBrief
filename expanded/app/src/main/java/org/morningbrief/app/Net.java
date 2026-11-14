package org.morningbrief.app;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class Net {
    private static final String UA = "MorningBrief/0.1 (+Android; local news reader)";
    private Net() {}

    static byte[] getBytes(String url, int maxBytes) throws Exception {
        URL current = new URL(url);
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection c = (HttpURLConnection) current.openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(16000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html, application/json, */*;q=0.5");
            c.setInstanceFollowRedirects(false);
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = c.getHeaderField("Location");
                c.disconnect();
                if (location == null) throw new Exception("Redirect without Location");
                current = new URL(current, location);
                continue;
            }
            if (code < 200 || code >= 300) {
                c.disconnect();
                throw new Exception("HTTP " + code + " for " + current);
            }
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int total = 0;
                int n;
                while ((n = in.read(buf)) >= 0) {
                    if (n == 0) continue;
                    int allowed = Math.min(n, maxBytes - total);
                    if (allowed > 0) out.write(buf, 0, allowed);
                    total += allowed;
                    if (total >= maxBytes) break;
                }
                return out.toByteArray();
            } finally {
                c.disconnect();
            }
        }
        throw new Exception("Too many redirects");
    }

    static String getString(String url, int maxBytes) throws Exception {
        return new String(getBytes(url, maxBytes), StandardCharsets.UTF_8);
    }
}
