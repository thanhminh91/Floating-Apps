package damjay.floating.projects;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import damjay.floating.projects.voicetranslator.FileLogger;

public class LogViewerActivity extends AppCompatActivity {
    private static final String TAG = "LogViewerActivity";
    
    private TextView logTextView;
    private ScrollView scrollView;
    private Handler handler;
    private Runnable updateLogRunnable;
    private File logFile;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_viewer);
        
        // Enable back button in action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Debug Logs");
        }
        
        logTextView = findViewById(R.id.logTextView);
        scrollView = findViewById(R.id.scrollView);
        handler = new Handler(Looper.getMainLooper());
        
        // Get log file from FileLogger
        FileLogger fileLogger = FileLogger.getInstance(this);
        logFile = new File(fileLogger.getLogFilePath());
        
        // Load logs initially
        loadLogs();
        
        // Auto-refresh logs every 2 seconds
        updateLogRunnable = new Runnable() {
            @Override
            public void run() {
                loadLogs();
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(updateLogRunnable);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && updateLogRunnable != null) {
            handler.removeCallbacks(updateLogRunnable);
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.log_viewer_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_refresh) {
            loadLogs();
            Toast.makeText(this, "Logs refreshed", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_share) {
            shareLogs();
            return true;
        } else if (id == R.id.action_clear) {
            clearLogs();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    private void loadLogs() {
        new Thread(() -> {
            StringBuilder logContent = new StringBuilder();
            
            try {
                if (logFile.exists()) {
                    BufferedReader reader = new BufferedReader(new FileReader(logFile));
                    String line;
                    
                    // Read all lines
                    while ((line = reader.readLine()) != null) {
                        logContent.append(line).append("\n");
                    }
                    reader.close();
                } else {
                    logContent.append("Log file not found: ").append(logFile.getAbsolutePath()).append("\n");
                }
            } catch (IOException e) {
                logContent.append("Error reading log file: ").append(e.getMessage()).append("\n");
                Log.e(TAG, "Error reading log file", e);
            }
            
            // Update UI on main thread
            handler.post(() -> {
                logTextView.setText(logContent.toString());
                // Auto scroll to bottom
                scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
            });
        }).start();
    }
    
    private void shareLogs() {
        try {
            if (!logFile.exists()) {
                Toast.makeText(this, "Log file not found", Toast.LENGTH_SHORT).show();
                return;
            }
            
            android.net.Uri fileUri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                logFile
            );
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Voice Translator Debug Logs");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Debug logs from Voice Translator app");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            startActivity(Intent.createChooser(shareIntent, "Share logs via"));
            
        } catch (Exception e) {
            Toast.makeText(this, "Error sharing logs: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "Error sharing logs", e);
        }
    }
    
    private void clearLogs() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Clear Logs")
            .setMessage("Are you sure you want to clear all logs?")
            .setPositiveButton("Clear", (dialog, which) -> {
                try {
                    if (logFile.exists()) {
                        logFile.delete();
                        // Create new empty file
                        logFile.createNewFile();
                        loadLogs();
                        Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Error clearing logs: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error clearing logs", e);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}