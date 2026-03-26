package tn.amin.mpro2.features.util.video;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Scanner;

import tn.amin.mpro2.debug.Logger;

/**
 * TikTok video downloader using TikWM API.
 */
public class TikTokDownloader {

    private static final String API_BASE = "https://www.tikwm.com/api/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public static VideoDownloadManager.VideoResult getVideoInfo(String tiktokUrl) throws IOException {
        HttpURLConnection conn = null;
        try {
            String encodedUrl = URLEncoder.encode(tiktokUrl, "UTF-8");
            String apiUrl = API_BASE + "?url=" + encodedUrl + "&hd=1";

            Logger.info("VIDEO_DOWNLOAD: TikWM API request for TikTok video");

            conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("TikWM API returned HTTP " + responseCode);
            }

            String response;
            try (InputStream is = conn.getInputStream();
                 Scanner scanner = new Scanner(is, "UTF-8")) {
                scanner.useDelimiter("\\A");
                response = scanner.hasNext() ? scanner.next() : "";
            }

            JSONObject json = new JSONObject(response);

            int code = json.optInt("code", -1);
            if (code != 0) {
                String msg = json.optString("msg", "Unknown error");
                throw new IOException("TikWM API error: " + msg);
            }

            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                throw new IOException("No data in TikWM API response");
            }

            // Try HD first, then regular play URL
            String videoUrl = data.optString("hdplay", null);
            if (videoUrl == null || videoUrl.isEmpty()) {
                videoUrl = data.optString("play", null);
            }
            if (videoUrl == null || videoUrl.isEmpty()) {
                throw new IOException("Could not extract video URL from TikWM response");
            }

            String title = data.optString("title", "TikTok Video");
            if (title.length() > 100) title = title.substring(0, 100) + "...";

            String thumbnail = data.optString("cover", null);
            if (thumbnail == null || thumbnail.isEmpty()) {
                thumbnail = data.optString("origin_cover", null);
            }

            return new VideoDownloadManager.VideoResult(videoUrl, title, thumbnail,
                    VideoDownloadManager.Platform.TIKTOK);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("TikTok download failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
