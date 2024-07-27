package damjay.floating.projects.customadapters;

import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.view.LayoutInflater;
import android.widget.ListView;
import android.bluetooth.BluetoothDevice;
import android.view.ViewGroup;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.sql.Array;
import java.util.ArrayList;
import java.util.Set;

public class BluetoothDeviceAdapter extends BaseAdapter {
    private Context context;
    private ListView bluetoothDevicesList;
    
    private ArrayList<BluetoothDevice> bluetoothDevices;
    
    public BluetoothDeviceAdapter(Context context, ListView bluetoothList) {
        this.context = context;
        this.bluetoothDevicesList = bluetoothList;
        
        startSearching();
    }

    private void startSearching() {
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            BluetoothAdapter adapter = bluetoothManager.getAdapter();
            if (adapter != null) {
                if (adapter.isEnabled()) {
                    Set<BluetoothDevice> bluetoothDevices = adapter.getBondedDevices();
                    if (bluetoothDevices != null) {
                        this.bluetoothDevices = new ArrayList<>(bluetoothDevices);
                    }
                }
            }
        }
    }

    @Override
    public int getCount() {
        return bluetoothDevices == null ? 0 : bluetoothDevices.size();
    }
    

    @Override
    public Object getItem(int position) {
        return bluetoothDevices != null && bluetoothDevices.size() > position ? bluetoothDevices.get(position) : null;
    }
    

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View view, ViewGroup viewGroup) {
        boolean deviceAvailable = bluetoothDevices != null && bluetoothDevices.size() > position;
        if (view == null) {
            view = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, viewGroup, false);
            if (deviceAvailable) {
                view.setTag(bluetoothDevices.get(position));
            }
        }
        if (deviceAvailable) {
            BluetoothDevice device = bluetoothDevices.get(position);
            TextView text1 = view.findViewById(android.R.id.text1);
            TextView text2 = view.findViewById(android.R.id.text2);
            text1.setText(device.getName());
            text2.setText(device.getAddress());
        }
        return view;
    }
    
}
