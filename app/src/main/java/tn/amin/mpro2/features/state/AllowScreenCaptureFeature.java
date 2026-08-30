package tn.amin.mpro2.features.state;

import tn.amin.mpro2.features.Feature;
import tn.amin.mpro2.features.FeatureId;
import tn.amin.mpro2.features.FeatureType;
import tn.amin.mpro2.hook.HookId;
import tn.amin.mpro2.hook.all.AllowScreenCaptureHook;
import tn.amin.mpro2.hook.listener.HookListenerResult;
import tn.amin.mpro2.orca.OrcaGateway;

public class AllowScreenCaptureFeature extends Feature
        implements AllowScreenCaptureHook.AllowScreenCaptureListener {
    public AllowScreenCaptureFeature(OrcaGateway gateway) {
        super(gateway);
    }

    @Override
    public FeatureId getId() {
        return FeatureId.ALLOW_SCREEN_CAPTURE;
    }

    @Override
    public FeatureType getType() {
        return FeatureType.CHECKABLE_STATE;
    }

    @Override
    public HookId[] getHookIds() {
        return new HookId[] { HookId.ALLOW_SCREEN_CAPTURE };
    }

    @Override
    public boolean isEnabledByDefault() {
        return false;
    }

    @Override
    public String getPreferenceKey() {
        return "mpro_allow_screen_capture";
    }

    @Override
    public HookListenerResult<Object> onScreenCaptureRegistration() {
        return isEnabled() ? HookListenerResult.consume() : HookListenerResult.ignore();
    }
}
