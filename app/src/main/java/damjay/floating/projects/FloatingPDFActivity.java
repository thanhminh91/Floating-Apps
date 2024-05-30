package damjay.floating.projects;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Html;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import damjay.floating.projects.R;
import damjay.floating.projects.customadapters.HistorySimpleAdapter;
import damjay.floating.projects.files.FileBrowserActivity;
import damjay.floating.projects.utils.FormatUtils;
import damjay.floating.projects.utils.IOUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class FloatingPDFActivity extends AppCompatActivity {
    public static final String HISTORY_EXTENSION = ".hst";
    public static final String HISTORY_FILE = "history" + HISTORY_EXTENSION;

    private static final int FLOAT_PERMISSION_REQUEST = 100;
    private static final int FILE_REQUEST_PERMISSION = 101;

    private File[] files;
    private EditText filePath;

    public static String returnedPath;

    private AlertDialog alertDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (viewIntent()) {
            return;
        }
        setContentView(R.layout.activity_floating_pdf);
        getSupportActionBar().setTitle(Html.fromHtml("<font color='#ffffff'>" + getResources().getString(R.string.floating_pdf) + "</font>"));
        // AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        initializeViews();
        checkPermission();
        if (savedInstanceState == null) {

        }
    }

    private boolean viewIntent() {
        Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            if (uri == null) return false;
            String name = IOUtils.getFileName(this, uri);
            if (name == null) {
                new Throwable("Name is null").printStackTrace();
                // TODO: Keep on going
                return false;
            }
            File newFile = new File(getCacheDir(), name);
            if (!IOUtils.safeCopy(this, uri, newFile)) {
                Toast.makeText(this, R.string.file_read_error, Toast.LENGTH_SHORT).show();
                return false;
            }
            return openFloatingPDF(null, newFile);
        }
        return false;
    }

    private void initializeViews() {
        try {
            filePath = findViewById(R.id.file_path);
            Button loadFileButton = findViewById(R.id.selectFile);
            Button selectFiles = findViewById(R.id.browseFile);
            populateList();

            loadFileButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String text = filePath.getText().toString();
                        if (text.trim().isEmpty()) {
                            Toast.makeText(FloatingPDFActivity.this, R.string.invalid_path_message, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        openFloatingPDF(new File(text), null);
                    }                    
                });
            selectFiles.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        FileBrowserActivity.currentInput = filePath.getText().toString();
                        Intent intent = new Intent(FloatingPDFActivity.this, FileBrowserActivity.class);
                        startActivity(intent);
                    }

                });
        } catch (Throwable report) {
            report.printStackTrace();
        }

    }

    private boolean openFloatingPDF(File file, File newFile) {
        try {
            if (newFile == null) {
                if (file == null || !file.exists()) {
                    Toast.makeText(this, R.string.file_load_error, Toast.LENGTH_LONG).show();
                    return false;
                }
                newFile = copyToCache(file);
                if (newFile == null) {
                    Toast.makeText(this, R.string.file_load_error, Toast.LENGTH_LONG).show();
                    return false;
                }
            }
            try {
                PDFReaderService.savePathHistory(getCacheDir(), newFile, 0);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            // If service is already running, stop it
            if (PDFReaderService.pdfFile != null) {
                stopService(new Intent(this, PDFReaderService.class));
            }
            PDFReaderService.pdfFile = newFile;
            // Start the service
            return launchService();
        } catch (Throwable t) {
            Toast.makeText(this, R.string.file_load_error, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void populateList() {
        ListView listView = findViewById(R.id.fileHistory);
        ArrayList<HashMap<String, Object>> list = new ArrayList<>();
        ArrayList<File> fileList = new ArrayList<>();
        String[] entries = {"fileName", "fileInfo"};

        try {
            files = getCacheDir().listFiles();
            // While reading my cache directory, permission restricted. Can this happen?
            if (files == null) return;

            for (File file : files) {
                HashMap<String, Object> map = new HashMap<>();
                if (file.getName().toLowerCase().endsWith(HISTORY_EXTENSION))
                    continue;
                map.put(entries[0], file.getName());
                map.put(entries[1], FormatUtils.formatDate(file.lastModified()) + ", " + FormatUtils.formatSize(file.length()));

                fileList.add(file);
                list.add(map);
            }

            files = fileList.toArray(new File[0]);

            SimpleAdapter adapter = new HistorySimpleAdapter(this, list, R.layout.history_files, entries, new int[]{R.id.file_name, R.id.file_info}, new HistorySimpleAdapter.Callback() {

                    @Override
                    public void delete(final int position) {
                        new AlertDialog.Builder(FloatingPDFActivity.this)
                            .setMessage(R.string.delete_file_confirm)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    if (files[position].delete()) {
                                        populateList();
                                    } else {
                                        Toast.makeText(FloatingPDFActivity.this, R.string.delete_file_error, Toast.LENGTH_LONG).show();
                                    }

                                }
                            })
                            .setNegativeButton(android.R.string.no, null)
                            .show();
                    }

                    public void run(final int position) {
                        if (files == null) {
                            // System.out.println("The array containing the files is null.");
                            return;
                        }
                        openFloatingPDF(null, files[position]);
                    }

                });
            listView.setAdapter(adapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                        if (files == null) {
                            // System.out.println("The array containing the files is null.");
                            return;
                        }
                        openFloatingPDF(null, files[position]);
                    }
                });
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private File copyToCache(File file) {
        File createdFile = new File(getCacheDir(), file.getName());
        try {
            FileInputStream inputStream = new FileInputStream(file);
            FileOutputStream outputStream = new FileOutputStream(createdFile);

            byte[] buffer = new byte[1024 * 50];
            int read;

            while ((read = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, read);
            }

            // Close the streams
            inputStream.close();
            outputStream.close();

            return createdFile;
        } catch (Throwable exception) {
        }
        return null;
    }

    public static void openDownloads(@NonNull Activity activity) {
        if (isSamsung()) {
            Intent intent = activity.getPackageManager()
                .getLaunchIntentForPackage("com.sec.android.app.myfiles");
            intent.setAction("samsung.myfiles.intent.action.LAUNCH_MY_FILES");
            intent.putExtra("samsung.myfiles.intent.extra.START_PATH", 
                            getDownloadsFile().getPath());
            activity.startActivity(intent);
        } else activity.startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
    }

    public static boolean isSamsung() {
        String manufacturer = Build.MANUFACTURER;
        if (manufacturer != null) return manufacturer.toLowerCase().equals("samsung");
        return false;
    }

    public static File getDownloadsFile() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    public boolean launchService() {
        if (checkPermission()) {
            startService(new Intent(this, PDFReaderService.class));
            finish();
            return true;
        }
        return false;
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            // If a dialog is open, don't open another
            if (alertDialog != null) return false;
            alertDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.grant_permissions)
                .setMessage(R.string.display_permission_message)
                .setCancelable(false)
                .setPositiveButton(R.string.settings, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                   Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, FLOAT_PERMISSION_REQUEST);
                    }
                })
                .setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                }).create();
            alertDialog.show();
            return false;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // If a dialog is open, don't open another
            if (alertDialog != null) return false;
            alertDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.grant_permissions)
                .setMessage(R.string.files_permission_message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) ActivityCompat.requestPermissions(FloatingPDFActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, FILE_REQUEST_PERMISSION);
                        else {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri uri = Uri.fromParts("package", getPackageName(), null);
                            intent.setData(uri);
                            startActivity(intent);
                        }
                        closeAlertDialog();
                    }
                })
                .setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                }).create();
            alertDialog.show();
            return false;
        } else {
            closeAlertDialog();
            return true;
        } 
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeAlertDialog();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (filePath != null && returnedPath != null) {
            filePath.setText(returnedPath);
            returnedPath = null;
        }
        checkPermission();
    }

    private void closeAlertDialog() {
        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != FILE_REQUEST_PERMISSION) return;
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            closeAlertDialog();
            viewIntent();
        } else {
            new AlertDialog.Builder(this)
                .setMessage(R.string.permission_denied_message)
                .setCancelable(false)
                .setPositiveButton(R.string.exit, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                })
                .show();
            // if (checkPermission())
            // viewIntent();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case FILE_REQUEST_PERMISSION:
            case FLOAT_PERMISSION_REQUEST:
                if (resultCode == RESULT_OK) {
                    closeAlertDialog();
                    viewIntent();
                } else {
                    if (checkPermission())
                        viewIntent();
                }
        }
    }


}
