package damjay.floating.projects.voicetranslator;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import damjay.floating.projects.R;

public class TranslationResultPopup {
    private static final String TAG = "TranslationResultPopup";
    
    private Context context;
    private WindowManager windowManager;
    private View popupView;
    private WindowManager.LayoutParams params;
    
    private TextView originalText;
    private TextView translatedText;
    private Button copyButton;
    private Button shareButton;
    private ImageButton closeButton;
    
    private boolean isVisible = false;
    private Handler mainHandler;
    
    public TranslationResultPopup(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        createPopupView();
    }
    
    private void createPopupView() {
        try {
            // Inflate the popup layout
            Log.d(TAG, "Inflating popup layout");
            popupView = LayoutInflater.from(context).inflate(R.layout.translation_result_popup, null);
            
            // Find views
            originalText = popupView.findViewById(R.id.originalText);
            translatedText = popupView.findViewById(R.id.translatedText);
            copyButton = popupView.findViewById(R.id.copyButton);
            shareButton = popupView.findViewById(R.id.shareButton);
            closeButton = popupView.findViewById(R.id.closeButton);
            
            Log.d(TAG, "Views found - originalText: " + originalText + ", translatedText: " + translatedText);
            
            // Set up click listeners
            if (copyButton != null) copyButton.setOnClickListener(v -> copyTranslatedText());
            if (shareButton != null) shareButton.setOnClickListener(v -> shareTranslatedText());
            if (closeButton != null) closeButton.setOnClickListener(v -> hide());
            
            // Create layout params
            createLayoutParams();
            Log.d(TAG, "Popup view created successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error creating popup view", e);
        }
    }
    
    private void createLayoutParams() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        
        // Position at bottom of screen, more compact with fixed height
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.x = 0;
        params.y = 50; // 50px from bottom (closer to bottom)
        params.width = displayMetrics.widthPixels - 40; // 20px margin on each side
        params.height = 220; // Fixed height to avoid covering entire screen
        
        Log.d(TAG, "Layout params created - Width: " + params.width + ", Screen width: " + displayMetrics.widthPixels);
    }
    
    public void showResult(String original, String translated) {
        Log.d(TAG, "showResult called - Original: '" + original + "', Translated: '" + translated + "'");
        mainHandler.post(() -> {
            if (originalText != null && translatedText != null) {
                Log.d(TAG, "Setting text - Original: '" + original + "', Translated: '" + translated + "'");
                originalText.setText(original != null ? original : "No original text");
                translatedText.setText(translated != null ? translated : "No translation");
                show();
                
                // Remove auto-hide - popup will stay until manually closed
            } else {
                Log.e(TAG, "TextViews are null - originalText: " + originalText + ", translatedText: " + translatedText);
            }
        });
    }
    
    private void show() {
        if (!isVisible && windowManager != null && popupView != null) {
            try {
                windowManager.addView(popupView, params);
                isVisible = true;
                Log.d(TAG, "Translation result popup shown");
            } catch (Exception e) {
                Log.e(TAG, "Error showing translation result popup", e);
            }
        }
    }
    
    private void hide() {
        if (isVisible && windowManager != null && popupView != null) {
            try {
                windowManager.removeView(popupView);
                isVisible = false;
                Log.d(TAG, "Translation result popup hidden");
            } catch (Exception e) {
                Log.e(TAG, "Error hiding translation result popup", e);
            }
        }
    }
    
    private void copyTranslatedText() {
        String text = translatedText.getText().toString();
        if (!text.isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Translated Text", text);
            clipboard.setPrimaryClip(clip);
            
            Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Text copied to clipboard");
        }
    }
    
    private void shareTranslatedText() {
        String original = originalText.getText().toString();
        String translated = translatedText.getText().toString();
        
        if (!translated.isEmpty()) {
            String shareText = "Original: " + original + "\n\nTranslated: " + translated;
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            try {
                context.startActivity(Intent.createChooser(shareIntent, "Share Translation")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Log.d(TAG, "Share intent launched");
            } catch (Exception e) {
                Log.e(TAG, "Error sharing text", e);
                Toast.makeText(context, "Error sharing text", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    public boolean isVisible() {
        return isVisible;
    }
    
    public void destroy() {
        hide();
        popupView = null;
        originalText = null;
        translatedText = null;
        copyButton = null;
        shareButton = null;
        closeButton = null;
    }
}