package tn.amin.mpro2.features.action;

import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;

import tn.amin.mpro2.R;
import tn.amin.mpro2.debug.Logger;
import tn.amin.mpro2.features.Feature;
import tn.amin.mpro2.features.FeatureId;
import tn.amin.mpro2.features.FeatureType;
import tn.amin.mpro2.hook.ActivityHook;
import tn.amin.mpro2.hook.HookId;
import tn.amin.mpro2.orca.OrcaGateway;
import tn.amin.mpro2.ui.toolbar.ToolbarButtonCategory;

public class AttachFileFeature extends Feature {
    public AttachFileFeature(OrcaGateway gateway) {
        super(gateway);
    }

    @Override
    public FeatureId getId() {
        return FeatureId.FILE_ATTACH;
    }

    @Override
    public FeatureType getType() {
        return FeatureType.ACTION;
    }

    @Override
    public HookId[] getHookIds() {
        return new HookId[0];
    }

    @Nullable
    @Override
    public String getPreferenceKey() {
        return "mpro_conversation_attach";
    }

    @Nullable
    @Override
    public ToolbarButtonCategory getToolbarCategory() {
        return ToolbarButtonCategory.QUICK_ACTION;
    }

    @Nullable
    @Override
    public Integer getToolbarDescription() {
        return R.string.feature_attach_file;
    }

    @Nullable
    @Override
    public Integer getDrawableResource() {
        return R.drawable.ic_toolbar_attach;
    }

    @Override
    public void executeAction() {
        if (!gateway.requireThreadKey()) return;

        Logger.info("FILE_ATTACH: starting file picker");
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        gateway.activityHook.startIntent(intent, ActivityHook.REQUESTCODE_PICKFILE, (data) -> {
            try {
                Uri fileUri = data.getData();
                if (fileUri == null) return;

                // Get file name and mime type
                String fileName = "attachment";
                Cursor cursor = gateway.getActivity().getContentResolver().query(fileUri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIdx >= 0) fileName = cursor.getString(nameIdx);
                    cursor.close();
                }

                String mimeType = gateway.getActivity().getContentResolver().getType(fileUri);
                if (mimeType == null) mimeType = "application/octet-stream";

                Logger.info("FILE_ATTACH: file=" + fileName + " mime=" + mimeType);

                // Share to Messenger via ACTION_SEND directly with original URI
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/octet-stream");
                shareIntent.setComponent(new ComponentName("com.facebook.orca",
                        "com.facebook.messenger.intents.ShareIntentHandler"));
                shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                shareIntent.setClipData(ClipData.newUri(
                        gateway.getActivity().getContentResolver(), fileName, fileUri));
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_ACTIVITY_NEW_TASK);
                Logger.info("FILE_ATTACH: launching share intent uri=" + fileUri);
                gateway.getActivity().startActivity(shareIntent);
            } catch (Throwable t) {
                Logger.error(t);
            }
        });
    }
}
