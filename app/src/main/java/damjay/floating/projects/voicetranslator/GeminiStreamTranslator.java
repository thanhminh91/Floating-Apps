package damjay.floating.projects.voicetranslator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.media.Image;
import android.media.ImageReader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class GeminiStreamTranslator {
    private static final String TAG = "GeminiStreamTranslator";
    private static final String GEMINI_LIVE_WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private Context context;
    private String apiKey;
    private OkHttpClient client;
    private WebSocket webSocket;
    private Handler mainHandler;
    
    // Screen recording components
    private MediaProjection mediaProjection;
    private MediaRecorder mediaRecorder;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private boolean isRecording = false;
    
    // Audio capture components
    private AudioRecord audioRecord;
    private Thread audioRecordThread;
    private boolean isAudioRecording = false;
    private byte[] latestAudioData;
    
    // Screen dimensions
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;
    
    // Translation settings
    private String sourceLanguage = "auto";
    private String targetLanguage = "vi"; // Default to Vietnamese
    
    // Streaming settings
    private static final int STREAMING_INTERVAL_MS = 500; // Stream every 500ms
    private boolean isStreamingActive = false;
    private Handler streamingHandler;
    
    public interface GeminiTranslationCallback {
        void onTranslationResult(String originalText, String translatedText);
        void onError(String error);
        void onStatusUpdate(String status);
    }
    
    private GeminiTranslationCallback callback;
    
    public GeminiStreamTranslator(Context context, String apiKey) {
        this.context = context;
        this.apiKey = apiKey;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.streamingHandler = new Handler(Looper.getMainLooper());
        
        // Get screen dimensions
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        this.screenWidth = metrics.widthPixels;
        this.screenHeight = metrics.heightPixels;
        this.screenDensity = metrics.densityDpi;
        
        // Create OkHttp client with longer timeouts for streaming
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    public void setLanguages(String sourceLanguage, String targetLanguage) {
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
    }
    
    public void setCallback(GeminiTranslationCallback callback) {
        this.callback = callback;
    }
    
    public void startStreamTranslation(MediaProjection mediaProjection) {
        this.mediaProjection = mediaProjection;
        
        if (callback != null) {
            callback.onStatusUpdate("Đang kết nối Gemini Live API...");
        }
        
        // Create WebSocket connection for Live API
        connectToGeminiLive();
    }
    
    private void connectToGeminiLive() {
        Request request = new Request.Builder()
                .url(GEMINI_LIVE_WS_URL + "?key=" + apiKey)
                .build();
        
        webSocket = client.newWebSocket(request, new GeminiLiveWebSocketListener());
        Log.d(TAG, "Connecting to Gemini Live API...");
    }
    
    public void stopStreamTranslation() {
        isStreamingActive = false;
        
        if (webSocket != null) {
            webSocket.close(1000, "Translation stopped");
            webSocket = null;
        }
        
        stopScreenRecording();
        
        if (callback != null) {
            callback.onStatusUpdate("Đã dừng dịch thuật");
        }
    }
    
    private void startPeriodicTranslation() {
        if (callback != null) {
            callback.onStatusUpdate("Đang bắt đầu phân tích màn hình...");
        }
        
        startScreenRecording();
    }
    
    private void startScreenRecording() {
        if (mediaProjection == null || isRecording) {
            return;
        }
        
        try {
            // Create ImageReader for screen capture
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
            
            // Create virtual display for screen recording
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "GeminiScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null
            );
            
            isRecording = true;
            
            if (callback != null) {
                callback.onStatusUpdate("Đang ghi màn hình và âm thanh...");
            }
            
            // Start audio recording
            startAudioRecording();
            
            // Start realtime streaming (will be triggered by WebSocket connection)
            Log.d(TAG, "Screen recording ready for streaming");
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting screen recording", e);
            if (callback != null) {
                callback.onError("Lỗi khi bắt đầu ghi màn hình: " + e.getMessage());
            }
        }
    }
    
    private void startAudioRecording() {
        try {
            int sampleRate = 44100;
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
            
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize);
            
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                audioRecord.startRecording();
                isAudioRecording = true;
                
                audioRecordThread = new Thread(() -> {
                    byte[] buffer = new byte[bufferSize];
                    while (isAudioRecording) {
                        int bytesRead = audioRecord.read(buffer, 0, bufferSize);
                        if (bytesRead > 0) {
                            // Store the latest audio data
                            latestAudioData = new byte[bytesRead];
                            System.arraycopy(buffer, 0, latestAudioData, 0, bytesRead);
                        }
                    }
                });
                audioRecordThread.start();
                
                Log.d(TAG, "Audio recording started");
            } else {
                Log.e(TAG, "AudioRecord initialization failed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting audio recording", e);
        }
    }
    
    private void stopScreenRecording() {
        isRecording = false;
        isAudioRecording = false;
        
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping audio recorder", e);
            }
            audioRecord = null;
        }
        
        if (audioRecordThread != null) {
            try {
                audioRecordThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            audioRecordThread = null;
        }
        
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping media recorder", e);
            }
            mediaRecorder = null;
        }
    }
    
    private void startRealtimeStreaming() {
        if (!isRecording || !isStreamingActive) return;
        
        streamingHandler.postDelayed(() -> {
            streamCurrentFrame();
            startRealtimeStreaming(); // Schedule next stream
        }, STREAMING_INTERVAL_MS); // Stream every 500ms
    }
    
    private void streamCurrentFrame() {
        if (webSocket == null || !isStreamingActive) return;
        
        try {
            // Capture current screen frame
            Bitmap screenBitmap = captureScreen();
            
            // Get current audio chunk
            byte[] audioData = latestAudioData;
            
            // Send to Gemini Live API
            sendFrameToGeminiLive(screenBitmap, audioData);
            
        } catch (Exception e) {
            Log.e(TAG, "Error streaming frame", e);
        }
    }
    
    private void sendFrameToGeminiLive(Bitmap screenBitmap, byte[] audioData) {
        try {
            JSONObject message = new JSONObject();
            
            // Create client content for Live API
            JSONObject clientContent = new JSONObject();
            JSONArray turns = new JSONArray();
            JSONObject turn = new JSONObject();
            JSONArray parts = new JSONArray();
            
            // Add screen frame if available
            if (screenBitmap != null) {
                String imageBase64 = bitmapToBase64(screenBitmap);
                if (imageBase64 != null) {
                    JSONObject imagePart = new JSONObject();
                    JSONObject inlineData = new JSONObject();
                    inlineData.put("mimeType", "image/jpeg");
                    inlineData.put("data", imageBase64);
                    imagePart.put("inlineData", inlineData);
                    parts.put(imagePart);
                }
            }
            
            // Add audio chunk if available
            if (audioData != null && audioData.length > 0) {
                String audioBase64 = Base64.encodeToString(audioData, Base64.NO_WRAP);
                JSONObject audioPart = new JSONObject();
                JSONObject audioInlineData = new JSONObject();
                audioInlineData.put("mimeType", "audio/pcm;rate=44100");
                audioInlineData.put("data", audioBase64);
                audioPart.put("inlineData", audioInlineData);
                parts.put(audioPart);
            }
            
            // Only send if we have content
            if (parts.length() > 0) {
                turn.put("parts", parts);
                turns.put(turn);
                clientContent.put("turns", turns);
                message.put("clientContent", clientContent);
                
                String messageStr = message.toString();
                webSocket.send(messageStr);
                
                Log.d(TAG, "Streamed frame to Gemini Live API");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending frame to Gemini Live", e);
        }
    }
    
    private Bitmap captureScreen() {
        if (imageReader == null) {
            return null;
        }
        
        try {
            Image image = imageReader.acquireLatestImage();
            if (image != null) {
                Image.Plane[] planes = image.getPlanes();
                Image.Plane plane = planes[0];
                int pixelStride = plane.getPixelStride();
                int rowStride = plane.getRowStride();
                int rowPadding = rowStride - pixelStride * screenWidth;
                
                // Create bitmap
                Bitmap bitmap = Bitmap.createBitmap(screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(plane.getBuffer());
                
                // Crop to actual screen size
                Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);
                
                image.close();
                return croppedBitmap;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error capturing screen", e);
        }
        
        return null;
    }
    
    private void setupGeminiLiveSession() {
        try {
            JSONObject setupMessage = new JSONObject();
            
            // Setup Live API session with translation configuration
            JSONObject setup = new JSONObject();
            
            // Response modalities - we want text responses for translation
            JSONArray responseModalities = new JSONArray();
            responseModalities.put("TEXT");
            setup.put("responseModalities", responseModalities);
            
            // Media resolution for screen capture
            setup.put("mediaResolution", "MEDIA_RESOLUTION_MEDIUM");
            
            // Context window compression for performance
            JSONObject contextWindowCompression = new JSONObject();
            contextWindowCompression.put("triggerTokens", "25600");
            JSONObject slidingWindow = new JSONObject();
            slidingWindow.put("targetTokens", "12800");
            contextWindowCompression.put("slidingWindow", slidingWindow);
            setup.put("contextWindowCompression", contextWindowCompression);
            
            setupMessage.put("setup", setup);
            
            // Send setup message
            webSocket.send(setupMessage.toString());
            
            // Send initial translation instruction
            sendTranslationInstruction();
            
            Log.d(TAG, "Gemini Live session setup complete");
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up Gemini Live session", e);
        }
    }
    
    private void sendTranslationInstruction() {
        try {
            JSONObject message = new JSONObject();
            JSONObject clientContent = new JSONObject();
            JSONArray turns = new JSONArray();
            JSONObject turn = new JSONObject();
            JSONArray parts = new JSONArray();
            
            // Add translation instruction
            JSONObject textPart = new JSONObject();
            textPart.put("text", createLiveTranslationPrompt());
            parts.put(textPart);
            
            turn.put("parts", parts);
            turns.put(turn);
            clientContent.put("turns", turns);
            message.put("clientContent", clientContent);
            
            webSocket.send(message.toString());
            
            Log.d(TAG, "Sent translation instruction to Gemini Live");
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending translation instruction", e);
        }
    }
    
    private String bitmapToBase64(Bitmap bitmap) {
        try {
            // Scale down bitmap for better performance
            int maxDimension = 1024;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            
            if (width > maxDimension || height > maxDimension) {
                float scale = Math.min((float) maxDimension / width, (float) maxDimension / height);
                int newWidth = Math.round(width * scale);
                int newHeight = Math.round(height * scale);
                bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
            }
            
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            // Use JPEG with medium quality for better compression
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            return Base64.encodeToString(byteArray, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Error converting bitmap to base64", e);
            return null;
        }
    }
    
    private String createLiveTranslationPrompt() {
        return String.format(
            "Bạn là một trợ lý dịch thuật AI chuyên nghiệp sử dụng Gemini Live API. Nhiệm vụ của bạn:\n\n" +
            "🎯 MỤC TIÊU: Dịch thuật realtime từ video/audio streaming\n" +
            "🔍 PHÂN TÍCH:\n" +
            "- Xử lý hình ảnh màn hình: Đọc tất cả text hiển thị (phụ đề, tiêu đề, nội dung)\n" +
            "- Xử lý âm thanh streaming: Chuyển đổi speech-to-text realtime\n" +
            "- Tổng hợp ngữ cảnh: Kết hợp thông tin từ cả visual và audio\n\n" +
            "📝 NGÔN NGỮ: %s → %s\n" +
            "⚡ REALTIME: Xử lý từng frame và audio chunk liên tục\n\n" +
            "🎪 ĐỊNH DẠNG OUTPUT:\n" +
            "{\n" +
            "  \"originalText\": \"[Văn bản gốc từ hình ảnh + âm thanh]\",\n" +
            "  \"translatedText\": \"[Bản dịch chính xác, tự nhiên]\",\n" +
            "  \"detectedLanguage\": \"[Ngôn ngữ phát hiện]\",\n" +
            "  \"context\": \"[Loại nội dung: video, phim, tin tức, game, etc.]\",\n" +
            "  \"confidence\": \"[Độ tin cậy: high/medium/low]\"\n" +
            "}\n\n" +
            "⚠️ LƯU Ý: Chỉ trả về JSON khi có nội dung mới hoặc thay đổi đáng kể. Tránh lặp lại nội dung cũ.",
            sourceLanguage.equals("auto") ? "Auto-detect" : sourceLanguage,
            targetLanguage
        );
    }
    
    private void parseGeminiResponse(String responseStr) {
        try {
            JSONObject response = new JSONObject(responseStr);
            
            if (response.has("candidates")) {
                JSONArray candidates = response.getJSONArray("candidates");
                if (candidates.length() > 0) {
                    JSONObject candidate = candidates.getJSONObject(0);
                    if (candidate.has("content")) {
                        JSONObject content = candidate.getJSONObject("content");
                        if (content.has("parts")) {
                            JSONArray parts = content.getJSONArray("parts");
                            if (parts.length() > 0) {
                                JSONObject part = parts.getJSONObject(0);
                                if (part.has("text")) {
                                    String responseText = part.getString("text");
                                    parseTranslationResponse(responseText);
                                }
                            }
                        }
                    }
                }
            }
            
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing Gemini response", e);
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onError("Lỗi phân tích phản hồi từ Gemini");
                }
            });
        }
    }
    
    public boolean isConnected() {
        return webSocket != null && isStreamingActive;
    }
    
    public boolean isRecording() {
        return isRecording;
    }
    
    private class GeminiLiveWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.d(TAG, "Gemini Live WebSocket connected");
            isStreamingActive = true;
            
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onStatusUpdate("Đã kết nối Gemini Live API");
                }
            });
            
            // Setup the session
            setupGeminiLiveSession();
            
            // Start screen recording and streaming
            startScreenRecording();
            startRealtimeStreaming();
        }
        
        @Override
        public void onMessage(WebSocket webSocket, String text) {
            Log.d(TAG, "Received from Gemini Live: " + text);
            
            try {
                JSONObject response = new JSONObject(text);
                
                // Handle server content (translation results)
                if (response.has("serverContent")) {
                    JSONObject serverContent = response.getJSONObject("serverContent");
                    
                    if (serverContent.has("modelTurn")) {
                        JSONObject modelTurn = serverContent.getJSONObject("modelTurn");
                        
                        if (modelTurn.has("parts")) {
                            JSONArray parts = modelTurn.getJSONArray("parts");
                            
                            for (int i = 0; i < parts.length(); i++) {
                                JSONObject part = parts.getJSONObject(i);
                                
                                if (part.has("text")) {
                                    String responseText = part.getString("text");
                                    parseTranslationResponse(responseText);
                                }
                            }
                        }
                    }
                    
                    // Check if turn is complete
                    if (serverContent.optBoolean("turnComplete", false)) {
                        Log.d(TAG, "Turn complete from Gemini Live");
                    }
                }
                
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing Gemini Live response", e);
            }
        }
        
        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            Log.e(TAG, "Gemini Live WebSocket failed", t);
            isStreamingActive = false;
            
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onError("Kết nối Gemini Live thất bại: " + t.getMessage());
                }
            });
        }
        
        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "Gemini Live WebSocket closing: " + code + " - " + reason);
            isStreamingActive = false;
        }
        
        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "Gemini Live WebSocket closed: " + code + " - " + reason);
            isStreamingActive = false;
            
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onStatusUpdate("Đã ngắt kết nối Gemini Live");
                }
            });
        }
    }
    
    private void parseTranslationResponse(String responseText) {
        try {
            // Try to parse as JSON first
            JSONObject jsonResponse = new JSONObject(responseText);
            
            String originalText = jsonResponse.optString("originalText", "");
            String translatedText = jsonResponse.optString("translatedText", "");
            String context = jsonResponse.optString("context", "");
            String detectedLanguage = jsonResponse.optString("detectedLanguage", "");
            String confidence = jsonResponse.optString("confidence", "");
            
            if (!originalText.isEmpty() && !translatedText.isEmpty()) {
                Log.d(TAG, "Gemini Live translation complete:");
                Log.d(TAG, "  Context: " + context);
                Log.d(TAG, "  Detected Language: " + detectedLanguage);
                Log.d(TAG, "  Confidence: " + confidence);
                Log.d(TAG, "  Original: " + originalText);
                Log.d(TAG, "  Translated: " + translatedText);
                
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onTranslationResult(originalText, translatedText);
                    }
                });
                return;
            }
        } catch (JSONException e) {
            // If not JSON, treat as plain text translation
            Log.d(TAG, "Response is not JSON, treating as plain text");
        }
        
        // Fallback: treat entire response as translation
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onTranslationResult("Screen + Audio content", responseText);
            }
        });
    }
    
    public void destroy() {
        stopStreamTranslation();
        
        // Stop streaming handler
        if (streamingHandler != null) {
            streamingHandler.removeCallbacksAndMessages(null);
        }
        
        if (client != null) {
            client.dispatcher().executorService().shutdown();
        }
    }
}