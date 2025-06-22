package damjay.floating.projects.voicetranslator;

import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Locale;

public class SystemAudioTranscriber {
    private static final String TAG = "SystemAudioTranscriber";
    
    private Context context;
    private FileLogger fileLogger;
    private Handler mainHandler;
    private GoogleAIClient googleAIClient;
    
    public interface TranscriptionCallback {
        void onTranscriptionSuccess(String transcribedText);
        void onTranscriptionError(String error);
    }
    
    public SystemAudioTranscriber(Context context, GoogleAIClient googleAIClient) {
        this.context = context;
        this.fileLogger = FileLogger.getInstance(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.googleAIClient = googleAIClient;
    }
    
    public void transcribeAudio(byte[] audioData, String languageCode, TranscriptionCallback callback) {
        fileLogger.d(TAG, "Starting audio transcription, data size: " + audioData.length + " bytes, language: " + languageCode);
        
        // Run transcription in background thread
        new Thread(() -> {
            try {
                // First, try to save audio data to a temporary file and use SpeechRecognizer
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    transcribeWithSpeechRecognizer(audioData, languageCode, callback);
                } else {
                    // Fallback: Use a simulated transcription based on audio analysis
                    transcribeWithFallback(audioData, languageCode, callback);
                }
            } catch (Exception e) {
                fileLogger.e(TAG, "Error during transcription", e);
                mainHandler.post(() -> callback.onTranscriptionError("Lỗi trong quá trình nhận dạng giọng nói: " + e.getMessage()));
            }
        }).start();
    }
    
    private void transcribeWithSpeechRecognizer(byte[] audioData, String languageCode, TranscriptionCallback callback) {
        fileLogger.d(TAG, "Using SpeechRecognizer for transcription");
        
        // Since SpeechRecognizer typically works with live audio input, 
        // we'll use a different approach for pre-recorded audio data
        
        // For system audio, we need to analyze the audio data and extract speech
        // This is a complex task that typically requires specialized libraries
        
        // For now, we'll implement a basic approach that analyzes audio characteristics
        // and provides intelligent sample text based on the audio properties
        analyzeAudioAndProvideTranscription(audioData, languageCode, callback);
    }
    
    private void analyzeAudioAndProvideTranscription(byte[] audioData, String languageCode, TranscriptionCallback callback) {
        fileLogger.d(TAG, "Analyzing audio data for transcription");
        
        try {
            // Analyze audio characteristics
            AudioAnalysisResult analysis = analyzeAudioData(audioData);
            
            fileLogger.d(TAG, "Audio analysis result: " + analysis.toString());
            
            if (!analysis.hasSpeech) {
                mainHandler.post(() -> callback.onTranscriptionError("Không phát hiện được giọng nói trong âm thanh"));
                return;
            }
            
            // Use Google AI Studio to transcribe the actual audio
            generateTranscriptionBasedOnAnalysis(analysis, languageCode, audioData, callback);
            
        } catch (Exception e) {
            fileLogger.e(TAG, "Error analyzing audio data", e);
            mainHandler.post(() -> callback.onTranscriptionError("Lỗi phân tích âm thanh: " + e.getMessage()));
        }
    }
    
    private void transcribeWithFallback(byte[] audioData, String languageCode, TranscriptionCallback callback) {
        fileLogger.d(TAG, "Using fallback transcription method");
        
        // Simulate processing time
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            mainHandler.post(() -> callback.onTranscriptionError("Quá trình nhận dạng bị gián đoạn"));
            return;
        }
        
        // Analyze audio and provide transcription
        analyzeAudioAndProvideTranscription(audioData, languageCode, callback);
    }
    
    private AudioAnalysisResult analyzeAudioData(byte[] audioData) {
        AudioAnalysisResult result = new AudioAnalysisResult();
        
        if (audioData == null || audioData.length < 1000) { // Less than ~0.02 seconds at 44.1kHz
            result.hasSpeech = false;
            result.confidence = 0.0f;
            return result;
        }
        
        // Convert byte array to 16-bit samples
        short[] samples = new short[audioData.length / 2];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) ((audioData[i * 2 + 1] << 8) | (audioData[i * 2] & 0xFF));
        }
        
        // Calculate basic audio characteristics
        result.averageAmplitude = calculateAverageAmplitude(samples);
        result.maxAmplitude = calculateMaxAmplitude(samples);
        result.zeroCrossingRate = calculateZeroCrossingRate(samples);
        result.duration = (float) samples.length / 44100.0f; // Assuming 44.1kHz sample rate
        
        // Determine if audio contains speech based on characteristics
        result.hasSpeech = determineSpeechPresence(result);
        result.confidence = calculateConfidence(result);
        
        // Analyze speech patterns for better transcription
        result.speechPattern = analyzeSpeechPattern(samples);
        
        return result;
    }
    
    private float calculateAverageAmplitude(short[] samples) {
        long sum = 0;
        for (short sample : samples) {
            sum += Math.abs(sample);
        }
        return (float) sum / samples.length;
    }
    
    private short calculateMaxAmplitude(short[] samples) {
        short max = 0;
        for (short sample : samples) {
            short abs = (short) Math.abs(sample);
            if (abs > max) {
                max = abs;
            }
        }
        return max;
    }
    
    private float calculateZeroCrossingRate(short[] samples) {
        int crossings = 0;
        for (int i = 1; i < samples.length; i++) {
            if ((samples[i] >= 0) != (samples[i - 1] >= 0)) {
                crossings++;
            }
        }
        return (float) crossings / samples.length;
    }
    
    private boolean determineSpeechPresence(AudioAnalysisResult result) {
        // Speech typically has:
        // - Moderate to high average amplitude
        // - Reasonable zero crossing rate (not too high, not too low)
        // - Sufficient duration
        
        boolean hasAmplitude = result.averageAmplitude > 500 && result.maxAmplitude > 2000;
        boolean hasReasonableZCR = result.zeroCrossingRate > 0.01f && result.zeroCrossingRate < 0.3f;
        boolean hasDuration = result.duration > 0.5f; // At least 0.5 seconds
        
        return hasAmplitude && hasReasonableZCR && hasDuration;
    }
    
    private float calculateConfidence(AudioAnalysisResult result) {
        if (!result.hasSpeech) {
            return 0.0f;
        }
        
        float amplitudeScore = Math.min(result.averageAmplitude / 5000.0f, 1.0f);
        float zcrScore = 1.0f - Math.abs(result.zeroCrossingRate - 0.1f) / 0.1f;
        float durationScore = Math.min(result.duration / 5.0f, 1.0f);
        
        return (amplitudeScore + zcrScore + durationScore) / 3.0f;
    }
    
    private String analyzeSpeechPattern(short[] samples) {
        // Analyze speech patterns to determine likely content type
        float energy = 0;
        for (short sample : samples) {
            energy += sample * sample;
        }
        energy = energy / samples.length;
        
        if (energy > 10000000) {
            return "high_energy"; // Likely music or loud speech
        } else if (energy > 1000000) {
            return "medium_energy"; // Likely normal speech
        } else {
            return "low_energy"; // Likely quiet speech or background
        }
    }
    
    private void generateTranscriptionBasedOnAnalysis(AudioAnalysisResult analysis, String languageCode, 
                                                     byte[] audioData, TranscriptionCallback callback) {
        // Use Google AI Studio to transcribe audio instead of generating sample text
        
        if (analysis.confidence < 0.3f) {
            callback.onTranscriptionError("Chất lượng âm thanh quá thấp để nhận dạng");
            return;
        }
        
        fileLogger.d(TAG, "Sending audio to Google AI Studio for transcription");
        
        // Convert raw audio data to WAV format for better compatibility
        byte[] wavAudioData = convertToWav(audioData);
        
        // Use Google AI Studio to transcribe the actual audio
        googleAIClient.transcribeAudio(wavAudioData, languageCode, new GoogleAIClient.TranscriptionCallback() {
            @Override
            public void onSuccess(String transcribedText) {
                fileLogger.d(TAG, "Google AI transcription successful: " + transcribedText);
                mainHandler.post(() -> callback.onTranscriptionSuccess(transcribedText));
            }

            @Override
            public void onError(String error) {
                fileLogger.e(TAG, "Google AI transcription failed: " + error);
                mainHandler.post(() -> callback.onTranscriptionError("Lỗi nhận dạng giọng nói: " + error));
            }
        });
    }
    
    private byte[] convertToWav(byte[] rawAudioData) {
        try {
            ByteArrayOutputStream wavStream = new ByteArrayOutputStream();
            
            // WAV header parameters
            int sampleRate = 44100;
            int channels = 1; // Mono
            int bitsPerSample = 16;
            int byteRate = sampleRate * channels * bitsPerSample / 8;
            int blockAlign = channels * bitsPerSample / 8;
            int dataSize = rawAudioData.length;
            int fileSize = 36 + dataSize;
            
            // Write WAV header
            wavStream.write("RIFF".getBytes());
            wavStream.write(intToByteArray(fileSize), 0, 4);
            wavStream.write("WAVE".getBytes());
            wavStream.write("fmt ".getBytes());
            wavStream.write(intToByteArray(16), 0, 4); // PCM header size
            wavStream.write(shortToByteArray((short) 1), 0, 2); // PCM format
            wavStream.write(shortToByteArray((short) channels), 0, 2);
            wavStream.write(intToByteArray(sampleRate), 0, 4);
            wavStream.write(intToByteArray(byteRate), 0, 4);
            wavStream.write(shortToByteArray((short) blockAlign), 0, 2);
            wavStream.write(shortToByteArray((short) bitsPerSample), 0, 2);
            wavStream.write("data".getBytes());
            wavStream.write(intToByteArray(dataSize), 0, 4);
            
            // Write audio data
            wavStream.write(rawAudioData);
            
            return wavStream.toByteArray();
        } catch (IOException e) {
            fileLogger.e(TAG, "Error converting to WAV format", e);
            return rawAudioData; // Return original data if conversion fails
        }
    }
    
    private byte[] intToByteArray(int value) {
        return new byte[] {
            (byte) (value & 0xff),
            (byte) ((value >> 8) & 0xff),
            (byte) ((value >> 16) & 0xff),
            (byte) ((value >> 24) & 0xff)
        };
    }
    
    private byte[] shortToByteArray(short value) {
        return new byte[] {
            (byte) (value & 0xff),
            (byte) ((value >> 8) & 0xff)
        };
    }

    private static class AudioAnalysisResult {
        boolean hasSpeech = false;
        float confidence = 0.0f;
        float averageAmplitude = 0.0f;
        short maxAmplitude = 0;
        float zeroCrossingRate = 0.0f;
        float duration = 0.0f;
        String speechPattern = "unknown";
        
        @Override
        public String toString() {
            return String.format("AudioAnalysis{hasSpeech=%b, confidence=%.2f, avgAmp=%.1f, maxAmp=%d, zcr=%.3f, duration=%.1fs, pattern=%s}",
                    hasSpeech, confidence, averageAmplitude, maxAmplitude, zeroCrossingRate, duration, speechPattern);
        }
    }
}