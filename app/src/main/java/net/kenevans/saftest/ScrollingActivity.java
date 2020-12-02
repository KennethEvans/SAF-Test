package net.kenevans.saftest;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
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
import java.util.Date;
import java.util.Locale;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.documentfile.provider.DocumentFile;

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
                Snackbar.make(view, "Test", Snackbar.LENGTH_LONG)
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
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                this.getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
            }
        } else if (requestCode == REQ_DB_FILE && resultCode == RESULT_OK) {
            Uri dataUri = resultData.getData();
            openDatabaseFromUri(dataUri);
        }
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
        if (id == R.id.action_permission) {
            getDocumentTree();
        } else if (id == R.id.action_show_files) {
            showFiles();
        } else if (id == R.id.action_open_database) {
            openDatabaseFromFileDialog();
        } else if (id == R.id.action_clear) {
            clearText();
        }
        return super.onOptionsItemSelected(item);
    }

    private void appendText(String text) {
        if (mTextView != null) {
            mTextView.append(text);
        }
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

    private void getDocumentTree() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION & Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
//        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uriToLoad);

        startActivityForResult(intent, REQ_GET_TREE);
    }

    private void showFiles() {
        try {
            SharedPreferences prefs = getPreferences(MODE_PRIVATE);
            String treeUriStr = prefs.getString(PREF_TREE_URI, null);
            Uri treeUri = Uri.parse(treeUriStr);
            if (treeUri == null) {
                Utils.errMsg(this, "Did not get tree Uri from preferences");
                return;
            }
            DocumentFile top = DocumentFile.fromTreeUri(this, treeUri);
            String topPath = getDocumentPathFromTreeUri(treeUri);
            String path = (topPath != null) ? topPath : top.toString();
            appendLine("\n" + "Files in directory " + path);
            DocumentFile[] files = top.listFiles();
            for (DocumentFile file : files) {
                appendLine(file.getName());
                appendLine("    directory=" + file.isDirectory());
                appendLine("    virtual=" + file.isVirtual());
                appendLine("    type=" + file.getType());
                appendLine("    modified=" + new Date(file.lastModified()));
                appendLine("    length=" + fileLength(file.length()));
            }
//            DocumentFile targetDocument = getDocumentFile(file, false);
//            OutputStream outStream = this.
//                    getContentResolver().openOutputStream(targetDocument
//                    .getUri());
        } catch (Exception ex) {
            Utils.excMsg(this, "Error showing files", ex);
        }
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

    @TargetApi(21)
    private static String getDocumentPathFromTreeUri(final Uri treeUri) {
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");
        if ((split.length >= 2) && (split[1] != null)) return split[1];
        else return File.separator;
    }

    void openDatabaseFromFileDialog() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT).setType("*/*");
        startActivityForResult(Intent.createChooser(intent, "Select a " +
                "database"), REQ_DB_FILE);
    }

    void openDatabaseFromUri(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            File file = File.createTempFile("sqlite", "");
            FileOutputStream outputStream = new FileOutputStream(file);
            byte[] buff = new byte[1024];
            int read;
            while ((read = inputStream.read(buff, 0, buff.length)) > 0)
                outputStream.write(buff, 0, read);
            inputStream.close();
            outputStream.close();
            openDatabaseFromFile(file.getPath());
        } catch (Exception ex) {
            Utils.excMsg(this, "Failed to open database from " + uri, ex);
        }
    }

    void openDatabaseFromFile(String filePath) {
        appendLine("\nDatabase: " + filePath);
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = SQLiteDatabase.openDatabase(filePath, null, 0);
            if (db == null) {
                Utils.errMsg(this, "Cannot open database");
                return;
            }
            appendLine("Path=" + db.getPath());
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
            Utils.excMsg(this, "Failed to open Database", ex);
        } finally {
            if (cursor != null) cursor.close();
            if (db != null) db.close();
        }
    }
}