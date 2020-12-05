package net.kenevans.saftest;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.List;

import androidx.annotation.Nullable;

/**
 * From https://stackoverflow.com/questions/34927748/android-5-0-documentfile
 * -from-tree-uri
 */
public final class FileUtil implements IConstants {

    private static final String PRIMARY_VOLUME_NAME = "primary";

    @Nullable
    public static String getFullPathFromUri(@Nullable final Uri treeUri,
                                                Context con) {
        if (treeUri == null) return null;
        String volumePath = getVolumePath(getVolumeIdFromUri(treeUri), con);
        if (volumePath == null) return File.separator;
        if (volumePath.endsWith(File.separator))
            volumePath = volumePath.substring(0, volumePath.length() - 1);
        Log.d(TAG, "getFullPathFromUri treeUri=" + treeUri);
        Log.d(TAG, "    volumePath=" + volumePath);
        String documentPath = getDocumentPathFromUri(treeUri);
        if (documentPath.endsWith(File.separator))
            documentPath = documentPath.substring(0, documentPath.length() - 1);
        Log.d(TAG, "    documentPath=" + documentPath);

        if (documentPath.length() > 0) {
            if (documentPath.startsWith(File.separator))
                return volumePath + documentPath;
            else
                return volumePath + File.separator + documentPath;
        } else return volumePath;
    }

    @Nullable
    public static String getFullPathFromTreeUri(@Nullable final Uri treeUri,
                                                Context con) {
        if (treeUri == null) return null;
        String volumePath = getVolumePath(getVolumeIdFromTreeUri(treeUri), con);
        if (volumePath == null) return File.separator;
        if (volumePath.endsWith(File.separator))
            volumePath = volumePath.substring(0, volumePath.length() - 1);
        Log.d(TAG, "getFullPathFromTreeUri treeUri=" + treeUri);
        Log.d(TAG, "    volumePath=" + volumePath);
        String documentPath = getDocumentPathFromTreeUri(treeUri);
        if (documentPath.endsWith(File.separator))
            documentPath = documentPath.substring(0, documentPath.length() - 1);
        Log.d(TAG, "    documentPath=" + documentPath);

        if (documentPath.length() > 0) {
            if (documentPath.startsWith(File.separator))
                return volumePath + documentPath;
            else
                return volumePath + File.separator + documentPath;
        } else return volumePath;
    }

    @SuppressLint("ObsoleteSdkInt")
    private static String getVolumePath(final String volumeId,
                                        Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            return getVolumePathForAndroid11AndAbove(volumeId, context);
        else
            return getVolumePathBeforeAndroid11(volumeId, context);
    }


    private static String getVolumePathBeforeAndroid11(final String volumeId,
                                                       Context context) {
        try {
            StorageManager mStorageManager =
                    (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            Class<?> storageVolumeClazz = Class.forName("android.os.storage" +
                    ".StorageVolume");
            Method getVolumeList = mStorageManager.getClass().getMethod(
                    "getVolumeList");
            Method getUuid = storageVolumeClazz.getMethod("getUuid");
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Method isPrimary = storageVolumeClazz.getMethod("isPrimary");
            Object result = getVolumeList.invoke(mStorageManager);

            final int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                String uuid = (String) getUuid.invoke(storageVolumeElement);
                Boolean primary =
                        (Boolean) isPrimary.invoke(storageVolumeElement);

                if (primary && PRIMARY_VOLUME_NAME.equals(volumeId))    //
                    // primary volume?
                    return (String) getPath.invoke(storageVolumeElement);

                if (uuid != null && uuid.equals(volumeId))    // other volumes?
                    return (String) getPath.invoke(storageVolumeElement);
            }
            // not found.
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.R)
    private static String getVolumePathForAndroid11AndAbove(final String volumeId, Context context) {
        try {
            StorageManager mStorageManager =
                    (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            List<StorageVolume> storageVolumes =
                    mStorageManager.getStorageVolumes();
            for (StorageVolume storageVolume : storageVolumes) {
                // primary volume?
                if (storageVolume.isPrimary() && PRIMARY_VOLUME_NAME.equals(volumeId))
                    return storageVolume.getDirectory().getPath();

                // other volumes?
                String uuid = storageVolume.getUuid();
                if (uuid != null && uuid.equals(volumeId))
                    return storageVolume.getDirectory().getPath();

            }
            // not found.
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static String getVolumeIdFromTreeUri(final Uri treeUri) {
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");
        if (split.length > 0) return split[0];
        else return null;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static String getVolumeIdFromUri(final Uri treeUri) {
        final String docId = DocumentsContract.getDocumentId(treeUri);
        final String[] split = docId.split(":");
        if (split.length > 0) return split[0];
        else return null;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        private static String getDocumentPathFromTreeUri(final Uri treeUri) {
        Log.d(TAG, "getDocumentPathFromTreeUri treeUri=" + treeUri);
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        Log.d(TAG, "    docId=" + docId);
        final String[] split = docId.split(":");
        int count = 0;
        for (String str : split) {
            Log.d(TAG, "    str " + (count++) + "=" + str);
        }
        if ((split.length >= 2) && (split[1] != null)) return split[1];
        else return File.separator;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static String getDocumentPathFromUri(final Uri treeUri) {
        Log.d(TAG, "getDocumentPathFromUri treeUri=" + treeUri);
        final String docId = DocumentsContract.getDocumentId(treeUri);
        Log.d(TAG, "    docId=" + docId);
        final String[] split = docId.split(":");
        int count = 0;
        for (String str : split) {
            Log.d(TAG, "    str " + (count++) + "=" + str);
        }
        if ((split.length >= 2) && (split[1] != null)) return split[1];
        else return File.separator;
    }
}