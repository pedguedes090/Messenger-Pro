package tn.amin.mpro2.features.util.video;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tn.amin.mpro2.debug.Logger;

/**
 * Scrapes Facebook video page HTML to extract direct SD/HD video URLs.
 * Based on fb-downloader-scrapper by victorsouzaleal.
 */
public class FacebookVideoDownloader {

    public static class VideoInfo {
        public final String originalUrl;
        public final String sdUrl;
        public final String hdUrl;
        public final String title;
        public final String thumbnail;

        public VideoInfo(String originalUrl, String sdUrl, String hdUrl, String title, String thumbnail) {
            this.originalUrl = originalUrl;
            this.sdUrl = sdUrl;
            this.hdUrl = hdUrl;
            this.title = title;
            this.thumbnail = thumbnail;
        }

        /**
         * Returns the best available quality URL (HD preferred).
         */
        public String getBestUrl() {
            if (hdUrl != null && !hdUrl.isEmpty()) return hdUrl;
            return sdUrl;
        }
    }

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    // Patterns to extract SD video URL
    private static final Pattern[] SD_PATTERNS = {
            Pattern.compile("\"browser_native_sd_url\":\"(.*?)\""),
            Pattern.compile("\"playable_url\":\"(.*?)\""),
            Pattern.compile("sd_src\\s*:\\s*\"([^\"]*)\""),
    };

    // Patterns to extract HD video URL
    private static final Pattern[] HD_PATTERNS = {
            Pattern.compile("\"browser_native_hd_url\":\"(.*?)\""),
            Pattern.compile("\"playable_url_quality_hd\":\"(.*?)\""),
            Pattern.compile("hd_src\\s*:\\s*\"([^\"]*)\""),
    };

    private static final Pattern TITLE_PATTERN =
            Pattern.compile("<meta\\s+name=\"description\"\\s+content=\"(.*?)\"");

    private static final Pattern TITLE_FALLBACK_PATTERN =
            Pattern.compile("<title>(.*?)</title>");

    private static final Pattern THUMB_PATTERN =
            Pattern.compile("\"preferred_thumbnail\":\\{\"image\":\\{\"uri\":\"(.*?)\"");

    /**
     * Check if a URL is a valid Facebook video URL.
     */
    public static boolean isFacebookVideoUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        return url.contains("facebook.com") || url.contains("fb.watch")
                || url.contains("fb.com") || url.contains("m.facebook.com");
    }

    /**
     * Extract Facebook video link from a text message.
     * Returns null if no FB link found.
     */
    public static String extractFacebookUrl(String text) {
        if (text == null || text.isEmpty()) return null;
        Pattern urlPattern = Pattern.compile(
                "(https?://(?:www\\.|m\\.)?(?:facebook\\.com|fb\\.watch|fb\\.com)/\\S+)");
        Matcher m = urlPattern.matcher(text);
        if (m.find()) return m.group(1);
        return null;
    }

    /**
     * Fetch video info from a Facebook video URL.
     * This performs a network request - must be called from a background thread.
     */
    public static VideoInfo getVideoInfo(String videoUrl) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(videoUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            connection.setRequestProperty("sec-fetch-mode", "navigate");
            connection.setRequestProperty("sec-fetch-site", "none");
            connection.setRequestProperty("upgrade-insecure-requests", "1");
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP error: " + responseCode);
            }

            String html;
            try (InputStream is = connection.getInputStream();
                 Scanner scanner = new Scanner(is, "UTF-8")) {
                scanner.useDelimiter("\\A");
                html = scanner.hasNext() ? scanner.next() : "";
            }

            // Unescape HTML entities
            html = html.replace("&quot;", "\"").replace("&amp;", "&");

            String sdUrl = matchFirst(html, SD_PATTERNS);
            String hdUrl = matchFirst(html, HD_PATTERNS);

            if (sdUrl == null) {
                throw new IOException("Could not extract video URL from page");
            }

            // Unescape unicode escapes in URLs
            sdUrl = unescapeUnicode(sdUrl);
            if (hdUrl != null) hdUrl = unescapeUnicode(hdUrl);

            String title = matchFirst(html, TITLE_PATTERN);
            if (title == null) title = matchFirst(html, TITLE_FALLBACK_PATTERN);
            if (title == null) title = "Facebook Video";

            String thumbnail = matchFirst(html, THUMB_PATTERN);
            if (thumbnail != null) thumbnail = unescapeUnicode(thumbnail);

            return new VideoInfo(videoUrl, sdUrl, hdUrl, title, thumbnail);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String matchFirst(String text, Pattern... patterns) {
        for (Pattern pattern : patterns) {
            Matcher m = pattern.matcher(text);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    private static String unescapeUnicode(String str) {
        if (str == null) return null;
        return str.replace("\\/", "/")
                .replace("\\u0025", "%")
                .replace("\\u003C", "<")
                .replace("\\u003E", ">")
                .replace("\\u0026", "&");
    }
}
