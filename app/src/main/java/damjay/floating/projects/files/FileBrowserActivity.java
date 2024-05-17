package damjay.floating.projects.files;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import androidx.appcompat.app.AppCompatActivity;
import damjay.floating.projects.FloatingPDFActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.customadapters.FileListAdapter;
import damjay.floating.projects.files.FileItem;
import java.io.File;
import java.io.IOException;

public class FileBrowserActivity extends AppCompatActivity {
    ListView fileList;
    public static String currentInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_browser);

        getSupportActionBar().setTitle(Html.fromHtml("<font color='#ffffff'>" + getResources().getString(R.string.floating_pdf) + "</font>"));

        fileList = findViewById(R.id.fileList);
        View upButton = findViewById(R.id.traverseUp);

        fileList.setAdapter(new FileListAdapter(this, validateInput()));

        fileList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> a, View v, int position, long id) {
                    final FileItem item = (FileItem) fileList.getItemAtPosition(position);
                    if (item.isDirectory()) {
                        FileListAdapter listAdapter = (FileListAdapter) fileList.getAdapter();
                        listAdapter.updatePath(item.getFile());
                        if (item.isDirectory()) fileList.setAdapter(listAdapter);
                    } else {
                        if (item.getFileName().toLowerCase().endsWith(".pdf")) {
                            showPDF(item.getFile());
                        } else {
                            new AlertDialog.Builder(FileBrowserActivity.this)
                                .setMessage(R.string.incorrect_format_message)
                                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int id) {
                                        showPDF(item.getFile()); 
                                    }
                                })
                                .setNegativeButton(android.R.string.no, null)
                                .show();
                        }
                    }
                }

            });
        upButton.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View view) {
                    onBackPressed();
                }   
            });
    }

    private File validateInput() {
        if (currentInput == null || currentInput.trim().length() == 0)
            return null;
        File file = new File(currentInput);
        if (file.isFile()) file = file.getParentFile();
        if (file.listFiles() != null) return file;
        return null;
    }

    @Override
    public void onBackPressed() {
        File parent = null;
        FileListAdapter listAdapter = (FileListAdapter) fileList.getAdapter();
        try {
            if (listAdapter.folder == null) {
                super.onBackPressed();
                return;
            }
            if (listAdapter.getCount() == 0)
                parent = listAdapter.folder.getParentFile();
            else
                parent = ((FileItem) fileList.getItemAtPosition(0)).getFile().getParentFile().getParentFile();
        } catch (Throwable t) {
        }
        if (parent != null) {
            listAdapter.updatePath(parent);
            fileList.setAdapter(listAdapter);
        }
    }

    public void showPDF(File file) {
        try {
            FloatingPDFActivity.returnedPath = file.getCanonicalPath();
        } catch (IOException e) {}
        super.onBackPressed();
    }

}
