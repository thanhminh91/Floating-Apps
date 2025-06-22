package damjay.floating.projects.voicetranslator;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileLogger {
    private static final String TAG = "FileLogger";
    private static final String LOG_FILE_NAME = "voice_translator_debug.log";
    private static final int MAX_LOG_SIZE = 5 * 1024 * 1024; // 5MB
    
    private static FileLogger instance;
    private File logFile;
    private SimpleDateFormat dateFormat;
    private Context context;
    
    private FileLogger(Context context) {
        this.context = context.getApplicationContext();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
        initLogFile();
    }
    
    public static synchronized FileLogger getInstance(Context context) {
        if (instance == null) {
            instance = new FileLogger(context);
        }
        return instance;
    }
    
    private void initLogFile() {
        try {
            // For Android 11+ (API 30+), use external files directory (no permission needed)
            // For Android 10 and below, check permission first
            File externalDir = context.getExternalFilesDir(null);
            
            if (externalDir != null && canUseExternalStorage()) {
                logFile = new File(externalDir, LOG_FILE_NAME);
                Log.d(TAG, "Log file location (external): " + logFile.getAbsolutePath());
            } else {
                // Fallback to internal storage
                File internalDir = context.getFilesDir();
                logFile = new File(internalDir, LOG_FILE_NAME);
                Log.d(TAG, "Log file location (internal): " + logFile.getAbsolutePath());
            }
            
            // Create file if it doesn't exist
            if (!logFile.exists()) {
                logFile.createNewFile();
                writeToFile("=== Voice Translator Debug Log Started ===");
                writeToFile("Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
                writeToFile("Android: " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")");
                writeToFile("App Version: " + getAppVersion());
                writeToFile("Log file: " + logFile.getAbsolutePath());
                writeToFile("==========================================");
            }
            
            // Check file size and rotate if needed
            if (logFile.length() > MAX_LOG_SIZE) {
                rotateLogFile();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize log file", e);
        }
    }
    
    private void rotateLogFile() {
        try {
            File oldLogFile = new File(logFile.getParent(), LOG_FILE_NAME + ".old");
            if (oldLogFile.exists()) {
                oldLogFile.delete();
            }
            logFile.renameTo(oldLogFile);
            logFile.createNewFile();
            writeToFile("=== Log file rotated ===");
        } catch (Exception e) {
            Log.e(TAG, "Failed to rotate log file", e);
        }
    }
    
    private String getAppVersion() {
        try {
            return context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    public void log(String tag, String level, String message) {
        // Also log to Android logcat
        switch (level) {
            case "D":
                Log.d(tag, message);
                break;
            case "I":
                Log.i(tag, message);
                break;
            case "W":
                Log.w(tag, message);
                break;
            case "E":
                Log.e(tag, message);
                break;
            default:
                Log.v(tag, message);
                break;
        }
        
        // Write to file
        String timestamp = dateFormat.format(new Date());
        String logEntry = String.format("%s %s/%s: %s", timestamp, level, tag, message);
        writeToFile(logEntry);
    }
    
    public void d(String tag, String message) {
        log(tag, "D", message);
    }
    
    public void i(String tag, String message) {
        log(tag, "I", message);
    }
    
    public void w(String tag, String message) {
        log(tag, "W", message);
    }
    
    public void e(String tag, String message) {
        log(tag, "E", message);
    }
    
    public void e(String tag, String message, Throwable throwable) {
        log(tag, "E", message + "\n" + Log.getStackTraceString(throwable));
    }
    
    private synchronized void writeToFile(String message) {
        if (logFile == null) {
            return;
        }
        
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(message + "\n");
            writer.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to log file", e);
        }
    }
    
    public String getLogFilePath() {
        return logFile != null ? logFile.getAbsolutePath() : "Log file not available";
    }
    
    public void clearLog() {
        try {
            if (logFile != null && logFile.exists()) {
                logFile.delete();
                initLogFile();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear log file", e);
        }
    }
    
    public void logSystemInfo() {
        i("SystemInfo", "=== SYSTEM INFORMATION ===");
        i("SystemInfo", "Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
        i("SystemInfo", "Android: " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")");
        i("SystemInfo", "Build: " + android.os.Build.DISPLAY);
        
        // Check for MIUI/HyperOS
        String miuiVersion = getSystemProperty("ro.miui.ui.version.name", "");
        String hyperOSVersion = getSystemProperty("ro.mi.os.version.name", "");
        if (!miuiVersion.isEmpty()) {
            i("SystemInfo", "MIUI Version: " + miuiVersion);
        }
        if (!hyperOSVersion.isEmpty()) {
            i("SystemInfo", "HyperOS Version: " + hyperOSVersion);
        }
        
        i("SystemInfo", "Available RAM: " + getAvailableMemory() + " MB");
        i("SystemInfo", "External Storage: " + (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) ? "Available" : "Not Available"));
        i("SystemInfo", "========================");
    }
    
    private String getSystemProperty(String key, String defaultValue) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            return (String) systemProperties.getMethod("get", String.class, String.class)
                .invoke(null, key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private long getAvailableMemory() {
        try {
            android.app.ActivityManager activityManager = (android.app.ActivityManager) 
                context.getSystemService(Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo memoryInfo = new android.app.ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            return memoryInfo.availMem / (1024 * 1024); // Convert to MB
        } catch (Exception e) {
            return -1;
        }
    }
    
    private boolean canUseExternalStorage() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ (API 30+): External files directory doesn't need permission
            return true;
        } else {
            // Android 10 and below: Need WRITE_EXTERNAL_STORAGE permission
            return context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
    }
}