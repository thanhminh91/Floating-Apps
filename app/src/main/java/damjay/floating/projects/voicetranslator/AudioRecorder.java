package damjay.floating.projects.voicetranslator;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class AudioRecorder {
    private static final String TAG = "AudioRecorder";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread;
    private ByteArrayOutputStream audioBuffer;
    private int bufferSize;

    public interface RecordingCallback {
        void onRecordingStarted();
        void onRecordingData(byte[] data);
        void onRecordingStopped(byte[] audioData);
        void onRecordingError(String error);
    }

    private RecordingCallback callback;

    public AudioRecorder(RecordingCallback callback) {
        this.callback = callback;
        this.audioBuffer = new ByteArrayOutputStream();
        
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2; // Fallback buffer size
        }
    }

    public boolean startRecording() {
        if (isRecording) {
            Log.w(TAG, "Recording already in progress");
            return false;
        }

        try {
            // Try different audio sources for better compatibility
            AudioRecord testRecord = null;
            int[] audioSources = {
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION
            };
            
            for (int audioSource : audioSources) {
                try {
                    testRecord = new AudioRecord(
                        audioSource,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        bufferSize
                    );
                    
                    if (testRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                        audioRecord = testRecord;
                        Log.d(TAG, "Successfully initialized AudioRecord with source: " + audioSource);
                        break;
                    } else {
                        testRecord.release();
                        testRecord = null;
                    }
                } catch (Exception e) {
                    if (testRecord != null) {
                        testRecord.release();
                        testRecord = null;
                    }
                    Log.w(TAG, "Failed to initialize AudioRecord with source " + audioSource + ": " + e.getMessage());
                }
            }

            if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed with all audio sources");
                callback.onRecordingError("Failed to initialize audio recorder. Please check microphone permissions and availability.");
                return false;
            }

            audioBuffer.reset();
            isRecording = true;
            
            // Start recording with error handling
            try {
                audioRecord.startRecording();
                
                // Verify recording state
                if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                    Log.e(TAG, "AudioRecord failed to start recording");
                    isRecording = false;
                    audioRecord.release();
                    audioRecord = null;
                    callback.onRecordingError("Failed to start recording. Microphone may be in use by another app.");
                    return false;
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "IllegalStateException when starting recording", e);
                isRecording = false;
                audioRecord.release();
                audioRecord = null;
                callback.onRecordingError("Cannot start recording: " + e.getMessage());
                return false;
            }

            recordingThread = new Thread(this::recordingLoop);
            recordingThread.start();

            callback.onRecordingStarted();
            Log.d(TAG, "Recording started successfully");
            return true;

        } catch (SecurityException e) {
            Log.e(TAG, "Recording permission not granted", e);
            callback.onRecordingError("Recording permission not granted");
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error starting recording", e);
            callback.onRecordingError("Error starting recording: " + e.getMessage());
            return false;
        }
    }

    public void stopRecording() {
        if (!isRecording) {
            return;
        }

        isRecording = false;

        if (recordingThread != null) {
            try {
                recordingThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Error waiting for recording thread to finish", e);
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
        callback.onRecordingStopped(audioData);
    }

    private void recordingLoop() {
        byte[] buffer = new byte[bufferSize];
        int consecutiveErrors = 0;
        final int MAX_CONSECUTIVE_ERRORS = 5;

        Log.d(TAG, "Recording loop started");
        
        while (isRecording && audioRecord != null) {
            try {
                int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                
                if (bytesRead > 0) {
                    audioBuffer.write(buffer, 0, bytesRead);
                    callback.onRecordingData(buffer);
                    consecutiveErrors = 0; // Reset error counter on successful read
                } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e(TAG, "Invalid operation during recording");
                    consecutiveErrors++;
                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                        callback.onRecordingError("Recording failed: Invalid operation");
                        break;
                    }
                } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e(TAG, "Bad value during recording");
                    consecutiveErrors++;
                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                        callback.onRecordingError("Recording failed: Bad value");
                        break;
                    }
                } else if (bytesRead == AudioRecord.ERROR_DEAD_OBJECT) {
                    Log.e(TAG, "AudioRecord object is dead");
                    callback.onRecordingError("Recording failed: Audio system error");
                    break;
                } else if (bytesRead == 0) {
                    // No data available, continue
                    consecutiveErrors++;
                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                        Log.w(TAG, "No audio data received for extended period");
                        callback.onRecordingError("No audio data received. Check microphone availability.");
                        break;
                    }
                    try {
                        Thread.sleep(10); // Brief pause to avoid busy waiting
                    } catch (InterruptedException e) {
                        Log.d(TAG, "Recording thread interrupted");
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during recording", e);
                consecutiveErrors++;
                if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                    callback.onRecordingError("Recording error: " + e.getMessage());
                    break;
                }
                try {
                    Thread.sleep(50); // Brief pause before retry
                } catch (InterruptedException ie) {
                    Log.d(TAG, "Recording thread interrupted during error recovery");
                    break;
                }
            }
        }
        
        Log.d(TAG, "Recording loop ended");
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void release() {
        stopRecording();
        try {
            audioBuffer.close();
        } catch (IOException e) {
            Log.e(TAG, "Error closing audio buffer", e);
        }
    }
}