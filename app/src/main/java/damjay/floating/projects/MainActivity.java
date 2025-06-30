package damjay.floating.projects;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import damjay.floating.projects.autoclicker.activity.ModeSelectorActivity;
import damjay.floating.projects.voicetranslator.VoiceTranslatorService;
import damjay.floating.projects.calculate.CalculatorService;
import damjay.floating.projects.clipboard.ClipboardService;
import damjay.floating.projects.timer.TimerService;

public class MainActivity extends AppCompatActivity {
    public static final int FLOAT_PERMISSION_REQUEST = 100;
    public static final int AUDIO_PERMISSION_REQUEST = 101;
    public static final int ALL_PERMISSIONS_REQUEST = 102;

    private AlertDialog alertDialog;
    
    // All required permissions
    private final String[] REQUIRED_PERMISSIONS = {
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    };

    // TODO: Don't make granting Display over other apps permission too strict
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.floating_calculator).setOnClickListener(getServiceClickListener(CalculatorService.class));
        findViewById(R.id.floating_voice_translator).setOnClickListener(getVoiceTranslatorClickListener());
        findViewById(R.id.floating_timer).setOnClickListener(getServiceClickListener(TimerService.class));
        View floatingClicker = findViewById(R.id.floating_clicker);
        floatingClicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, NumberRangeService.class);
                startService(intent);
            }
        });
        
        // Add long press listener for quick access to random number generator
        floatingClicker.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(MainActivity.this, NumberRangeService.class);
                startService(intent);
                return true;
            }
        });
        findViewById(R.id.floating_music).setOnClickListener(v -> Toast.makeText(this, R.string.floating_music_coming, Toast.LENGTH_LONG).show());
        findViewById(R.id.floating_copyTextField).setOnClickListener(getServiceClickListener(ClipboardService.class));
        findViewById(R.id.floating_browser).setOnClickListener(v -> Toast.makeText(this, R.string.floating_browser_coming, Toast.LENGTH_LONG).show());
        findViewById(R.id.floating_network).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, NetworkMonitorActivity.class);
                startActivity(intent);
            }
        });

        // Set up app header long press menu for quick actions
        setupAppHeaderMenu();

        // Request all required permissions first
        requestAllRequiredPermissions();

        // Request for optional optimization
        checkBatteryOptimization();
        // Request for compulsory permissions
//      checkPermissions();
    }

    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        // Is battery optimization enabled?
        if (!((PowerManager) getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(MainActivity.class.getPackage().getName())) {
            // If a dialog is open, don't open another
            if (alertDialog != null) return;
            alertDialog = new AlertDialog.Builder(this)
                .setMessage(R.string.ignore_battery_optimization)
                .setPositiveButton(R.string.settings, (dialog, id) -> {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    closeAlertDialog();
                    startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, (dialog, id) -> {
                    closeAlertDialog();
                    checkPermissions();
                })
                .setCancelable(false)
                .create();
            alertDialog.show();
        } else {
            checkPermissions();
        }
    }

    private boolean checkPermissions() {
        // Check if display over apps permission is enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            if (alertDialog != null) return false;
            alertDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.grant_permissions)
                .setMessage(R.string.display_permission_message)
                .setCancelable(false)
                .setPositiveButton(R.string.settings, (dialog, id) -> {
                    closeAlertDialog();
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                               Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, FLOAT_PERMISSION_REQUEST);
                })
                .setNegativeButton(R.string.exit, (dialog, id) -> finish()).create();
            alertDialog.show();
            return false;
        } else {
            closeAlertDialog();
            return true;
        }
    }

    private View.OnClickListener getActivityClickListener(final Class<?> clazz) {
        return view -> {
            Intent intent = new Intent(MainActivity.this, clazz);
            startActivity(intent);
        };
    }

    private View.OnClickListener getServiceClickListener(final Class<?> clazz) {
        return view -> {
            Intent intent = new Intent(MainActivity.this, clazz);
            startService(intent);
        };
    }
    
    private View.OnClickListener getVoiceTranslatorClickListener() {
        return view -> {
            // Check audio permission before starting voice translator service
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
                requestAudioPermission();
            } else {
                try {
                    Intent intent = new Intent(MainActivity.this, VoiceTranslatorService.class);
                    startService(intent);
                    Toast.makeText(this, "Starting Voice Translator...", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Error starting Voice Translator: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        };
    }
    
    private void requestAudioPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
            // Show explanation dialog
            new AlertDialog.Builder(this)
                .setTitle("Cần quyền ghi âm")
                .setMessage("Ứng dụng cần quyền ghi âm để sử dụng tính năng dịch giọng nói. Vui lòng cấp quyền để tiếp tục.")
                .setPositiveButton("Cấp quyền", (dialog, which) -> {
                    ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.RECORD_AUDIO}, 
                        AUDIO_PERMISSION_REQUEST);
                })
                .setNegativeButton("Hủy", null)
                .show();
        } else {
            // Request permission directly
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.RECORD_AUDIO}, 
                AUDIO_PERMISSION_REQUEST);
        }
    }

    private void requestAllRequiredPermissions() {
        // Filter out permissions that are already granted
        java.util.List<String> missingPermissions = new java.util.ArrayList<>();
        
        for (String permission : REQUIRED_PERMISSIONS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    // Skip storage permissions on Android 11+ where they're handled differently
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
                        (permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE) || 
                         permission.equals(Manifest.permission.READ_EXTERNAL_STORAGE))) {
                        continue;
                    }
                    missingPermissions.add(permission);
                }
            }
        }
        
        if (!missingPermissions.isEmpty()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Required Permissions");
            builder.setMessage("This app needs the following permissions to work properly:\n\n" +
                "• Microphone - for voice recording\n" +
                "• Storage - for saving logs\n" +
                "• Foreground Service - for background operation\n\n" +
                "Please grant all permissions to continue.");
            builder.setPositiveButton("Grant Permissions", (dialog, which) -> {
                ActivityCompat.requestPermissions(this, 
                    missingPermissions.toArray(new String[0]), 
                    ALL_PERMISSIONS_REQUEST);
            });
            builder.setNegativeButton("Skip", null);
            builder.setCancelable(false);
            builder.show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == AUDIO_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start voice translator service
                Intent intent = new Intent(MainActivity.this, VoiceTranslatorService.class);
                startService(intent);
                Toast.makeText(this, "Quyền ghi âm đã được cấp", Toast.LENGTH_SHORT).show();
            } else {
                // Permission denied
                Toast.makeText(this, "Cần quyền ghi âm để sử dụng tính năng dịch giọng nói", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == ALL_PERMISSIONS_REQUEST) {
            int grantedCount = 0;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    grantedCount++;
                }
            }
            
            if (grantedCount == grantResults.length) {
                Toast.makeText(this, "All permissions granted! ✓", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Some permissions denied. App may not work properly.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        /*if (requestCode == FLOAT_PERMISSION_REQUEST && resultCode == RESULT_OK) {
            closeAlertDialog();
        } else {*/
            if (checkPermissions()) {
                closeAlertDialog();
            }
        //}
    }

    private void closeAlertDialog() {
        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }
    }

    private void setupAppHeaderMenu() {
        View appHeader = findViewById(R.id.app_header);
        if (appHeader != null) {
            appHeader.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showQuickActionMenu();
                    return true;
                }
            });
        }
    }
    
    private void showQuickActionMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Quick Actions");
        
        String[] options = {
            "🎯 Random Number Generator",
            "🎤 Video Voice Translator", 
            "🧮 Calculator",
            "⏱️ Timer",
            "📋 Clipboard",
            "📊 Network Monitor",
            "📋 Debug Logs"
        };
        
        builder.setItems(options, (dialog, which) -> {
            Intent intent;
            switch (which) {
                case 0: // Random Number Generator
                    intent = new Intent(MainActivity.this, NumberRangeService.class);
                    startService(intent);
                    break;
                case 1: // Video Voice Translator
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                        != PackageManager.PERMISSION_GRANTED) {
                        requestAudioPermission();
                    } else {
                        try {
                            intent = new Intent(MainActivity.this, VoiceTranslatorService.class);
                            startService(intent);
                            Toast.makeText(this, "Starting Voice Translator...", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(this, "Error starting Voice Translator: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                    break;
                case 2: // Calculator
                    intent = new Intent(MainActivity.this, CalculatorService.class);
                    startService(intent);
                    break;
                case 3: // Timer
                    intent = new Intent(MainActivity.this, TimerService.class);
                    startService(intent);
                    break;
                case 4: // Clipboard
                    intent = new Intent(MainActivity.this, ClipboardService.class);
                    startService(intent);
                    break;
                case 5: // Network Monitor
                    intent = new Intent(MainActivity.this, NetworkMonitorActivity.class);
                    startActivity(intent);
                    break;
                case 6: // Debug Logs
                    intent = new Intent(MainActivity.this, LogViewerActivity.class);
                    startActivity(intent);
                    break;
            }
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (alertDialog == null)
            checkPermissions();
    }

}
