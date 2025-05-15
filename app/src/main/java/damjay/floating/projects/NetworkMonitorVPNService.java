package damjay.floating.projects;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import damjay.floating.projects.models.HttpPacket;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;

public class NetworkMonitorVPNService extends VpnService {
    private static final String TAG = "NetworkMonitorVPN";
    private static final String NOTIFICATION_CHANNEL_ID = "network_monitor_channel";
    private static final int NOTIFICATION_ID = 1;
    
    private Thread vpnThread;
    private ParcelFileDescriptor vpnInterface;
    private ExecutorService executorService;
    private ConcurrentLinkedQueue<HttpPacket> packetQueue;
    private boolean isRunning = false;
    
    private static final Pattern HTTP_REQUEST_PATTERN = Pattern.compile(
        "^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS) ([^ ]+) HTTP/[0-9.]+\\r\\n" +
        "((?:[A-Za-z-]+: [^\\r\\n]+\\r\\n)*)" +
        "\\r\\n" +
        "(.*)",
        Pattern.DOTALL
    );
    
    private static final Pattern HTTPS_SNI_PATTERN = Pattern.compile(
        "\\x00\\x00([\\x00-\\xFF]{2})\\x01\\x00.{2}\\x00([\\x00-\\xFF]+?)\\x00"
    );

    @Override
    public void onCreate() {
        super.onCreate();
        packetQueue = new ConcurrentLinkedQueue<>();
        executorService = Executors.newFixedThreadPool(3);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isRunning) {
            return START_STICKY;
        }
        
        isRunning = true;
        vpnThread = new Thread(() -> {
            try {
                vpnInterface = establishVPN();
                
                // Start packet processing threads
                executorService.submit(this::processVpnInput);
                executorService.submit(this::processPacketQueue);
                
            } catch (Exception e) {
                Log.e(TAG, "Error in VPN thread", e);
                isRunning = false;
            }
        });
        vpnThread.start();
        return START_STICKY;
    }

    private ParcelFileDescriptor establishVPN() {
        Builder builder = new Builder()
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .setSession("Network Monitor")
            .setMtu(1500);
            
        // Allow all apps to use this VPN
        try {
            builder.addDisallowedApplication(getPackageName());
        } catch (Exception e) {
            Log.e(TAG, "Error excluding self from VPN", e);
        }
        
        return builder.establish();
    }

    private void processVpnInput() {
        FileInputStream fis = new FileInputStream(vpnInterface.getFileDescriptor());
        ByteBuffer packet = ByteBuffer.allocate(32767);
        byte[] data = new byte[32767];
        
        try {
            while (isRunning) {
                int length = fis.read(data);
                if (length > 0) {
                    // Process IP packet
                    packet.clear();
                    packet.put(data, 0, length);
                    packet.flip();
                    
                    // Extract protocol, source and destination from IP header
                    if (packet.remaining() >= 20) {
                        byte version = (byte) (packet.get(0) >> 4);
                        if (version == 4) {
                            // IPv4
                            byte protocol = packet.get(9);
                            int headerLength = (packet.get(0) & 0x0F) * 4;
                            
                            if (protocol == 6) {  // TCP
                                processTcpPacket(packet, headerLength);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading from VPN interface", e);
        }
    }
    
    private void processTcpPacket(ByteBuffer packet, int ipHeaderLength) {
        if (packet.remaining() < ipHeaderLength + 20) {
            return; // Not enough data for TCP header
        }
        
        int tcpHeaderLength = ((packet.get(ipHeaderLength + 12) & 0xF0) >> 4) * 4;
        int totalHeaderLength = ipHeaderLength + tcpHeaderLength;
        
        if (packet.remaining() < totalHeaderLength) {
            return; // Not enough data
        }
        
        // Extract source and destination ports
        int sourcePort = ((packet.get(ipHeaderLength) & 0xFF) << 8) | (packet.get(ipHeaderLength + 1) & 0xFF);
        int destPort = ((packet.get(ipHeaderLength + 2) & 0xFF) << 8) | (packet.get(ipHeaderLength + 3) & 0xFF);
        
        // Check if this is HTTP or HTTPS traffic
        if (destPort == 80 || destPort == 443 || sourcePort == 80 || sourcePort == 443) {
            // Extract payload
            if (packet.remaining() > totalHeaderLength) {
                byte[] payload = new byte[packet.remaining() - totalHeaderLength];
                packet.position(totalHeaderLength);
                packet.get(payload);
                
                if (destPort == 80) {
                    // HTTP traffic
                    processHttpPayload(payload);
                } else if (destPort == 443) {
                    // HTTPS traffic (TLS)
                    processTlsPayload(payload);
                }
            }
        }
    }
    
    private void processHttpPayload(byte[] payload) {
        try {
            String data = new String(payload, "UTF-8");
            HttpPacket packet = parseHttpRequest(data);
            if (packet != null) {
                packetQueue.add(packet);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing HTTP payload", e);
        }
    }
    
    private void processTlsPayload(byte[] payload) {
        try {
            // Try to extract SNI (Server Name Indication) from TLS ClientHello
            String hexPayload = bytesToHex(payload);
            if (hexPayload.contains("0100")) {
                // Potential ClientHello
                String sni = extractSni(payload);
                if (sni != null && !sni.isEmpty()) {
                    HttpPacket packet = new HttpPacket();
                    packet.setMethod("HTTPS");
                    packet.setUrl("https://" + sni);
                    packet.addHeader("Host", sni);
                    packetQueue.add(packet);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing TLS payload", e);
        }
    }
    
    private String extractSni(byte[] data) {
        try {
            for (int i = 0; i < data.length - 5; i++) {
                // Look for Server Name extension type (0x0000)
                if (data[i] == 0x00 && data[i+1] == 0x00 && 
                    data[i+2] == 0x00 && data[i+3] == 0x00) {
                    
                    // Skip to the SNI hostname
                    int offset = i + 7;
                    if (offset < data.length) {
                        int length = (data[offset] & 0xFF);
                        offset++;
                        
                        if (offset + length <= data.length) {
                            byte[] hostname = new byte[length];
                            System.arraycopy(data, offset, hostname, 0, length);
                            return new String(hostname, "UTF-8");
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting SNI", e);
        }
        return null;
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private void processPacketQueue() {
        while (isRunning) {
            HttpPacket packet = packetQueue.poll();
            if (packet != null) {
                broadcastPacket(packet);
            } else {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private HttpPacket parseHttpRequest(String requestData) {
        Matcher matcher = HTTP_REQUEST_PATTERN.matcher(requestData);
        if (!matcher.find()) {
            return null;
        }

        HttpPacket packet = new HttpPacket();
        packet.setMethod(matcher.group(1));
        packet.setUrl(matcher.group(2));

        // Parse headers
        String headers = matcher.group(3);
        for (String header : headers.split("\\r\\n")) {
            if (header.isEmpty()) continue;
            String[] parts = header.split(": ", 2);
            if (parts.length == 2) {
                packet.addHeader(parts[0], parts[1]);
            }
        }

        // Parse body
        String body = matcher.group(4);
        if (body != null && !body.isEmpty()) {
            packet.setBody(body);
        }

        return packet;
    }

    private void broadcastPacket(HttpPacket packet) {
        Intent intent = new Intent("PacketData");
        intent.putExtra("method", packet.getMethod());
        intent.putExtra("url", packet.getUrl());
        intent.putExtra("curl", packet.toCurl());
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Network Monitor",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Network monitoring service");
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, NetworkMonitorActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, 
            PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Network Monitor")
            .setContentText("Monitoring network traffic")
            .setSmallIcon(R.drawable.network_monitor_logo)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW);
            
        return builder.build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        
        if (executorService != null) {
            executorService.shutdownNow();
        }
        
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing VPN interface", e);
            }
        }
        
        if (vpnThread != null) {
            vpnThread.interrupt();
        }
    }
}