package tn.amin.mpro2.features.state;

import tn.amin.mpro2.features.Feature;
import tn.amin.mpro2.features.FeatureId;
import tn.amin.mpro2.features.FeatureType;
import tn.amin.mpro2.hook.HookId;
import tn.amin.mpro2.hook.all.OpenLinksExternallyHook;
import tn.amin.mpro2.hook.listener.HookListenerResult;
import tn.amin.mpro2.orca.OrcaGateway;

public class OpenLinksExternallyFeature extends Feature
        implements OpenLinksExternallyHook.OpenLinksExternallyListener {
    public OpenLinksExternallyFeature(OrcaGateway gateway) {
        super(gateway);
    }

    @Override
    public FeatureId getId() {
        return FeatureId.OPEN_LINKS_EXTERNALLY;
    }

    @Override
    public FeatureType getType() {
        return FeatureType.CHECKABLE_STATE;
    }

    @Override
    public HookId[] getHookIds() {
        return new HookId[] { HookId.OPEN_LINKS_EXTERNALLY };
    }

    @Override
    public boolean isEnabledByDefault() {
        return false;
    }

    @Override
    public String getPreferenceKey() {
        return "mpro_open_links_externally";
    }

    @Override
    public HookListenerResult<Object> onLinkOpen() {
        return isEnabled() ? HookListenerResult.consume() : HookListenerResult.ignore();
    }
}
