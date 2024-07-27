package damjay.floating.projects.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Locale;

import static damjay.floating.projects.bluetooth.BluetoothOperations.BluetoothOperationsConstants.*;

public class BluetoothOperations implements Runnable {
    private BluetoothSocket socket;
    private BluetoothOperationsCallback bluetoothCallback;
    private Throwable openError;

    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    
    private boolean closed = false;

    public BluetoothOperations(BluetoothSocket socket) {
        this.socket = socket;

        try {
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream = new DataOutputStream(socket.getOutputStream());
        } catch (Throwable openError) {
            this.openError = openError;
            openError.printStackTrace();
        }
    }
    
    public BluetoothSocket getSocket() {
        return this.socket;
    }
    
    public void startReading(BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        
        this.bluetoothCallback = bluetoothOperationsCallback;
        if (openError != null) {
            handle(openError, bluetoothCallback);
            return;
        }
        else
            new Thread(this).start();
    }

    @Override
    public void run() {
        if (openError != null) {
            onError(bluetoothCallback, openError);
            return;
        }
        
        while (!closed) {
            try {
                int type = inputStream.read();
                if (type == -1) continue;
                switch (type) {
                    case TYPE_TEXT:
                        onSuccess(bluetoothCallback, TYPE_TEXT, inputStream.readUTF());
                        break;
                    case TYPE_BYTE:
                        onSuccess(bluetoothCallback, TYPE_TEXT, inputStream.readByte());
                        break;
                    case TYPE_SHORT:
                         onSuccess(bluetoothCallback, TYPE_TEXT, inputStream.readShort());                   
                        break;
                    case TYPE_CHAR:
                        onSuccess(bluetoothCallback, TYPE_TEXT, inputStream.readChar());
                        break;
                    case TYPE_INT:
                        onSuccess(bluetoothCallback, TYPE_TEXT, inputStream.readInt());
                        break;
                    case TYPE_LONG:
                        onSuccess(bluetoothCallback, TYPE_TEXT, inputStream.readLong());
                        break;
                    case TYPE_FLOAT:
                        onSuccess(bluetoothCallback, TYPE_TEXT, inputStream.readFloat());
                        break;
                    case TYPE_DOUBLE:
                        onSuccess(bluetoothCallback, TYPE_TEXT, inputStream.readDouble());
                        break;
                    case TYPE_RAW_C0NTENT:
                        byte[] bytes = new byte[inputStream.readUnsignedShort()];
                        inputStream.readFully(bytes);
                        onSuccess(bluetoothCallback, TYPE_RAW_C0NTENT, bytes);
                        break;
                    case TYPE_EXIT:
                        onSuccess(bluetoothCallback, TYPE_EXIT, null);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown type " + type);
                }
            } catch (Throwable t) {
                t.printStackTrace();
                if (closed) return;
                onError(bluetoothCallback, t);
                break;
            }
        }
    }
    
    public void write(String content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(TYPE_TEXT);
            outputStream.writeUTF(content);
            onSuccess(bluetoothOperationsCallback, TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }
    
    public void write(byte content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(TYPE_BYTE);
            outputStream.writeByte(content);
            onSuccess(bluetoothOperationsCallback, TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }
    
    public void write(short content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(TYPE_SHORT);
            outputStream.writeShort(content);
            onSuccess(bluetoothOperationsCallback, TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }
    
    public void write(char content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(TYPE_CHAR);
            outputStream.writeChar(content);
            onSuccess(bluetoothOperationsCallback, TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }
    
    public void write(int content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(TYPE_INT);
            outputStream.writeInt(content);
            onSuccess(bluetoothOperationsCallback, TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void write(long content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(TYPE_LONG);
            outputStream.writeLong(content);
            onSuccess(bluetoothOperationsCallback, TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }
    
    public void write(float content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(TYPE_FLOAT);
            outputStream.writeFloat(content);
            onSuccess(bluetoothOperationsCallback, TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void write(double content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(TYPE_DOUBLE);
            outputStream.writeDouble(content);
            onSuccess(bluetoothOperationsCallback, TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void write(byte[] content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(TYPE_RAW_C0NTENT);
            outputStream.write(content);
            onSuccess(bluetoothOperationsCallback, TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }
    
    public void writeExit(BluetoothOperationsCallback bluetoothOperationsCallback) {
        try {
            outputStream.write(TYPE_EXIT);
            onSuccess(bluetoothOperationsCallback, TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void close() {
        try {
            closed = true;
            inputStream.close();
            outputStream.close();
            socket.close();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
    
    private void checkNull(BluetoothOperationsCallback bluetoothOperationsCallback) {
    	if (bluetoothOperationsCallback == null)
            throw new NullPointerException("bluetoothOperationsCallback = null");
    }
    
    private void handle(Throwable t, BluetoothOperationsCallback bluetoothOperationsCallback) {
        t.printStackTrace();
        onError(bluetoothOperationsCallback, t);
    }
    
    private void onSuccess(BluetoothOperationsCallback callback, byte type, Object returnValue) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(type, returnValue));
    }
    
    private void onError(BluetoothOperationsCallback callback, Throwable error) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onError(error));
    }

    public static interface BluetoothOperationsCallback {
        int READ_OPERATION = 0;
        int ERROR = 2;

        /*
         * params: operationType - The operation type, either read or error operation.
         * returnValue - If it is a read operation the returnValue is of type byte[].
         * If operationType is ERROR, returnValue will be either Throwable or null
         */
        void onSuccess(byte type, Object returnValue);
        
        void onError(Throwable t);
    }

    public static interface BluetoothOperationsConstants {
        byte TYPE_TEXT = 0;
        byte TYPE_BYTE = 1;
        byte TYPE_SHORT = 2;
        byte TYPE_CHAR = 3;
        byte TYPE_INT = 4;
        byte TYPE_LONG = 5;
        byte TYPE_FLOAT = 6;
        byte TYPE_DOUBLE = 7;
        /**
         * In the case of a raw content, the maximum number of bytes accepted
         * is 65535 (two bytes). The first two bytes of the content to be
         * written is the total length of data to be written.
         */
        byte TYPE_RAW_C0NTENT = 8;
        byte TYPE_SUCCESS = 9;
        byte TYPE_EXIT = 10;
    }

}