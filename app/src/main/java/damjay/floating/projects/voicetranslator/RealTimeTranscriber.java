package damjay.floating.projects.voicetranslator;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

public class RealTimeTranscriber {
    private static final String TAG = "RealTimeTranscriber";
    
    private Context context;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private TranscriptionCallback callback;
    private boolean isListening = false;
    
    public interface TranscriptionCallback {
        void onTranscriptionResult(String text);
        void onTranscriptionError(String error);
        void onTranscriptionStarted();
        void onTranscriptionStopped();
    }
    
    public RealTimeTranscriber(Context context, TranscriptionCallback callback) {
        this.context = context;
        this.callback = callback;
        initializeSpeechRecognizer();
    }
    
    private void initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    Log.d(TAG, "Ready for speech");
                    isListening = true;
                    callback.onTranscriptionStarted();
                }

                @Override
                public void onBeginningOfSpeech() {
                    Log.d(TAG, "Beginning of speech");
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    // Audio level changed
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                    // Audio buffer received
                }

                @Override
                public void onEndOfSpeech() {
                    Log.d(TAG, "End of speech");
                }

                @Override
                public void onError(int error) {
                    Log.e(TAG, "Speech recognition error: " + getErrorMessage(error));
                    isListening = false;
                    callback.onTranscriptionError(getErrorMessage(error));
                }

                @Override
                public void onResults(Bundle results) {
                    Log.d(TAG, "Speech recognition results received");
                    isListening = false;
                    
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String transcribedText = matches.get(0);
                        Log.d(TAG, "Transcribed text: " + transcribedText);
                        callback.onTranscriptionResult(transcribedText);
                    } else {
                        callback.onTranscriptionError("No speech detected");
                    }
                    callback.onTranscriptionStopped();
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String partialText = matches.get(0);
                        Log.d(TAG, "Partial result: " + partialText);
                        // You can use this for real-time display
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                    // Speech recognition event
                }
            });
        } else {
            Log.e(TAG, "Speech recognition not available on this device");
            callback.onTranscriptionError("Speech recognition not available on this device");
        }
    }
    
    public void startTranscription(String languageCode) {
        if (speechRecognizer == null) {
            callback.onTranscriptionError("Speech recognizer not initialized");
            return;
        }
        
        if (isListening) {
            Log.w(TAG, "Already listening");
            return;
        }
        
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, getLocaleFromLanguageCode(languageCode));
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, getLocaleFromLanguageCode(languageCode));
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        
        // For better accuracy
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500);
        
        try {
            speechRecognizer.startListening(recognizerIntent);
            Log.d(TAG, "Started speech recognition for language: " + languageCode);
        } catch (Exception e) {
            Log.e(TAG, "Error starting speech recognition", e);
            callback.onTranscriptionError("Error starting speech recognition: " + e.getMessage());
        }
    }
    
    public void stopTranscription() {
        if (speechRecognizer != null && isListening) {
            speechRecognizer.stopListening();
            isListening = false;
            Log.d(TAG, "Stopped speech recognition");
        }
    }
    
    public void destroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        isListening = false;
    }
    
    public boolean isListening() {
        return isListening;
    }
    
    private String getLocaleFromLanguageCode(String languageCode) {
        switch (languageCode) {
            case "vi": return "vi-VN";
            case "en": return "en-US";
            case "zh": return "zh-CN";
            case "ja": return "ja-JP";
            case "ko": return "ko-KR";
            case "es": return "es-ES";
            case "fr": return "fr-FR";
            case "de": return "de-DE";
            case "ru": return "ru-RU";
            case "ar": return "ar-SA";
            case "auto": return Locale.getDefault().toString();
            default: return "en-US";
        }
    }
    
    private String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Audio recording error";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Client side error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Insufficient permissions";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "No speech input detected";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Recognition service busy";
            case SpeechRecognizer.ERROR_SERVER:
                return "Server error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "No speech input timeout";
            default:
                return "Unknown error occurred";
        }
    }
    
    public static boolean isRecognitionAvailable(Context context) {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }
}