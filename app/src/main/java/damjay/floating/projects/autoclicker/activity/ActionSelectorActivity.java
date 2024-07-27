package damjay.floating.projects.autoclicker.activity;

import android.app.AlertDialog;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import androidx.appcompat.app.AppCompatActivity;

import damjay.floating.projects.R;
import damjay.floating.projects.autoclicker.service.ClickerAccessibilityService;

public class ActionSelectorActivity extends AppCompatActivity {
    public static final int SERVICE_ACCESSIBILITY = 100;
    
    static BluetoothSocket bluetoothSocket;
    private AlertDialog alertDialog;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_action_selector);
        
        setResponse();
    }
    
    private void setResponse() {
        findViewById(R.id.asService).setOnClickListener((v) -> startAccessibilityService());
        findViewById(R.id.asController).setOnClickListener((v) -> showControlActivity());
    }
    
    private void startAccessibilityService() {
        if(checkIfEnabled())
            startClickerAccessibilityService();
    }

    private void startClickerAccessibilityService() {
        Intent intent = new Intent(this, ClickerAccessibilityService.class);
        ClickerAccessibilityService.bluetoothSocket = bluetoothSocket;
        finish();
        startService(intent);
    }

    private void showControlActivity() {
        Intent intent = new Intent(this, ClickerActivity.class);
        ClickerActivity.bluetoothSocket = bluetoothSocket;
        startActivity(intent);
    }
    
    private boolean checkIfEnabled() {
        int accessEnabled = 0; 
        try { 
            accessEnabled = Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) { 
            e.printStackTrace(); 
        }
        if (accessEnabled == 0) {
            alertDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.activate_service)
                .setMessage(R.string.activate_service_message)
                .setPositiveButton(R.string.settings, (dialog, id) -> showAccessibilityPage())
                .setNegativeButton(R.string.cancel, (dialog, id) -> finish())
                .create();
            alertDialog.show();
            
        }
        return accessEnabled != 0;
    }
    
    private void showAccessibilityPage() {
        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityForResult(intent, SERVICE_ACCESSIBILITY);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        
        if (checkIfEnabled())
            startClickerAccessibilityService();
    }

}
