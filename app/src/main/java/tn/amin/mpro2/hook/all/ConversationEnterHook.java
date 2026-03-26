package tn.amin.mpro2.hook.all;

import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import tn.amin.mpro2.debug.Logger;
import tn.amin.mpro2.hook.BaseHook;
import tn.amin.mpro2.hook.HookId;
import tn.amin.mpro2.hook.HookTime;
import tn.amin.mpro2.hook.helper.OrcaHookHelper;
import tn.amin.mpro2.hook.unobfuscation.OrcaUnobfuscator;
import tn.amin.mpro2.orca.OrcaGateway;

public class ConversationEnterHook extends BaseHook {
    @Override
    public HookId getId() {
        return HookId.CONVERSATION_ENTER;
    }

    @Override
    public HookTime getHookTime() {
        return HookTime.AFTER_DEOBFUSCATION;
    }

    @Override
    protected Set<XC_MethodHook.Unhook> injectInternal(OrcaGateway gateway) {
        int apiCode = gateway.unobfuscator.getAPICode(OrcaUnobfuscator.API_CONVERSATION_ENTER);
        Logger.info("ConversationEnterHook: apiCode=" + apiCode);
        return OrcaHookHelper.hookFeature(apiCode,
                "O", "Orca", gateway.classLoader, wrap(new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        long threadKey = -1;
                        // Try args[6] as String (original format)
                        if (param.args.length > 6 && param.args[6] instanceof String) {
                            try {
                                threadKey = Long.parseLong((String) param.args[6]);
                            } catch (NumberFormatException ignored) {}
                        }
                        // Fallback: try args[4] as Long
                        if (threadKey <= 0 && param.args.length > 4 && param.args[4] instanceof Long) {
                            threadKey = (Long) param.args[4];
                        }
                        if (threadKey > 0) {
                            Logger.info("ConversationEnterHook: threadKey=" + threadKey);
                            final long tk = threadKey;
                            notifyListeners((listener) -> ((ConversationEnterListener) listener).onConversationEnter(tk));
                        }
                    }
                }));
    }

    public interface ConversationEnterListener {
        void onConversationEnter(Long threadKey);
    }
}
