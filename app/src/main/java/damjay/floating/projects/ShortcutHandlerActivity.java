package damjay.floating.projects;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class ShortcutHandlerActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Get the action from the intent
        String action = getIntent().getAction();
        
        if (action != null && action.equals("OPEN_RANDOM_NUMBER")) {
            // Start the NumberRangeService
            Intent serviceIntent = new Intent(this, NumberRangeService.class);
            startService(serviceIntent);
        }
        
        // Finish this activity immediately
        finish();
    }
}