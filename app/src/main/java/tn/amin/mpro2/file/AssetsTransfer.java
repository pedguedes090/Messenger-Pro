package tn.amin.mpro2.file;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import tn.amin.mpro2.debug.Logger;

public class AssetsTransfer {

    private final Context mContext;
    private final AssetManager mAssetManager;
    private DocumentFile mDestDirectory;

    private static final String targetFolder = "mpro_files";

    public AssetsTransfer(Context context, Uri dest) {
        mContext = context;
        mAssetManager = mContext.getAssets();

        DocumentFile rootDirectory = DocumentFile.fromTreeUri(mContext, dest);
        assert rootDirectory != null;

        mDestDirectory = rootDirectory.findFile(targetFolder);
        if (mDestDirectory == null) {
            mDestDirectory = rootDirectory.createDirectory(targetFolder);
        }
    }

    public boolean transferAllAssets() {
        return copyAssetFolder(targetFolder, mDestDirectory);
    }

    private boolean copyAssetFolder(String source, DocumentFile dest) {
        try {
            String[] files = mAssetManager.list(source);
            boolean res = true;
            for (String file : files) {
                if (file.contains(".")) {
                    DocumentFile fileDocument = dest.findFile(file);
                    if (fileDocument != null) {
                        fileDocument.delete();
                    }

                    fileDocument = dest.createFile("*/*", file);
                    assert fileDocument != null;

                    res &= copyAsset(source + "/" + file,
                            fileDocument);
                }
                else {
                    DocumentFile dir = dest.findFile(file);
                    if (dir == null)
                        dir = dest.createDirectory(file);
                    res &= copyAssetFolder(source + "/" + file,
                            dir);
                }
            }
            return res;
        } catch (IOException e) {
            Logger.error(e);
            return false;
        }
    }

    private boolean copyAsset(String source, DocumentFile dest) {
        try (InputStream in = mAssetManager.open(source);
             OutputStream out = mContext.getContentResolver().openOutputStream(dest.getUri())) {
            if (out == null) return false;

            FileHelper.copyFile(in, out);
            out.flush();
            return true;
        } catch (IOException e) {
            Logger.error(e);
            return false;
        }
    }
}
