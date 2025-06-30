package damjay.floating.projects.voicetranslator;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import android.util.Base64;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GoogleAIClient {
    private static final String TAG = "GoogleAIClient";
    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    
    private OkHttpClient client;
    private Gson gson;
    private String apiKey;

    public GoogleAIClient(String apiKey) {
        this.apiKey = apiKey;
        this.gson = new Gson();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public interface TranslationCallback {
        void onSuccess(String translatedText);
        void onError(String error);
    }

    public interface TranscriptionCallback {
        void onSuccess(String transcribedText);
        void onError(String error);
    }

    public void transcribeAudio(byte[] audioData, String languageCode, TranscriptionCallback callback) {
        Log.d(TAG, "Starting audio transcription with Google AI Studio");
        
        try {
            // Convert audio data to base64
            String base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP);
            
            // Build transcription prompt
            String prompt = buildTranscriptionPrompt(languageCode);
            
            // Create request JSON with audio data
            JsonObject requestJson = new JsonObject();
            JsonArray contentsArray = new JsonArray();
            JsonObject content = new JsonObject();
            JsonArray partsArray = new JsonArray();
            
            // Add text prompt
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", prompt);
            partsArray.add(textPart);
            
            // Add audio data
            JsonObject audioPart = new JsonObject();
            JsonObject inlineData = new JsonObject();
            inlineData.addProperty("mime_type", "audio/wav");
            inlineData.addProperty("data", base64Audio);
            audioPart.add("inline_data", inlineData);
            partsArray.add(audioPart);
            
            content.add("parts", partsArray);
            contentsArray.add(content);
            requestJson.add("contents", contentsArray);
            
            RequestBody body = RequestBody.create(
                MediaType.parse("application/json"), 
                requestJson.toString()
            );

            Request request = new Request.Builder()
                    .url(API_BASE_URL + "?key=" + apiKey)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Transcription request failed", e);
                    callback.onError("Lỗi kết nối mạng: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (response.isSuccessful()) {
                            String responseBody = response.body().string();
                            Log.d(TAG, "Transcription response: " + responseBody);
                            String transcribedText = parseTranscriptionResponse(responseBody);
                            if (transcribedText != null && !transcribedText.trim().isEmpty()) {
                                callback.onSuccess(transcribedText);
                            } else {
                                callback.onError("Không thể nhận dạng giọng nói từ audio");
                            }
                        } else {
                            String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                            Log.e(TAG, "API error: " + response.code() + ", body: " + errorBody);
                            callback.onError("Lỗi API: " + response.code());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing transcription response", e);
                        callback.onError("Lỗi xử lý phản hồi: " + e.getMessage());
                    } finally {
                        response.close();
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error preparing transcription request", e);
            callback.onError("Lỗi chuẩn bị yêu cầu: " + e.getMessage());
        }
    }

    public void transcribeAndTranslate(byte[] audioData, String sourceLanguage, 
                                     String targetLanguage, TranslationCallback callback) {
        // Note: This method is deprecated in favor of using SystemAudioTranscriber
        // directly in VoiceTranslatorService for better control and error handling
        Log.w(TAG, "transcribeAndTranslate is deprecated, use SystemAudioTranscriber instead");
        callback.onError("Method deprecated, use SystemAudioTranscriber for audio transcription");
    }

    public void translateText(String text, String sourceLanguage, String targetLanguage, 
                            TranslationCallback callback) {
        String prompt = buildTranslationPrompt(text, sourceLanguage, targetLanguage);
        
        JsonObject requestJson = new JsonObject();
        JsonObject contents = new JsonObject();
        JsonObject parts = new JsonObject();
        parts.addProperty("text", prompt);
        
        contents.add("parts", gson.toJsonTree(new JsonObject[]{gson.fromJson(parts.toString(), JsonObject.class)}));
        requestJson.add("contents", gson.toJsonTree(new JsonObject[]{gson.fromJson(contents.toString(), JsonObject.class)}));

        RequestBody body = RequestBody.create(
            MediaType.parse("application/json"), 
            requestJson.toString()
        );

        Request request = new Request.Builder()
                .url(API_BASE_URL + "?key=" + apiKey)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Translation request failed", e);
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        String translatedText = parseTranslationResponse(responseBody);
                        callback.onSuccess(translatedText);
                    } else {
                        callback.onError("API error: " + response.code());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing response", e);
                    callback.onError("Error parsing response: " + e.getMessage());
                } finally {
                    response.close();
                }
            }
        });
    }

    private String buildTranslationPrompt(String text, String sourceLanguage, String targetLanguage) {
        StringBuilder prompt = new StringBuilder();
        
        // Enhanced prompt for better translation quality, especially for Vietnamese
        if (targetLanguage.equals("vi")) {
            prompt.append("Bạn là một chuyên gia dịch thuật chuyên nghiệp. Hãy dịch đoạn text sau ");
            if (!sourceLanguage.equals("auto")) {
                prompt.append("từ ").append(getLanguageNameVietnamese(sourceLanguage)).append(" ");
            }
            prompt.append("sang tiếng Việt một cách tự nhiên, chính xác và phù hợp với ngữ cảnh:\n\n");
            prompt.append("\"").append(text).append("\"\n\n");
            prompt.append("Yêu cầu:\n");
            prompt.append("- Dịch sát nghĩa, tự nhiên như người Việt nói\n");
            prompt.append("- Giữ nguyên tone và ý nghĩa của câu gốc\n");
            prompt.append("- Sử dụng từ ngữ phù hợp với ngữ cảnh\n");
            prompt.append("- Chỉ trả về bản dịch, không giải thích thêm\n");
        } else {
            prompt.append("You are a professional translator. Translate the following text ");
            
            if (!sourceLanguage.equals("auto")) {
                prompt.append("from ").append(getLanguageName(sourceLanguage)).append(" ");
            }
            
            prompt.append("to ").append(getLanguageName(targetLanguage)).append(" accurately and naturally:\n\n");
            prompt.append("\"").append(text).append("\"\n\n");
            prompt.append("Requirements:\n");
            prompt.append("- Maintain the original meaning and tone\n");
            prompt.append("- Use natural, contextually appropriate language\n");
            prompt.append("- Provide only the translation without explanations\n");
        }
        
        return prompt.toString();
    }

    private String getLanguageName(String code) {
        switch (code) {
            case "vi": return "Vietnamese";
            case "en": return "English";
            case "zh": return "Chinese";
            case "ja": return "Japanese";
            case "ko": return "Korean";
            case "es": return "Spanish";
            case "fr": return "French";
            case "de": return "German";
            case "ru": return "Russian";
            case "ar": return "Arabic";
            default: return "English";
        }
    }
    
    private String getLanguageNameVietnamese(String code) {
        switch (code) {
            case "vi": return "tiếng Việt";
            case "en": return "tiếng Anh";
            case "zh": return "tiếng Trung";
            case "ja": return "tiếng Nhật";
            case "ko": return "tiếng Hàn";
            case "es": return "tiếng Tây Ban Nha";
            case "fr": return "tiếng Pháp";
            case "de": return "tiếng Đức";
            case "ru": return "tiếng Nga";
            case "ar": return "tiếng Ả Rập";
            default: return "tiếng Anh";
        }
    }

    private String buildTranscriptionPrompt(String languageCode) {
        StringBuilder prompt = new StringBuilder();
        
        if (languageCode.equals("vi")) {
            prompt.append("Bạn là chuyên gia phiên âm âm thanh. Hãy chuyển đổi nội dung âm thanh thành văn bản tiếng Việt chính xác:\n\n");
            prompt.append("Yêu cầu:\n");
            prompt.append("- Phiên âm chính xác từng từ, câu\n");
            prompt.append("- Sử dụng dấu câu phù hợp\n");
            prompt.append("- Viết hoa đúng ngữ pháp tiếng Việt\n");
            prompt.append("- Chỉ trả về văn bản đã phiên âm, không giải thích\n");
            prompt.append("- Nếu âm thanh không rõ, hãy cố gắng phiên âm những gì nghe được");
        } else {
            prompt.append("You are an expert audio transcriber. Please transcribe the audio content to text");
            
            if (!languageCode.equals("auto")) {
                prompt.append(" in ").append(getLanguageName(languageCode));
            }
            
            prompt.append(":\n\n");
            prompt.append("Requirements:\n");
            prompt.append("- Transcribe accurately word by word\n");
            prompt.append("- Use proper punctuation and capitalization\n");
            prompt.append("- Provide only the transcribed text without explanations\n");
            prompt.append("- If audio is unclear, transcribe what you can hear clearly");
        }
        
        return prompt.toString();
    }

    private String parseTranscriptionResponse(String responseBody) {
        try {
            JsonObject response = gson.fromJson(responseBody, JsonObject.class);
            String transcribedText = response.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString().trim();
            
            Log.d(TAG, "Parsed transcription: " + transcribedText);
            return transcribedText;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing transcription response", e);
            return null;
        }
    }

    private String parseTranslationResponse(String responseBody) {
        try {
            JsonObject response = gson.fromJson(responseBody, JsonObject.class);
            return response.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString().trim();
        } catch (Exception e) {
            Log.e(TAG, "Error parsing translation response", e);
            return "Translation error";
        }
    }


}