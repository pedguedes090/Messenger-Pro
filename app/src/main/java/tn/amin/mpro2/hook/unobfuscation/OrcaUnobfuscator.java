package tn.amin.mpro2.hook.unobfuscation;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;

import org.apache.commons.lang3.math.NumberUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.MultiDexContainer;
import org.jf.dexlib2.iface.instruction.FiveRegisterInstruction;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.instruction.RegisterRangeInstruction;
import org.jf.dexlib2.iface.instruction.TwoRegisterInstruction;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.iface.reference.StringReference;
import org.jf.dexlib2.immutable.reference.ImmutableFieldReference;

import io.github.neonorbit.dexplore.DexFactory;
import io.github.neonorbit.dexplore.Dexplore;
import io.github.neonorbit.dexplore.filter.ClassFilter;
import io.github.neonorbit.dexplore.filter.DexFilter;
import io.github.neonorbit.dexplore.filter.MethodFilter;
import io.github.neonorbit.dexplore.filter.ReferenceTypes;
import io.github.neonorbit.dexplore.reference.FieldRefData;
import io.github.neonorbit.dexplore.result.ClassData;
import io.github.neonorbit.dexplore.result.FieldData;
import io.github.neonorbit.dexplore.result.MethodData;
import io.github.neonorbit.dexplore.util.DexLog;
import tn.amin.mpro2.constants.OrcaClassNames;
import tn.amin.mpro2.debug.Logger;
import tn.amin.mpro2.file.StorageConstants;

public class OrcaUnobfuscator {
    private Dexplore mDexplore;
    private String mPath;
    private ClassLoader mClassLoader;
    private boolean mSearchAgain = true;

    private SharedPreferences.Editor mEditor = null;
    private SharedPreferences mPref = null;

    private final HashMap<String, Class<?>> mUnobfuscatedClasses = new HashMap<>();
    private final HashMap<String, Method> mUnobfuscatedMethods = new HashMap<>();
    private final HashMap<String, Field> mUnobfuscatedFields = new HashMap<>();
    private final HashMap<String, Integer> mUnobfuscatedApis = new HashMap<>();

    public static final String CLASS_TYPING_INDICATOR_DISPATCHER = "TypingIndicatorDispatcher";
    public static final String METHOD_MESSAGE_GETTEXT = "Message/getText";
    public static final String METHOD_MESSAGES_DECODER_DECODE = "MessagesDecoder/decode";
    public static final String FIELD_MESSAGE_THREADKEY = "Message-threadKey";
    public static final String FIELD_MESSAGE_ID = "Message-id";
    public static final String FIELD_MESSAGE_TEXT = "Message-text";
    public static final String FIELD_MESSAGE_PARTICIPANT_INFO = "Message-participantInfo";
    public static final String FIELD_SECRET_STRING_CONTENT = "SecretString-content";
    public static final String FIELD_SECRET_STRING_STARS = "SecretString-stars";
    public static final String FIELD_MESSAGES_COLLECTION_MESSAGES = "MessagesCollection-messages";
    public static final String FIELD_PARTICIPANT_INFO_USER_KEY = "ParticipantInfo-userKey";
    public static final String API_NOTIFICATION = "API_NOTIFICATION";
    public static final String API_CONVERSATION_ENTER = "API_CONVERSATION_ENTER";
    public static final String API_CONVERSATION_LEAVE = "API_CONVERSATION_LEAVE";
    public static final String API_MESSAGE_SEEN = "API_MESSAGE_SEEN";

    public OrcaUnobfuscator(Context context, String path, ClassLoader classLoader, boolean searchAgain) {
        mClassLoader = classLoader;
        mPath = path;
        mSearchAgain = searchAgain;
        mPref = context.getSharedPreferences(StorageConstants.unobfPrefName, Context.MODE_PRIVATE);
        mEditor = mPref.edit();

        if (mSearchAgain) {
            Logger.verbose("Creating Dexplore instance");
            DexLog.enable();
            mDexplore = DexFactory.load(mPath);
        }

        reloadAllInternal();

        Logger.info("Successfully unobfuscated all components !");
        // TODO use QueryBatch
    }

    public Class<?> loadNotificationEngine() {
        return loadClass("NotificationEngine", new ClassFilter.Builder()
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool ->
                        pool.contains("[Notification engine internal error] it missed one of mandatory fields")
                ).build());
    }

    public Class<?> loadTypingIndicatorDispatcher() {
        return loadClass(CLASS_TYPING_INDICATOR_DISPATCHER, new ClassFilter.Builder()
                .setReferenceTypes(ReferenceTypes.builder().addString().addMethodWithDetails().build())
                .setReferenceFilter(pool ->
                        pool.stringsContain("/t_st") &&
                                pool.methodSignaturesContain("java.lang.System.arraycopy(java.lang.Object,int,java.lang.Object,int,int):void"))
                .build());
    }

    private Method loadMessagesDecoderDecode() {
        ClassFilter.Builder classFilterBuilder = new ClassFilter.Builder();
        if (OrcaClassNames.MESSAGES_DECODER != null) {
            classFilterBuilder.setClasses(OrcaClassNames.MESSAGES_DECODER);
        } else {
            // Class removed in newer Messenger, search broadly by method string references
            classFilterBuilder
                    .setReferenceTypes(ReferenceTypes.builder().addString().build())
                    .setReferenceFilter(pool ->
                            pool.stringsContain("Magic words offsets") ||
                            pool.stringsContain("typing_indicator:"));
        }
        return loadMethod(METHOD_MESSAGES_DECODER_DECODE,
                classFilterBuilder.build(),
                new MethodFilter.Builder()
                        .setReferenceTypes(ReferenceTypes.builder().addString().build())
                        .setReferenceFilter(pool -> pool.contains("messageStreamingState") ||
                                pool.contains("Magic words offsets %s and length %s mismatch.") ||
                                pool.contains("typing_indicator:"))
                        .build());
    }

    public Method loadMessageMethod(String attributeName) {
        return loadMethod("Message/get" + Character.toUpperCase(attributeName.charAt(0)) + attributeName.substring(1),
                new ClassFilter.Builder().setClasses(OrcaClassNames.MESSAGE).build(),
                new MethodFilter.Builder()
                        .setReferenceTypes(ReferenceTypes.builder().addString().build())
                        .setReferenceFilter(pool -> pool.contains(attributeName))
                        .build());
    }

    public Field loadMessageFieldThreadKey() {
        return loadField(FIELD_MESSAGE_THREADKEY, () -> {
            ClassData NewMessageNotification = mDexplore.findClass(
                    DexFilter.MATCH_ALL, ClassFilter.ofClass(OrcaClassNames.NEW_MESSAGE_NOTIFICATION));
            if (NewMessageNotification == null) {
                Logger.error("NewMessageNotification is null");
                return null;
            }

            List<FieldRefData> refData = NewMessageNotification.getReferencePool().getFieldSection().stream()
                    .filter(fieldRefData ->
                            fieldRefData.getDeclaringClass().equals(OrcaClassNames.MESSAGE) &&
                                    fieldRefData.getType().equals(OrcaClassNames.THREAD_KEY))
                    .collect(Collectors.toList());
            if (refData.size() == 0) {
                Logger.error("Could not find Message.threadKey");
                return null;
            }

            return refData.get(0);
        });
    }

    private Field loadMessageFieldText() {
        return loadField(FIELD_MESSAGE_TEXT, () -> {
            MethodData writeToParcel = mDexplore.findMethod(
                    DexFilter.MATCH_ALL,
                    ClassFilter.ofClass(OrcaClassNames.MESSAGE),
                    MethodFilter.ofMethod("writeToParcel", Arrays.asList(Parcel.class.getName(), "int")));

            if (writeToParcel == null) {
                Logger.error("Could not get ReferencePool for method Message.writeToParcel");
                return null;
            }

            List<FieldRefData> refDataWriteToParcel = writeToParcel.getReferencePool().getFieldSection();

            Method getTextMethod = mUnobfuscatedMethods.get(METHOD_MESSAGE_GETTEXT);
            if (getTextMethod == null) {
                Logger.error("Method.getText cannot be null, skipping field text");
                return null;
            }

            MethodData getText = mDexplore.findMethod(
                    DexFilter.MATCH_ALL,
                    ClassFilter.ofClass(OrcaClassNames.MESSAGE),
                    MethodFilter.ofMethod(getTextMethod.getName()));

            if (getText == null) {
                Logger.error("Could not get ReferencePool for method Message.getText");
                return null;
            }

            List<FieldRefData> refDataGetText = getText.getReferencePool().getFieldSection().stream()
                    .filter(fieldRefData -> {
                        return fieldRefData.getDeclaringClass().equals(OrcaClassNames.MESSAGE) &&
                                fieldRefData.getType().equals(OrcaClassNames.SECRET_STRING) &&
                                refDataWriteToParcel.contains(fieldRefData);
                    })
                    .collect(Collectors.toList());

            if (refDataGetText.size() != 1) {
                Logger.error("Expected to find 1 SecretString in Message.getText, got " + refDataGetText.size());
                return null;
            }

            return refDataGetText.get(0);
        });
    }

    private Field loadMessageParticipantInfo() {
        return loadField(FIELD_MESSAGE_PARTICIPANT_INFO, () -> {
            ClassData MessageRepliedTo = mDexplore.findClass(DexFilter.MATCH_ALL,
                    ClassFilter.ofClass(OrcaClassNames.MESSAGE_REPLIED_TO));

            if (MessageRepliedTo == null) {
                Logger.error("Unable to load class MessageRepliedTo");
                return null;
            }

            List<FieldRefData> refDataList = MessageRepliedTo.getReferencePool().getFieldSection().stream()
                    .filter(fieldRefData -> fieldRefData.getDeclaringClass().equals(OrcaClassNames.MESSAGE) &&
                            fieldRefData.getType().equals(OrcaClassNames.PARTICIPANT_INFO))
                    .collect(Collectors.toList());

            if (refDataList.size() == 0) {
                Logger.error("Expected to find at least 1 ParticipantInfo in MessageRepliedTo, got " + refDataList.size());
                return null;
            }

            return refDataList.get(0);
        });
    }

    private Field loadSecretStringFieldStars() {
        return loadField(FIELD_SECRET_STRING_STARS, () -> {
            MethodData SecretStringToString = mDexplore.findMethod(
                    DexFilter.MATCH_ALL,
                    ClassFilter.ofClass(OrcaClassNames.SECRET_STRING),
                    MethodFilter.ofMethod("toString"));

            if (SecretStringToString == null) {
                Logger.error("Failed to find method SecretString.toString");
                return null;
            }

            List<FieldRefData> refData = SecretStringToString.getReferencePool().getFieldSection().stream()
                    .filter(fieldRefData ->
                            fieldRefData.getDeclaringClass().equals(OrcaClassNames.SECRET_STRING) &&
                                    fieldRefData.getType().equals(String.class.getName()))
                    .collect(Collectors.toList());

            if (refData.size() != 1) {
                Logger.error("Expected to find 1 String in SecretString.toString, got " + refData.size());
                return null;
            }

            return refData.get(0);
        });
    }

    private Field loadSecretStringFieldContent() {
        return loadField(FIELD_SECRET_STRING_CONTENT, () -> {
            Field secretStringStars = mUnobfuscatedFields.get(FIELD_SECRET_STRING_STARS);
            if (secretStringStars == null) {
                Logger.error(FIELD_SECRET_STRING_STARS + " is null, skipping " + FIELD_SECRET_STRING_CONTENT);
            }

            ClassData SecretString = mDexplore.findClass(
                    DexFilter.MATCH_ALL,
                    ClassFilter.ofClass(OrcaClassNames.SECRET_STRING));

            if (SecretString == null) {
                Logger.error("Failed to find class SecretString");
                return null;
            }

            List<FieldRefData> refData = SecretString.getReferencePool().getFieldSection().stream()
                    .filter(fieldRefData -> fieldRefData.getDeclaringClass().equals(OrcaClassNames.SECRET_STRING) &&
                            fieldRefData.getType().equals(String.class.getName()) &&
                            !fieldRefData.getName().equals(secretStringStars.getName()))
                    .collect(Collectors.toList());

            if (refData.size() == 0) {
                Logger.error("Expected to find at least 1 String in SecretString, got " + refData.size());
                return null;
            }

            return refData.get(0);
        });
    }

    private Field loadMessagesCollectionMessages() {
        return loadField(FIELD_MESSAGES_COLLECTION_MESSAGES, () -> {
            ClassData MessagesCollection = mDexplore.findClass(
                    DexFilter.MATCH_ALL,
                    ClassFilter.ofClass(OrcaClassNames.MESSAGES_COLLECTION));

            if (MessagesCollection == null) {
                Logger.error("Failed to find class MessagesCollection");
                return null;
            }

            List<FieldRefData> refData = MessagesCollection.getReferencePool().getFieldSection().stream()
                    .filter(fieldRefData -> fieldRefData.getDeclaringClass().equals(OrcaClassNames.MESSAGES_COLLECTION) &&
                            fieldRefData.getType().contains("ImmutableList"))
                    .collect(Collectors.toList());

            if (refData.size() == 0) {
                Logger.error("Expected to find at least 1 ImmutableList in MessagesCollection, got " + refData.size());
                return null;
            }

            return refData.get(0);
        });
    }

    private Field loadParticipantInfoUserKey() {
        return loadField(FIELD_PARTICIPANT_INFO_USER_KEY, () -> {
            ClassData ParticipantInfo = mDexplore.findClass(
                    DexFilter.MATCH_ALL,
                    ClassFilter.ofClass(OrcaClassNames.PARTICIPANT_INFO));

            if (ParticipantInfo == null) {
                Logger.error("Failed to find class ParticipantInfo");
                return null;
            }

            List<FieldRefData> refData = ParticipantInfo.getReferencePool().getFieldSection().stream()
                    .filter(fieldRefData -> fieldRefData.getDeclaringClass().equals(OrcaClassNames.PARTICIPANT_INFO) &&
                            fieldRefData.getType().equals(OrcaClassNames.USER_KEY))
                    .collect(Collectors.toList());

            if (refData.size() == 0) {
                Logger.error("Expected to find at least 1 UserKey in ParticipantInfo, got " + refData.size());
                return null;
            }

            return refData.get(0);
        });
    }

    private int loadAPINotification() {
        return NumberUtils.toInt(mPref.getString(API_NOTIFICATION, null), -1);
    }

    private int loadAPIConversationEnter() {
        if (!mPref.contains(API_CONVERSATION_ENTER))
            mPref.edit().putString(API_CONVERSATION_ENTER, "6").apply();
        return Integer.parseInt(mPref.getString(API_CONVERSATION_ENTER, "6"));
    }

    private int loadAPIConversationLeave() {
        if (!mPref.contains(API_CONVERSATION_LEAVE))
            mPref.edit().putString(API_CONVERSATION_LEAVE, "7").apply();
        return Integer.parseInt(mPref.getString(API_CONVERSATION_LEAVE, "7"));
    }

    private int loadAPIMessageSeen() {
        return NumberUtils.toInt(mPref.getString(API_MESSAGE_SEEN, null), -1);
    }

    public Field loadMessageFieldId() {
        return loadField(FIELD_MESSAGE_ID, () -> {
            FieldRefData wireField = findMessageIdFieldFromDatabaseWireKey();
            if (wireField != null) {
                Logger.info("Resolved Message.id through msg_id database serialization");
                return wireField;
            }

            Logger.warn("Message.id serializer fingerprint unavailable; trying legacy cache reference");
            ClassData MsysThreadsCache = mDexplore.findClass(DexFilter.MATCH_ALL,
                    ClassFilter.builder()
                            .setReferenceTypes(ReferenceTypes.builder().addString().build())
                            .setReferenceFilter(pool -> pool.stringsContain("MsysThreadsCache"))
                            .build());
            if (MsysThreadsCache == null) {
                Logger.error("MsysThreadsCache is null");
                return null;
            }

            List<FieldRefData> refData = MsysThreadsCache.getReferencePool().getFieldSection().stream()
                    .filter(fieldRefData ->
                            fieldRefData.getDeclaringClass().equals(OrcaClassNames.MESSAGE) &&
                                    fieldRefData.getType().equals(String.class.getName()))
                    .collect(Collectors.toList());

            if (refData.size() == 0) {
                Logger.error("Could not find Message.id");
                return null;
            }

            return refData.get(0);
        });
    }

    private FieldRefData findMessageIdFieldFromDatabaseWireKey() {
        final String messageDescriptor = "L" + OrcaClassNames.MESSAGE.replace('.', '/') + ";";
        FieldReference resolved = null;
        try {
            MultiDexContainer<? extends DexBackedDexFile> container =
                    DexFileFactory.loadDexContainer(new java.io.File(mPath), Opcodes.getDefault());
            for (String dexName : container.getDexEntryNames()) {
                MultiDexContainer.DexEntry<? extends DexBackedDexFile> entry = container.getEntry(dexName);
                if (entry == null) continue;
                for (ClassDef classDef : entry.getDexFile().getClasses()) {
                    for (org.jf.dexlib2.iface.Method method : classDef.getMethods()) {
                        if (!method.getReturnType().equals("V") || method.getParameterTypes().isEmpty()
                                || !method.getParameterTypes().get(method.getParameterTypes().size() - 1)
                                .equals(messageDescriptor)
                                || !methodContainsString(method, "DbInsertThreadsHandler.updateMessagesTablesInternal")
                                || !methodContainsString(method, "msg_id")) {
                            continue;
                        }

                        FieldReference candidate = findFieldWrittenToMsgIdColumn(method, messageDescriptor);
                        if (candidate == null) continue;
                        if (resolved != null && (!resolved.getDefiningClass().equals(candidate.getDefiningClass())
                                || !resolved.getName().equals(candidate.getName())
                                || !resolved.getType().equals(candidate.getType()))) {
                            Logger.warn("Message.id serializer fingerprint is ambiguous");
                            return null;
                        }
                        resolved = candidate;
                    }
                }
            }
        } catch (Exception e) {
            Logger.warn("Message.id DEX scan failed: " + e.getClass().getSimpleName());
            return null;
        }

        return resolved == null ? null : FieldRefData.build(
                new ImmutableFieldReference(resolved.getDefiningClass(), resolved.getName(), resolved.getType()), true);
    }

    private boolean methodContainsString(org.jf.dexlib2.iface.Method method, String value) {
        if (method.getImplementation() == null) return false;
        for (Instruction instruction : method.getImplementation().getInstructions()) {
            if (!(instruction instanceof ReferenceInstruction)) continue;
            Reference reference = ((ReferenceInstruction) instruction).getReference();
            if (reference instanceof StringReference
                    && ((StringReference) reference).getString().equals(value)) return true;
        }
        return false;
    }

    private FieldReference findFieldWrittenToMsgIdColumn(org.jf.dexlib2.iface.Method method,
                                                          String messageDescriptor) {
        List<Instruction> instructions = new java.util.ArrayList<>();
        for (Instruction instruction : method.getImplementation().getInstructions()) instructions.add(instruction);

        for (int i = 0; i < instructions.size(); i++) {
            Instruction marker = instructions.get(i);
            if (!(marker instanceof ReferenceInstruction) || !(marker instanceof OneRegisterInstruction)) continue;
            Reference markerReference = ((ReferenceInstruction) marker).getReference();
            if (!(markerReference instanceof StringReference)
                    || !((StringReference) markerReference).getString().equals("msg_id")) continue;

            Set<Integer> keyRegisters = new HashSet<>();
            Set<Integer> valueRegisters = new HashSet<>();
            keyRegisters.add(((OneRegisterInstruction) marker).getRegisterA());
            FieldReference candidateField = null;
            int limit = Math.min(instructions.size(), i + 14);

            for (int j = i + 1; j < limit; j++) {
                Instruction instruction = instructions.get(j);
                String opcode = instruction.getOpcode().name();
                if (instruction instanceof TwoRegisterInstruction && opcode.startsWith("MOVE_OBJECT")) {
                    TwoRegisterInstruction move = (TwoRegisterInstruction) instruction;
                    if (keyRegisters.contains(move.getRegisterB())) keyRegisters.add(move.getRegisterA());
                    if (valueRegisters.contains(move.getRegisterB())) valueRegisters.add(move.getRegisterA());
                }

                if (instruction instanceof ReferenceInstruction && opcode.startsWith("IGET_OBJECT")
                        && instruction instanceof TwoRegisterInstruction) {
                    Reference reference = ((ReferenceInstruction) instruction).getReference();
                    if (reference instanceof FieldReference) {
                        FieldReference field = (FieldReference) reference;
                        if (field.getDefiningClass().equals(messageDescriptor)
                                && field.getType().equals("Ljava/lang/String;")) {
                            candidateField = field;
                            valueRegisters.clear();
                            valueRegisters.add(((TwoRegisterInstruction) instruction).getRegisterA());
                        }
                    }
                }

                if (!(instruction instanceof ReferenceInstruction) || !opcode.startsWith("INVOKE")) continue;
                Reference reference = ((ReferenceInstruction) instruction).getReference();
                if (!(reference instanceof MethodReference)) continue;
                MethodReference called = (MethodReference) reference;
                if (!called.getDefiningClass().equals("Landroid/content/ContentValues;")
                        || !called.getName().equals("put") || !called.getReturnType().equals("V")
                        || !called.getParameterTypes().equals(Arrays.asList(
                                "Ljava/lang/String;", "Ljava/lang/String;"))) continue;

                int keyRegister = -1;
                int valueRegister = -1;
                if (instruction instanceof FiveRegisterInstruction
                        && ((FiveRegisterInstruction) instruction).getRegisterCount() >= 3) {
                    FiveRegisterInstruction invoke = (FiveRegisterInstruction) instruction;
                    keyRegister = invoke.getRegisterD();
                    valueRegister = invoke.getRegisterE();
                } else if (instruction instanceof RegisterRangeInstruction
                        && ((RegisterRangeInstruction) instruction).getRegisterCount() >= 3) {
                    int start = ((RegisterRangeInstruction) instruction).getStartRegister();
                    keyRegister = start + 1;
                    valueRegister = start + 2;
                }
                if (candidateField != null && keyRegisters.contains(keyRegister)
                        && valueRegisters.contains(valueRegister)) return candidateField;
            }
        }
        return null;
    }

    private Class<?> loadClass(String simpleName, ClassFilter classFilter) {
        ClassData classData;
        if (mSearchAgain) {
            classData = mDexplore.findClass(DexFilter.MATCH_ALL, classFilter);

            if (classData == null)
                return null;

            mEditor.putString(simpleName, classData.serialize());
        } else {
            String raw = mPref.getString(simpleName, null);

            if (raw == null)
                return null;

            classData = ClassData.deserialize(raw);
        }

        return classData.loadClass(mClassLoader);
    }

    private Method loadMethod(String simpleName, ClassFilter classFilter, MethodFilter methodFilter) {
        MethodData methodData;
        if (mSearchAgain) {
            methodData = mDexplore.findMethod(DexFilter.MATCH_ALL, classFilter, methodFilter);

            if (methodData == null)
                return null;

            mEditor.putString(simpleName, methodData.serialize());
        } else {
            String raw = mPref.getString(simpleName, null);

            if (raw == null)
                return null;

            methodData = MethodData.deserialize(raw);
        }

        return methodData.loadMethod(mClassLoader);
    }

    private Field loadField(String simpleName, Supplier<FieldRefData> supplier) {
        FieldData fieldData;
        if (mSearchAgain) {
            FieldRefData refData = supplier.get();

            if (refData == null)
                return null;

            String serialized = "f:" + refData.getDeclaringClass() + ":" + refData.getName() + ":" + refData.getType();
            mEditor.putString(simpleName, serialized);
            fieldData = FieldData.deserialize(serialized);
        } else {
            String raw = mPref.getString(simpleName, null);

            if (raw == null)
                return null;

            fieldData = FieldData.deserialize(raw);
        }

        return fieldData.loadField(mClassLoader);
    }

    public void save() {
        if (mEditor != null) mEditor.apply();
    }

    public Class<?> getClass(String name) {
        return mUnobfuscatedClasses.get(name);
    }

    public Method getMethod(String name) {
        return mUnobfuscatedMethods.get(name);
    }

    public Field getField(String name) {
        return mUnobfuscatedFields.get(name);
    }

    public Integer getAPICode(String name) {
        return mUnobfuscatedApis.get(name);
    }

    public SharedPreferences getPreferences() {
        return mPref;
    }

    public void reloadAll() {
        mSearchAgain = false;

        reloadAllInternal();
    }

    private void reloadAllInternal() {
        Logger.verbose("Loading class " + CLASS_TYPING_INDICATOR_DISPATCHER);
        mUnobfuscatedClasses.put(CLASS_TYPING_INDICATOR_DISPATCHER, loadTypingIndicatorDispatcher());

        Logger.verbose("Loading method " + METHOD_MESSAGE_GETTEXT);
        mUnobfuscatedMethods.put(METHOD_MESSAGE_GETTEXT, loadMessageMethod("text"));
        Logger.verbose("Loading method " + METHOD_MESSAGES_DECODER_DECODE);
        mUnobfuscatedMethods.put(METHOD_MESSAGES_DECODER_DECODE, loadMessagesDecoderDecode());

        Logger.verbose("Loading field " + FIELD_MESSAGE_THREADKEY);
        mUnobfuscatedFields.put(FIELD_MESSAGE_THREADKEY, loadMessageFieldThreadKey());
        Logger.verbose("Loading field " + FIELD_MESSAGE_ID);
        mUnobfuscatedFields.put(FIELD_MESSAGE_ID, loadMessageFieldId());
        Logger.verbose("Loading field " + FIELD_MESSAGE_TEXT);
        mUnobfuscatedFields.put(FIELD_MESSAGE_TEXT, loadMessageFieldText());
        Logger.verbose("Loading field " + FIELD_MESSAGE_PARTICIPANT_INFO);
        mUnobfuscatedFields.put(FIELD_MESSAGE_PARTICIPANT_INFO, loadMessageParticipantInfo());
        Logger.verbose("Loading field " + FIELD_SECRET_STRING_STARS);
        mUnobfuscatedFields.put(FIELD_SECRET_STRING_STARS, loadSecretStringFieldStars());
        Logger.verbose("Loading field " + FIELD_SECRET_STRING_CONTENT);
        mUnobfuscatedFields.put(FIELD_SECRET_STRING_CONTENT, loadSecretStringFieldContent());
        Logger.verbose("Loading field " + FIELD_MESSAGES_COLLECTION_MESSAGES);
        mUnobfuscatedFields.put(FIELD_MESSAGES_COLLECTION_MESSAGES, loadMessagesCollectionMessages());
        Logger.verbose("Loading field " + FIELD_PARTICIPANT_INFO_USER_KEY);
        mUnobfuscatedFields.put(FIELD_PARTICIPANT_INFO_USER_KEY, loadParticipantInfoUserKey());

        Logger.verbose("Loading api " + API_NOTIFICATION);
        mUnobfuscatedApis.put(API_NOTIFICATION, loadAPINotification());
        Logger.verbose("Loading api " + API_CONVERSATION_ENTER);
        mUnobfuscatedApis.put(API_CONVERSATION_ENTER, loadAPIConversationEnter());
        Logger.verbose("Loading api " + API_CONVERSATION_LEAVE);
        mUnobfuscatedApis.put(API_CONVERSATION_LEAVE, loadAPIConversationLeave());
        Logger.verbose("Loading api " + API_MESSAGE_SEEN);
        mUnobfuscatedApis.put(API_MESSAGE_SEEN, loadAPIMessageSeen());
    }
}
