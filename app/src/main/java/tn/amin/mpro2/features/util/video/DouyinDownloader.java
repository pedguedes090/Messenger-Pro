package tn.amin.mpro2.features.util.video;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Scanner;

import tn.amin.mpro2.debug.Logger;

/**đasa
 * Douyin (Chinese TikTok) video downloader using public API.
 */
public class DouyinDownloader {

    private static final String API_BASE = "https://douyin.cuorz.com/api/douyin/detail?url=";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public static VideoDownloadManager.VideoResult getVideoInfo(String douyinUrl) throws IOException {
        HttpURLConnection conn = null;
        try {
            String encodedUrl = URLEncoder.encode(douyinUrl, "UTF-8");
            String apiUrl = API_BASE + encodedUrl;

            Logger.info("VIDEO_DOWNLOAD: Douyin API request: " + apiUrl);

            conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("Douyin API returned HTTP " + responseCode);
            }

            String response;
            try (InputStream is = conn.getInputStream();
                 Scanner scanner = new Scanner(is, "UTF-8")) {
                scanner.useDelimiter("\\A");
                response = scanner.hasNext() ? scanner.next() : "";
            }

            JSONObject json = new JSONObject(response);

            // Support both old schema ({code,data}) and new schema ({status,...}).
            if (json.has("code")) {
                int code = json.optInt("code", -1);
                if (code != 0) {
                    String msg = json.optString("msg", json.optString("message", "Unknown error"));
                    throw new IOException("Douyin API error: " + msg);
                }
            } else if (json.has("status")) {
                String status = json.optString("status", "");
                if (!"ok".equalsIgnoreCase(status) && !"success".equalsIgnoreCase(status)) {
                    String msg = json.optString("message", "Unknown error");
                    throw new IOException("Douyin API error: " + msg);
                }
            }

            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                data = json;
            }

            String videoUrl = extractVideoUrl(json, data);

            if (videoUrl == null || videoUrl.isEmpty()) {
                throw new IOException("Could not extract video URL from Douyin response");
            }

            String title = firstNonEmpty(
                    data.optString("title", null),
                    json.optString("title", null),
                    json.optJSONObject("response") != null
                            && json.optJSONObject("response").optJSONObject("aweme_detail") != null
                            ? json.optJSONObject("response").optJSONObject("aweme_detail").optString("desc", null)
                            : null,
                    "Douyin Video"
            );
            if (title.length() > 100) title = title.substring(0, 100) + "...";

            String thumbnail = firstNonEmpty(
                    extractUrlField(data, "cover"),
                    extractUrlField(data, "origin_cover"),
                    extractUrlField(json, "cover"),
                    extractUrlField(json, "origin_cover"),
                    extractNestedVideoCoverUrl(json)
            );

            return new VideoDownloadManager.VideoResult(videoUrl, title, thumbnail,
                    VideoDownloadManager.Platform.DOUYIN);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Douyin download failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String extractVideoUrl(JSONObject root, JSONObject data) {
        String videoUrl = firstNonEmpty(
                data.optString("nwm_video_url", null),
                data.optString("video_url", null),
                root.optString("nwm_video_url", null),
                root.optString("video_url", null),
                root.optString("video", null)
        );
        if (isNonEmpty(videoUrl)) return videoUrl;

        JSONObject dataVideo = data.optJSONObject("video");
        videoUrl = firstNonEmpty(
                dataVideo != null ? dataVideo.optString("play_addr_no_watermark", null) : null,
                dataVideo != null ? dataVideo.optString("play_addr", null) : null,
                dataVideo != null ? firstUrlFromArray(dataVideo.optJSONArray("url_list")) : null
        );
        if (isNonEmpty(videoUrl)) return videoUrl;

        JSONObject response = root.optJSONObject("response");
        JSONObject aweme = response != null ? response.optJSONObject("aweme_detail") : null;
        JSONObject video = aweme != null ? aweme.optJSONObject("video") : null;
        JSONObject playAddr = video != null ? video.optJSONObject("play_addr") : null;
        JSONObject downloadAddr = video != null ? video.optJSONObject("download_addr") : null;

        videoUrl = firstNonEmpty(
                playAddr != null ? firstUrlFromArray(playAddr.optJSONArray("url_list")) : null,
                downloadAddr != null ? firstUrlFromArray(downloadAddr.optJSONArray("url_list")) : null
        );
        return videoUrl;
    }

    private static String extractNestedVideoCoverUrl(JSONObject root) {
        JSONObject response = root.optJSONObject("response");
        JSONObject aweme = response != null ? response.optJSONObject("aweme_detail") : null;
        JSONObject video = aweme != null ? aweme.optJSONObject("video") : null;
        JSONObject cover = video != null ? video.optJSONObject("cover") : null;
        JSONObject originCover = video != null ? video.optJSONObject("origin_cover") : null;

        return firstNonEmpty(
                cover != null ? firstUrlFromArray(cover.optJSONArray("url_list")) : null,
                originCover != null ? firstUrlFromArray(originCover.optJSONArray("url_list")) : null
        );
    }

    private static String extractUrlField(JSONObject obj, String key) {
        if (obj == null) return null;
        Object value = obj.opt(key);
        if (value instanceof String) return (String) value;
        if (value instanceof JSONObject) {
            JSONObject valueObj = (JSONObject) value;
            return firstUrlFromArray(valueObj.optJSONArray("url_list"));
        }
        return null;
    }

    private static String firstUrlFromArray(JSONArray arr) {
        if (arr == null || arr.length() == 0) return null;
        for (int i = 0; i < arr.length(); i++) {
            String value = arr.optString(i, null);
            if (isNonEmpty(value)) return value;
        }
        return null;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (isNonEmpty(value)) return value;
        }
        return null;
    }

    private static boolean isNonEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
