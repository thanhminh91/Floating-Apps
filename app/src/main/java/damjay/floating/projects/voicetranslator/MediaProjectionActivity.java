package damjay.floating.projects.voicetranslator;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;

public class MediaProjectionActivity extends Activity {
    private static final String TAG = "MediaProjectionActivity";
    private static final int REQUEST_CODE_MEDIA_PROJECTION = 1001;
    
    public static final String ACTION_REQUEST_MEDIA_PROJECTION = "REQUEST_MEDIA_PROJECTION";
    public static final String EXTRA_RESULT_CODE = "RESULT_CODE";
    public static final String EXTRA_RESULT_DATA = "RESULT_DATA";
    
    private MediaProjectionManager mediaProjectionManager;
    private FileLogger fileLogger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        fileLogger = FileLogger.getInstance(this);
        fileLogger.d(TAG, "MediaProjectionActivity created");
        
        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        
        String action = getIntent().getAction();
        if (ACTION_REQUEST_MEDIA_PROJECTION.equals(action)) {
            requestMediaProjectionPermission();
        } else {
            fileLogger.w(TAG, "Unknown action: " + action);
            finish();
        }
    }
    
    private void requestMediaProjectionPermission() {
        fileLogger.d(TAG, "Requesting MediaProjection permission");
        try {
            Intent intent = mediaProjectionManager.createScreenCaptureIntent();
            startActivityForResult(intent, REQUEST_CODE_MEDIA_PROJECTION);
        } catch (Exception e) {
            fileLogger.e(TAG, "Failed to create screen capture intent", e);
            sendResultToService(RESULT_CANCELED, null);
            finish();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        fileLogger.d(TAG, "onActivityResult received:");
        fileLogger.d(TAG, "  - Request code: " + requestCode + " (expected: " + REQUEST_CODE_MEDIA_PROJECTION + ")");
        fileLogger.d(TAG, "  - Result code: " + resultCode + " (RESULT_OK=" + RESULT_OK + ", RESULT_CANCELED=" + RESULT_CANCELED + ")");
        fileLogger.d(TAG, "  - Data intent: " + (data != null ? "present" : "null"));
        
        if (requestCode == REQUEST_CODE_MEDIA_PROJECTION) {
            fileLogger.d(TAG, "Sending result to service...");
            sendResultToService(resultCode, data);
        } else {
            fileLogger.w(TAG, "Unexpected request code: " + requestCode);
        }
        
        finish();
    }
    
    private void sendResultToService(int resultCode, Intent data) {
        Intent serviceIntent = new Intent(this, VoiceTranslatorService.class);
        serviceIntent.setAction(VoiceTranslatorService.ACTION_MEDIA_PROJECTION_RESULT);
        serviceIntent.putExtra(EXTRA_RESULT_CODE, resultCode);
        if (data != null) {
            serviceIntent.putExtra(EXTRA_RESULT_DATA, data);
        }
        
        try {
            startService(serviceIntent);
            fileLogger.d(TAG, "Sent MediaProjection result to service: " + resultCode);
        } catch (Exception e) {
            fileLogger.e(TAG, "Failed to send result to service", e);
        }
    }
}