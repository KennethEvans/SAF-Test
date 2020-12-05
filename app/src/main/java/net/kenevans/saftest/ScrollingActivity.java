package net.kenevans.saftest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class ScrollingActivity extends AppCompatActivity implements IConstants {
    private TextView mTextView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scrolling);
        Toolbar toolbar = findViewById(R.id.toolbar);
        mTextView = findViewById(R.id.text);
        setSupportActionBar(toolbar);
        CollapsingToolbarLayout toolBarLayout =
                findViewById(R.id.toolbar_layout);
        toolBarLayout.setTitle(getTitle());

        FloatingActionButton fab =
                findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Clear the view", Snackbar.LENGTH_LONG)
                        .setAction("Clear", new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                clearText();
                            }
                        }).show();
            }
        });
    }

    @Override
    public final void onActivityResult(final int requestCode,
                                       final int resultCode,
                                       final Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == REQ_GET_TREE) {
            Uri treeUri;
            if (resultCode == Activity.RESULT_OK) {
                // Get Uri from Storage Access Framework.
                treeUri = resultData.getData();

                SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE)
                        .edit();
                editor.putString(PREF_TREE_URI, treeUri.toString());
                editor.apply();

                // Persist access permissions.
                final int takeFlags = resultData.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                this.getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
            }
        } else if (requestCode == REQ_DB_FILE && resultCode == RESULT_OK) {
            Uri dataUri = resultData.getData();
            // This gets an exception
            openDatabaseFromUri(dataUri);
        } else if (requestCode == REQ_DB_TEMP_FILE && resultCode == RESULT_OK) {
            Uri dataUri = resultData.getData();
            openTempDatabaseFromUri(dataUri);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putCharSequence("savedText", mTextView.getText());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedState) {
        super.onRestoreInstanceState(savedState);
        mTextView.setText(savedState.getCharSequence("savedText"));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_scrolling, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_show_files) {
            showFiles();
        } else if (id == R.id.action_select_permission) {
            selectPermission();
        } else if (id == R.id.action_request_permission) {
            requestPremission();
        } else if (id == R.id.action_show_permissions) {
            showPermissions();
        } else if (id == R.id.action_release_permission) {
            releasePermission();
        } else if (id == R.id.action_show_info) {
            showInfo();
        } else if (id == R.id.action_open_file) {
            openFileFromTree();
        } else if (id == R.id.action_open_database_from_tree) {
            openDatabaseFromTree();
        } else if (id == R.id.action_open_temp_database) {
            openDatabaseFromSystemFileDialogUsingTempDatabase();
        } else if (id == R.id.action_open_database) {
            openDatabaseFromSystemFileDialog();
        } else if (id == R.id.action_clear) {
            clearText();
        }
        return super.onOptionsItemSelected(item);
    }

    private void append(String text) {
        if (mTextView != null) {
            mTextView.append(text);
        }
    }

    private void appendLine() {
        appendLine("");
    }

    private void appendLine(String text) {
        if (mTextView != null) {
            mTextView.append(text + "\n");
        }
    }

    private void setText(String text) {
        if (mTextView != null) {
            mTextView.setText(text);
        }
    }

    private void clearText() {
        if (mTextView != null) {
            mTextView.setText("");
        }
    }

    /**
     * Uses ACTION_OPEN_DOCUMENT_TREE to request premission for a part of the
     * file system.
     */
    private void requestPremission() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION & Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
//        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uriToLoad);

        startActivityForResult(intent, REQ_GET_TREE);
    }

    /**
     * Shows information. For debugging.
     */
    private void showInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("\nSAF Test Info\n");
        // Get saved Uri
        String treeUriStr =
                getPreferences(MODE_PRIVATE).getString(PREF_TREE_URI, null);
        sb.append("PREF_TREE_URI=" + treeUriStr + "\n");
        append(sb.toString());
    }

    /**
     * List the files under PREF_TREE_URI with information about them. The
     * information is from uriInfo
     *
     * @see #docUriInfo
     */
    private void showFiles() {
        try {
            SharedPreferences prefs = getPreferences(MODE_PRIVATE);
            String treeUriStr = prefs.getString(PREF_TREE_URI, null);
            if (treeUriStr == null) {
                Utils.errMsg(this, "There is no tree Uri set");
                return;
            }
            Uri treeUri = Uri.parse(treeUriStr);
            append("\n" + "Files for ");
            String treeDocumentId =
                    DocumentsContract.getTreeDocumentId(treeUri);
            Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri,
                    treeDocumentId);
            append(docUriInfo(docUri));
//            appendLine("\n" + "Files for " + treeUri.getLastPathSegment());
//            // Test
//            appendLine("Internal documentId=" + DocUtils.getDocumentId
//            (treeUri));
//            appendLine("    isDocumentUri="
//                    + DocumentsContract.isDocumentUri(this, treeUri));
//            appendLine("    docUri=" + docUri);
//            appendLine("    path=" + FileUtil.getFullPathFromUri(this,
//                    treeUri));
//
//            // This returns "" for a treeUri
//            append(docUriInfo(docUri));
//            append(uriInfo(treeUri));
            appendLine();
            List<Uri> children = DocUtils.getChildren(this, treeUri);
//            if (children == null) {
//                Utils.errMsg(this, "Could not get children for " +
//                treeUriStr);
//                return;
//            }
            for (Uri uri : children) {
                appendLine(docUriInfo(uri));
            }
        } catch (Exception ex) {
            Utils.excMsg(this, "Error showing files", ex);
            Log.e(TAG, "Error showing files", ex);
        }
    }

    /**
     * Shows information about the persisted permissions.
     */
    private void showPermissions() {
        ContentResolver resolver = this.getContentResolver();
        List<UriPermission> permissionList =
                resolver.getPersistedUriPermissions();
        StringBuilder sb = new StringBuilder();
        for (UriPermission permission : permissionList) {
            sb.append(permission.getUri() + "\n");
            sb.append("    time=" + new Date(permission.getPersistedTime()) + "\n");
            sb.append("    access=" + (permission.isReadPermission() ? "R" : "")
                    + (permission.isWritePermission() ? "W" : "") + "\n");
            sb.append("    special objects flag=" + permission.describeContents() + "\n");
            sb.append("\n");
        }
        appendLine("\nPersistent Permissions");
        append(sb.toString());
    }

    private void selectPermission() {
        ContentResolver resolver = this.getContentResolver();
        final List<UriPermission> permissionList =
                resolver.getPersistedUriPermissions();
        int nPermissions = permissionList.size();
        if (nPermissions == 0) {
            Utils.warnMsg(this, "There are no persisted permissions");
            return;
        }
        String[] items = new String[nPermissions];
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        Uri uri;
        String uriStr;
        int selected = 0;
        for (int i = 0; i < nPermissions; i++) {
            uri = permissionList.get(i).getUri();
            uriStr = uri.toString();
            items[i] = uri.getPath();
            if (treeUriStr != null && treeUriStr.equals(uriStr)) {
                selected = i;
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getText(R.string.action_select_permission));
        builder.setSingleChoiceItems(items, selected,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        dialog.dismiss();
                        SharedPreferences.Editor editor =
                                getPreferences(MODE_PRIVATE)
                                        .edit();
                        editor.putString(PREF_TREE_URI,
                                permissionList.get(item).getUri().toString());
                        editor.apply();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    private void releasePermission() {
        ContentResolver resolver = this.getContentResolver();
        final List<UriPermission> permissionList =
                resolver.getPersistedUriPermissions();
        int nPermissions = permissionList.size();
        if (nPermissions == 0) {
            Utils.warnMsg(this, "There are no persisted permissions");
            return;
        }
        String[] items = new String[nPermissions];
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        Uri uri;
        String uriStr;
        int selected = 0;
        for (int i = 0; i < nPermissions; i++) {
            uri = permissionList.get(i).getUri();
            uriStr = uri.toString();
            items[i] = uri.getPath();
            if (treeUriStr != null && treeUriStr.equals(uriStr)) {
                selected = i;
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getText(R.string.action_select_permission));
        builder.setSingleChoiceItems(items, selected,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        dialog.dismiss();
                        Uri uri = permissionList.get(item).getUri();
                        ContentResolver resolver =
                                ScrollingActivity.this.getContentResolver();
                        resolver.releasePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        // Set the preference to null
                        SharedPreferences.Editor editor =
                                getPreferences(MODE_PRIVATE).edit();
                        editor.putString(PREF_TREE_URI, null);
                        editor.apply();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Gets DocumentContract information for a document Uri.
     *
     * @param docUri The Uri.
     * @return The information or "" if Uri is not a document Uri.
     */
    private String docUriInfo(Uri docUri) {
        // Only works for documents
        if (!DocumentsContract.isDocumentUri(this, docUri)) return "";
        StringBuilder sb = new StringBuilder();
        ContentResolver contentResolver = this.getContentResolver();
        String path = FileUtil.getFullPathFromUri(this, docUri);
        String displayName = "NA";
        double size = Double.NaN;
        String sizeStr = "NA";
        long lastModified = -1;
        String lastModifiedStr = "NA";
        String mimeType = "NA";
        int flags = 0;
        String[] projection = {
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_FLAGS
        };
        Cursor cursor = null;
        try {
            cursor = contentResolver.query(docUri, projection,
                    null, null, null);
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                if (cursor.getColumnIndex(projection[0]) != -1) {
                    displayName = cursor.getString(0);
                }
                if (cursor.getColumnIndex(projection[1]) != -1) {
                    size = cursor.getDouble(1);
                }
                if (cursor.getColumnIndex(projection[2]) != -1) {
                    lastModified = cursor.getLong(2);
                }
                if (cursor.getColumnIndex(projection[3]) != -1) {
                    mimeType = cursor.getString(3);
                }
                if (cursor.getColumnIndex(projection[4]) != -1) {
                    flags = cursor.getInt(4);
                }
            }
        } finally {
            DocUtils.closeQuietly(cursor);
        }
        if (size != Double.NaN) sizeStr = fileLength(size);
        if (lastModified != -1) {
            lastModifiedStr = new Date(lastModified).toString();
        }
        boolean canWrite =
                (flags & DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0;
        boolean canDelete =
                (flags & DocumentsContract.Document.FLAG_SUPPORTS_DELETE) != 0;
        boolean canMove = false;
        boolean canCopy = false;
        if (Build.VERSION.SDK_INT >= 24) {
            canMove =
                    (flags & DocumentsContract.Document.FLAG_SUPPORTS_MOVE) != 0;
            canCopy =
                    (flags & DocumentsContract.Document.FLAG_SUPPORTS_COPY) != 0;
        }
        boolean canRename =
                (flags & DocumentsContract.Document.FLAG_SUPPORTS_RENAME) != 0;
        boolean canCreate =
                (flags & DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) != 0;
        sb.append(displayName + "\n");
        sb.append("    path=" + path + "\n");
        sb.append("    isDocumentUri=" + DocumentsContract.isDocumentUri(this,
                docUri) + "\n");
        sb.append("    isDirectory="
                + DocUtils.isDirectory(mimeType) +
                "\n");
//        String docId = DocumentsContract.getDocumentId(docUri);
//        String treeDocId = DocumentsContract.getTreeDocumentId(docUri);
//        sb.append("    treeDocId=" + treeDocId + "\n");
//        sb.append("    docId=" + docId + "\n");
//        sb.append("    docUri=" + docUri + "\n");
//        sb.append("    doc path=" + docUri.getPath() + "\n");
//        sb.append("    last path segment=" + docUri.getPath() + "\n");
        sb.append("    size=" + sizeStr + "\n");
        sb.append("    mime type=" + mimeType + "\n");
        sb.append("    last modified=" + lastModifiedStr + "\n");
        sb.append("    canWrite=" + canWrite + " canDelete=" + canDelete
                + " canMove=" + canMove + "\n");
        sb.append("    canCopy=" + canCopy + " canRename=" + canRename
                + " canCreate=" + canCreate + "\n");
        return sb.toString();
    }

    /**
     * Gets general information about a Uri.
     *
     * @param uri The Uri.
     * @return The information.
     */
    private String uriInfo(Uri uri) {
        StringBuilder sb = new StringBuilder();
        sb.append("    uri=" + uri + "\n");
        sb.append("    scheme=" + uri.getScheme() + "\n");
        sb.append("    authority=" + uri.getAuthority() + "\n");
        sb.append("    fragment=" + uri.getFragment() + "\n");
        sb.append("    scheme=" + uri.getScheme() + "\n");
        sb.append("    path=" + uri.getPath() + "\n");
        sb.append("    last path segment=" + uri.getLastPathSegment() + "\n");
        List<String> segments = uri.getPathSegments();
        for (String segment : segments) {
            sb.append("    path segment=" + segment + "\n");
        }
        return sb.toString();
    }

    /**
     * Turns a number of bytes into a formatted form as Bytes, MB, KB, GB, TB.
     *
     * @param length The given length.
     * @return The formatted String.
     */
    private String fileLength(double length) {
        if (length < 1024) {
            return String.format(Locale.US, "%.0f", length) + " Bytes";
        } else if (length < 1024d * 1024) {
            return String.format(Locale.US, "%.1f", length / 1024) + " KB";
        } else if (length < 1024d * 1024 * 1024) {
            return String.format(Locale.US, "%.1f",
                    length / (1024d * 1024)) +
                    " MB";
        } else if (length < 1024d * 1024 * 1024 * 1024) {
            return String.format(Locale.US, "%.1f",
                    length / (1024d * 1024 * 1024)) + " GB";
        } else {
            return String.format(Locale.US, "%.1f",
                    length / (1024d * 1024 * 1024 * 1024)) + " TB";
        }
    }

    /**
     * Opens the first data base found in the tree.
     */
    void openDatabaseFromTree() {
        appendLine("\nOpening First Database in Tree");
        try {
            SharedPreferences prefs = getPreferences(MODE_PRIVATE);
            String treeUriStr = prefs.getString(PREF_TREE_URI, null);
            if (treeUriStr == null) {
                Utils.errMsg(this, "There is no tree Uri set");
                return;
            }
            Uri treeUri = Uri.parse(treeUriStr);
            Uri childrenUri =
                    DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,
                            DocumentsContract.getTreeDocumentId(treeUri));
            List<Uri> children = new ArrayList<>();
            Cursor cursor = null;
            try {
                cursor = this.getContentResolver().query(childrenUri,
                        new String[]{
                                DocumentsContract.Document.COLUMN_DOCUMENT_ID},
                        null,
                        null, null);
                String documentId;
                Uri documentUri;
                while (cursor.moveToNext()) {
                    documentId = cursor.getString(0);
                    documentUri =
                            DocumentsContract.buildDocumentUriUsingTree(treeUri,
                                    documentId);
                    children.add(documentUri);
                }
            } finally {
                DocUtils.closeQuietly(cursor);
            }
            boolean found = false;
            String path;
            for (Uri uri : children) {
                path = FileUtil.getFullPathFromUri(this, uri);
                Log.d(TAG, "    path=" + path);
                if (path.endsWith(".db")) {
                    found = true;
                    openDatabaseFromFile(path);
                }
            }
            if (!found) {
                Utils.errMsg(this, "Did not find any .db files");
            }
        } catch (Exception ex) {
            String msg = "Error opening database";
            appendLine(msg);
            Utils.excMsg(this, msg, ex);
            Log.e(TAG, msg, ex);
        }
    }

    /**
     * Opens the first data base found in the tree.
     */
    void openFileFromTree() {
        appendLine("\nOpening First File in Tree");
        try {
            SharedPreferences prefs = getPreferences(MODE_PRIVATE);
            String treeUriStr = prefs.getString(PREF_TREE_URI, null);
            if (treeUriStr == null) {
                Utils.errMsg(this, "There is no tree Uri set");
                return;
            }
            Uri treeUri = Uri.parse(treeUriStr);
            List<Uri> children = DocUtils.getChildren(this, treeUri);
            Log.d(TAG,
                    "openDatabaseFromFile: children.size()=" + children.size());
            if (children.size() == 0) {
                Utils.errMsg(this, "There are no files in the tree");
                return;
            }
            Uri docUri = children.get(0);
            String path = FileUtil.getFullPathFromUri(this, docUri);
            File file = new File(path);
            appendLine("    path=" + file.getPath());
            if (!file.exists()) {
                appendLine("    Does not exist");
                return;
            }
            appendLine("    canRead=" + file.canRead()
                    + " canWrite=" + file.canWrite());
        } catch (Exception ex) {
            String msg = "Error finding a file to open";
            appendLine(msg);
            Utils.excMsg(this, msg, ex);
            Log.e(TAG, msg, ex);
        }
    }

    /**
     * Opens a database picked by the system file chooser.
     */
    void openDatabaseFromSystemFileDialog() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT).setType("*/*");
        startActivityForResult(Intent.createChooser(intent, "Select a " +
                "database"), REQ_DB_FILE);
    }

    /**
     * Opens a database picked by the system file chooser. Uses a temporary
     * file that is a copy of the database.
     */
    void openDatabaseFromSystemFileDialogUsingTempDatabase() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT).setType("*/*");
        startActivityForResult(Intent.createChooser(intent, "Select a " +
                "database"), REQ_DB_TEMP_FILE);
    }

    /**
     * Creates a copy of a database picked by the system file chooser and
     * opens that.
     */
    void openTempDatabaseFromUri(Uri uri) {
        appendLine("\nOpening Database Using a Temporary Copy");
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            File file = File.createTempFile("sqlite", ".db");
            FileOutputStream outputStream = new FileOutputStream(file);
            byte[] buff = new byte[1024];
            int read;
            while ((read = inputStream.read(buff, 0, buff.length)) > 0)
                outputStream.write(buff, 0, read);
            inputStream.close();
            outputStream.close();
            openDatabaseFromFile(file.getPath());
        } catch (Exception ex) {
            String msg = "Failed to open temp database from " + uri;
            appendLine(msg);
            Utils.excMsg(this, msg, ex);

            Log.e(TAG, msg, ex);
        }
    }

    /**
     * Opens a database from a Uri. Gets the path from FileUtil
     * .getFullPathFromUri and calls openDatabaseFromFile. Will fail if
     * it is a content Uri and not a tree Uri.
     *
     * @param uri The Uri.
     */
    void openDatabaseFromUri(Uri uri) {
        appendLine("\nOpening Database Using Path from Uri");
        Log.d(TAG, "openDatabaseFromUri: uri=" + uri);
        try {
            String path = FileUtil.getFullPathFromUri(this, uri);
            appendLine("    path=" + path);
            Log.d(TAG, "openDatabaseFromUri: uri=" + uri);
            Log.d(TAG, "    path=" + path);
            openDatabaseFromFile(path);
        } catch (Exception ex) {
            String msg = "Failed to open database from " + uri;
            appendLine(msg);
            Utils.excMsg(this, msg, ex);
            Log.e(TAG, msg, ex);
        }
    }

    /**
     * Opens a database from a file path. (The only way it can be done as far
     * as we know.)
     *
     * @param filePath The path.
     */
    void openDatabaseFromFile(String filePath) {
        Log.d(TAG, "openDatabaseFromFile: filePath=" + filePath);
        appendLine("Database: " + filePath);
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = SQLiteDatabase.openDatabase(filePath, null, 0);
            if (db == null) {
                Utils.errMsg(this, "Cannot open database");
                return;
            }
            appendLine("Version=" + db.getVersion());
            String SQL_GET_ALL_TABLES = "SELECT * FROM sqlite_master WHERE " +
                    "type='table'";
            cursor = db.rawQuery(SQL_GET_ALL_TABLES, null);
            if (cursor == null) {
                Utils.errMsg(this, "Failed to get tables");
                return;
            }
            appendLine("Number of tables=" + cursor.getCount());
            String[] colNames = cursor.getColumnNames();
            appendLine("Column Names");
            for (String col : colNames) {
                appendLine("    " + col);
            }
            appendLine("Tables");
            while (cursor.moveToNext()) {
                String name = cursor.getString(1);
                String type = cursor.getString(0);
                appendLine("    " + name + " type=" + type);
            }
        } catch (Exception ex) {
            String msg = "Failed to open Database from " + filePath;
            appendLine(msg);
            Utils.excMsg(this, msg, ex);
            Log.e(TAG, msg, ex);
        } finally {
            if (cursor != null) cursor.close();
            if (db != null) db.close();
        }
    }
}