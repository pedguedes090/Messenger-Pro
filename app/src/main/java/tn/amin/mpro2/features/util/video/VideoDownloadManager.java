package tn.amin.mpro2.features.util.video;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unified video download manager supporting Facebook, Instagram, TikTok, and Douyin.
 */
public class VideoDownloadManager {

    public enum Platform {
        FACEBOOK("Facebook"),
        INSTAGRAM("Instagram"),
        TIKTOK("TikTok"),
        DOUYIN("Douyin"),
        UNKNOWN("Unknown");

        public final String displayName;
        Platform(String displayName) { this.displayName = displayName; }
    }

    public static class VideoResult {
        public final String downloadUrl;
        public final String title;
        public final String thumbnail;
        public final Platform platform;

        public VideoResult(String downloadUrl, String title, String thumbnail, Platform platform) {
            this.downloadUrl = downloadUrl;
            this.title = title;
            this.thumbnail = thumbnail;
            this.platform = platform;
        }
    }

    // URL patterns for each platform
    private static final Pattern FACEBOOK_URL = Pattern.compile(
            "https?://(?:www\\.|m\\.)?(?:facebook\\.com|fb\\.watch|fb\\.com)/\\S+");
    private static final Pattern INSTAGRAM_URL = Pattern.compile(
            "https?://(?:www\\.)?instagram\\.com/(?:p|reel|reels|tv)/[A-Za-z0-9_-]+/?\\S*");
    private static final Pattern INSTAGRAM_SHARE_URL = Pattern.compile(
            "https?://(?:www\\.)?instagram\\.com/share/\\S+");
    private static final Pattern TIKTOK_URL = Pattern.compile(
            "https?://(?:www\\.|vm\\.|vt\\.)?tiktok\\.com/\\S+");
    private static final Pattern DOUYIN_URL = Pattern.compile(
            "https?://(?:www\\.|v\\.)?douyin\\.com/\\S+");

    /**
     * Detect platform from URL.
     */
    public static Platform detectPlatform(String url) {
        if (url == null || url.isEmpty()) return Platform.UNKNOWN;
        if (DOUYIN_URL.matcher(url).find()) return Platform.DOUYIN;
        if (TIKTOK_URL.matcher(url).find()) return Platform.TIKTOK;
        if (INSTAGRAM_URL.matcher(url).find() || INSTAGRAM_SHARE_URL.matcher(url).find()) return Platform.INSTAGRAM;
        if (FACEBOOK_URL.matcher(url).find()) return Platform.FACEBOOK;
        return Platform.UNKNOWN;
    }

    /**
     * Check if the URL is a supported video URL from any platform.
     */
    public static boolean isSupportedUrl(String url) {
        return detectPlatform(url) != Platform.UNKNOWN;
    }

    /**
     * Extract a supported video URL from text.
     */
    public static String extractUrl(String text) {
        if (text == null || text.isEmpty()) return null;

        // Try each platform pattern
        Pattern[] patterns = { DOUYIN_URL, TIKTOK_URL, INSTAGRAM_URL, INSTAGRAM_SHARE_URL, FACEBOOK_URL };
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.find()) return m.group(0);
        }
        return null;
    }

    /**
     * Fetch video info from a URL. Routes to the correct downloader based on platform.
     * Must be called from a background thread.
     */
    public static VideoResult getVideoInfo(String url) throws IOException {
        Platform platform = detectPlatform(url);
        switch (platform) {
            case FACEBOOK:
                return getFacebookVideo(url);
            case INSTAGRAM:
                return InstagramDownloader.getVideoInfo(url);
            case TIKTOK:
                return TikTokDownloader.getVideoInfo(url);
            case DOUYIN:
                return DouyinDownloader.getVideoInfo(url);
            default:
                throw new IOException("Unsupported URL: " + url);
        }
    }

    private static VideoResult getFacebookVideo(String url) throws IOException {
        FacebookVideoDownloader.VideoInfo info = FacebookVideoDownloader.getVideoInfo(url);
        String downloadUrl = info.getBestUrl();
        if (downloadUrl == null || downloadUrl.isEmpty()) {
            throw new IOException("Could not extract Facebook video URL");
        }
        return new VideoResult(downloadUrl, info.title, info.thumbnail, Platform.FACEBOOK);
    }

    /**
     * Get a filename prefix for the platform.
     */
    public static String getFilePrefix(Platform platform) {
        switch (platform) {
            case FACEBOOK: return "fb_video_";
            case INSTAGRAM: return "ig_video_";
            case TIKTOK: return "tt_video_";
            case DOUYIN: return "dy_video_";
            default: return "video_";
        }
    }
}
