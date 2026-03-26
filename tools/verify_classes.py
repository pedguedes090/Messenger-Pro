import os

# All hardcoded class names from OrcaClassNames.java and OrcaInfo.java
classes_to_find = {
    'SECRET_STRING': 'com.facebook.secure.secrettypes.SecretString',
    'NOTIFICATION_SCOPE': 'com.facebook.msys.util.NotificationScope',
    'MCI_EXECUTION': 'com.facebook.msys.mci.Execution',
    'ATTACHMENT': 'com.facebook.msys.mci.Attachment',
    'MAILBOX': 'com.facebook.msys.mca.Mailbox',
    'MAILBOX_CONFIG': 'com.facebook.msys.mca.MailboxConfig',
    'MESSAGE': 'com.facebook.messaging.model.messages.Message',
    'THREAD_KEY': 'com.facebook.messaging.model.threadkey.ThreadKey',
    'USER_KEY': 'com.facebook.user.model.UserKey',
    'PARTICIPANT_INFO': 'com.facebook.messaging.model.messages.ParticipantInfo',
    'MESSAGE_REPLIED_TO': 'com.facebook.messaging.model.messages.MessageRepliedTo',
    'NEW_MESSAGE_NOTIFICATION': 'com.facebook.messaging.notify.type.NewMessageNotification',
    'MESSAGES_DECODER': 'com.facebook.messaging.msys.common.translator.AbstractMsysMessagesCollectionTranslator',
    'MESSAGES_COLLECTION': 'com.facebook.messaging.model.messages.MessagesCollection',
    'THREAD_THEME_INFO': 'com.facebook.messaging.customthreads.model.ThreadThemeInfo',
    'MAILBOX_SDK_JNI': 'com.facebook.sdk.mca.MailboxSDKJNI',
    'MAILBOX_CORE_JNI': 'com.facebook.core.mca.MailboxCoreJNI',
    'CQL_RESULT_SET': 'com.facebook.msys.mci.CQLResultSet',
    'ORCA_MAIN_ACTIVITY': 'com.facebook.messenger.neue.MainActivity',
    'ORCA_APPLICATION': 'com.facebook.messenger.app.MessengerApplication',
    'ORCA_RECEIVER': 'com.facebook.messaging.livelocation.bindings.MessengerLiveLocationBooter',
    'ACCOUNT_SESSION': 'com.facebook.msys.mci.AccountSession',
    'ADS_SUPPLIER': 'com.facebook.messaging.business.inboxads.plugins.inboxads.itemsupplier.InboxAdsItemSupplierImplementation',
}

def to_dex_format(class_name):
    return 'L' + class_name.replace('.', '/') + ';'

extract_dir = r'C:\Users\dun\Downloads\MessengerPro-master\MessengerPro-master\apkmess\extracted'
dex_files = sorted([os.path.join(extract_dir, f) for f in os.listdir(extract_dir) if f.endswith('.dex')])

# Load all DEX data
dex_data = {}
for dex_file in dex_files:
    with open(dex_file, 'rb') as f:
        dex_data[os.path.basename(dex_file)] = f.read()
    print("Loaded: " + os.path.basename(dex_file))

print("\n=== CLASS NAME VERIFICATION IN MESSENGER 553 ===\n")

found_count = 0
missing_count = 0

for key, class_name in classes_to_find.items():
    dex_pattern = to_dex_format(class_name).encode('utf-8')
    dot_pattern = class_name.encode('utf-8')
    found_in = []
    for dex_name, data in dex_data.items():
        if dex_pattern in data or dot_pattern in data:
            found_in.append(dex_name)
    
    if found_in:
        found_count += 1
        files = ", ".join(found_in)
        print("[OK]      " + key + ": " + class_name)
        print("          -> " + files)
    else:
        missing_count += 1
        print("[MISSING] " + key + ": " + class_name)
    print()

print("=" * 60)
print("FOUND: " + str(found_count) + " / " + str(len(classes_to_find)))
print("MISSING: " + str(missing_count) + " / " + str(len(classes_to_find)))
