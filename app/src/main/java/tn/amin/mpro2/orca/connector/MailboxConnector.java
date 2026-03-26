package tn.amin.mpro2.orca.connector;

import android.util.Base64;

import androidx.core.util.Consumer;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import tn.amin.mpro2.constants.OrcaClassNames;
import tn.amin.mpro2.debug.Logger;
import tn.amin.mpro2.hook.all.MessageSentHook;
import tn.amin.mpro2.orca.builder.AttachmentBuilder;
import tn.amin.mpro2.orca.datatype.GenericMessage;
import tn.amin.mpro2.orca.datatype.MediaAttachment;
import tn.amin.mpro2.orca.datatype.MediaMessage;
import tn.amin.mpro2.orca.datatype.Mention;
import tn.amin.mpro2.orca.datatype.TextMessage;
import tn.amin.mpro2.orca.wrapper.AuthDataWrapper;
import tn.amin.mpro2.util.XposedHilfer;

public class MailboxConnector {
    public final WeakReference<Object> mailbox;
    private final AuthDataWrapper authData;
    private final ClassLoader classLoader;
    private volatile WeakReference<Object> captured7Da = new WeakReference<>(null);

    public MailboxConnector(Object mailbox, AuthDataWrapper authData, ClassLoader classLoader) {
        this.mailbox = new WeakReference<>(mailbox);
        this.authData = authData;
        this.classLoader = classLoader;
    }

    public void set7Da(Object instance) {
        captured7Da = new WeakReference<>(instance);
        Logger.info("MailboxConnector: captured 7Da instance: " + instance.getClass().getName());
    }

    public Object get7Da() {
        Object instance = captured7Da.get();
        if (instance != null) return instance;

        // Try to find 7Da lazily by searching Mailbox fields
        try {
            Object mbx = mailbox.get();
            if (mbx != null) {
                Class<?> cls7Da = Class.forName("X.7Da", false, classLoader);
                Object found = findInstanceOfType(mbx, cls7Da, new HashSet<>(), 3);
                if (found != null) {
                    set7Da(found);
                    return found;
                }
            }
        } catch (Throwable t) {
            Logger.info("get7Da: lazy search failed: " + t.getMessage());
        }
        return null;
    }

    private Object findInstanceOfType(Object root, Class<?> targetType, HashSet<Integer> visited, int maxDepth) {
        if (root == null || maxDepth <= 0) return null;
        int id = System.identityHashCode(root);
        if (visited.contains(id)) return null;
        visited.add(id);

        try {
            Class<?> clazz = root.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field f : clazz.getDeclaredFields()) {
                    if (f.getType().isPrimitive()) continue;
                    try {
                        f.setAccessible(true);
                        Object val = f.get(root);
                        if (val != null && targetType.isInstance(val)) {
                            Logger.info("Found 7Da at " + clazz.getName() + "." + f.getName());
                            return val;
                        }
                    } catch (Throwable ignored) {}
                }
                clazz = clazz.getSuperclass();
            }
            // Recurse one level into non-primitive fields
            clazz = root.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field f : clazz.getDeclaredFields()) {
                    if (f.getType().isPrimitive()) continue;
                    if (f.getType().getName().startsWith("java.lang.")) continue;
                    try {
                        f.setAccessible(true);
                        Object val = f.get(root);
                        if (val != null) {
                            Object result = findInstanceOfType(val, targetType, visited, maxDepth - 1);
                            if (result != null) return result;
                        }
                    } catch (Throwable ignored) {}
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable t) {
            Logger.info("findInstanceOfType error: " + t.getMessage());
        }
        return null;
    }

    public void sendMessage(GenericMessage messageToSend, final long threadKey, final int delay) {
        switch (messageToSend.getType()) {
            case GenericMessage.TYPE_TEXT:
                sendText((TextMessage) messageToSend, threadKey, delay);
                break;

            case GenericMessage.TYPE_MEDIA:
                sendMedia((MediaMessage) messageToSend, threadKey, delay);
                break;
        }
    }

    public void sendText(final TextMessage textMessage, final long threadKey, final int delay) {
        // Try new SDK dispatch (v553+)
        try {
            final Class<?> MailboxSDKJNI = XposedHelpers.findClass(OrcaClassNames.MAILBOX_SDK_JNI, classLoader);
            final Set<Method> newDispatchList = XposedHilfer.findAllMethods(MailboxSDKJNI, MessageSentHook.DISPATCH_METHOD_NEW);
            if (newDispatchList.size() == 1) {
                final Method dispatch = newDispatchList.iterator().next();
                Logger.info("sendText: using new dispatch (SDKJNI)");
                
                // Log param types for diagnostics
                Class<?>[] paramTypes = dispatch.getParameterTypes();
                StringBuilder sb = new StringBuilder("sendText dispatch params: ");
                for (Class<?> pt : paramTypes) sb.append(pt.getSimpleName()).append(",");
                Logger.info(sb.toString());
                
                preDispatch(notificationScope -> {
                    try {
                        String threadKeyEncoded = "T_" + Base64.encodeToString(
                                ("MESSENGER:fbid:" + threadKey).getBytes(), Base64.NO_WRAP);
                        Object[] params = new Object[paramTypes.length];
                        params[0] = 71; // action code for text message
                        params[1] = mailbox.get();
                        params[2] = threadKeyEncoded;
                        params[3] = textMessage.content;
                        // params[4] = null (SendTextMessageOptionalParams)
                        // params[5] = null (LoggingOption)
                        params[6] = notificationScope;
                        // params[7] = null
                        XposedBridge.invokeOriginalMethod(dispatch, null, params);
                    } catch (Throwable t) {
                        Logger.error("sendText new dispatch failed: " + t.getMessage());
                        Logger.error(t);
                    }
                }, delay);
                return;
            }
        } catch (Throwable t) {
            Logger.info("sendText: new dispatch not available, trying old");
        }

        // Fallback: old CoreJNI dispatch
        final Class<?> MailboxCoreJNI = XposedHelpers.findClass(OrcaClassNames.MAILBOX_CORE_JNI, classLoader);
        final Set<Method> disptachList = XposedHilfer.findAllMethods(MailboxCoreJNI, MessageSentHook.DISPATCH_METHOD);
        if (disptachList.size() != 1)
            Logger.error(new RuntimeException("dispatchList size (" + disptachList.size() + ") != 1"));
        final Method disptach = disptachList.iterator().next();

        preDispatch(notificationScope -> {
            long time = System.currentTimeMillis() * 1000;
            Object[] disptachParams = new Object[] {
                    9, 65540, threadKey, mailbox.get(), "", 1, textMessage.content, null, null, null, null, null, null, textMessage.replyMessageId != null? 1: 0, 0, null, null, null, time, null, null, null, null, null, null, false, null, notificationScope
            };

            disptachParams[8] = Mention.joinRangeStarts(textMessage.mentions);
            disptachParams[9] = Mention.joinRangeEnds(textMessage.mentions);
            disptachParams[10] = Mention.joinThreadKeys(textMessage.mentions);
            disptachParams[11] = Mention.joinTypes(textMessage.mentions);
            disptachParams[12] = textMessage.replyMessageId;
            try {
                XposedBridge.invokeOriginalMethod(disptach, null, disptachParams);
            } catch (Throwable t) {
                Logger.error(t);
            }
        }, delay);
    }

    public void reactToMessage(final String reaction, final String messageId, final long threadKey, final int delay) {
        final Class<?> MailboxCoreJNI = XposedHelpers.findClass(OrcaClassNames.MAILBOX_SDK_JNI, classLoader);
        final Set<Method> disptachList = XposedHilfer.findAllMethods(MailboxCoreJNI, "dispatchVJOOOOOOOO");
        if (disptachList.size() != 1)
            Logger.error(new RuntimeException("dispatchList size (" + disptachList.size() + ") != 1"));
        final Method disptach = disptachList.iterator().next();

        preDispatch(notificationScope -> {
            long time = System.currentTimeMillis();
            Object[] disptachParams = new Object[] {
                    48, threadKey, mailbox.get(), reaction, messageId, time, null, null, null, notificationScope
            };

            try {
                XposedBridge.invokeOriginalMethod(disptach, null, disptachParams);
            } catch (Throwable t) {
                Logger.error(t);
            }
        }, delay);
    }
    public void sendSticker(final long stickerId, final long threadKey, final int delay) {
        sendSticker(stickerId, threadKey, delay, null);
    }

    public void sendSticker(final long stickerId, final long threadKey, final int delay, final String replyId) {
        Logger.info("Sending sticker " + stickerId + "!");

        final Class<?> MailboxCoreJNI = XposedHelpers.findClass(OrcaClassNames.MAILBOX_CORE_JNI, classLoader);
        final Set<Method> disptachList = XposedHilfer.findAllMethods(MailboxCoreJNI, "dispatchVIIIJJOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOZ");
        if (disptachList.size() != 1)
            Logger.error(new RuntimeException("dispatchList size (" + disptachList.size() + ") != 1"));
        final Method disptach = disptachList.iterator().next();

        preDispatch(notificationScope -> {
            long time = System.currentTimeMillis() * 1000;
            try {
                XposedBridge.invokeOriginalMethod(disptach, null, new Object[] {
                        12, 0, 0, 65540, threadKey, stickerId, mailbox.get(), "", 1, null, null, null, null, null, null, "", null, null, "", null, null, null, null, "You sent a sticker.", null, null, null, null, null, null, replyId, replyId != null? 1: 0, null, null, time, null, null, null, null, null, notificationScope, true
                });
            } catch (Throwable t) {
                Logger.error(t);
            }
        }, delay);
    }

    public void sendMedia(MediaMessage message, final long threadKey, final int delay) {
        // TODO use proper method for multiple files

        for (MediaAttachment attachment: message.mediaAttachments) {
            sendAttachment(attachment, threadKey, delay, message.replyMessageId);
        }
    }


    public void sendAttachment(MediaAttachment attachment, final long threadKey, final int delay) {
        sendAttachment(attachment, threadKey, delay, null);
    }

    public void sendAttachment(MediaAttachment attachment, final long threadKey, final int delay, final String replyId) {
        Logger.info("sendAttachment: file=" + attachment.path + " fileName=" + attachment.fileName + " threadKey=" + threadKey);

        // Try calling 7Da.A0Q (sendFileAttachmentMessage) directly
        Object sdk7Da = captured7Da.get();
        if (sdk7Da != null) {
            try {
                String threadKeyEncoded = "T_" + Base64.encodeToString(
                        ("MESSENGER:fbid:" + threadKey).getBytes(), Base64.NO_WRAP);

                // Find A0Q method on the 7Da instance
                Method a0qMethod = null;
                for (Method m : sdk7Da.getClass().getDeclaredMethods()) {
                    if (m.getName().equals("A0Q")) {
                        a0qMethod = m;
                        break;
                    }
                }

                if (a0qMethod != null) {
                    final Method sendFile = a0qMethod;
                    sendFile.setAccessible(true);

                    // A0Q params: (FileOptParams, LoggingOption, Number, String, String, String, String, String)
                    // 5 Strings: threadKeyEncoded, filePath, fileName, mimeType, caption
                    String mimeType = attachment.fileName != null && attachment.fileName.endsWith(".jpg")
                            ? "image/jpeg" : "application/octet-stream";
                    if (attachment.fileName != null) {
                        String lower = attachment.fileName.toLowerCase();
                        if (lower.endsWith(".png")) mimeType = "image/png";
                        else if (lower.endsWith(".gif")) mimeType = "image/gif";
                        else if (lower.endsWith(".mp4")) mimeType = "video/mp4";
                        else if (lower.endsWith(".webp")) mimeType = "image/webp";
                    }

                    final String finalMimeType = mimeType;
                    final String filePath = attachment.path.getAbsolutePath();

                    Logger.info("sendAttachment: calling 7Da.A0Q with threadKey=" + threadKeyEncoded
                            + " path=" + filePath + " name=" + attachment.fileName + " mime=" + finalMimeType);

                    // Call A0Q directly - it's a high-level coroutine method that handles its own threading
                    // Do NOT wrap in preDispatch/nativeScheduleTask as that conflicts with internal coroutines
                    final String fName = attachment.fileName;
                    new Thread(() -> {
                        try {
                            Object result = sendFile.invoke(sdk7Da, null, null, null,
                                    threadKeyEncoded, filePath, fName, finalMimeType, null);
                            Logger.info("sendAttachment: 7Da.A0Q returned: " + result);
                        } catch (Throwable t) {
                            Logger.error("sendAttachment: 7Da.A0Q failed: " + t.getMessage());
                            Logger.error(t);
                            if (t.getCause() != null) {
                                Logger.error("sendAttachment: A0Q cause: " + t.getCause().getMessage());
                                Logger.error(t.getCause());
                            }
                        }
                    }).start();
                    return;
                } else {
                    Logger.error("sendAttachment: A0Q method not found on 7Da");
                }
            } catch (Throwable t) {
                Logger.error("sendAttachment: 7Da approach failed: " + t.getMessage());
                Logger.error(t);
            }
        } else {
            Logger.error("sendAttachment: No 7Da instance captured yet. Try sending a text message first to initialize.");
        }

        // Fallback: try old CoreJNI dispatch
        try {
            AttachmentBuilder builder = new AttachmentBuilder(classLoader);
            builder.setFile(attachment.path);
            builder.setFileName(attachment.fileName);
            if (attachment.type != AttachmentBuilder.FILETYPE_UNKNOWN) {
                builder.setType(attachment.type);
            }
            final Object attachmentObj = builder.build();
            if (attachmentObj == null) {
                Logger.error("sendAttachment: failed to build Attachment object for fallback");
                return;
            }

            final Class<?> MailboxCoreJNI = XposedHelpers.findClass(OrcaClassNames.MAILBOX_CORE_JNI, classLoader);
            final Set<Method> dispatchList = XposedHilfer.findAllMethods(MailboxCoreJNI, MessageSentHook.DISPATCH_METHOD);
            if (dispatchList.size() == 1) {
                final Method dispatch = dispatchList.iterator().next();
                Logger.info("sendAttachment: using old CoreJNI dispatch as fallback");

                preDispatch(notificationScope -> {
                    long time = System.currentTimeMillis() * 1000;
                    try {
                        Object[] dispatchParams = new Object[] {
                                10, 65540, threadKey, mailbox.get(), "", 1,
                                null, attachmentObj, null, null, null, null,
                                replyId, replyId != null ? 1 : 0,
                                0, null, null, null, time,
                                null, null, null, null, null, null, false, null, notificationScope
                        };
                        XposedBridge.invokeOriginalMethod(dispatch, null, dispatchParams);
                        Logger.info("sendAttachment: old dispatch succeeded");
                    } catch (Throwable t) {
                        Logger.error("sendAttachment: old dispatch failed: " + t.getMessage());
                        Logger.error(t);
                    }
                }, delay);
                return;
            }
        } catch (Throwable t) {
            Logger.error("sendAttachment: CoreJNI fallback not available: " + t.getMessage());
        }

        Logger.error("sendAttachment: no suitable send method found");
    }

    private void executeAsync(Runnable runnable) {
        final Class<?> Execution = XposedHelpers.findClass(OrcaClassNames.MCI_EXECUTION, classLoader);

        // Try new signature first (with AccountSession), fall back to old
        Method nativeScheduleTask = null;
        boolean hasAccountSession = false;
        try {
            Class<?> accountSession = Class.forName("com.facebook.msys.mci.AccountSession", false, classLoader);
            nativeScheduleTask = XposedHelpers.findMethodExact(Execution, "nativeScheduleTask",
                    Runnable.class, accountSession, int.class, int.class, double.class, String.class);
            hasAccountSession = true;
        } catch (Throwable ignored) {}

        if (nativeScheduleTask == null) {
            try {
                nativeScheduleTask = XposedHelpers.findMethodExact(Execution, "nativeScheduleTask",
                        Runnable.class, int.class, int.class, double.class, String.class);
            } catch (Throwable ignored) {}
        }

        if (nativeScheduleTask == null) {
            Logger.error("nativeScheduleTask not found with any known signature");
            return;
        }

        try {
            if (hasAccountSession) {
                nativeScheduleTask.invoke(null, runnable, null, 1, 0, 0 / 1000.0d, "MPro2Thread");
            } else {
                nativeScheduleTask.invoke(null, runnable, 1, 0, 0 / 1000.0d, "MPro2Thread");
            }
        } catch (Throwable t) {
            Logger.error(t);
        }
    }

    private void preDispatch(Consumer<Object> dispatchExecutor, final int delay) {
        final Class<?> NotificationScope = XposedHelpers.findClass(OrcaClassNames.NOTIFICATION_SCOPE, classLoader);

        new Thread(() -> {
            try {
                Logger.info("Sending message in " + delay + " milliseconds...");
                Thread.sleep(delay);
                executeAsync(() -> {
                    try {
                        Logger.info("Inside async");

                        final Object notificationScope = XposedHelpers.findConstructorExact(NotificationScope).newInstance();
                        dispatchExecutor.accept(notificationScope);
                    } catch (Throwable t) {
                        Logger.error(t);
                    }
                });
            } catch (Throwable t) {
                Logger.error(t);
            }
        }).start();
    }
}
