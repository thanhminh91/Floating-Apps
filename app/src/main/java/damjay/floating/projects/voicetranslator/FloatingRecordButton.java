package damjay.floating.projects.voicetranslator;

import android.app.Service;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import damjay.floating.projects.R;

public class FloatingRecordButton {
    private static final String TAG = "FloatingRecordButton";
    
    private Context context;
    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;
    
    private ImageButton recordButton;
    private TextView statusText;
    
    private boolean isRecording = false;
    private boolean isVisible = false;
    
    private OnRecordClickListener recordClickListener;
    private Handler mainHandler;
    
    public interface OnRecordClickListener {
        void onStartRecord();
        void onStopRecord();
    }
    
    public FloatingRecordButton(Context context) {
        Log.d(TAG, "FloatingRecordButton constructor called");
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        Log.d(TAG, "WindowManager: " + (windowManager != null));
        
        createFloatingView();
        Log.d(TAG, "FloatingRecordButton initialized successfully");
    }
    
    private void createFloatingView() {
        try {
            Log.d(TAG, "Creating floating view...");
            
            // Inflate the floating view layout
            floatingView = LayoutInflater.from(context).inflate(R.layout.floating_record_button, null);
            Log.d(TAG, "Layout inflated: " + (floatingView != null));
            
            // Find views
            recordButton = floatingView.findViewById(R.id.recordButton);
            statusText = floatingView.findViewById(R.id.statusText);
            
            Log.d(TAG, "Views found - recordButton: " + (recordButton != null) + ", statusText: " + (statusText != null));
            
            // Set up click listener
            recordButton.setOnClickListener(v -> toggleRecording());
            
            // Set up touch listener for dragging
            setUpTouchListener();
            
            // Create layout params
            createLayoutParams();
            
            Log.d(TAG, "Floating view created successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error creating floating view", e);
            e.printStackTrace();
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
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        
        // Position at right edge of screen, slightly lower than center
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = displayMetrics.widthPixels - 80; // 80px from right edge (smaller margin)
        params.y = (int)(displayMetrics.heightPixels * 0.6); // 60% down from top
    }
    
    private void setUpTouchListener() {
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean isDragging = false;
            
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) (event.getRawX() - initialTouchX);
                        int deltaY = (int) (event.getRawY() - initialTouchY);
                        
                        // Check if it's a drag gesture
                        if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                            isDragging = true;
                            params.x = initialX + deltaX;
                            params.y = initialY + deltaY;
                            
                            // Keep button within screen bounds
                            DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
                            params.x = Math.max(0, Math.min(params.x, displayMetrics.widthPixels - floatingView.getWidth()));
                            params.y = Math.max(0, Math.min(params.y, displayMetrics.heightPixels - floatingView.getHeight()));
                            
                            if (windowManager != null) {
                                windowManager.updateViewLayout(floatingView, params);
                            }
                        }
                        return true;
                        
                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            // It's a click, not a drag
                            recordButton.performClick();
                        }
                        return true;
                }
                return false;
            }
        });
    }
    
    private void toggleRecording() {
        Log.d(TAG, "Record button clicked. Current state: " + isRecording);
        
        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }
    
    private void startRecording() {
        isRecording = true;
        updateUI();
        
        if (recordClickListener != null) {
            recordClickListener.onStartRecord();
        }
        
        Log.d(TAG, "Recording started");
    }
    
    private void stopRecording() {
        isRecording = false;
        updateUI();
        
        if (recordClickListener != null) {
            recordClickListener.onStopRecord();
        }
        
        Log.d(TAG, "Recording stopped");
    }
    
    private void updateUI() {
        mainHandler.post(() -> {
            if (recordButton != null) {
                if (isRecording) {
                    recordButton.setImageResource(R.drawable.stop_small);
                    recordButton.setBackgroundResource(R.drawable.floating_record_button_recording_bg);
                    statusText.setText("Recording...");
                    statusText.setVisibility(View.VISIBLE);
                    // Add pulsing animation
                    startPulseAnimation();
                } else {
                    recordButton.setImageResource(R.drawable.play_small);
                    recordButton.setBackgroundResource(R.drawable.floating_record_button_bg);
                    statusText.setText("Tap to Record");
                    statusText.setVisibility(View.GONE);
                    // Stop animation
                    stopPulseAnimation();
                }
            }
        });
    }
    
    private void startPulseAnimation() {
        if (recordButton != null) {
            recordButton.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(500)
                .withEndAction(() -> {
                    if (recordButton != null && isRecording) {
                        recordButton.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(500)
                            .withEndAction(() -> {
                                if (isRecording) {
                                    startPulseAnimation(); // Continue pulsing
                                }
                            })
                            .start();
                    }
                })
                .start();
        }
    }
    
    private void stopPulseAnimation() {
        if (recordButton != null) {
            recordButton.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(200)
                .start();
        }
    }
    
    public void show() {
        Log.d(TAG, "show() called - isVisible: " + isVisible + ", windowManager: " + (windowManager != null) + ", floatingView: " + (floatingView != null));
        
        if (!isVisible && windowManager != null && floatingView != null) {
            try {
                Log.d(TAG, "Adding floating view to WindowManager...");
                windowManager.addView(floatingView, params);
                isVisible = true;
                Log.d(TAG, "Floating record button shown successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error showing floating record button", e);
                e.printStackTrace();
            }
        } else {
            if (isVisible) {
                Log.w(TAG, "Floating button already visible");
            }
            if (windowManager == null) {
                Log.e(TAG, "WindowManager is null");
            }
            if (floatingView == null) {
                Log.e(TAG, "FloatingView is null");
            }
        }
    }
    
    public void hide() {
        if (isVisible && windowManager != null && floatingView != null) {
            try {
                windowManager.removeView(floatingView);
                isVisible = false;
                Log.d(TAG, "Floating record button hidden");
            } catch (Exception e) {
                Log.e(TAG, "Error hiding floating record button", e);
            }
        }
    }
    
    public void setOnRecordClickListener(OnRecordClickListener listener) {
        this.recordClickListener = listener;
    }
    
    public boolean isRecording() {
        return isRecording;
    }
    
    public boolean isVisible() {
        return isVisible;
    }
    
    public void setRecordingState(boolean recording) {
        this.isRecording = recording;
        updateUI();
    }
    
    public void showStatusText(String text) {
        mainHandler.post(() -> {
            if (statusText != null) {
                statusText.setText(text);
                statusText.setVisibility(View.VISIBLE);
                
                // Hide after 3 seconds
                mainHandler.postDelayed(() -> {
                    if (statusText != null && !isRecording) {
                        statusText.setVisibility(View.GONE);
                    }
                }, 3000);
            }
        });
    }
    
    public void destroy() {
        stopPulseAnimation();
        hide();
        floatingView = null;
        recordButton = null;
        statusText = null;
        recordClickListener = null;
    }
}