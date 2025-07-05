package damjay.floating.projects.voicetranslator;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import damjay.floating.projects.MainActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.customadapters.TranslationHistoryAdapter;
import damjay.floating.projects.models.Language;
import damjay.floating.projects.models.TranslationHistory;
import damjay.floating.projects.utils.ViewsUtils;

public class VoiceTranslatorService extends Service implements AudioRecorder.RecordingCallback, SystemAudioCapture.SystemAudioCallback {
    private static final String TAG = "VoiceTranslatorService";
    private static final String GOOGLE_AI_API_KEY = "AIzaSyBte96Vxw7OyF6_D7MgHmCoNuw-MG-ID90"; // Replace with actual API key
    private static final int MEDIA_PROJECTION_REQUEST_CODE = 1001;
    
    public static final String ACTION_MEDIA_PROJECTION_RESULT = "MEDIA_PROJECTION_RESULT";
    
    // Notification constants
    private static final String NOTIFICATION_CHANNEL_ID = "voice_translator_channel";
    private static final int NOTIFICATION_ID = 1;
    
    private FileLogger fileLogger;
    
    private WindowManager windowManager;
    private View view;
    private WindowManager.LayoutParams params;
    private Handler mainHandler;
    
    // UI Components
    private Spinner sourceLanguageSpinner;
    private Spinner targetLanguageSpinner;
    private Button recordButton;
    private Button systemAudioButton;
    private Button geminiStreamButton;
    private TextView recordingStatus;
    private TextView statusText;
    private TextView originalText;
    private TextView translatedText;
    private View translationResult;
    private Button copyButton;
    private Button shareButton;
    private ListView historyListView;
    private Button clearHistoryButton;
    private Button debugLogButton;
    
    // Core Components
    private AudioRecorder audioRecorder;
    private SystemAudioCapture systemAudioCapture;
    private RealTimeTranscriber realTimeTranscriber;
    private GoogleAIClient googleAIClient;
    private List<TranslationHistory> translationHistory;
    private TranslationHistoryAdapter historyAdapter;
    
    // New floating components
    private FloatingRecordButton floatingRecordButton;
    private TranslationResultPopup translationResultPopup;
    private GeminiStreamTranslator geminiStreamTranslator;
    
    // State
    private boolean isRecording = false;
    private boolean isCapturingSystemAudio = false;
    private boolean isGeminiStreamActive = false;
    private boolean isForegroundServiceStarted = false;
    private boolean hasMediaProjectionPermission = false;
    private Language selectedSourceLanguage;
    private Language selectedTargetLanguage;
    private MediaProjection mediaProjection;
    private String lastTranscribedText = "";
    
    // Static MediaProjection data to persist across service restarts
    private static MediaProjection staticMediaProjection;
    private static boolean staticHasPermission = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        fileLogger.d(TAG, "onStartCommand called, flags: " + flags + ", startId: " + startId);
        
        // Always ensure we're running as foreground service
        if (!isForegroundServiceStarted) {
            try {
                fileLogger.d(TAG, "Starting foreground service in onStartCommand...");
                startForegroundService();
            } catch (Exception e) {
                fileLogger.e(TAG, "Failed to start foreground service in onStartCommand", e);
                showToast("Failed to start service: " + e.getMessage());
                // If we can't start as foreground, stop the service
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        
        if (intent != null && ACTION_MEDIA_PROJECTION_RESULT.equals(intent.getAction())) {
            handleMediaProjectionResult(intent);
        }
        return START_STICKY;
    }
    
    private void handleMediaProjectionResult(Intent intent) {
        int resultCode = intent.getIntExtra(MediaProjectionActivity.EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent data = intent.getParcelableExtra(MediaProjectionActivity.EXTRA_RESULT_DATA);
        
        fileLogger.d(TAG, "MediaProjection result received:");
        fileLogger.d(TAG, "  - Result code: " + resultCode + " (RESULT_OK=" + Activity.RESULT_OK + ")");
        fileLogger.d(TAG, "  - Data intent: " + (data != null ? "present" : "null"));
        
        showDebugToast("MediaProjection result: " + resultCode);
        
        if (resultCode == Activity.RESULT_OK && data != null) {
            try {
                fileLogger.d(TAG, "Permission granted, upgrading service to MediaProjection type first...");
                
                // First upgrade to MediaProjection foreground service type
                hasMediaProjectionPermission = true; // Set this first so upgrade method works
                upgradeToMediaProjectionService();
                
                fileLogger.d(TAG, "Creating MediaProjection...");
                mediaProjection = SystemAudioCapture.getMediaProjection(this, resultCode, data);
                
                if (mediaProjection != null) {
                    fileLogger.d(TAG, "MediaProjection created successfully");
                    systemAudioCapture.setMediaProjection(mediaProjection);
                    
                    // Store MediaProjection for future use
                    staticMediaProjection = mediaProjection;
                    staticHasPermission = true;
                    
                    // Hide main popup and show floating button
                    mainHandler.post(() -> {
                        fileLogger.d(TAG, "About to minimize view and show floating button");
                        minimizeView();
                        showFloatingRecordButton();
                        showToast("Đã cấp quyền thành công! Mở ứng dụng video bất kỳ và nhấn nút ghi âm.");
                        fileLogger.d(TAG, "Completed showing floating button");
                    });
                    
                    fileLogger.d(TAG, "MediaProjection permission granted, showing floating record button");
                } else {
                    fileLogger.e(TAG, "MediaProjection is null after creation");
                    hasMediaProjectionPermission = false; // Reset on failure
                    showToast("Không thể tạo MediaProjection");
                }
            } catch (Exception e) {
                fileLogger.e(TAG, "Error handling MediaProjection result", e);
                hasMediaProjectionPermission = false; // Reset on failure
                showToast("Lỗi khi xử lý quyền capture âm thanh: " + e.getMessage());
            }
        } else {
            fileLogger.w(TAG, "MediaProjection permission denied or cancelled");
            fileLogger.w(TAG, "  - Expected RESULT_OK (" + Activity.RESULT_OK + "), got: " + resultCode);
            mainHandler.post(() -> {
                systemAudioButton.setText("Capture Video Audio");
                recordingStatus.setVisibility(View.GONE);
                showToast("Cần cấp quyền để capture âm thanh từ video");
            });
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize file logger first
        fileLogger = FileLogger.getInstance(this);
        fileLogger.logSystemInfo();
        fileLogger.i(TAG, "=== VoiceTranslatorService onCreate ===");
        
        Log.d(TAG, "VoiceTranslatorService onCreate");
        showToast("VoiceTranslatorService started!");
        
        // Start as foreground service for MediaProjection
        try {
            startForegroundService();
            fileLogger.d(TAG, "Foreground service started successfully");
        } catch (Exception e) {
            fileLogger.e(TAG, "Failed to start foreground service in onCreate", e);
            showToast("Failed to start foreground service: " + e.getMessage());
            // If we can't start as foreground, stop the service
            stopSelf();
            return;
        }
        
        // Restore MediaProjection if available
        restoreMediaProjection();
        
        mainHandler = new Handler(Looper.getMainLooper());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        // Initialize components
        audioRecorder = new AudioRecorder(this);
        systemAudioCapture = new SystemAudioCapture(this, this);
        realTimeTranscriber = new RealTimeTranscriber(this, new RealTimeTranscriber.TranscriptionCallback() {
            @Override
            public void onTranscriptionResult(String text) {
                handleTranscriptionResult(text);
            }

            @Override
            public void onTranscriptionError(String error) {
                handleTranscriptionError(error);
            }

            @Override
            public void onTranscriptionStarted() {
                Log.d(TAG, "Real-time transcription started");
            }

            @Override
            public void onTranscriptionStopped() {
                Log.d(TAG, "Real-time transcription stopped");
                isRecording = false;
                updateRecordingUI();
            }
        });
        googleAIClient = new GoogleAIClient(GOOGLE_AI_API_KEY);
        translationHistory = new ArrayList<>();
        
        // Initialize floating components
        floatingRecordButton = new FloatingRecordButton(this);
        translationResultPopup = new TranslationResultPopup(this);
        geminiStreamTranslator = new GeminiStreamTranslator(this, GOOGLE_AI_API_KEY);
        
        // Set up Gemini stream translator callback
        geminiStreamTranslator.setCallback(new GeminiStreamTranslator.GeminiTranslationCallback() {
            @Override
            public void onTranslationResult(String originalText, String translatedText) {
                handleGeminiTranslationSuccess(originalText, translatedText);
            }
            
            @Override
            public void onError(String error) {
                handleGeminiTranslationError(error);
            }
            
            @Override
            public void onStatusUpdate(String status) {
                handleGeminiStatusUpdate(status);
            }
        });
        
        // Set up floating record button listener
        floatingRecordButton.setOnRecordClickListener(new FloatingRecordButton.OnRecordClickListener() {
            @Override
            public void onStartRecord() {
                startFloatingRecording();
            }

            @Override
            public void onStopRecord() {
                stopFloatingRecording();
            }
        });
        
        // Setup UI
        try {
            fileLogger.d(TAG, "Setting up UI...");
            setupView();
            setupLanguageSpinners();
            setupEventListeners();
            fileLogger.d(TAG, "UI setup completed");
        } catch (Exception e) {
            fileLogger.e(TAG, "Failed to setup UI", e);
            showToast("Failed to setup UI: " + e.getMessage());
            stopSelf();
            return;
        }
        
        // Add view to window
        try {
            fileLogger.d(TAG, "Adding view to window...");
            windowManager.addView(view, params);
            windowManager.updateViewLayout(view, params);
            addTouchListeners(view);
            
            minimizeView();
            fileLogger.d(TAG, "Main view added to window successfully");
            showToast("Voice Translator is ready!");
        } catch (Exception e) {
            fileLogger.e(TAG, "Failed to add main view to window", e);
            // Show a toast to user
            showToast("Failed to show Voice Translator. Check overlay permission: " + e.getMessage());
            // Stop the service if we can't show the view
            stopSelf();
            return;
        }
        
        // Check initial permissions and microphone availability
        checkInitialSetup();
    }
    
    private void restoreMediaProjection() {
        if (staticHasPermission && staticMediaProjection != null) {
            fileLogger.d(TAG, "Restoring MediaProjection from previous session");
            mediaProjection = staticMediaProjection;
            hasMediaProjectionPermission = true;
            
            if (systemAudioCapture != null) {
                systemAudioCapture.setMediaProjection(mediaProjection);
            }
            
            // Upgrade to MediaProjection foreground service type
            if (isForegroundServiceStarted) {
                upgradeToMediaProjectionService();
            }
            
            // Update UI to show permission is available
            mainHandler.post(() -> {
                systemAudioButton.setText("Record Video Audio ✓");
            });
            
            fileLogger.d(TAG, "MediaProjection restored successfully");
        } else {
            fileLogger.d(TAG, "No previous MediaProjection to restore");
        }
    }
    
    private void checkInitialSetup() {
        // Log system information for debugging
        Log.d(TAG, "Android version: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        Log.d(TAG, "Device: " + Build.MANUFACTURER + " " + Build.MODEL);
        
        // Check for HyperOS/MIUI specific information
        String miuiVersion = getSystemProperty("ro.miui.ui.version.name", "");
        String hyperOSVersion = getSystemProperty("ro.mi.os.version.name", "");
        if (!miuiVersion.isEmpty()) {
            Log.d(TAG, "MIUI Version: " + miuiVersion);
        }
        if (!hyperOSVersion.isEmpty()) {
            Log.d(TAG, "HyperOS Version: " + hyperOSVersion);
        }
        
        // Check permissions
        boolean hasRecordPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            == PackageManager.PERMISSION_GRANTED;
        Log.d(TAG, "RECORD_AUDIO permission: " + hasRecordPermission);
        
        if (!hasRecordPermission) {
            Log.w(TAG, "RECORD_AUDIO permission not granted. User will need to grant it manually.");
        }
        
        // Check microphone availability
        checkMicrophoneAvailability();
    }
    
    private String getSystemProperty(String key, String defaultValue) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            return (String) systemProperties.getMethod("get", String.class, String.class)
                .invoke(null, key, defaultValue);
        } catch (Exception e) {
            Log.d(TAG, "Could not get system property " + key);
            return defaultValue;
        }
    }
    
    private void checkMicrophoneAvailability() {
        try {
            // Test if we can create an AudioRecord instance
            int bufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.w(TAG, "AudioRecord buffer size check failed");
                return;
            }
            
            AudioRecord testRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                44100,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            );
            
            if (testRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                Log.d(TAG, "Microphone is available");
                testRecord.release();
            } else {
                Log.w(TAG, "Microphone initialization failed");
                testRecord.release();
            }
        } catch (SecurityException e) {
            Log.w(TAG, "No permission to test microphone: " + e.getMessage());
        } catch (Exception e) {
            Log.w(TAG, "Error testing microphone availability: " + e.getMessage());
        }
    }
    
    private void startForegroundService() {
        if (isForegroundServiceStarted) {
            fileLogger.d(TAG, "Foreground service already started");
            return; // Already started
        }
        
        try {
            fileLogger.d(TAG, "Starting foreground service...");
            createNotificationChannel();
            
            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            
            Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Video Voice Translator")
                    .setContentText("Ready to translate video audio")
                    .setSmallIcon(R.drawable.voice_translator_logo)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .build();
            
            // Start with microphone type (always needed for voice translation)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                fileLogger.d(TAG, "Started foreground service with MICROPHONE type");
            } else {
                startForeground(NOTIFICATION_ID, notification);
                fileLogger.d(TAG, "Started foreground service (legacy Android)");
            }
            isForegroundServiceStarted = true;
            
        } catch (SecurityException e) {
            fileLogger.e(TAG, "SecurityException starting foreground service - missing permissions", e);
            showToast("Missing permissions for background service. Please grant all permissions.");
            isForegroundServiceStarted = false;
            throw e;
        } catch (Exception e) {
            fileLogger.e(TAG, "Failed to start foreground service", e);
            showToast("Failed to start background service: " + e.getMessage());
            isForegroundServiceStarted = false;
            throw e;
        }
    }
    
    private void upgradeToMediaProjectionService() {
        if (!isForegroundServiceStarted) {
            fileLogger.w(TAG, "Cannot upgrade to MediaProjection service - foreground service not started");
            return;
        }
        
        try {
            fileLogger.d(TAG, "Upgrading to MediaProjection foreground service type...");
            
            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            
            Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Video Voice Translator")
                    .setContentText("Recording video audio for translation")
                    .setSmallIcon(R.drawable.voice_translator_logo)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .build();
            
            // Try to upgrade to combined MICROPHONE + MEDIA_PROJECTION type only if we have the permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasMediaProjectionPermission) {
                try {
                    // Combine MICROPHONE and MEDIA_PROJECTION types
                    int serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE | ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
                    startForeground(NOTIFICATION_ID, notification, serviceType);
                    fileLogger.d(TAG, "Successfully upgraded to MICROPHONE + MEDIA_PROJECTION foreground service type");
                } catch (Exception e) {
                    fileLogger.w(TAG, "Failed to upgrade to combined type, continuing with MICROPHONE only: " + e.getMessage());
                    // Update notification content but keep MICROPHONE type only
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                }
            } else {
                // For older Android versions or when no MediaProjection permission, just update the notification with MICROPHONE type
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                } else {
                    startForeground(NOTIFICATION_ID, notification);
                }
                fileLogger.d(TAG, "Updated foreground service notification with MICROPHONE type");
            }
            
        } catch (Exception e) {
            fileLogger.e(TAG, "Failed to upgrade to MediaProjection service", e);
            // Don't stop the service, just log the error
        }
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Voice Translator Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Voice translation service notifications");
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void setupView() {
        view = LayoutInflater.from(this).inflate(R.layout.voice_translator_layout, null);
        params = getLayoutParams();
        
        // Get UI references
        sourceLanguageSpinner = view.findViewById(R.id.sourceLanguageSpinner);
        targetLanguageSpinner = view.findViewById(R.id.targetLanguageSpinner);
        recordButton = view.findViewById(R.id.recordButton);
        systemAudioButton = view.findViewById(R.id.systemAudioButton);
        geminiStreamButton = view.findViewById(R.id.geminiStreamButton);
        recordingStatus = view.findViewById(R.id.recordingStatus);
        statusText = view.findViewById(R.id.statusText);
        originalText = view.findViewById(R.id.originalText);
        translatedText = view.findViewById(R.id.translatedText);
        translationResult = view.findViewById(R.id.translationResult);
        copyButton = view.findViewById(R.id.copyButton);
        shareButton = view.findViewById(R.id.shareButton);
        historyListView = view.findViewById(R.id.historyListView);
        clearHistoryButton = view.findViewById(R.id.clearHistoryButton);
        debugLogButton = view.findViewById(R.id.debugLogButton);
        
        // Setup history adapter
        historyAdapter = new TranslationHistoryAdapter(this, translationHistory);
        historyListView.setAdapter(historyAdapter);
        
        initViewSize();
    }

    private void setupLanguageSpinners() {
        Language[] languages = Language.getSupportedLanguages();
        
        ArrayAdapter<Language> sourceAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, languages);
        sourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sourceLanguageSpinner.setAdapter(sourceAdapter);
        
        ArrayAdapter<Language> targetAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, languages);
        targetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        targetLanguageSpinner.setAdapter(targetAdapter);
        
        // Set default selections
        sourceLanguageSpinner.setSelection(0); // Auto detect
        targetLanguageSpinner.setSelection(1); // Vietnamese
        
        selectedSourceLanguage = languages[0];
        selectedTargetLanguage = languages[1];
        
        // Set listeners
        sourceLanguageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedSourceLanguage = languages[position];
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        targetLanguageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedTargetLanguage = languages[position];
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupEventListeners() {
        // Window controls
        view.findViewById(R.id.minimizedVoiceTranslator).setOnClickListener(v -> maximizeView());
        view.findViewById(R.id.minimizeVoiceTranslator).setOnClickListener(v -> minimizeView());
        view.findViewById(R.id.voiceTranslatorCloseView).setOnClickListener(v -> stopSelf());
        view.findViewById(R.id.voiceTranslatorLaunchApp)
            .setOnClickListener(v -> ViewsUtils.launchApp(this, MainActivity.class));
        
        // Recording buttons
        recordButton.setOnClickListener(v -> toggleRecording());
        systemAudioButton.setOnClickListener(v -> toggleSystemAudioCapture());
        geminiStreamButton.setOnClickListener(v -> toggleGeminiStreamTranslation());
        
        // Translation result buttons
        copyButton.setOnClickListener(v -> copyTranslatedText());
        shareButton.setOnClickListener(v -> shareTranslatedText());
        
        // History controls
        clearHistoryButton.setOnClickListener(v -> clearHistory());
        debugLogButton.setOnClickListener(v -> showDebugLogMenu());
        
        historyListView.setOnItemClickListener((parent, view, position, id) -> {
            TranslationHistory history = translationHistory.get(position);
            showTranslationResult(history.getOriginalText(), history.getTranslatedText());
        });
    }

    private void toggleRecording() {
        Log.d(TAG, "Toggle recording button pressed. Current state: " + (isRecording ? "recording" : "not recording"));
        
        if (!checkPermissions()) {
            Log.w(TAG, "Permission check failed");
            return;
        }
        
        if (isRecording) {
            Log.d(TAG, "Stopping recording...");
            stopRecording();
        } else {
            Log.d(TAG, "Starting recording...");
            startRecording();
        }
    }
    
    private void toggleSystemAudioCapture() {
        fileLogger.d(TAG, "Toggle system audio capture button pressed. Current state: " + (isCapturingSystemAudio ? "capturing" : "not capturing"));
        showDebugToast("System Audio button pressed");
        
        if (hasMediaProjectionPermission) {
            // If we have permission, show floating button directly
            fileLogger.d(TAG, "Permission already granted, showing floating button...");
            mainHandler.post(() -> {
                minimizeView();
                showFloatingRecordButton();
                showToast("Mở ứng dụng video bất kỳ và nhấn nút ghi âm để bắt đầu.");
            });
        } else {
            // Request permission first
            fileLogger.d(TAG, "Starting system audio capture request...");
            requestSystemAudioCapture();
        }
    }
    
    private void requestSystemAudioCapture() {
        fileLogger.d(TAG, "Requesting system audio capture permission...");
        
        // Check Android version first
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            fileLogger.w(TAG, "System audio capture requires Android 10+, current: " + Build.VERSION.SDK_INT);
            showToast("Tính năng capture âm thanh hệ thống cần Android 10 trở lên");
            return;
        }
        
        // Check if we already have permission
        if (staticHasPermission && staticMediaProjection != null) {
            fileLogger.d(TAG, "MediaProjection permission already granted, using existing permission");
            mediaProjection = staticMediaProjection;
            hasMediaProjectionPermission = true;
            systemAudioCapture.setMediaProjection(mediaProjection);
            
            mainHandler.post(() -> {
                minimizeView();
                showFloatingRecordButton();
                showToast("Sử dụng quyền đã có. Mở ứng dụng video và nhấn nút ghi âm.");
            });
            return;
        }
        
        // Update UI to show requesting permission
        mainHandler.post(() -> {
            systemAudioButton.setText("Requesting Permission...");
            recordingStatus.setText("Đang yêu cầu quyền ghi âm...");
            recordingStatus.setVisibility(View.VISIBLE);
        });
        
        // Request media projection permission
        requestMediaProjectionPermission();
    }

    private boolean checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            showPermissionRequiredDialog();
            return false;
        }
        return true;
    }
    
    private void showPermissionRequiredDialog() {
        showToast(getString(R.string.recording_permission_required));
        
        // Create intent to open app settings
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        try {
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open app settings", e);
            showToast("Vui lòng cấp quyền ghi âm trong Cài đặt > Ứng dụng > " + getString(R.string.app_name) + " > Quyền");
        }
    }

    private void startRecording() {
        Log.d(TAG, "Attempting to start recording...");
        
        // Double-check permissions before starting
        if (!checkPermissions()) {
            Log.w(TAG, "Recording permission not granted");
            return;
        }
        
        // Use RealTimeTranscriber for better speech recognition
        if (RealTimeTranscriber.isRecognitionAvailable(this)) {
            realTimeTranscriber.startTranscription(selectedSourceLanguage.getCode());
            isRecording = true;
            updateRecordingUI();
            Log.d(TAG, "Real-time transcription started successfully");
        } else {
            // Fallback to AudioRecorder if SpeechRecognizer is not available
            if (audioRecorder.startRecording()) {
                isRecording = true;
                updateRecordingUI();
                Log.d(TAG, "Audio recording started successfully (fallback mode)");
            } else {
                Log.e(TAG, "Failed to start recording");
                showToast("Không thể bắt đầu ghi âm. Vui lòng kiểm tra quyền microphone.");
            }
        }
    }

    private void stopRecording() {
        Log.d(TAG, "Stopping recording...");
        
        // Stop real-time transcriber if it's being used
        if (realTimeTranscriber.isListening()) {
            realTimeTranscriber.stopTranscription();
        } else {
            // Stop audio recorder if it's being used as fallback
            audioRecorder.stopRecording();
        }
        
        isRecording = false;
        updateRecordingUI();
        Log.d(TAG, "Recording stopped");
    }
    
    private void startSystemAudioCapture() {
        fileLogger.d(TAG, "Attempting to start system audio capture...");
        showDebugToast("Starting system audio capture");
        
        // Check Android version first
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            fileLogger.w(TAG, "System audio capture requires Android 10+, current: " + Build.VERSION.SDK_INT);
            showToast("Tính năng capture âm thanh hệ thống cần Android 10 trở lên");
            return;
        }
        
        // SystemAudioCapture will call onPermissionRequired() if MediaProjection is not available
        // The actual capture will start in handleMediaProjectionResult() after permission is granted
        systemAudioCapture.startSystemAudioCapture();
    }
    
    private void stopSystemAudioCapture() {
        Log.d(TAG, "Stopping system audio capture...");
        systemAudioCapture.stopSystemAudioCapture();
        isCapturingSystemAudio = false;
        updateSystemAudioUI();
        Log.d(TAG, "System audio capture stopped");
    }

    private void updateRecordingUI() {
        mainHandler.post(() -> {
            if (isRecording) {
                recordButton.setText(getString(R.string.stop_recording));
                recordingStatus.setText(getString(R.string.recording));
                recordingStatus.setVisibility(View.VISIBLE);
                Log.d(TAG, "UI updated: Recording started");
            } else {
                recordButton.setText(getString(R.string.start_recording));
                if (!isCapturingSystemAudio) {
                    recordingStatus.setText(getString(R.string.processing));
                }
                Log.d(TAG, "UI updated: Recording stopped");
            }
        });
    }
    
    private void updateSystemAudioUI() {
        mainHandler.post(() -> {
            if (isCapturingSystemAudio) {
                systemAudioButton.setText("Stop Capture");
                recordingStatus.setText("Đang capture âm thanh từ video...");
                recordingStatus.setVisibility(View.VISIBLE);
                fileLogger.d(TAG, "UI updated: System audio capture started");
            } else {
                systemAudioButton.setText("Capture Video Audio");
                if (!isRecording) {
                    recordingStatus.setVisibility(View.GONE);
                }
                fileLogger.d(TAG, "UI updated: System audio capture stopped");
            }
        });
    }

    // AudioRecorder.RecordingCallback implementation
    @Override
    public void onRecordingStarted() {
        Log.d(TAG, "Recording started");
    }

    @Override
    public void onRecordingData(byte[] data) {
        // Real-time processing could be implemented here
    }

    @Override
    public void onRecordingStopped(byte[] audioData) {
        Log.d(TAG, "Recording stopped, processing audio...");
        
        mainHandler.post(() -> {
            recordingStatus.setText(getString(R.string.processing));
        });
        
        // Process the audio data
        googleAIClient.transcribeAndTranslate(
            audioData, 
            selectedSourceLanguage.getCode(), 
            selectedTargetLanguage.getCode(),
            new GoogleAIClient.TranslationCallback() {
                @Override
                public void onSuccess(String translatedText) {
                    mainHandler.post(() -> {
                        // Extract original text from the transcription process
                        // In a real implementation, this would come from the transcription step
                        String originalText = getLastTranscribedText();
                        handleTranslationSuccess(originalText, translatedText);
                    });
                }

                @Override
                public void onError(String error) {
                    mainHandler.post(() -> handleTranslationError(error));
                }
            }
        );
    }

    @Override
    public void onRecordingError(String error) {
        Log.e(TAG, "Recording error: " + error);
        mainHandler.post(() -> {
            String userFriendlyError = getUserFriendlyError(error);
            showToast(userFriendlyError);
            recordingStatus.setVisibility(View.GONE);
            recordButton.setText(getString(R.string.start_recording));
            isRecording = false;
        });
    }
    
    private String getUserFriendlyError(String error) {
        if (error.contains("permission")) {
            return "Cần cấp quyền ghi âm để sử dụng tính năng này";
        } else if (error.contains("in use") || error.contains("busy")) {
            return "Microphone đang được sử dụng bởi ứng dụng khác";
        } else if (error.contains("initialize") || error.contains("availability")) {
            return "Không thể truy cập microphone. Vui lòng kiểm tra thiết bị";
        } else if (error.contains("Invalid operation") || error.contains("Bad value")) {
            return "Lỗi hệ thống âm thanh. Vui lòng thử lại";
        } else {
            return "Lỗi ghi âm: " + error;
        }
    }

    private void handleTranslationSuccess(String original, String translated) {
        fileLogger.d(TAG, "Translation successful:");
        fileLogger.d(TAG, "  Original (" + selectedSourceLanguage.getCode() + "): " + original);
        fileLogger.d(TAG, "  Translated (" + selectedTargetLanguage.getCode() + "): " + translated);
        
        mainHandler.post(() -> {
            recordingStatus.setVisibility(View.GONE);
            
            // Show result on popup if floating button is visible, otherwise on main view
            if (floatingRecordButton != null && floatingRecordButton.isVisible()) {
                fileLogger.d(TAG, "Showing translation result on popup");
                translationResultPopup.showResult(original, translated);
                showToast("Dịch thuật thành công!");
            } else {
                fileLogger.d(TAG, "Showing translation result on main view");
                showTranslationResult(original, translated);
                showToast("Dịch thuật thành công!");
            }
            
            // Add to history
            TranslationHistory history = new TranslationHistory(
                original, translated, 
                selectedSourceLanguage.getName(), 
                selectedTargetLanguage.getName()
            );
            translationHistory.add(0, history); // Add to beginning
            historyAdapter.updateHistory(translationHistory);
            
            fileLogger.d(TAG, "Translation result saved to history");
        });
    }

    private void handleTranslationError(String error) {
        fileLogger.e(TAG, "Translation error: " + error);
        mainHandler.post(() -> {
            recordingStatus.setVisibility(View.GONE);
            showToast("Lỗi dịch thuật: " + error);
        });
    }

    private void showTranslationResult(String original, String translated) {
        originalText.setText(original);
        translatedText.setText(translated);
        translationResult.setVisibility(View.VISIBLE);
    }

    private void copyTranslatedText() {
        String text = translatedText.getText().toString();
        if (!text.isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Translated Text", text);
            clipboard.setPrimaryClip(clip);
            showToast(getString(R.string.copy_text));
        }
    }

    private void shareTranslatedText() {
        String text = translatedText.getText().toString();
        if (!text.isEmpty()) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, text);
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_text))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    private void clearHistory() {
        translationHistory.clear();
        historyAdapter.updateHistory(translationHistory);
        showToast(getString(R.string.clear_history));
    }

    private void initViewSize() {
        view.getViewTreeObserver().addOnGlobalLayoutListener(
            new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    View mainContent = view.findViewById(R.id.mainContent);
                    if (mainContent != null) {
                        mainContent.getLayoutParams().width = ViewsUtils.getViewWidth(400.0f);
                        mainContent.getLayoutParams().height = ViewsUtils.getViewHeight(600f);
                    }
                }
            });
    }

    private void minimizeView() {
        view.findViewById(R.id.windowControls).setVisibility(View.GONE);
        view.findViewById(R.id.mainContent).setVisibility(View.GONE);
        view.findViewById(R.id.minimizedVoiceTranslator).setVisibility(View.VISIBLE);
    }

    private void maximizeView() {
        view.findViewById(R.id.windowControls).setVisibility(View.VISIBLE);
        view.findViewById(R.id.mainContent).setVisibility(View.VISIBLE);
        view.findViewById(R.id.minimizedVoiceTranslator).setVisibility(View.GONE);
    }

    private WindowManager.LayoutParams getLayoutParams() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;
        return params;
    }

    private void addTouchListeners(View view) {
        View.OnTouchListener listener = ViewsUtils.getViewTouchListener(this, view, windowManager, params);
        ViewsUtils.addTouchListener(view, listener, true, true, ListView.class, Spinner.class, Button.class);
    }

    private void showToast(String message) {
        Log.d(TAG, "Showing toast: " + message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
    
    private void showDebugToast(String message) {
        Log.d(TAG, "Debug: " + message);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
            Toast.makeText(this, "Debug: " + message, Toast.LENGTH_LONG).show();
        }
    }

    private String getLastTranscribedText() {
        // In a real implementation, this would store the transcribed text from the audio processing
        // For now, we'll generate sample text based on the selected source language
        switch (selectedSourceLanguage.getCode()) {
            case "vi":
                return "Xin chào, đây là một bản ghi âm mẫu để kiểm tra tính năng dịch thuật.";
            case "en":
                return "Hello, this is a sample audio recording to test the translation feature.";
            case "zh":
                return "你好，这是一个用于测试翻译功能的示例音频录音。";
            case "ja":
                return "こんにちは、これは翻訳機能をテストするためのサンプル音声録音です。";
            case "ko":
                return "안녕하세요, 이것은 번역 기능을 테스트하기 위한 샘플 오디오 녹음입니다.";
            default:
                return "Hello, this is a sample audio recording to test the translation feature.";
        }
    }

    // SystemAudioCapture.SystemAudioCallback implementation
    @Override
    public void onCaptureStarted() {
        Log.d(TAG, "System audio capture started");
        mainHandler.post(() -> {
            recordingStatus.setText("Capturing System Audio...");
            recordingStatus.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void onCaptureData(byte[] data) {
        // Process audio data if needed
        Log.v(TAG, "System audio data received: " + data.length + " bytes");
    }

    @Override
    public void onCaptureStopped(byte[] audioData) {
        fileLogger.d(TAG, "System audio capture stopped, processing audio data...");
        fileLogger.d(TAG, "Audio data size: " + (audioData != null ? audioData.length : 0) + " bytes");
        
        mainHandler.post(() -> {
            recordingStatus.setText("Đang xử lý âm thanh...");
            recordingStatus.setVisibility(View.VISIBLE);
        });
        
        // Process the captured audio data for translation
        processSystemAudioData(audioData);
    }

    @Override
    public void onCaptureError(String error) {
        Log.e(TAG, "System audio capture error: " + error);
        mainHandler.post(() -> {
            String userFriendlyError = getUserFriendlySystemAudioError(error);
            showToast(userFriendlyError);
            recordingStatus.setVisibility(View.GONE);
            systemAudioButton.setText("Capture Video Audio");
            isCapturingSystemAudio = false;
        });
    }

    @Override
    public void onPermissionRequired() {
        Log.d(TAG, "MediaProjection permission required");
        mainHandler.post(() -> {
            systemAudioButton.setText("Requesting Permission...");
            recordingStatus.setText("Yêu cầu quyền capture âm thanh...");
            recordingStatus.setVisibility(View.VISIBLE);
            showToast("Cần cấp quyền để capture âm thanh từ video. Vui lòng cho phép khi được yêu cầu.");
            requestMediaProjectionPermission();
        });
    }
    
    private void processSystemAudioData(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            Log.w(TAG, "No system audio data to process");
            fileLogger.w(TAG, "System audio data is null or empty, length: " + (audioData != null ? audioData.length : 0));
            mainHandler.post(() -> {
                showToast("Không có dữ liệu âm thanh để xử lý");
                recordingStatus.setVisibility(View.GONE);
            });
            return;
        }
        
        fileLogger.d(TAG, "Processing system audio data, size: " + audioData.length + " bytes");
        
        // Show processing status
        mainHandler.post(() -> {
            recordingStatus.setText("Đang xử lý âm thanh...");
            recordingStatus.setVisibility(View.VISIBLE);
        });
        
        // Use SystemAudioTranscriber to convert audio to text, then translate
        SystemAudioTranscriber transcriber = new SystemAudioTranscriber(this, googleAIClient);
        transcriber.transcribeAudio(audioData, selectedSourceLanguage.getCode(), new SystemAudioTranscriber.TranscriptionCallback() {
            @Override
            public void onTranscriptionSuccess(String transcribedText) {
                fileLogger.d(TAG, "System audio transcription successful: " + transcribedText);
                
                if (transcribedText == null || transcribedText.trim().isEmpty()) {
                    fileLogger.w(TAG, "Transcribed text is empty");
                    mainHandler.post(() -> {
                        showToast("Không phát hiện được giọng nói trong âm thanh");
                        recordingStatus.setVisibility(View.GONE);
                    });
                    return;
                }
                
                // Update UI to show transcription
                mainHandler.post(() -> {
                    recordingStatus.setText("Đang dịch văn bản...");
                });
                
                // Now translate the transcribed text
                googleAIClient.translateText(
                    transcribedText,
                    selectedSourceLanguage.getCode(),
                    selectedTargetLanguage.getCode(),
                    new GoogleAIClient.TranslationCallback() {
                        @Override
                        public void onSuccess(String translatedText) {
                            fileLogger.d(TAG, "System audio translation successful");
                            handleFloatingTranslationSuccess(transcribedText, translatedText);
                        }

                        @Override
                        public void onError(String error) {
                            fileLogger.e(TAG, "System audio translation failed: " + error);
                            handleTranslationError("Lỗi dịch thuật: " + error);
                        }
                    }
                );
            }

            @Override
            public void onTranscriptionError(String error) {
                fileLogger.e(TAG, "System audio transcription failed: " + error);
                mainHandler.post(() -> {
                    String userFriendlyError = getUserFriendlyTranscriptionError(error);
                    showToast(userFriendlyError);
                    recordingStatus.setVisibility(View.GONE);
                });
            }
        });
    }
    
    private String getUserFriendlySystemAudioError(String error) {
        if (error.contains("Android 10")) {
            return "Tính năng capture âm thanh hệ thống cần Android 10 trở lên";
        } else if (error.contains("permission") || error.contains("MediaProjection")) {
            return "Cần cấp quyền để capture âm thanh từ video";
        } else if (error.contains("initialize") || error.contains("failed")) {
            return "Không thể khởi tạo capture âm thanh hệ thống";
        } else {
            return "Lỗi capture âm thanh: " + error;
        }
    }
    
    private void requestMediaProjectionPermission() {
        try {
            Intent intent = new Intent(this, MediaProjectionActivity.class);
            intent.setAction(MediaProjectionActivity.ACTION_REQUEST_MEDIA_PROJECTION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            showToast("Vui lòng cho phép chia sẻ màn hình để capture âm thanh");
            fileLogger.d(TAG, "Started MediaProjectionActivity to request permission");
        } catch (Exception e) {
            fileLogger.e(TAG, "Failed to start MediaProjectionActivity", e);
            showToast("Không thể yêu cầu quyền capture âm thanh");
        }
    }

    // Handle transcription results from RealTimeTranscriber
    private void handleTranscriptionResult(String transcribedText) {
        Log.d(TAG, "Transcription result: " + transcribedText);
        lastTranscribedText = transcribedText;
        
        // Automatically translate the transcribed text
        googleAIClient.translateText(
            transcribedText,
            selectedSourceLanguage.getCode(),
            selectedTargetLanguage.getCode(),
            new GoogleAIClient.TranslationCallback() {
                @Override
                public void onSuccess(String translatedText) {
                    handleTranslationSuccess(transcribedText, translatedText);
                }

                @Override
                public void onError(String error) {
                    handleTranslationError(error);
                }
            }
        );
    }
    
    private void handleTranscriptionError(String error) {
        Log.e(TAG, "Transcription error: " + error);
        mainHandler.post(() -> {
            String userFriendlyError = getUserFriendlyTranscriptionError(error);
            showToast(userFriendlyError);
            recordingStatus.setVisibility(View.GONE);
        });
    }
    
    private String getUserFriendlyTranscriptionError(String error) {
        if (error.contains("No speech")) {
            return "Không phát hiện giọng nói. Vui lòng thử lại.";
        } else if (error.contains("Network")) {
            return "Lỗi mạng. Kiểm tra kết nối internet.";
        } else if (error.contains("permissions")) {
            return "Cần quyền ghi âm để sử dụng tính năng này.";
        } else if (error.contains("not available")) {
            return "Tính năng nhận dạng giọng nói không khả dụng trên thiết bị này.";
        } else {
            return "Lỗi nhận dạng giọng nói: " + error;
        }
    }

    private void showDebugLogMenu() {
        if (fileLogger == null) {
            showToast("File logger not initialized");
            return;
        }
        
        // Check storage permission first
        if (!hasStoragePermission()) {
            showToast("Cần quyền truy cập storage để ghi log file. Vui lòng cấp quyền trong Settings.");
            openAppSettings();
            return;
        }
        
        // Show menu with options
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Debug Log Options");
        
        String[] options = {
            "View Log File Path",
            "Share Log File", 
            "Clear Log File",
            "Open Log File",
            "Copy Log Path"
        };
        
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    showLogFileInfo();
                    break;
                case 1:
                    shareLogFile();
                    break;
                case 2:
                    clearLogFile();
                    break;
                case 3:
                    openLogFile();
                    break;
                case 4:
                    copyLogPath();
                    break;
            }
        });
        
        builder.setNegativeButton("Cancel", null);
        
        try {
            android.app.AlertDialog dialog = builder.create();
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            dialog.show();
        } catch (Exception e) {
            fileLogger.e(TAG, "Failed to show debug log menu", e);
            showToast("Cannot show menu. Check overlay permission.");
        }
    }
    
    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ uses scoped storage, external files dir doesn't need permission
            return true;
        } else {
            // Android 10 and below need WRITE_EXTERNAL_STORAGE
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    private void openAppSettings() {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            fileLogger.e(TAG, "Failed to open app settings", e);
            showToast("Cannot open settings");
        }
    }
    
    private void showLogFileInfo() {
        if (fileLogger != null) {
            String logPath = fileLogger.getLogFilePath();
            fileLogger.i(TAG, "=== LOG FILE INFO REQUESTED ===");
            fileLogger.i(TAG, "Log file location: " + logPath);
            fileLogger.i(TAG, "You can find this file in your device storage");
            fileLogger.i(TAG, "Share this file for debugging support");
            fileLogger.i(TAG, "===============================");
            
            showToast("Log file: " + logPath);
        }
    }
    
    private void shareLogFile() {
        if (fileLogger == null) return;
        
        try {
            String logPath = fileLogger.getLogFilePath();
            java.io.File logFile = new java.io.File(logPath);
            
            if (!logFile.exists()) {
                showToast("Log file not found");
                return;
            }
            
            android.net.Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                this, 
                getPackageName() + ".fileprovider", 
                logFile
            );
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Voice Translator Debug Log");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Debug log file from Voice Translator app");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            startActivity(Intent.createChooser(shareIntent, "Share Log File").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            
        } catch (Exception e) {
            fileLogger.e(TAG, "Failed to share log file", e);
            showToast("Cannot share log file: " + e.getMessage());
        }
    }
    
    private void clearLogFile() {
        if (fileLogger != null) {
            fileLogger.clearLog();
            showToast("Log file cleared");
        }
    }
    
    private void openLogFile() {
        if (fileLogger == null) return;
        
        try {
            String logPath = fileLogger.getLogFilePath();
            java.io.File logFile = new java.io.File(logPath);
            
            if (!logFile.exists()) {
                showToast("Log file not found");
                return;
            }
            
            android.net.Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                this, 
                getPackageName() + ".fileprovider", 
                logFile
            );
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, "text/plain");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            startActivity(intent);
            
        } catch (Exception e) {
            fileLogger.e(TAG, "Failed to open log file", e);
            showToast("Cannot open log file. Try sharing instead.");
        }
    }
    
    private void copyLogPath() {
        if (fileLogger != null) {
            String logPath = fileLogger.getLogFilePath();
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Log Path", logPath);
            clipboard.setPrimaryClip(clip);
            showToast("Log path copied to clipboard");
        }
    }
    
    // New methods for floating record functionality
    
    private void showFloatingRecordButton() {
        if (floatingRecordButton != null) {
            // Check overlay permission first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    fileLogger.e(TAG, "Cannot show floating button - missing overlay permission");
                    showToast("Cần cấp quyền hiển thị trên ứng dụng khác để hiện nút ghi âm");
                    return;
                }
            }
            
            try {
                floatingRecordButton.show();
                fileLogger.d(TAG, "Floating record button shown successfully");
                showToast("Nút ghi âm đã xuất hiện! Tìm nút tròn màu xanh ở bên phải màn hình.");
            } catch (Exception e) {
                fileLogger.e(TAG, "Error showing floating record button", e);
                showToast("Lỗi hiển thị nút ghi âm: " + e.getMessage());
            }
        } else {
            fileLogger.e(TAG, "FloatingRecordButton is null!");
            showToast("Lỗi: Nút ghi âm chưa được khởi tạo");
        }
    }
    
    private void hideFloatingRecordButton() {
        if (floatingRecordButton != null) {
            floatingRecordButton.hide();
            fileLogger.d(TAG, "Floating record button hidden");
        }
    }
    
    private void startFloatingRecording() {
        fileLogger.d(TAG, "Starting floating recording...");
        
        if (mediaProjection == null) {
            fileLogger.e(TAG, "MediaProjection is null, cannot start recording");
            floatingRecordButton.showStatusText("Lỗi: Không có quyền ghi âm");
            return;
        }
        
        // Start system audio capture
        if (systemAudioCapture.startSystemAudioCapture()) {
            isCapturingSystemAudio = true;
            floatingRecordButton.setRecordingState(true);
            floatingRecordButton.showStatusText("Đang ghi âm...");
            fileLogger.d(TAG, "System audio capture started from floating button");
        } else {
            fileLogger.e(TAG, "Failed to start system audio capture from floating button");
            floatingRecordButton.showStatusText("Lỗi ghi âm");
        }
    }
    
    private void stopFloatingRecording() {
        fileLogger.d(TAG, "Stopping floating recording...");
        
        if (isCapturingSystemAudio) {
            systemAudioCapture.stopSystemAudioCapture();
            isCapturingSystemAudio = false;
            floatingRecordButton.setRecordingState(false);
            floatingRecordButton.showStatusText("Đang xử lý...");
            fileLogger.d(TAG, "System audio capture stopped from floating button");
        }
    }
    
    private void handleFloatingTranslationSuccess(String original, String translated) {
        fileLogger.d(TAG, "Floating translation successful:");
        fileLogger.d(TAG, "  Original (" + selectedSourceLanguage.getCode() + "): " + original);
        fileLogger.d(TAG, "  Translated (" + selectedTargetLanguage.getCode() + "): " + translated);
        
        mainHandler.post(() -> {
            // Hide status text on floating button
            floatingRecordButton.showStatusText("Hoàn thành!");
            
            // Show translation result in popup
            if (translationResultPopup != null) {
                translationResultPopup.showResult(original, translated);
            }
            
            // Add to history
            TranslationHistory history = new TranslationHistory(
                original, translated, 
                selectedSourceLanguage.getName(), 
                selectedTargetLanguage.getName()
            );
            translationHistory.add(0, history); // Add to beginning
            if (historyAdapter != null) {
                historyAdapter.updateHistory(translationHistory);
            }
            
            fileLogger.d(TAG, "Translation result shown in popup");
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        if (fileLogger != null) {
            fileLogger.i(TAG, "=== VoiceTranslatorService onDestroy ===");
        }
        
        // Stop foreground service
        if (isForegroundServiceStarted) {
            stopForeground(true);
            isForegroundServiceStarted = false;
        }
        
        if (audioRecorder != null) {
            audioRecorder.release();
        }
        if (systemAudioCapture != null) {
            systemAudioCapture.release();
        }
        if (realTimeTranscriber != null) {
            realTimeTranscriber.destroy();
        }
        if (windowManager != null && view != null) {
            try {
                windowManager.removeView(view);
            } catch (Exception e) {
                fileLogger.w(TAG, "Error removing main view: " + e.getMessage());
            }
        }
        
        // Clean up floating components
        if (floatingRecordButton != null) {
            floatingRecordButton.destroy();
            floatingRecordButton = null;
        }
        if (translationResultPopup != null) {
            translationResultPopup.destroy();
            translationResultPopup = null;
        }
        
        // Keep MediaProjection for next time (don't release it)
        // mediaProjection will be stored in staticMediaProjection
        fileLogger.d(TAG, "Service destroyed, MediaProjection preserved for next session");
    }
    
    // Gemini Stream Translation handlers
    private void handleGeminiTranslationSuccess(String originalText, String translatedText) {
        fileLogger.d(TAG, "Gemini translation successful:");
        fileLogger.d(TAG, "  Original: " + originalText);
        fileLogger.d(TAG, "  Translated: " + translatedText);
        
        mainHandler.post(() -> {
            // Show result on popup if floating button is visible, otherwise on main view
            if (floatingRecordButton != null && floatingRecordButton.isVisible()) {
                fileLogger.d(TAG, "Showing Gemini translation result on popup");
                translationResultPopup.showResult(originalText, translatedText);
                showToast("Gemini dịch thuật thành công!");
            } else {
                fileLogger.d(TAG, "Showing Gemini translation result on main view");
                showTranslationResult(originalText, translatedText);
                showToast("Gemini dịch thuật thành công!");
            }
            
            // Add to history
            TranslationHistory history = new TranslationHistory(
                originalText, 
                translatedText, 
                selectedSourceLanguage != null ? selectedSourceLanguage.getName() : "Auto",
                selectedTargetLanguage != null ? selectedTargetLanguage.getName() : "Vietnamese"
            );
            translationHistory.add(0, history);
            
            if (historyAdapter != null) {
                historyAdapter.updateHistory(translationHistory);
            }
        });
    }
    
    private void handleGeminiTranslationError(String error) {
        fileLogger.e(TAG, "Gemini translation error: " + error);
        
        mainHandler.post(() -> {
            showToast("Lỗi Gemini: " + error);
            // Update status on UI
            if (statusText != null) {
                statusText.setText("Lỗi Gemini: " + error);
                statusText.setVisibility(View.VISIBLE);
            }
        });
    }
    
    private void handleGeminiStatusUpdate(String status) {
        fileLogger.d(TAG, "Gemini status: " + status);
        
        mainHandler.post(() -> {
            showToast(status);
            // Update status on UI
            if (statusText != null) {
                statusText.setText(status);
                statusText.setVisibility(View.VISIBLE);
            }
        });
    }
    
    // Methods to control Gemini Stream Translation
    private void startGeminiStreamTranslation() {
        if (geminiStreamTranslator != null && mediaProjection != null) {
            // Set languages based on current selection
            String sourceLang = selectedSourceLanguage != null ? selectedSourceLanguage.getCode() : "auto";
            String targetLang = selectedTargetLanguage != null ? selectedTargetLanguage.getCode() : "vi";
            
            geminiStreamTranslator.setLanguages(sourceLang, targetLang);
            geminiStreamTranslator.startStreamTranslation(mediaProjection);
            
            isGeminiStreamActive = true;
            fileLogger.d(TAG, "Started Gemini stream translation");
        } else {
            showToast("Cần quyền chia sẻ màn hình để sử dụng Gemini");
            fileLogger.w(TAG, "Cannot start Gemini stream translation - missing MediaProjection");
        }
    }
    
    private void stopGeminiStreamTranslation() {
        if (geminiStreamTranslator != null) {
            geminiStreamTranslator.stopStreamTranslation();
            isGeminiStreamActive = false;
            fileLogger.d(TAG, "Stopped Gemini stream translation");
        }
    }
    
    // Gemini Stream Translation toggle
    private void toggleGeminiStreamTranslation() {
        fileLogger.d(TAG, "Toggle Gemini Stream button pressed. Current state: " + 
                     (isGeminiStreamActive ? "active" : "inactive"));
        
        if (!hasMediaProjectionPermission || mediaProjection == null) {
            showToast("Cần quyền chia sẻ màn hình trước! Nhấn 'Record Video Audio' trước.");
            fileLogger.w(TAG, "Cannot start Gemini Stream - missing MediaProjection permission");
            return;
        }
        
        if (isGeminiStreamActive) {
            fileLogger.d(TAG, "Stopping Gemini Stream translation...");
            stopGeminiStreamTranslation();
        } else {
            fileLogger.d(TAG, "Starting Gemini Stream translation...");
            startGeminiStreamTranslation();
        }
        
        updateGeminiStreamUI();
    }
    
    private void updateGeminiStreamUI() {
        mainHandler.post(() -> {
            if (geminiStreamButton != null) {
                if (isGeminiStreamActive) {
                    geminiStreamButton.setText("Stop Gemini");
                    geminiStreamButton.setBackgroundResource(R.drawable.round_button_pressed);
                } else {
                    geminiStreamButton.setText("Gemini Stream");
                    geminiStreamButton.setBackgroundResource(R.drawable.round_button);
                }
            }
        });
    }
    
    // Static method to clear MediaProjection when app is fully closed
    public static void clearMediaProjectionPermission() {
        if (staticMediaProjection != null) {
            try {
                staticMediaProjection.stop();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
            staticMediaProjection = null;
        }
        staticHasPermission = false;
    }
}