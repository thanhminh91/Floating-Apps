package damjay.floating.projects.voicetranslator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class SystemAudioCapture {
    private static final String TAG = "SystemAudioCapture";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    
    private AudioRecord audioRecord;
    private boolean isCapturing = false;
    private Thread captureThread;
    private ByteArrayOutputStream audioBuffer;
    private int bufferSize;
    private MediaProjection mediaProjection;
    private Context context;
    private FileLogger fileLogger;

    public interface SystemAudioCallback {
        void onCaptureStarted();
        void onCaptureData(byte[] data);
        void onCaptureStopped(byte[] audioData);
        void onCaptureError(String error);
        void onPermissionRequired(); // Need to request MediaProjection permission
    }

    private SystemAudioCallback callback;

    public SystemAudioCapture(Context context, SystemAudioCallback callback) {
        this.context = context;
        this.callback = callback;
        this.audioBuffer = new ByteArrayOutputStream();
        this.fileLogger = FileLogger.getInstance(context);
        
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2; // Fallback buffer size
        }
        
        fileLogger.d(TAG, "SystemAudioCapture initialized with buffer size: " + bufferSize);
    }

    public void setMediaProjection(MediaProjection mediaProjection) {
        this.mediaProjection = mediaProjection;
    }

    public boolean startSystemAudioCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            callback.onCaptureError("System audio capture requires Android 10 or higher");
            return false;
        }

        if (isCapturing) {
            Log.w(TAG, "System audio capture already in progress");
            return false;
        }

        if (mediaProjection == null) {
            Log.w(TAG, "MediaProjection not available, requesting permission");
            callback.onPermissionRequired();
            return false;
        }

        try {
            fileLogger.d(TAG, "Creating AudioPlaybackCaptureConfiguration...");
            // Create AudioPlaybackCaptureConfiguration for system audio
            AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA) // Capture media audio (videos, music)
                .addMatchingUsage(AudioAttributes.USAGE_GAME) // Capture game audio
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN) // Capture other audio
                .build();
            fileLogger.d(TAG, "AudioPlaybackCaptureConfiguration created successfully");

            fileLogger.d(TAG, "Creating AudioFormat...");
            AudioFormat audioFormat = new AudioFormat.Builder()
                .setEncoding(AUDIO_FORMAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build();
            fileLogger.d(TAG, "AudioFormat created: " + SAMPLE_RATE + "Hz, Mono, 16-bit");

            fileLogger.d(TAG, "Creating AudioRecord with buffer size: " + bufferSize);
            audioRecord = new AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(config)
                .build();
            fileLogger.d(TAG, "AudioRecord created");

            fileLogger.d(TAG, "Checking AudioRecord state...");
            int state = audioRecord.getState();
            fileLogger.d(TAG, "AudioRecord state: " + state + " (STATE_INITIALIZED=" + AudioRecord.STATE_INITIALIZED + ")");
            
            if (state != AudioRecord.STATE_INITIALIZED) {
                fileLogger.e(TAG, "Failed to initialize AudioRecord for system audio capture. State: " + state);
                callback.onCaptureError("Failed to initialize system audio capture. State: " + state);
                return false;
            }

            fileLogger.d(TAG, "AudioRecord initialized successfully, starting recording...");
            audioBuffer.reset();
            isCapturing = true;
            audioRecord.startRecording();

            int recordingState = audioRecord.getRecordingState();
            fileLogger.d(TAG, "AudioRecord recording state: " + recordingState + " (RECORDSTATE_RECORDING=" + AudioRecord.RECORDSTATE_RECORDING + ")");
            
            if (recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                fileLogger.e(TAG, "Failed to start system audio recording. Recording state: " + recordingState);
                isCapturing = false;
                audioRecord.release();
                audioRecord = null;
                callback.onCaptureError("Failed to start system audio recording. State: " + recordingState);
                return false;
            }

            captureThread = new Thread(this::captureLoop);
            captureThread.start();

            callback.onCaptureStarted();
            fileLogger.d(TAG, "System audio capture started successfully");
            return true;

        } catch (Exception e) {
            fileLogger.e(TAG, "Error starting system audio capture", e);
            callback.onCaptureError("Error starting system audio capture: " + e.getMessage());
            return false;
        }
    }

    public void stopSystemAudioCapture() {
        if (!isCapturing) {
            return;
        }

        isCapturing = false;

        if (captureThread != null) {
            try {
                captureThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Error waiting for capture thread to finish", e);
            }
        }

        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping AudioRecord", e);
            }
            audioRecord = null;
        }

        byte[] audioData = audioBuffer.toByteArray();
        callback.onCaptureStopped(audioData);
    }

    private void captureLoop() {
        byte[] buffer = new byte[bufferSize];
        int consecutiveErrors = 0;
        int consecutiveZeroReads = 0;
        final int MAX_CONSECUTIVE_ERRORS = 5;
        final int MAX_CONSECUTIVE_ZERO_READS = 100; // Allow more zero reads for system audio
        
        long totalBytesRead = 0;
        long startTime = System.currentTimeMillis();

        fileLogger.d(TAG, "System audio capture loop started with buffer size: " + bufferSize);
        
        while (isCapturing && audioRecord != null) {
            try {
                int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                
                if (bytesRead > 0) {
                    audioBuffer.write(buffer, 0, bytesRead);
                    callback.onCaptureData(buffer);
                    consecutiveErrors = 0; // Reset error counter on successful read
                    consecutiveZeroReads = 0; // Reset zero read counter
                    totalBytesRead += bytesRead;
                    
                    // Log progress every 5 seconds
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - startTime > 5000) {
                        fileLogger.d(TAG, "Captured " + totalBytesRead + " bytes so far");
                        startTime = currentTime;
                    }
                    
                } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                    fileLogger.e(TAG, "Invalid operation during system audio capture");
                    consecutiveErrors++;
                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                        callback.onCaptureError("System audio capture failed: Invalid operation");
                        break;
                    }
                } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                    fileLogger.e(TAG, "Bad value during system audio capture");
                    consecutiveErrors++;
                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                        callback.onCaptureError("System audio capture failed: Bad value");
                        break;
                    }
                } else if (bytesRead == AudioRecord.ERROR_DEAD_OBJECT) {
                    fileLogger.e(TAG, "AudioRecord object is dead");
                    callback.onCaptureError("System audio capture failed: Audio system error");
                    break;
                } else if (bytesRead == 0) {
                    // No data available, this is common for system audio
                    consecutiveZeroReads++;
                    if (consecutiveZeroReads >= MAX_CONSECUTIVE_ZERO_READS) {
                        fileLogger.w(TAG, "No system audio data received for extended period (" + consecutiveZeroReads + " consecutive zero reads)");
                        // Reset counter but continue - system audio might be silent
                        consecutiveZeroReads = 0;
                    }
                    try {
                        Thread.sleep(10); // Brief pause to avoid busy waiting
                    } catch (InterruptedException e) {
                        fileLogger.d(TAG, "System audio capture thread interrupted");
                        break;
                    }
                }
            } catch (Exception e) {
                fileLogger.e(TAG, "Error during system audio capture", e);
                consecutiveErrors++;
                if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                    callback.onCaptureError("System audio capture error: " + e.getMessage());
                    break;
                }
                try {
                    Thread.sleep(50); // Brief pause before retry
                } catch (InterruptedException ie) {
                    fileLogger.d(TAG, "System audio capture thread interrupted during error recovery");
                    break;
                }
            }
        }
        
        fileLogger.d(TAG, "System audio capture loop ended. Total bytes captured: " + totalBytesRead);
    }

    public boolean isCapturing() {
        return isCapturing;
    }

    public void release() {
        stopSystemAudioCapture();
        try {
            audioBuffer.close();
        } catch (IOException e) {
            Log.e(TAG, "Error closing audio buffer", e);
        }
        
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    public static Intent createMediaProjectionIntent(Context context) {
        MediaProjectionManager mediaProjectionManager = 
            (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        return mediaProjectionManager.createScreenCaptureIntent();
    }

    public static MediaProjection getMediaProjection(Context context, int resultCode, Intent data) {
        MediaProjectionManager mediaProjectionManager = 
            (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        return mediaProjectionManager.getMediaProjection(resultCode, data);
    }
}