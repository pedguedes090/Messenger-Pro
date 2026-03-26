package tn.amin.mpro2.features.util.video;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import tn.amin.mpro2.debug.Logger;

/**
 * Instagram video/reel downloader using Instagram's GraphQL API.
 * Based on instagram-direct-url by victorsouzaleal.
 */
public class InstagramDownloader {

    private static final String GRAPHQL_URL = "https://www.instagram.com/graphql/query";
    private static final String DOCUMENT_ID = "9510064595728286";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public static VideoDownloadManager.VideoResult getVideoInfo(String postUrl) throws IOException {
        try {
            // Resolve share URLs first
            postUrl = resolveRedirect(postUrl);
            String shortcode = extractShortcode(postUrl);
            if (shortcode == null) {
                throw new IOException("Could not extract shortcode from Instagram URL");
            }

            Logger.info("VIDEO_DOWNLOAD: Instagram shortcode=" + shortcode);

            String csrfToken = getCSRFToken();
            JSONObject mediaData = fetchMediaData(shortcode, csrfToken);

            // Extract video URL
            String videoUrl = null;
            String title = "";
            String thumbnail = null;

            boolean isSidecar = "XDTGraphSidecar".equals(mediaData.optString("__typename"));

            if (isSidecar) {
                // Multi-media post: find first video
                JSONArray edges = mediaData.getJSONObject("edge_sidecar_to_children").getJSONArray("edges");
                for (int i = 0; i < edges.length(); i++) {
                    JSONObject node = edges.getJSONObject(i).getJSONObject("node");
                    if (node.optBoolean("is_video", false)) {
                        videoUrl = node.getString("video_url");
                        thumbnail = node.optString("display_url", null);
                        break;
                    }
                }
                if (videoUrl == null) {
                    // No video in sidecar, get first image
                    JSONObject firstNode = edges.getJSONObject(0).getJSONObject("node");
                    videoUrl = firstNode.getString("display_url");
                }
            } else {
                if (mediaData.optBoolean("is_video", false)) {
                    videoUrl = mediaData.getString("video_url");
                    thumbnail = mediaData.optString("display_url", null);
                } else {
                    videoUrl = mediaData.getString("display_url");
                }
            }

            // Extract caption
            try {
                JSONArray captionEdges = mediaData.getJSONObject("edge_media_to_caption").getJSONArray("edges");
                if (captionEdges.length() > 0) {
                    title = captionEdges.getJSONObject(0).getJSONObject("node").getString("text");
                    if (title.length() > 100) title = title.substring(0, 100) + "...";
                }
            } catch (Exception ignored) {}

            if (title.isEmpty()) title = "Instagram Video";

            if (videoUrl == null) {
                throw new IOException("Could not extract video/image URL from Instagram post");
            }

            return new VideoDownloadManager.VideoResult(videoUrl, title, thumbnail,
                    VideoDownloadManager.Platform.INSTAGRAM);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Instagram download failed: " + e.getMessage(), e);
        }
    }

    private static String resolveRedirect(String url) throws IOException {
        if (!url.contains("/share/")) return url;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.connect();
            return conn.getURL().toString();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String extractShortcode(String url) {
        String[] parts = url.split("/");
        String[] postTags = {"p", "reel", "tv", "reels"};
        for (int i = 0; i < parts.length; i++) {
            for (String tag : postTags) {
                if (tag.equals(parts[i]) && i + 1 < parts.length) {
                    String code = parts[i + 1];
                    if (!code.isEmpty() && !code.startsWith("?")) return code;
                }
            }
        }
        return null;
    }

    private static String getCSRFToken() throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("https://www.instagram.com/").openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.connect();

            String cookies = conn.getHeaderField("Set-Cookie");
            if (cookies != null && cookies.contains("csrftoken=")) {
                String token = cookies.split("csrftoken=")[1].split(";")[0];
                return token;
            }

            // Fallback: try all cookie headers
            for (int i = 0; ; i++) {
                String key = conn.getHeaderFieldKey(i);
                if (key == null && i > 0) break;
                if ("Set-Cookie".equalsIgnoreCase(key)) {
                    String val = conn.getHeaderField(i);
                    if (val != null && val.contains("csrftoken=")) {
                        return val.split("csrftoken=")[1].split(";")[0];
                    }
                }
            }
            throw new IOException("CSRF token not found");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static JSONObject fetchMediaData(String shortcode, String csrfToken) throws Exception {
        HttpURLConnection conn = null;
        try {
            String body = "variables=" + java.net.URLEncoder.encode(
                    "{\"shortcode\":\"" + shortcode + "\",\"fetch_tagged_user_count\":null," +
                    "\"hoisted_comment_id\":null,\"hoisted_reply_id\":null}", "UTF-8")
                    + "&doc_id=" + DOCUMENT_ID;

            conn = (HttpURLConnection) new URL(GRAPHQL_URL).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("X-CSRFToken", csrfToken);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IOException("Instagram API returned HTTP " + code);
            }

            String response;
            try (InputStream is = conn.getInputStream();
                 Scanner scanner = new Scanner(is, "UTF-8")) {
                scanner.useDelimiter("\\A");
                response = scanner.hasNext() ? scanner.next() : "";
            }

            JSONObject json = new JSONObject(response);
            JSONObject media = json.optJSONObject("data");
            if (media == null) throw new IOException("No data in Instagram response");

            JSONObject shortcodeMedia = media.optJSONObject("xdt_shortcode_media");
            if (shortcodeMedia == null) {
                throw new IOException("Post not found or not supported. Check if the link is valid.");
            }

            return shortcodeMedia;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
