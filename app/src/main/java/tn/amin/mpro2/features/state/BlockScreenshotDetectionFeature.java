package tn.amin.mpro2.features.state;

import tn.amin.mpro2.features.Feature;
import tn.amin.mpro2.features.FeatureId;
import tn.amin.mpro2.features.FeatureType;
import tn.amin.mpro2.hook.HookId;
import tn.amin.mpro2.hook.all.BlockScreenshotDetectionHook;
import tn.amin.mpro2.hook.listener.HookListenerResult;
import tn.amin.mpro2.orca.OrcaGateway;

public class BlockScreenshotDetectionFeature extends Feature
        implements BlockScreenshotDetectionHook.BlockScreenshotDetectionListener {
    public BlockScreenshotDetectionFeature(OrcaGateway gateway) {
        super(gateway);
    }

    @Override
    public FeatureId getId() {
        return FeatureId.BLOCK_SCREENSHOT_DETECTION;
    }

    @Override
    public FeatureType getType() {
        return FeatureType.CHECKABLE_STATE;
    }

    @Override
    public HookId[] getHookIds() {
        return new HookId[] { HookId.BLOCK_SCREENSHOT_DETECTION };
    }

    @Override
    public boolean isEnabledByDefault() {
        return false;
    }

    @Override
    public String getPreferenceKey() {
        return "mpro_block_screenshot_detection";
    }

    @Override
    public HookListenerResult<Object> onScreenshotDetected() {
        return isEnabled() ? HookListenerResult.consume() : HookListenerResult.ignore();
    }
}
