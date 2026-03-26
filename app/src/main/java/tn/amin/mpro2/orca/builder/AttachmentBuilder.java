package tn.amin.mpro2.orca.builder;

import android.media.MediaMetadataRetriever;
import android.webkit.MimeTypeMap;

import androidx.exifinterface.media.ExifInterface;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.lang.reflect.Constructor;

import de.robv.android.xposed.XposedHelpers;
import tn.amin.mpro2.constants.OrcaClassNames;
import tn.amin.mpro2.debug.Logger;

public class AttachmentBuilder {
    private final Constructor<?> mAttachmentConstructor;

    private String mAbsolutePath;
    private String mFileName;
    private String mMimeType;
    private Long mFileSize;
    private Long mTime;
    private long mWidth = 0L;
    private long mHeight = 0L;
    private long mDuration = 0L;
    private long mFileType = FILETYPE_UNKNOWN;

    public static final long FILETYPE_UNKNOWN = -1L;
    public static final long FILETYPE_IMAGE = 2L;
    public static final long FILETYPE_AUDIO = 5L;
    public static final long FILETYPE_OTHER = 6L;

    public AttachmentBuilder(ClassLoader classLoader) {
        Class<?> Attachment = XposedHelpers.findClass(OrcaClassNames.ATTACHMENT, classLoader);

        Constructor<?>[] allCtors = Attachment.getDeclaredConstructors();
        Constructor<?> foundCtor = null;

        // Log all constructors for diagnostics
        Logger.info("AttachmentBuilder: Attachment class has " + allCtors.length + " constructors");
        for (Constructor<?> ctor : allCtors) {
            Class<?>[] pts = ctor.getParameterTypes();
            StringBuilder sb = new StringBuilder("  Attachment(");
            for (Class<?> pt : pts) sb.append(pt.getSimpleName()).append(", ");
            sb.append(") -- ").append(pts.length).append(" params");
            Logger.info(sb.toString());
        }

        try {
            foundCtor = XposedHelpers.findConstructorExact(Attachment, java.lang.String.class, java.lang.String.class, java.lang.Long.class, java.lang.String.class, java.lang.Long.class, java.lang.String.class, java.lang.Long.class, boolean.class, boolean.class, java.lang.String.class, java.lang.String.class, java.lang.Long.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.Long.class, java.lang.Long.class, java.lang.Long.class, java.lang.Long.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, byte[].class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.Integer.class, java.lang.Long.class, java.lang.Integer.class, boolean.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class);
        } catch (Throwable t) {
            Logger.info("AttachmentBuilder: exact 45-param constructor not found, trying fallback");
            // Fallback: find the constructor with the most params
            int maxParams = 0;
            for (Constructor<?> ctor : allCtors) {
                if (ctor.getParameterTypes().length > maxParams) {
                    maxParams = ctor.getParameterTypes().length;
                    foundCtor = ctor;
                }
            }
            if (foundCtor != null) {
                Logger.info("AttachmentBuilder: using fallback constructor with " + maxParams + " params");
            }
        }
        mAttachmentConstructor = foundCtor;

        mTime = System.currentTimeMillis() * 1000;
    }

    public AttachmentBuilder setType(long type) {
        mFileType = type;
        return this;
    }

    public AttachmentBuilder setFile(File file) {
        mAbsolutePath = file.getAbsolutePath();
        mFileName = file.getName();

        mFileSize = file.length();
        mMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                FilenameUtils.getExtension(file.getName()));

        updateFileType();
        return this;
    }

    public AttachmentBuilder setFileName(String fileName) {
        mFileName = fileName;
        return this;
    }

    public AttachmentBuilder setResolution(long width, long height) {
        mWidth = width;
        mHeight = height;
        return this;
    }

    public Object build() {
        if (mAttachmentConstructor == null) {
            Logger.error("AttachmentBuilder: no constructor available");
            return null;
        }

        updateMetadata();

        Class<?>[] paramTypes = mAttachmentConstructor.getParameterTypes();
        int paramCount = paramTypes.length;
        Logger.info("AttachmentBuilder.build(): constructor has " + paramCount + " params");

        // Try the known 45-param layout first
        if (paramCount == 45) {
            try {
                return mAttachmentConstructor.newInstance(
                        mTime.toString(), mTime.toString(), mFileType,
                        mFileName, mFileSize, null, 0L, true, false,
                        mAbsolutePath, mMimeType, mDuration,
                        mAbsolutePath, mMimeType,
                        null,
                        mWidth, mHeight,
                        null,null,null,null,null,null,null,null,null,null,null,null,null,null,
                        mAbsolutePath,
                        null,null,null,null,null, 0L, 0, false, null, null, null, null, null);
            } catch (Throwable t) {
                Logger.error("AttachmentBuilder: 45-param build failed: " + t.getMessage());
                Logger.error(t);
                return null;
            }
        }

        // Dynamic: try to fill params based on types
        try {
            Object[] params = new Object[paramCount];
            int strIdx = 0;
            // Fill with defaults based on type
            for (int i = 0; i < paramCount; i++) {
                Class<?> pt = paramTypes[i];
                if (pt == String.class) {
                    // First few String params in order: time, time, fileName, ?, absolutePath, mimeType, absolutePath, mimeType, ...absolutePath
                    switch (strIdx) {
                        case 0: params[i] = mTime.toString(); break;
                        case 1: params[i] = mTime.toString(); break;
                        case 2: params[i] = mFileName; break;
                        case 3: params[i] = null; break;
                        case 4: params[i] = mAbsolutePath; break;
                        case 5: params[i] = mMimeType; break;
                        case 6: params[i] = mAbsolutePath; break;
                        case 7: params[i] = mMimeType; break;
                        default: params[i] = null; break;
                    }
                    strIdx++;
                } else if (pt == long.class) {
                    params[i] = 0L;
                } else if (pt == Long.class) {
                    // First Long = fileType, then fileSize, duration, width, height
                    params[i] = null;
                } else if (pt == int.class) {
                    params[i] = 0;
                } else if (pt == Integer.class) {
                    params[i] = null;
                } else if (pt == boolean.class) {
                    params[i] = false;
                } else if (pt == byte[].class) {
                    params[i] = null;
                } else {
                    params[i] = null;
                }
            }
            
            // Override key fields based on position pattern:
            // Find first Long and set it to fileType
            boolean setFileType = false, setFileSize = false;
            for (int i = 0; i < paramCount; i++) {
                if (paramTypes[i] == Long.class && !setFileType) {
                    params[i] = mFileType;
                    setFileType = true;
                } else if (paramTypes[i] == Long.class && !setFileSize) {
                    params[i] = mFileSize;
                    setFileSize = true;
                }
            }

            // Find boolean params: first=true (hasAttachment), second=false
            boolean setFirst = false;
            for (int i = 0; i < paramCount; i++) {
                if (paramTypes[i] == boolean.class && !setFirst) {
                    params[i] = true;
                    setFirst = true;
                    break;
                }
            }

            Logger.info("AttachmentBuilder.build(): attempting dynamic construction with " + paramCount + " params");
            return mAttachmentConstructor.newInstance(params);
        } catch (Throwable t) {
            Logger.error("AttachmentBuilder: dynamic build failed: " + t.getMessage());
            Logger.error(t);
            return null;
        }
    }

    private void updateMetadata() {
        if (mFileType == FILETYPE_IMAGE) {
            if (mWidth == 0 || mHeight == 0) {
                try {
                    ExifInterface exif = new ExifInterface(mAbsolutePath);

                    mWidth = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0);
                    mHeight = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0);

                } catch (Throwable t) {
                    Logger.error(t);
                }
            }
        } else if (mFileType == FILETYPE_AUDIO) {
            if (mDuration == 0) {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    retriever.setDataSource(mAbsolutePath);
                    String durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    mDuration = Integer.parseInt(durationString);
                    retriever.release();
                } catch (Throwable t) {
                    Logger.error(t);
                }
            }
        }

        Logger.info("Width: " + mWidth + ", Height: " + mHeight + ", Duration: " + mDuration);
    }

    private void updateFileType() {
        if (mFileType == FILETYPE_UNKNOWN) {
            if (mMimeType == null) {
                mFileType = FILETYPE_OTHER;
            } else if (mMimeType.startsWith("image")) {
                mFileType = FILETYPE_IMAGE;
            } else if (mMimeType.startsWith("audio")) {
                mFileType = FILETYPE_AUDIO;
            } else {
                mFileType = FILETYPE_OTHER;
            }
        }

        Logger.info("File type: " + mFileType);
    }
}
