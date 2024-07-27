package damjay.floating.projects.bluetooth;

public interface BluetoothCallback {
    int ERROR = 0;
    int SUCCESS = 1;
    
    void onResult(int resultCode, Object artifact);
}
