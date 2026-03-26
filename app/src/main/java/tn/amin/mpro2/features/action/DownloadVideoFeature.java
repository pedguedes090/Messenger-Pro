package tn.amin.mpro2.features.action;

import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import tn.amin.mpro2.R;
import tn.amin.mpro2.debug.Logger;
import tn.amin.mpro2.features.Feature;
import tn.amin.mpro2.features.FeatureId;
import tn.amin.mpro2.features.FeatureType;
import tn.amin.mpro2.features.util.video.FacebookVideoDownloader;
import tn.amin.mpro2.features.util.video.VideoDownloadManager;
import tn.amin.mpro2.hook.HookId;
import tn.amin.mpro2.hook.all.ConversationLeaveHook;
import tn.amin.mpro2.hook.all.MessagesDisplayHook;
import tn.amin.mpro2.orca.OrcaGateway;
import tn.amin.mpro2.orca.wrapper.MessageWrapper;
import tn.amin.mpro2.orca.wrapper.MessagesCollectionWrapper;
import tn.amin.mpro2.ui.toolbar.ToolbarButtonCategory;

public class DownloadVideoFeature extends Feature
        implements ConversationLeaveHook.ConversationLeaveListener,
                   MessagesDisplayHook.MessageDisplayHookListener {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Pending FB video URL detected from clipboard
    private static volatile String pendingVideoUrl = null;
    private boolean clipboardListenerRegistered = false;
    private boolean dialogShowing = false;

    // Auto-detected video URLs from displayed messages (all platforms)
    private final List<String> detectedVideoUrls = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_DETECTED_URLS = 30;

    public DownloadVideoFeature(OrcaGateway gateway) {
        super(gateway);
    }

    @Override
    public FeatureId getId() {
        return FeatureId.VIDEO_DOWNLOAD;
    }

    @Override
    public FeatureType getType() {
        return FeatureType.ACTION;
    }

    @Override
    public HookId[] getHookIds() {
        return new HookId[] { HookId.CONVERSATION_LEAVE, HookId.MESSAGES_DISPLAY };
    }

    @Override
    public void onConversationLeave() {
        pendingVideoUrl = null;
        dialogShowing = false;
        detectedVideoUrls.clear();
    }

    // ---- MessagesDisplayHook.MessageDisplayHookListener ----

    @Override
    public void onMessageDisplay(@Nullable MessageWrapper message, int index,
                                 int count, MessagesCollectionWrapper messagesCollection) {
        if (message == null || !isEnabled()) return;

        // 1) Extract video URL from message text (all platforms)
        String text = message.getText();
        if (text != null && !text.isEmpty()) {
            String videoUrl = VideoDownloadManager.extractUrl(text);
            if (videoUrl != null) {
                addDetectedUrl(videoUrl);
                return;
            }
        }

        // 2) Reflect on raw message object to find URLs in attachment/share fields
        Object rawObj = message.getRawObject();
        if (rawObj != null) {
            findUrlsInObject(rawObj, 2);
        }
    }

    private void addDetectedUrl(String url) {
        if (detectedVideoUrls.contains(url)) return;
        if (detectedVideoUrls.size() >= MAX_DETECTED_URLS) {
            detectedVideoUrls.remove(0);
        }
        detectedVideoUrls.add(url);
        VideoDownloadManager.Platform platform = VideoDownloadManager.detectPlatform(url);
        Logger.info("VIDEO_DOWNLOAD: auto-detected " + platform.displayName + " URL: " + url);
    }

    /**
     * Recursively search an object's fields for strings that contain video URLs (all platforms).
     */
    private void findUrlsInObject(Object obj, int maxDepth) {
        if (obj == null || maxDepth <= 0) return;
        try {
            for (Field field : obj.getClass().getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object val = field.get(obj);
                    if (val instanceof String) {
                        String videoUrl = VideoDownloadManager.extractUrl((String) val);
                        if (videoUrl != null) {
                            addDetectedUrl(videoUrl);
                        }
                    } else if (val instanceof List) {
                        for (Object item : (List<?>) val) {
                            if (item instanceof String) {
                                String videoUrl = VideoDownloadManager.extractUrl((String) item);
                                if (videoUrl != null) addDetectedUrl(videoUrl);
                            } else if (item != null && maxDepth > 1) {
                                findUrlsInObject(item, maxDepth - 1);
                            }
                        }
                    } else if (val != null && maxDepth > 1
                            && !field.getType().isPrimitive()
                            && !field.getType().isEnum()
                            && !field.getType().getName().startsWith("java.lang.")
                            && !field.getType().getName().startsWith("android.")) {
                        findUrlsInObject(val, maxDepth - 1);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    /**
     * Register a clipboard listener for auto-detecting FB video URLs.
     * Called from MProPatcher when activity is available.
     */
    public void registerClipboardListener(Context context) {
        if (clipboardListenerRegistered) return;
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) return;

            clipboard.addPrimaryClipChangedListener(() -> {
                if (!isEnabled()) return;
                try {
                    ClipData clip = clipboard.getPrimaryClip();
                    if (clip == null || clip.getItemCount() == 0) return;
                    CharSequence text = clip.getItemAt(0).getText();
                    if (text == null) return;

                    String videoUrl = VideoDownloadManager.extractUrl(text.toString());
                    if (videoUrl != null) {
                        pendingVideoUrl = videoUrl;
                        VideoDownloadManager.Platform p = VideoDownloadManager.detectPlatform(videoUrl);
                        Logger.info("VIDEO_DOWNLOAD: clipboard " + p.displayName + " link detected: " + videoUrl);
                        mainHandler.post(() -> showDownloadDialog(videoUrl));
                    }
                } catch (Exception e) {
                    Logger.verbose("Clipboard listener error: " + e.getMessage());
                }
            });
            clipboardListenerRegistered = true;
            Logger.info("VIDEO_DOWNLOAD: clipboard listener registered");
        } catch (Exception e) {
            Logger.verbose("Could not register clipboard listener: " + e.getMessage());
        }
    }

    /**
     * Called from MProPatcher when a long-press is detected in the message area.
     * Returns true if a download dialog was shown.
     */
    public boolean onLongPressInMessageArea() {
        if (!isEnabled()) return false;
        if (dialogShowing) return false;

        // Only trigger when inside a conversation
        if (gateway.currentThreadKey == null) return false;

        // 1) Check auto-detected URLs from messages first
        if (!detectedVideoUrls.isEmpty()) {
            List<String> urls = new ArrayList<>(detectedVideoUrls);
            if (urls.size() == 1) {
                showDownloadDialog(urls.get(0));
            } else {
                showUrlListDialog(urls);
            }
            return true;
        }

        // 2) Fall back to pending clipboard URL
        String url = pendingVideoUrl;
        if (url == null) {
            url = getClipboardVideoUrl();
        }
        if (url == null) return false;

        pendingVideoUrl = null;
        showDownloadDialog(url);
        return true;
    }

    /**
     * Show download confirmation dialog for a video URL.
     */
    private void showDownloadDialog(String videoUrl) {
        if (dialogShowing) return;
        dialogShowing = true;
        pendingVideoUrl = null;

        mainHandler.post(() -> {
            Context ctx = gateway.getActivity();
            if (ctx == null) ctx = gateway.getContext();
            if (ctx == null) {
                dialogShowing = false;
                return;
            }

            final Context context = ctx;
            showCustomConfirmDialog(
                    context,
                    videoUrl,
                    false,
                    null,
                    () -> dialogShowing = false
            );
        });
    }

    /**
     * Show a list of detected FB URLs for the user to pick from.
     */
    private void showUrlListDialog(List<String> urls) {
        if (dialogShowing) return;
        dialogShowing = true;

        mainHandler.post(() -> {
            Context ctx = gateway.getActivity();
            if (ctx == null) ctx = gateway.getContext();
            if (ctx == null) {
                dialogShowing = false;
                return;
            }

            // Show most recent first
            List<String> reversed = new ArrayList<>(urls);
            Collections.reverse(reversed);
            showCustomUrlPickerDialog(
                    ctx,
                    reversed,
                    false,
                    () -> dialogShowing = false
            );
        });
    }

    private String getClipboardVideoUrl() {
        try {
            Context context = gateway.getActivity();
            if (context == null) context = gateway.getContext();
            if (context == null) return null;

            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).getText();
                    if (text != null) {
                        String videoUrl = VideoDownloadManager.extractUrl(text.toString());
                        if (videoUrl != null) return videoUrl;
                    }
                }
            }
        } catch (Exception e) {
            Logger.verbose("Could not read clipboard: " + e.getMessage());
        }
        return null;
    }

    @Nullable
    @Override
    public String getPreferenceKey() {
        return "mpro_conversation_download_video";
    }

    @Nullable
    @Override
    public ToolbarButtonCategory getToolbarCategory() {
        return ToolbarButtonCategory.QUICK_ACTION;
    }

    @Nullable
    @Override
    public Integer getToolbarDescription() {
        return R.string.feature_download_video;
    }

    @Nullable
    @Override
    public Integer getDrawableResource() {
        return R.drawable.ic_toolbar_download;
    }

    @Override
    public void executeAction() {
        Context context = gateway.getActivity();
        if (context == null) {
            context = gateway.getContext();
        }
        if (context == null) return;

        // If there are auto-detected URLs from messages, offer them
        if (!detectedVideoUrls.isEmpty()) {
            List<String> urls = new ArrayList<>(detectedVideoUrls);
            Collections.reverse(urls);
            showCustomUrlPickerDialog(context, urls, true, null);
            return;
        }

        // If there's a pending detected URL, offer it directly
        String pending = pendingVideoUrl;
        if (pending != null) {
            pendingVideoUrl = null;
            final Context ctx = context;
            showCustomConfirmDialog(
                    context,
                    pending,
                    true,
                    () -> {
                        pendingVideoUrl = pending;
                        showUrlDialog(ctx, "");
                    },
                    null
            );
            return;
        }

        // Fallback: check clipboard for a supported video URL
        String clipText = "";
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).getText();
                    if (text != null && VideoDownloadManager.isSupportedUrl(text.toString())) {
                        clipText = text.toString();
                    }
                }
            }
        } catch (Exception e) {
            Logger.verbose("Could not read clipboard: " + e.getMessage());
        }

        showUrlDialog(context, clipText);
    }

    private void showUrlDialog(Context context, String prefillUrl) {
        ContextThemeWrapper dialogContext = new ContextThemeWrapper(context, R.style.AppTheme);
        View dialogView = LayoutInflater.from(dialogContext).inflate(R.layout.dialog_video_download_input, null, false);
        TextInputEditText input = dialogView.findViewById(R.id.input_video_url);

        if (prefillUrl != null && !prefillUrl.isEmpty()) {
            input.setText(prefillUrl);
            input.selectAll();
        }

        newDialogBuilder(context)
                .setTitle("Paste video link")
                .setMessage("Supported: Facebook, Instagram, TikTok, Douyin")
                .setView(dialogView)
                .setPositiveButton("Download now", (dialog, which) -> {
                    String raw = input.getText() == null ? "" : input.getText().toString();
                    String url = raw.trim();
                    if (url.isEmpty()) return;

                    if (!VideoDownloadManager.isSupportedUrl(url)) {
                        Toast.makeText(context, "Unsupported URL. Supports: Facebook, Instagram, TikTok, Douyin", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Toast.makeText(context, "Fetching video info...", Toast.LENGTH_SHORT).show();
                    startDownload(context, url);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private MaterialAlertDialogBuilder newDialogBuilder(Context context) {
        ContextThemeWrapper dialogContext = new ContextThemeWrapper(context, R.style.AppTheme);
        return new MaterialAlertDialogBuilder(dialogContext)
                .setIcon(R.drawable.ic_toolbar_download);
    }

    private void showCustomConfirmDialog(Context context,
                                         String videoUrl,
                                         boolean showOtherUrl,
                                         @Nullable Runnable onOtherUrl,
                                         @Nullable Runnable onDismiss) {
        ContextThemeWrapper dialogContext = new ContextThemeWrapper(context, R.style.AppTheme);
        View dialogView = LayoutInflater.from(dialogContext).inflate(R.layout.dialog_video_download_confirm, null, false);

        VideoDownloadManager.Platform platform = VideoDownloadManager.detectPlatform(videoUrl);

        TextView titleView = dialogView.findViewById(R.id.dialog_title);
        TextView subtitleView = dialogView.findViewById(R.id.dialog_subtitle);
        TextView urlView = dialogView.findViewById(R.id.dialog_url);

        MaterialButton buttonDownload = dialogView.findViewById(R.id.button_download);
        MaterialButton buttonOther = dialogView.findViewById(R.id.button_other);
        MaterialButton buttonCancel = dialogView.findViewById(R.id.button_cancel);

        titleView.setText(context.getString(R.string.video_download_dialog_title, platform.displayName));
        subtitleView.setText(R.string.video_download_dialog_subtitle);
        urlView.setText(formatForDialog(videoUrl));

        AlertDialog dialog = new MaterialAlertDialogBuilder(dialogContext)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (onDismiss != null) {
            dialog.setOnDismissListener(d -> onDismiss.run());
        }

        if (showOtherUrl) {
            buttonOther.setVisibility(View.VISIBLE);
            buttonOther.setOnClickListener(v -> {
                dialog.dismiss();
                if (onOtherUrl != null) {
                    onOtherUrl.run();
                }
            });
        } else {
            buttonOther.setVisibility(View.GONE);
        }

        buttonDownload.setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(context, "Fetching video info...", Toast.LENGTH_SHORT).show();
            startDownload(context, videoUrl);
        });
        buttonCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showCustomUrlPickerDialog(Context context,
                                           List<String> urls,
                                           boolean showOtherUrl,
                                           @Nullable Runnable onDismiss) {
        ContextThemeWrapper dialogContext = new ContextThemeWrapper(context, R.style.AppTheme);
        View dialogView = LayoutInflater.from(dialogContext).inflate(R.layout.dialog_video_download_list, null, false);

        TextView titleView = dialogView.findViewById(R.id.dialog_list_title);
        TextView subtitleView = dialogView.findViewById(R.id.dialog_list_subtitle);
        ListView listView = dialogView.findViewById(R.id.dialog_list_urls);
        MaterialButton buttonOther = dialogView.findViewById(R.id.button_list_other);
        MaterialButton buttonCancel = dialogView.findViewById(R.id.button_list_cancel);

        titleView.setText(R.string.video_download_list_title);
        subtitleView.setText(R.string.video_download_list_subtitle);

        listView.setAdapter(new UrlListAdapter(dialogContext, urls));

        AlertDialog dialog = new MaterialAlertDialogBuilder(dialogContext)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (onDismiss != null) {
            dialog.setOnDismissListener(d -> onDismiss.run());
        }

        listView.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            String picked = urls.get(position);
            showCustomConfirmDialog(context, picked, showOtherUrl, () -> showUrlDialog(context, ""), null);
        });

        if (showOtherUrl) {
            buttonOther.setVisibility(View.VISIBLE);
            buttonOther.setOnClickListener(v -> {
                dialog.dismiss();
                showUrlDialog(context, "");
            });
        } else {
            buttonOther.setVisibility(View.GONE);
        }

        buttonCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private static class UrlListAdapter extends BaseAdapter {
        private final Context context;
        private final List<String> urls;

        UrlListAdapter(Context context, List<String> urls) {
            this.context = context;
            this.urls = urls;
        }

        @Override
        public int getCount() {
            return urls.size();
        }

        @Override
        public Object getItem(int position) {
            return urls.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(context).inflate(R.layout.dialog_video_download_list_item, parent, false);
            }

            String url = urls.get(position);
            VideoDownloadManager.Platform platform = VideoDownloadManager.detectPlatform(url);

            TextView platformView = view.findViewById(R.id.item_platform);
            TextView urlView = view.findViewById(R.id.item_url);

            platformView.setText(platform.displayName);
            urlView.setText(url);
            return view;
        }
    }

    private String[] buildDialogItems(List<String> urls) {
        String[] items = new String[urls.size()];
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            VideoDownloadManager.Platform platform = VideoDownloadManager.detectPlatform(url);
            items[i] = platform.displayName + " - " + formatForDialog(url);
        }
        return items;
    }

    private String formatForDialog(String url) {
        if (url == null) return "";
        if (url.length() <= 80) return url;
        return url.substring(0, 77) + "...";
    }

    private void startDownload(Context context, String videoUrl) {
        new Thread(() -> {
            try {
                VideoDownloadManager.VideoResult result = VideoDownloadManager.getVideoInfo(videoUrl);

                if (result.downloadUrl == null || result.downloadUrl.isEmpty()) {
                    mainHandler.post(() ->
                            Toast.makeText(context, "Could not extract video URL", Toast.LENGTH_LONG).show());
                    return;
                }

                String prefix = VideoDownloadManager.getFilePrefix(result.platform);
                String fileName = prefix + System.currentTimeMillis() + ".mp4";

                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(result.downloadUrl));
                request.setTitle(result.title != null ? result.title : result.platform.displayName + " Video");
                request.setDescription("Downloading " + result.platform.displayName + " video...");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

                DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(request);
                    mainHandler.post(() ->
                            Toast.makeText(context, "Download started: " + fileName, Toast.LENGTH_LONG).show());
                    Logger.info("VIDEO_DOWNLOAD: enqueued " + result.platform.displayName + " download: " + fileName);
                }
            } catch (Exception e) {
                Logger.error("VIDEO_DOWNLOAD: " + e.getMessage());
                Logger.error(e);
                mainHandler.post(() ->
                        Toast.makeText(context, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "mpro-video-download").start();
    }
}
