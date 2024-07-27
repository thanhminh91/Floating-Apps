package damjay.floating.projects.autoclicker.activity;

import android.app.AlertDialog;
import android.bluetooth.BluetoothSocket;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.bluetooth.BluetoothOperations;
import damjay.floating.projects.bluetooth.BluetoothOperations.BluetoothOperationsCallback;
import static damjay.floating.projects.bluetooth.BluetoothOperations.BluetoothOperationsConstants.*;

public class ClickerActivity extends AppCompatActivity implements BluetoothOperationsCallback, View.OnClickListener {
    public static BluetoothSocket bluetoothSocket;
    private static BluetoothOperations btOperation;
    private LinearLayout clickerContainer;
    private Button addButton;
    private Button removeButton;

    public static final byte CLICKER_ADD_POINT = -1;
    public static final byte CLICKER_DELETE_POINT = -2;
    
    private int curNumOfButtons = 0;
    
    private boolean pendingAddButton = false;
    private boolean pendingRemoveButton = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clicker);

        clickerContainer = findViewById(R.id.clickerContainer);
        addButton = findViewById(R.id.addButton);
        removeButton = findViewById(R.id.removeButton);

        if (bluetoothSocket != null) {
            btOperation = new BluetoothOperations(bluetoothSocket);
            btOperation.startReading(this);
        } else {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.bluetooth_error_occurred)
                    .setNegativeButton(R.string.finish, (dialog, id) -> finish())
                    .setCancelable(false)
                    .create()
                    .show();
            return;
        }
        // Set on click listener for "Add" and "Remove"
        addButton.setOnClickListener((v) -> sendToDevice(CLICKER_ADD_POINT, this));
        removeButton.setOnClickListener((v) -> sendToDevice(CLICKER_DELETE_POINT, this));
        // Disable the "Remove" button
        removeButton.setEnabled(false);
        // Minus, Select, zoom, timer
    }

    public void sendToDevice(byte value, BluetoothOperationsCallback callback) {
        if (btOperation != null) {
            btOperation.write(value, callback);
            pendingAddButton = value == CLICKER_ADD_POINT ? true : pendingAddButton;
            pendingRemoveButton = value == CLICKER_DELETE_POINT ? true : pendingRemoveButton;
        }
        else Toast.makeText(this, R.string.null_socket, Toast.LENGTH_SHORT).show();
    }
    
    private void addNewButton() {
        pendingAddButton = false;
        curNumOfButtons++;
        removeButton.setEnabled(true);
        showButtons();
    }
    
    private void removeLastButton() {
        pendingRemoveButton = false;
        if (--curNumOfButtons <= 0) {
            curNumOfButtons = 0;
            removeButton.setEnabled(false);
        }
        showButtons();
    }
    
    private void showButtons() {
        final int maxButtonOnLine = 4;
        int buttonsOnLine = 0;
        
        // Get the number of buttons to be on a line
        for (int i = 1; i <= curNumOfButtons && i <= maxButtonOnLine; i++) {
            buttonsOnLine = i;
            if (i * (i + 1) >= curNumOfButtons) {
                break;
            }
        }
        
        clickerContainer.removeAllViews();
        
        int curButton = 0;
        while (curButton < curNumOfButtons) {
            LinearLayout horizontalLine = new LinearLayout(this);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0);
            layoutParams.weight = 1;
            horizontalLine.setOrientation(LinearLayout.HORIZONTAL);
            horizontalLine.setLayoutParams(layoutParams);
            
            for (int i = 0; i < buttonsOnLine; i++, curButton++) {
                Button button = new Button(this);
                LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT);
                buttonParams.weight = 1;
                int marginPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3.0f, getResources().getDisplayMetrics());
                buttonParams.setMargins(marginPx, marginPx, marginPx, marginPx);
                
                button.setLayoutParams(buttonParams);
                button.setText(Integer.toString(curButton + 1));
                button.setOnClickListener(this);
                horizontalLine.addView(button);
            }
            clickerContainer.addView(horizontalLine);
        }
    }
    
    public void onClick(View view) {
        btOperation.write(Byte.parseByte(((Button) view).getText().toString()), this);
    }
    
    @Override
    public void onSuccess(byte type, Object returnValue) {
        switch (type) {
            case TYPE_SUCCESS:
                if (pendingAddButton)
                    addNewButton();
                if (pendingRemoveButton)
                    removeLastButton();
                break;
            case TYPE_BYTE:
                byte byteValue = (byte) returnValue;
                if (byteValue == CLICKER_ADD_POINT) {
                    addNewButton();
                } else if (byteValue == CLICKER_DELETE_POINT) {
                    removeLastButton();
                } else {
                    // Invalid command sent
                    System.out.println("Invalid byteValue: " + byteValue);
                }
                break;
            case TYPE_EXIT:
                btOperation.close();
                new AlertDialog.Builder(this)
                    .setTitle(R.string.device_disconnected)
                    .setMessage(R.string.device_disconnected_message)
                    .setCancelable(false)
                    .setNeutralButton(R.string.exit, (dialog, id) -> finish())
                    .create()
                    .show();
                break;
            default:
                // Should we close?
                System.out.println("Unrecognized type: " + type);
        }
    }

    @Override
    public void onError(Throwable t) {
        new AlertDialog.Builder(this)
            .setMessage(getResources().getString(R.string.bluetooth_error_occurred) + (t == null ? "" : getResources().getString(R.string.reason, t.getMessage())))
            .setNegativeButton(R.string.finish, (dialog, id) -> finish())
            .setCancelable(false)
            .create()
            .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (btOperation != null) btOperation.close();
    }
    
}
