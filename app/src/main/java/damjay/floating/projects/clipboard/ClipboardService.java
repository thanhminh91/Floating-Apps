package damjay.floating.projects.clipboard;

import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import damjay.floating.projects.R;
import damjay.floating.projects.utils.TouchState;
import damjay.floating.projects.utils.ViewsUtils;

public class ClipboardService extends Service {
    private static final String TAG = "ClipboardService";
    private static final String SERVICE_TYPE = "_clipshare._tcp";
    private static final String SERVICE_NAME = "FloatingClipShare";
    private static final int PORT = 0; // Use 0 to let the system pick an available port

    private WindowManager windowManager;
    private View floatingView;
    private TextView statusTextView;
    private ImageView previewImageView;
    private TouchState touchState;

    private NsdManager nsdManager;
    private NsdManager.RegistrationListener registrationListener;
    private NsdManager.DiscoveryListener discoveryListener;
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private ClipboardManager clipboardManager;
    
    private boolean isRunning = false;
    private int servicePort;

    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newCachedThreadPool();
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        
        setupFloatingUI();
        setupClipboardListener();
        startNetworkService();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case "SHARE_TEXT":
                    shareCurrentClipboardContent();
                    break;
                case "SCAN_DEVICES":
                    startDiscovery();
                    break;
            }
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopNetworkService();
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
        }
        super.onDestroy();
    }

    private void setupFloatingUI() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        
        // Inflate the floating view layout
        floatingView = inflater.inflate(R.layout.clipboard_floating_layout, null);
        
        // Set up the touch state for dragging
        touchState = new TouchState();
        
        // Find views
        statusTextView = floatingView.findViewById(R.id.status_text);
        previewImageView = floatingView.findViewById(R.id.preview_image);
        Button shareButton = floatingView.findViewById(R.id.share_button);
        Button scanButton = floatingView.findViewById(R.id.scan_button);
        View closeButton = floatingView.findViewById(R.id.close_button);
        
        // Set click listeners
        shareButton.setOnClickListener(v -> shareCurrentClipboardContent());
        scanButton.setOnClickListener(v -> startDiscovery());
        closeButton.setOnClickListener(v -> stopSelf());
        
        // Setup floating window parameters
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewsUtils.getWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 100;
        
        // Add view to window manager
        windowManager.addView(floatingView, params);
        
        // Setup touch listener for dragging
        floatingView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchState.savePosition(event.getRawX(), event.getRawY());
                    touchState.saveCoordinates(params.x, params.y);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float xDiff = event.getRawX() - touchState.getStartRawX();
                    float yDiff = event.getRawY() - touchState.getStartRawY();
                    params.x = touchState.getCoordinateX() + (int) xDiff;
                    params.y = touchState.getCoordinateY() + (int) yDiff;
                    windowManager.updateViewLayout(floatingView, params);
                    return true;
                default:
                    return false;
            }
        });
        
        updateStatus("Clipboard service started");
    }

    private void setupClipboardListener() {
        clipboardManager.addPrimaryClipChangedListener(() -> {
            if (clipboardManager.hasPrimaryClip()) {
                ClipData clipData = clipboardManager.getPrimaryClip();
                if (clipData != null && clipData.getItemCount() > 0) {
                    ClipData.Item item = clipData.getItemAt(0);
                    
                    // Clear the preview image first
                    previewImageView.setVisibility(View.GONE);
                    
                    if (item.getText() != null) {
                        String text = item.getText().toString();
                        updateStatus("Text copied: " + (text.length() > 20 ? text.substring(0, 20) + "..." : text));
                    } else if (item.getUri() != null) {
                        try {
                            InputStream inputStream = getContentResolver().openInputStream(item.getUri());
                            if (inputStream != null) {
                                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                                if (bitmap != null) {
                                    previewImageView.setImageBitmap(bitmap);
                                    previewImageView.setVisibility(View.VISIBLE);
                                    updateStatus("Image copied to clipboard");
                                }
                                inputStream.close();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error handling clipboard image", e);
                            updateStatus("Error loading image from clipboard");
                        }
                    }
                }
            }
        });
    }

    private void startNetworkService() {
        try {
            // Create the server socket on an available port
            serverSocket = new ServerSocket(PORT);
            servicePort = serverSocket.getLocalPort();
            
            // Start the server thread to accept connections
            executorService.submit(this::acceptConnections);
            
            // Register the service on the network
            registerService();
            
            isRunning = true;
            updateStatus("Service started on port " + servicePort);
        } catch (IOException e) {
            Log.e(TAG, "Failed to start network service", e);
            updateStatus("Failed to start network service");
        }
    }
    
    private void stopNetworkService() {
        isRunning = false;
        
        // Unregister the service
        if (nsdManager != null && registrationListener != null) {
            try {
                nsdManager.unregisterService(registrationListener);
            } catch (Exception e) {
                Log.e(TAG, "Failed to unregister service", e);
            }
        }
        
        // Stop discovery
        if (nsdManager != null && discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener);
            } catch (Exception e) {
                Log.e(TAG, "Failed to stop discovery", e);
            }
        }
        
        // Close the server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close server socket", e);
            }
        }
        
        // Shutdown the executor service
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    private void registerService() {
        nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
        
        // Create the service info
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(SERVICE_NAME);
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(servicePort);
        
        // Create the registration listener
        registrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Service registration failed: " + errorCode);
                updateStatus("Service registration failed");
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Service unregistration failed: " + errorCode);
            }

            @Override
            public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service registered: " + serviceInfo.getServiceName());
                updateStatus("Service registered: " + serviceInfo.getServiceName());
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service unregistered: " + serviceInfo.getServiceName());
            }
        };
        
        // Register the service
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
    }
    
    private void startDiscovery() {
        if (nsdManager == null) {
            nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
        }
        
        // If we're already discovering, stop the current discovery first
        if (discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener);
            } catch (Exception e) {
                Log.e(TAG, "Failed to stop existing discovery", e);
            }
        }
        
        updateStatus("Scanning for devices...");
        
        // Create the discovery listener
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery start failed: " + errorCode);
                updateStatus("Discovery start failed");
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery stop failed: " + errorCode);
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.d(TAG, "Discovery started");
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "Discovery stopped");
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service found: " + serviceInfo.getServiceName());
                
                if (serviceInfo.getServiceType().equals(SERVICE_TYPE) && 
                    !serviceInfo.getServiceName().equals(SERVICE_NAME)) {
                    
                    // Resolve the service to get the host and port
                    nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                        @Override
                        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                            Log.e(TAG, "Resolve failed: " + errorCode);
                        }

                        @Override
                        public void onServiceResolved(NsdServiceInfo serviceInfo) {
                            Log.d(TAG, "Service resolved: " + serviceInfo.getServiceName() + 
                                  " host: " + serviceInfo.getHost() + " port: " + serviceInfo.getPort());
                            
                            updateStatus("Found device: " + serviceInfo.getServiceName());
                            
                            // Connect to the service and send clipboard data
                            String host = serviceInfo.getHost().getHostAddress();
                            int port = serviceInfo.getPort();
                            
                            executorService.submit(() -> sendClipboardTo(host, port));
                        }
                    });
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service lost: " + serviceInfo.getServiceName());
            }
        };
        
        // Start the discovery
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }
    
    private void acceptConnections() {
        while (isRunning) {
            try {
                Socket socket = serverSocket.accept();
                executorService.submit(() -> handleIncomingConnection(socket));
            } catch (IOException e) {
                if (isRunning) {
                    Log.e(TAG, "Error accepting connection", e);
                }
            }
        }
    }
    
    private void handleIncomingConnection(Socket socket) {
        try {
            DataInputStream inputStream = new DataInputStream(socket.getInputStream());
            
            // Read the content type (text or image)
            int contentType = inputStream.readInt();
            
            if (contentType == 0) { // Text
                // Read the text length
                int textLength = inputStream.readInt();
                
                // Read the text
                byte[] textBytes = new byte[textLength];
                inputStream.readFully(textBytes);
                String text = new String(textBytes);
                
                // Set the text to clipboard
                runOnUiThread(() -> {
                    setTextToClipboard(text);
                    updateStatus("Received text from " + socket.getInetAddress().getHostAddress());
                });
            } else if (contentType == 1) { // Image
                // Read the image size
                int imageSize = inputStream.readInt();
                
                // Read the image data
                byte[] imageData = new byte[imageSize];
                inputStream.readFully(imageData);
                
                // Convert to bitmap and set to clipboard
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                if (bitmap != null) {
                    runOnUiThread(() -> {
                        setImageToClipboard(bitmap);
                        previewImageView.setImageBitmap(bitmap);
                        previewImageView.setVisibility(View.VISIBLE);
                        updateStatus("Received image from " + socket.getInetAddress().getHostAddress());
                    });
                }
            }
            
            socket.close();
        } catch (IOException e) {
            Log.e(TAG, "Error handling connection", e);
        }
    }
    
    private void sendClipboardTo(String host, int port) {
        try {
            Socket socket = new Socket(host, port);
            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
            
            // Check if we have a primary clip
            if (clipboardManager.hasPrimaryClip()) {
                ClipData clipData = clipboardManager.getPrimaryClip();
                if (clipData != null && clipData.getItemCount() > 0) {
                    ClipData.Item item = clipData.getItemAt(0);
                    
                    if (item.getText() != null) {
                        // Send text
                        String text = item.getText().toString();
                        byte[] textBytes = text.getBytes();
                        
                        outputStream.writeInt(0); // 0 = text
                        outputStream.writeInt(textBytes.length);
                        outputStream.write(textBytes);
                        
                        runOnUiThread(() -> updateStatus("Text sent to " + host));
                    } else if (item.getUri() != null) {
                        try {
                            // Send image
                            InputStream inputStream = getContentResolver().openInputStream(item.getUri());
                            if (inputStream != null) {
                                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                                if (bitmap != null) {
                                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                                    byte[] imageData = baos.toByteArray();
                                    
                                    outputStream.writeInt(1); // 1 = image
                                    outputStream.writeInt(imageData.length);
                                    outputStream.write(imageData);
                                    
                                    runOnUiThread(() -> updateStatus("Image sent to " + host));
                                }
                                inputStream.close();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error sending image", e);
                            runOnUiThread(() -> updateStatus("Error sending image"));
                        }
                    }
                }
            } else {
                runOnUiThread(() -> updateStatus("Nothing in clipboard to send"));
            }
            
            socket.close();
        } catch (IOException e) {
            Log.e(TAG, "Error sending clipboard data", e);
            runOnUiThread(() -> updateStatus("Error sending to " + host));
        }
    }
    
    private void shareCurrentClipboardContent() {
        startDiscovery();
    }
    
    private void setTextToClipboard(String text) {
        ClipData clipData = ClipData.newPlainText("Shared Text", text);
        clipboardManager.setPrimaryClip(clipData);
        Toast.makeText(this, "Text received and copied to clipboard", Toast.LENGTH_SHORT).show();
    }
    
    private void setImageToClipboard(Bitmap bitmap) {
        ClipData clipData = ClipData.newUri(getContentResolver(), "Shared Image", 
                ViewsUtils.getUriFromBitmap(this, bitmap));
        clipboardManager.setPrimaryClip(clipData);
        Toast.makeText(this, "Image received and copied to clipboard", Toast.LENGTH_SHORT).show();
    }
    
    private void updateStatus(String status) {
        if (statusTextView != null) {
            statusTextView.setText(status);
        }
    }
    
    private void runOnUiThread(Runnable runnable) {
        if (floatingView != null) {
            floatingView.post(runnable);
        }
    }
    
    // Helper method to get the device's IP address
    public static String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') < 0) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting IP address", e);
        }
        return "127.0.0.1";
    }
}
