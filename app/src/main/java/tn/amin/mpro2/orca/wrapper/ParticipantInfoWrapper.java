package tn.amin.mpro2.orca.wrapper;

import android.os.Parcelable;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

import tn.amin.mpro2.debug.Logger;
import tn.amin.mpro2.hook.unobfuscation.OrcaUnobfuscator;
import tn.amin.mpro2.orca.OrcaGateway;
import tn.amin.mpro2.ui.WrapperHelper;

public class ParticipantInfoWrapper {
    private final OrcaGateway gateway;
    private final WeakReference<Object> mObject;

    private final Field mUserKeyField;
    private final Field mNameField;

    public ParticipantInfoWrapper(OrcaGateway gateway, Object participantInfo) {
        this.gateway = gateway;
        mObject = new WeakReference<>(participantInfo);

        mUserKeyField = gateway.unobfuscator.getField(OrcaUnobfuscator.FIELD_PARTICIPANT_INFO_USER_KEY);

        // Messenger 576: ParticipantInfo.A0A is the SecretString holding the display name
        // (confirmed via decompilation: "AppComponentStats.ATTRIBUTE_NAME" = A0A).
        Field nameField = null;
        try {
            nameField = participantInfo.getClass().getDeclaredField("A0A");
            nameField.setAccessible(true);
        } catch (Throwable t) {
            Logger.warn("ParticipantInfoWrapper: name field A0A unavailable: " + t.getMessage());
        }
        mNameField = nameField;
    }

    public UserKeyWrapper getUserKey() {
        Object userKey = WrapperHelper.fieldGet(mUserKeyField, mObject.get());
        if (!(userKey instanceof Parcelable)) return null;

        return new UserKeyWrapper((Parcelable) userKey);
    }

    public String getName() {
        Object obj = mObject.get();
        if (obj == null || mNameField == null) return null;
        try {
            Object secretString = mNameField.get(obj);
            if (secretString == null) return null;
            return new SecretStringWrapper(gateway, secretString).getContent();
        } catch (Throwable t) {
            return null;
        }
    }
}
