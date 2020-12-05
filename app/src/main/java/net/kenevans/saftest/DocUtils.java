package net.kenevans.saftest;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class DocUtils implements IConstants {
    static public List<Uri> getChildren(Context ctx, Uri uri) {
//        if (!DocumentsContract.isDocumentUri(ctx, uri)) return null;
//        if (!isDirectory(ctx, uri)) return null;
        ContentResolver contentResolver = ctx.getContentResolver();
        Uri childrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(uri,
                        DocumentsContract.getTreeDocumentId(uri));
        List<Uri> children = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = contentResolver.query(childrenUri,
                    new String[]{
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID},
                    null,
                    null,
                    null);
            String documentId;
            Uri documentUri;
            while (cursor.moveToNext()) {
                documentId = cursor.getString(0);
                documentUri = DocumentsContract.buildDocumentUriUsingTree(uri,
                        documentId);
                children.add(documentUri);
            }
        } finally {
            closeQuietly(cursor);
        }
        return children;
    }

    static public List<Uri> getDocuments(Context ctx, Uri uri) {
        List<Uri> children = getChildren(ctx, uri);
        List<Uri> documents = new ArrayList<>();
        for (Uri uri1 : children) {
            if (!isDirectory(ctx, uri)) documents.add(uri1);
        }
        return documents;
    }

    static public List<Uri> getDirectories(Context ctx, Uri uri) {
        List<Uri> children = getChildren(ctx, uri);
        List<Uri> dirs = new ArrayList<>();
        for (Uri uri1 : children) {
            if (isDirectory(ctx, uri)) dirs.add(uri1);
        }
        return dirs;
    }

    static List<Uri> traverseDirectoryEntries(Context ctx, Uri treeUri) {
        ContentResolver contentResolver = ctx.getContentResolver();
        Uri children =
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,
                        DocumentsContract.getTreeDocumentId(treeUri));

        // Keep track of our directory hierarchy
        List<Uri> dirNodes = new LinkedList<>();
        dirNodes.add(children);

        while (!dirNodes.isEmpty()) {
            children = dirNodes.remove(0); // get the item from top
            Log.d(TAG, "node uri: " + children);
            Cursor cursor = null;
            try {
                cursor = contentResolver.query(children, new String[]{
                                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                                DocumentsContract.Document.COLUMN_MIME_TYPE},
                        null, null, null);
                while (cursor.moveToNext()) {
                    final String docId = cursor.getString(0);
                    final String name = cursor.getString(1);
                    final String mime = cursor.getString(2);
                    Log.d(TAG, "docId: " + docId + ", name: " + name + ", " +
                            "mime: " + mime);
                    if (isDirectory(mime)) {
                        final Uri newNode =
                                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId);
                        dirNodes.add(newNode);
                    }
                }
            } finally {
                closeQuietly(cursor);
            }
        }
        return dirNodes;
    }

    // Util method to check if the mime type is a directory
    static public boolean isDirectory(Context ctx, Uri uri) {
        if (!DocumentsContract.isDocumentUri(ctx, uri)) return false;
        ContentResolver contentResolver = ctx.getContentResolver();
        Cursor cursor = null;
        String mimeType = "NA";
        try {
            cursor = contentResolver.query(uri, new String[]{
                            DocumentsContract.Document.COLUMN_MIME_TYPE},
                    null, null, null);
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                mimeType = cursor.getString(0);
            }
        } finally {
            closeQuietly(cursor);
        }
        return DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
    }

    // Util method to check if the mime type is a directory
    static public boolean isDirectory(String mimeType) {
        return DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
    }

    // Util method to close a closeable
    static public void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception ignore) {
                // ignore exception
            }
        }
    }

    /**
     * Private version of DocumentsContract.getDocumentId. Use for debugging.
     *
     * @param documentUri The Uri.
     * @return The id.
     */
    public static String getDocumentId(Uri documentUri) {
        final List<String> paths = documentUri.getPathSegments();
        if (paths.size() >= 2 && "document".equals(paths.get(0))) {
            return paths.get(1);
        }
        if (paths.size() >= 4 && "tree".equals(paths.get(0))
                && "document".equals(paths.get(2))) {
            return paths.get(3);
        }
        throw new IllegalArgumentException("Invalid URI: " + documentUri);
    }

}
