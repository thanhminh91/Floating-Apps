package damjay.floating.projects;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import damjay.floating.projects.voicetranslator.VoiceTranslatorService;

public class VoiceTranslatorLauncherActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Start VoiceTranslatorService immediately
        try {
            Intent serviceIntent = new Intent(this, VoiceTranslatorService.class);
            startService(serviceIntent);
            Toast.makeText(this, "Starting Video Voice Translator...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error starting Voice Translator: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        
        // Close this activity immediately
        finish();
    }
}