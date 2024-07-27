package damjay.floating.projects.autoclicker.activity;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import damjay.floating.projects.autoclicker.service.ClickerAccessibilityService;
import damjay.floating.projects.bluetooth.BluetoothCallback;
import damjay.floating.projects.bluetooth.BluetoothServerThread;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import damjay.floating.projects.R;
import java.util.UUID;

public class HostActivity extends AppCompatActivity implements BluetoothCallback {
    private BluetoothAdapter adapter;
    private BluetoothSocket socket;
    
    private AlertDialog alertDialog;
    private BluetoothServerThread serverThread;
    private boolean closed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host);

        getSupportActionBar().setTitle(R.string.asHost);

        if (startListening()) {
            startWaiting();
        } else {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.bluetooth_error_occurred)
                    .setPositiveButton(R.string.finish, (dialog, id) -> {
                        dialog.dismiss();
                        finish();
                    })
                    .setCancelable(false)
                    .create()
                    .show();
        }
    }
    
    private boolean startListening() {
        try {
            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
            adapter = bluetoothManager.getAdapter();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (adapter != null) {
            serverThread = new BluetoothServerThread(
                            this,
                            adapter,
                            getResources().getString(R.string.app_name),
                            UUID.fromString(getResources().getString(R.string.clicker_uuid)));
            serverThread.start();
            return true;
        }
        return false;
    }

    private void startWaiting() {
        View view = getLayoutInflater().inflate(R.layout.loading_view, null);
        TextView loadingText = view.findViewById(R.id.loading_text);
        loadingText.setText(R.string.waiting_for_connection);

        alertDialog = new AlertDialog.Builder(this)
                .setView(view)
                .setNegativeButton(R.string.cancel, (dialog, id) -> {
                    dialog.dismiss();
                    cancel();
                })
                .setCancelable(false)
                .create();
        alertDialog.show();
    }
    
    private void cancel() {
        closed = true;
    	if (serverThread != null) {
            serverThread.cancel();
        }
        finish();
    }

    @Override
    public void onResult(int resultCode, Object artifact) {
        runOnUiThread(() -> checkResult(resultCode, artifact));
    }
    
    private void startSelectorActivity() {
        Intent intent = new Intent(this, ActionSelectorActivity.class);
        ActionSelectorActivity.bluetoothSocket = socket;
        // finish();
        startActivity(intent);
    }
    
    public void checkResult(int resultCode, Object artifact) {
        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }
        if (resultCode == BluetoothCallback.SUCCESS) {
            if (artifact != null && artifact instanceof BluetoothSocket) {
                // Connected successfully
                socket = (BluetoothSocket) artifact;
                startSelectorActivity();
            }
        } else {
            if (closed) return;
            new AlertDialog.Builder(this)
                .setMessage(R.string.bluetooth_error_occurred)
                .setPositiveButton(R.string.finish, (dialog, id) -> {
                    dialog.dismiss();
                    finish();
                })
                .setCancelable(false)
                .create()
                .show();
        }
    }

}
