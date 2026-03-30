package tn.amin.mpro2.features.state;

import androidx.annotation.Nullable;

import tn.amin.mpro2.features.Feature;
import tn.amin.mpro2.features.FeatureId;
import tn.amin.mpro2.features.FeatureType;
import tn.amin.mpro2.hook.HookId;
import tn.amin.mpro2.hook.all.PresenceStatusSentHook;
import tn.amin.mpro2.hook.listener.HookListenerResult;
import tn.amin.mpro2.orca.OrcaGateway;

public class PresenceStealthFeature extends Feature
        implements PresenceStatusSentHook.PresenceStatusSentListener {

    public PresenceStealthFeature(OrcaGateway gateway) {
        super(gateway);
    }

    @Override
    public FeatureId getId() {
        return FeatureId.PRESENCE_STEALTH;
    }

    @Override
    public FeatureType getType() {
        return FeatureType.CHECKABLE_STATE;
    }

    @Override
    public HookId[] getHookIds() {
        return new HookId[] { HookId.PRESENCE_STATUS_SEND };
    }

    @Override
    public boolean isEnabledByDefault() {
        return false;
    }

    @Nullable
    @Override
    public String getPreferenceKey() {
        return "mpro_conversation_presence_status";
    }

    @Override
    public HookListenerResult<Boolean> onPresenceStatusSent() {
        return HookListenerResult.consume(!isEnabled());
    }
}