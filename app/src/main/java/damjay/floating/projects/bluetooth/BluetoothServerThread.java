package damjay.floating.projects.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import damjay.floating.projects.autoclicker.activity.HostActivity;
import java.util.UUID;

public class BluetoothServerThread extends Thread {
    private BluetoothServerSocket serverSocket;
    private BluetoothCallback callback;
    
    public BluetoothServerThread(BluetoothCallback callback, BluetoothAdapter adapter, String name, UUID uuid) {
        this.callback = callback;
        try {
            serverSocket = adapter.listenUsingRfcommWithServiceRecord(name, uuid);
        } catch (Throwable t) {
            t.printStackTrace();
            callback.onResult(BluetoothCallback.ERROR, t);
            return;
        }
        if (serverSocket == null) {
            callback.onResult(BluetoothCallback.ERROR, null);
        }
    }
    
    @Override
    public void run() {
        if (serverSocket == null) return;
        BluetoothSocket socket = null;
        try {
            while (serverSocket != null && socket == null) {
                socket = serverSocket.accept();
            }
        } catch (Throwable t) {
            t.printStackTrace();
            callback.onResult(BluetoothCallback.ERROR, t);
            try {
                serverSocket.close();
            } catch (Throwable closeError) {
                closeError.printStackTrace();
            }
            return;
        }
        if (socket != null) {
            callback.onResult(BluetoothCallback.SUCCESS, socket);
        }
        try {
            if (serverSocket != null)
                serverSocket.close();
        } catch (Throwable closeError) {
            closeError.printStackTrace();
        }
    }
    
    public void cancel() {
        try {
            serverSocket.close();
            serverSocket = null;
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
    
}
