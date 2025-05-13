package damjay.floating.projects;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import damjay.floating.projects.models.HttpPacket;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NetworkMonitorVPNService extends VpnService {
    private static final String TAG = "NetworkMonitorVPN";
    private Thread vpnThread;
    private ParcelFileDescriptor vpnInterface;
    private static final Pattern HTTP_REQUEST_PATTERN = Pattern.compile(
        "^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS) ([^ ]+) HTTP/[0-9.]+\r\n" +
        "((?:[A-Za-z-]+: [^\r\n]+\r\n)*)" +
        "\r\n" +
        "(.*)",
        Pattern.DOTALL
    );

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        vpnThread = new Thread(() -> {
            try {
                vpnInterface = establishVPN();
                FileChannel inputChannel = new FileInputStream(vpnInterface.getFileDescriptor()).getChannel();
                FileChannel outputChannel = new FileOutputStream(vpnInterface.getFileDescriptor()).getChannel();
                handlePackets(inputChannel, outputChannel);
            } catch (Exception e) {
                Log.e(TAG, "Error in VPN thread", e);
            }
        });
        vpnThread.start();
        return START_STICKY;
    }

    private ParcelFileDescriptor establishVPN() {
        return new Builder()
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .establish();
    }

    private void handlePackets(FileChannel inputChannel, FileChannel outputChannel) {
        ByteBuffer buffer = ByteBuffer.allocate(32767);
        StringBuilder packetBuilder = new StringBuilder();
        
        while (!Thread.interrupted()) {
            try {
                int length = inputChannel.read(buffer);
                if (length > 0) {
                    buffer.flip();
                    byte[] packet = new byte[length];
                    buffer.get(packet);
                    
                    // Convert bytes to string and append to builder
                    String packetData = new String(packet);
                    packetBuilder.append(packetData);
                    
                    // Try to parse complete HTTP requests
                    String accumulated = packetBuilder.toString();
                    int endIndex;
                    while ((endIndex = accumulated.indexOf("\r\n\r\n")) != -1) {
                        String request = accumulated.substring(0, endIndex + 4);
                        accumulated = accumulated.substring(endIndex + 4);
                        
                        HttpPacket httpPacket = parseHttpRequest(request);
                        if (httpPacket != null) {
                            // Broadcast packet to activity
                            broadcastPacket(httpPacket);
                        }
                    }
                    packetBuilder = new StringBuilder(accumulated);
                    
                    buffer.clear();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading packet", e);
                break;
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
        for (String header : headers.split("\r\n")) {
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (vpnThread != null) {
            vpnThread.interrupt();
        }
    }
} 