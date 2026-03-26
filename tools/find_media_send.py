import struct, re, sys

targets = ['MediaSend', 'sendImage', 'sendPhoto', 'sendMedia', 'MediaUpload', 
           'ImageSend', 'PhotoSend', 'mediaSend', 'uploadMedia', 'uploadImage', 
           'ComposeFragment', 'Composer', 'MediaPicker', 'sendAttachment',
           'MediaMessage', 'ImageAttachment', 'PhotoAttachment',
           'sendMediaMessage', 'handleMediaResult', 'processImage',
           'nativeAttach', 'createAttachment', 'newAttachment',
           'MediaResourceCreator', 'mediaPrepare', 'nativeCreateMedia',
           'ContentAttachment', 'AttachmentData']

for dex_num in range(1, 13):
    fname = f'apkmess/extracted/classes{dex_num}.dex' if dex_num > 1 else 'apkmess/extracted/classes.dex'
    try:
        with open(fname, 'rb') as f:
            data = f.read()
        text = data.decode('ascii', errors='ignore')
        for target in targets:
            idx = 0
            count = 0
            while count < 5:
                idx = text.find(target, idx)
                if idx == -1:
                    break
                start = max(0, idx - 30)
                end = min(len(text), idx + len(target) + 80)
                context = text[start:end]
                context = ''.join(c if c.isprintable() else '.' for c in context)
                print(f'[{fname}] "{target}": ...{context}...')
                idx += len(target)
                count += 1
    except FileNotFoundError:
        pass
