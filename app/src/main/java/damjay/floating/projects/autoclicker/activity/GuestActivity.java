package damjay.floating.projects.autoclicker.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import static android.view.ViewGroup.LayoutParams.*;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import damjay.floating.projects.R;
import damjay.floating.projects.bluetooth.BluetoothOperations;
import damjay.floating.projects.customadapters.BluetoothDeviceAdapter;

import java.util.UUID;

public class GuestActivity extends AppCompatActivity
        implements AdapterView.OnItemClickListener, Runnable {
    private ListView deviceList;
    private BluetoothDeviceAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;

    private AlertDialog waitingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initView();
        setContentView(deviceList);
        getSupportActionBar().setTitle(R.string.asGuest);
    }

    private void initView() {
        deviceList = new ListView(this);
        deviceList.setLayoutParams(new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        View content = getLayoutInflater().inflate(R.layout.activity_guest, null);
        deviceList.addHeaderView(content);
        bluetoothAdapter = new BluetoothDeviceAdapter(this, deviceList);
        deviceList.setAdapter(bluetoothAdapter);
        deviceList.setOnItemClickListener(this);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onItemClick(AdapterView<?> adapter, View childView, int position, long id) {
        Object tag = childView.getTag();
        if (!(tag instanceof BluetoothDevice)) return;
        BluetoothDevice device = (BluetoothDevice) tag;
        BluetoothSocket socket = null;
        try {
            socket =
                    device.createRfcommSocketToServiceRecord(
                            UUID.fromString(getResources().getString(R.string.clicker_uuid)));
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (socket == null) {
            onComplete(null);
        } else {
            this.bluetoothSocket = socket;
            new Thread(this).start();
            startWaiting();
        }
    }

    private void onComplete(BluetoothSocket connectedSocket) {
        if (waitingDialog != null) {
            waitingDialog.dismiss();
            waitingDialog = null;
        }
        if (connectedSocket == null) {
            // Error occurred
            new AlertDialog.Builder(this)
                    .setMessage(R.string.bluetooth_error_occurred)
                    .setCancelable(true)
                    .create()
                    .show();
        } else {
            // Connected with other device successfully
            Intent intent = new Intent(this, ActionSelectorActivity.class);
            ActionSelectorActivity.bluetoothSocket = connectedSocket;
            startActivity(intent);
        }
    }

    private void startWaiting() {
        View view = getLayoutInflater().inflate(R.layout.loading_view, null);
        TextView loadingText = view.findViewById(R.id.loading_text);
        loadingText.setText(R.string.waiting_for_connection);

        waitingDialog =
                new AlertDialog.Builder(this)
                        .setView(view)
                        .setNegativeButton(R.string.cancel, (dialog, id) -> {
                            dialog.dismiss();
                            cancel();
                        })
                        .setCancelable(false)
                        .create();
        waitingDialog.show();
    }

    private void cancel() {
        finish();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void run() {
        // bluetoothAdapter.cancelDiscovery();
        boolean connected = false;
        try {
            bluetoothSocket.connect();
            connected = true;
        } catch (Throwable connectException) {
            connectException.printStackTrace();
            try {
                bluetoothSocket.close();
                bluetoothSocket = null;
            } catch (Throwable closeException) {
                closeException.printStackTrace();
            }
        }
        final boolean connectedFlag = connected;
        runOnUiThread(() -> onComplete(connectedFlag ? bluetoothSocket : null));
    }

}
