package damjay.floating.projects.models;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TranslationHistory {
    private String originalText;
    private String translatedText;
    private String sourceLanguage;
    private String targetLanguage;
    private long timestamp;

    public TranslationHistory(String originalText, String translatedText, 
                            String sourceLanguage, String targetLanguage) {
        this.originalText = originalText;
        this.translatedText = translatedText;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.timestamp = System.currentTimeMillis();
    }

    public String getOriginalText() {
        return originalText;
    }

    public String getTranslatedText() {
        return translatedText;
    }

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public String getTargetLanguage() {
        return targetLanguage;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getFormattedTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public String getLanguagePair() {
        return sourceLanguage + " → " + targetLanguage;
    }
}