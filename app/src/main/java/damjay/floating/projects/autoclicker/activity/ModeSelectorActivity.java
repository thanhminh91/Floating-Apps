package damjay.floating.projects.autoclicker.activity;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import android.view.View;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import damjay.floating.projects.MainActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.utils.ViewsUtils;

public class ModeSelectorActivity extends AppCompatActivity {
    public static final int ENABLE_BLUETOOTH = 104;
    public static final int BLUETOOTH_PERMISSIONS = 105;

    private Class pendingLaunchClass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mode_selector);

        getSupportActionBar().setTitle(R.string.select_connect_mode);
        initializeViews();
        if (permissionsGranted())
            checkBluetooth();
        else
            showPermissions();
    }

    private void initializeViews() {
        findViewById(R.id.asHost)
                .setOnClickListener(getClickListener(HostActivity.class));
        findViewById(R.id.asGuest)
                .setOnClickListener(getClickListener(GuestActivity.class));
    }
    
    public View.OnClickListener getClickListener(final Class clazz) {
    	return (v) -> {
            if (permissionsGranted()) {
                if (checkBluetooth()) {
                    startActivity(new Intent(this, clazz));
                } else {
                    pendingLaunchClass = clazz;
                }
            } else {
                showPermissions();
            }
        };
    }

    private boolean checkBluetooth() {
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
            if (bluetoothManager != null) {
                BluetoothAdapter adapter = bluetoothManager.getAdapter();
                if (adapter != null) {
                    if (!adapter.isEnabled()) {
                        new AlertDialog.Builder(this)
                            .setMessage(R.string.activate_bluetooth)
                            .setPositiveButton(R.string.ok, (dialog, id) -> {
                                dialog.dismiss();
                                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                                startActivityForResult(intent, ENABLE_BLUETOOTH);
                                })
                            .setNegativeButton(R.string.cancel, (dialog, id) -> finish())
                            .setCancelable(false)
                            .create()
                            .show();
                    } else {
                        return true;
                    }
                    return false;
                }
            }
        }
        new AlertDialog.Builder(this)
            .setMessage(R.string.bluetooth_not_supported)
            .setPositiveButton(R.string.back, (dialog, id) -> finish())
            .setCancelable(false)
            .create()
            .show();
        return false;
    }
    
    private boolean permissionsGranted() {
        return Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }
    
    private void showPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)) {
                new AlertDialog.Builder(this)
                    .setMessage(R.string.bluetooth_permission_needed)
                    .setPositiveButton(R.string.grant, (dialog, id) -> {dialog.dismiss(); requestPermissions();})
                    .setNegativeButton(R.string.cancel, (dialog, id) -> finish())
                    .create()
                    .show();
            } else {
                requestPermissions();
            }
        }
    }

    @RequiresApi(value = 23)
    private void requestPermissions() {
        requestPermissions(new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_CONNECT}, BLUETOOTH_PERMISSIONS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ENABLE_BLUETOOTH) {
            if (resultCode != RESULT_OK) {
                new AlertDialog.Builder(this)
                    .setMessage(R.string.enable_bluetooth)
                    .setPositiveButton(R.string.cancel, (dialog, id) -> finish())
                    .setCancelable(false)
                    .create()
                    .show();
            } else {
                if (pendingLaunchClass != null) {
                    if (permissionsGranted()) {
                        startActivity(new Intent(this, pendingLaunchClass));
                    }
                    pendingLaunchClass = null;
                }
            }
        } else if (requestCode == BLUETOOTH_PERMISSIONS) {
            if (permissionsGranted()) {
                checkBluetooth();
            } else {
                new AlertDialog.Builder(this)
                    .setMessage(R.string.bluetooth_permission_needed)
                    .setPositiveButton(R.string.settings, (dialog, id) -> {
                        dialog.dismiss();
                        ViewsUtils.openAppInfo(this, MainActivity.class.getPackage().getName(), BLUETOOTH_PERMISSIONS);
                    })
                    .setNegativeButton(R.string.cancel, (dialog, id) -> finish())
                    .setCancelable(false)
                    .create()
                    .show();
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == BLUETOOTH_PERMISSIONS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkBluetooth();
            } else {
                if (permissionsGranted()) return;
                new AlertDialog.Builder(this)
                    .setMessage(R.string.bluetooth_permission_needed)
                    .setPositiveButton(R.string.settings, (dialog, id) -> {
                        dialog.dismiss();
                        ViewsUtils.openAppInfo(this, MainActivity.class.getPackage().getName(), BLUETOOTH_PERMISSIONS);
                    })
                    .setNegativeButton(R.string.cancel, (dialog, id) -> finish())
                    .setCancelable(false)
                    .create()
                    .show();
            }
        }
    }

}
