package tn.amin.mpro2.features.state;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import tn.amin.mpro2.R;
import tn.amin.mpro2.debug.Logger;
import tn.amin.mpro2.features.Feature;
import tn.amin.mpro2.features.FeatureId;
import tn.amin.mpro2.features.FeatureType;
import tn.amin.mpro2.features.util.image.DefaultCameraMaster;
import tn.amin.mpro2.file.FileHelper;
import tn.amin.mpro2.hook.HookId;
import tn.amin.mpro2.hook.all.CameraLaunchHook;
import tn.amin.mpro2.hook.listener.HookListenerResult;
import tn.amin.mpro2.orca.OrcaGateway;

public class DefaultCameraFeature extends Feature
        implements CameraLaunchHook.CameraLaunchListener {
    public DefaultCameraFeature(OrcaGateway gateway) {
        super(gateway);
    }

    @Override
    public FeatureId getId() {
        return FeatureId.DEFAULT_CAMERA;
    }

    @Override
    public FeatureType getType() {
        return FeatureType.CHECKABLE_STATE;
    }

    @Override
    public HookId[] getHookIds() {
        return new HookId[] { HookId.CAMERA_LAUNCH };
    }

    @Override
    public boolean isEnabledByDefault() {
        return false;
    }

    @Nullable
    @Override
    public String getPreferenceKey() {
        return "mpro_image_default_camera";
    }

    @Override
    public HookListenerResult<Boolean> onCameraLaunch() {
        if (!isEnabled()) return HookListenerResult.ignore();

        final long threadKey = gateway.currentThreadKey;

        Logger.info("DefaultCameraFeature: starting default camera for threadKey=" + threadKey);

        new Handler(Looper.getMainLooper()).post(() -> {
            boolean success = DefaultCameraMaster.launchCamera(gateway.activityHook, FileHelper.generateUniqueFilename("jpg"), (imageUri) -> {
                Logger.info("DefaultCameraFeature: camera returned with URI=" + imageUri);
                shareImageViaIntent(imageUri);
            });

            if (!success) {
                Logger.error("DefaultCameraFeature: failed to launch camera");
                gateway.getToaster().toast(R.string.camera_need_permission, true);
            }
        });

        return HookListenerResult.consume(true);
    }

    private void shareImageViaIntent(Uri imageUri) {
        Activity activity = gateway.activityHook.currentActivity.get();
        if (activity == null) {
            Logger.error("DefaultCameraFeature: activity is null, cannot share image");
            return;
        }

        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/jpeg");
            shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
            shareIntent.setPackage("com.facebook.orca");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(shareIntent);
            Logger.info("DefaultCameraFeature: launched share intent with URI=" + imageUri);
        } catch (Throwable t) {
            Logger.error("DefaultCameraFeature: share intent failed: " + t.getMessage());
            Logger.error(t);
        }
    }
}
