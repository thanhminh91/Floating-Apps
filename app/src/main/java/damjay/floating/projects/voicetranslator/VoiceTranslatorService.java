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
    private TextView recordingStatus;
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
    
    // State
    private boolean isRecording = false;
    private boolean isCapturingSystemAudio = false;
    private boolean isForegroundServiceStarted = false;
    private Language selectedSourceLanguage;
    private Language selectedTargetLanguage;
    private MediaProjection mediaProjection;
    private String lastTranscribedText = "";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Ensure we're running as foreground service for MediaProjection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isForegroundServiceStarted) {
            try {
                startForegroundService();
            } catch (Exception e) {
                fileLogger.e(TAG, "Failed to start foreground service in onStartCommand", e);
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
                fileLogger.d(TAG, "Creating MediaProjection...");
                mediaProjection = SystemAudioCapture.getMediaProjection(this, resultCode, data);
                
                if (mediaProjection != null) {
                    fileLogger.d(TAG, "MediaProjection created successfully");
                    systemAudioCapture.setMediaProjection(mediaProjection);
                    
                    // Now start system audio capture
                    fileLogger.d(TAG, "Starting system audio capture with MediaProjection...");
                    if (systemAudioCapture.startSystemAudioCapture()) {
                        isCapturingSystemAudio = true;
                        updateSystemAudioUI();
                        fileLogger.d(TAG, "System audio capture started successfully after permission granted");
                        showDebugToast("System audio capture started!");
                    } else {
                        fileLogger.e(TAG, "Failed to start system audio capture even after permission granted");
                        showToast("Không thể bắt đầu capture âm thanh hệ thống");
                    }
                } else {
                    fileLogger.e(TAG, "MediaProjection is null after creation");
                    showToast("Không thể tạo MediaProjection");
                }
            } catch (Exception e) {
                fileLogger.e(TAG, "Error handling MediaProjection result", e);
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
        
        // Start as foreground service for MediaProjection
        startForegroundService();
        
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
        
        // Setup UI
        setupView();
        setupLanguageSpinners();
        setupEventListeners();
        
        // Add view to window
        windowManager.addView(view, params);
        windowManager.updateViewLayout(view, params);
        addTouchListeners(view);
        
        minimizeView();
        
        // Check initial permissions and microphone availability
        checkInitialSetup();
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
            return; // Already started
        }
        
        createNotificationChannel();
        
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Voice Translator")
                .setContentText("Voice translation service is running")
                .setSmallIcon(R.drawable.voice_translator_logo)
                .setContentIntent(pendingIntent)
                .build();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        
        isForegroundServiceStarted = true;
        fileLogger.d(TAG, "Started foreground service with MediaProjection type");
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
        recordingStatus = view.findViewById(R.id.recordingStatus);
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
        
        if (isCapturingSystemAudio) {
            fileLogger.d(TAG, "Stopping system audio capture...");
            stopSystemAudioCapture();
        } else {
            fileLogger.d(TAG, "Starting system audio capture...");
            startSystemAudioCapture();
        }
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
            showTranslationResult(original, translated);
            showToast("Dịch thuật thành công!");
            
            // Add to history
            TranslationHistory history = new TranslationHistory(
                original, translated, 
                selectedSourceLanguage.getName(), 
                selectedTargetLanguage.getName()
            );
            translationHistory.add(0, history); // Add to beginning
            historyAdapter.updateHistory(translationHistory);
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
                            handleTranslationSuccess(transcribedText, translatedText);
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        if (fileLogger != null) {
            fileLogger.i(TAG, "=== VoiceTranslatorService onDestroy ===");
        }
        
        // Stop foreground service
        stopForeground(true);
        
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
            windowManager.removeView(view);
        }
    }
}