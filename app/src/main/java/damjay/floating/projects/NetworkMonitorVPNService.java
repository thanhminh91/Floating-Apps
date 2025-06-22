package damjay.floating.projects;

import android.content.Intent;
import android.content.pm.ServiceInfo;
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
import android.system.OsConstants;

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
        
        // Start as foreground service with proper notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, createNotification());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isRunning) {
            Log.d(TAG, "Dịch vụ VPN đã đang chạy");
            
            // Nếu nhận được intent kiểm tra, gửi một gói tin thử nghiệm
            if (intent != null && "TEST".equals(intent.getAction())) {
                HttpPacket testPacket = new HttpPacket();
                testPacket.setMethod("TEST");
                testPacket.setUrl("https://manual.test/connection");
                testPacket.addHeader("Content-Type", "application/json");
                testPacket.addHeader("Test-Time", String.valueOf(System.currentTimeMillis()));
                packetQueue.add(testPacket);
                Log.d(TAG, "Đã thêm gói tin thử nghiệm vào hàng đợi");
                
                // Gửi broadcast để thông báo rằng dịch vụ đang hoạt động
                Intent statusIntent = new Intent("VPNStatus");
                statusIntent.putExtra("status", "running");
                LocalBroadcastManager.getInstance(this).sendBroadcast(statusIntent);
            }
            
            return START_STICKY;
        }
        
        Log.d(TAG, "Đang khởi động dịch vụ VPN");
        
        // Gửi broadcast để thông báo rằng dịch vụ đang khởi động
        Intent statusIntent = new Intent("VPNStatus");
        statusIntent.putExtra("status", "starting");
        LocalBroadcastManager.getInstance(this).sendBroadcast(statusIntent);
        
        isRunning = true;
        vpnThread = new Thread(() -> {
            try {
                Log.d(TAG, "Luồng VPN đã bắt đầu");
                vpnInterface = establishVPN();
                
                if (vpnInterface == null) {
                    Log.e(TAG, "Không thể thiết lập VPN interface");
                    isRunning = false;
                    
                    // Gửi broadcast thông báo lỗi
                    Intent errorIntent = new Intent("VPNStatus");
                    errorIntent.putExtra("status", "error");
                    errorIntent.putExtra("message", "Không thể thiết lập VPN interface");
                    LocalBroadcastManager.getInstance(this).sendBroadcast(errorIntent);
                    
                    return;
                }
                
                // Khởi động các luồng xử lý gói tin
                executorService.submit(this::processVpnInput);
                executorService.submit(this::processPacketQueue);
                
                // Thêm một gói tin thử nghiệm để xác minh xử lý hàng đợi
                HttpPacket testPacket = new HttpPacket();
                testPacket.setMethod("INIT");
                testPacket.setUrl("https://init.test/connection");
                testPacket.addHeader("Content-Type", "application/json");
                testPacket.addHeader("Init-Time", String.valueOf(System.currentTimeMillis()));
                packetQueue.add(testPacket);
                
                // Gửi broadcast thông báo VPN đã sẵn sàng
                Intent readyIntent = new Intent("VPNStatus");
                readyIntent.putExtra("status", "ready");
                LocalBroadcastManager.getInstance(this).sendBroadcast(readyIntent);
                
                Log.d(TAG, "VPN đã sẵn sàng và đang theo dõi lưu lượng mạng");
                
            } catch (Exception e) {
                Log.e(TAG, "Lỗi trong luồng VPN", e);
                isRunning = false;
                
                // Gửi broadcast thông báo lỗi
                Intent errorIntent = new Intent("VPNStatus");
                errorIntent.putExtra("status", "error");
                errorIntent.putExtra("message", "Lỗi: " + e.getMessage());
                LocalBroadcastManager.getInstance(this).sendBroadcast(errorIntent);
            }
        });
        
        vpnThread.start();
        return START_STICKY;
    }

    private ParcelFileDescriptor establishVPN() {
        Builder builder = new Builder()
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)  // Định tuyến tất cả lưu lượng qua VPN
            .addDnsServer("8.8.8.8")
            .addDnsServer("8.8.4.4")
            .setSession("Network Monitor")
            .setMtu(1500)
            .allowFamily(android.system.OsConstants.AF_INET)  // Cho phép IPv4
            .allowFamily(android.system.OsConstants.AF_INET6); // Cho phép IPv6
            
        // Loại trừ ứng dụng của chúng ta khỏi VPN để tránh vòng lặp
        try {
            builder.addDisallowedApplication(getPackageName());
            
            // Ghi log quá trình thiết lập VPN
            Log.d(TAG, "Đang thiết lập kết nối VPN");
            
            // Gửi gói tin thử nghiệm để xác minh broadcast đang hoạt động
            HttpPacket testPacket = new HttpPacket();
            testPacket.setMethod("TEST");
            testPacket.setUrl("https://vpn.test/connection");
            testPacket.addHeader("Content-Type", "application/json");
            packetQueue.add(testPacket);
            
            // Thiết lập VPN
            ParcelFileDescriptor vpnInterface = builder.establish();
            if (vpnInterface == null) {
                Log.e(TAG, "Không thể thiết lập VPN interface");
                return null;
            }
            
            Log.d(TAG, "VPN interface đã được thiết lập thành công");
            return vpnInterface;
            
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khi cấu hình VPN", e);
            return null;
        }
    }

    private void processVpnInput() {
        if (vpnInterface == null) {
            Log.e(TAG, "VPN interface không tồn tại, không thể xử lý gói tin");
            return;
        }
        
        FileInputStream fis = new FileInputStream(vpnInterface.getFileDescriptor());
        FileOutputStream fos = new FileOutputStream(vpnInterface.getFileDescriptor());
        ByteBuffer packet = ByteBuffer.allocate(32767);
        byte[] data = new byte[32767];
        
        try {
            while (isRunning) {
                // Đọc gói tin từ VPN interface
                int length = fis.read(data);
                if (length > 0) {
                    // Xử lý gói tin IP
                    packet.clear();
                    packet.put(data, 0, length);
                    packet.flip();
                    
                    // Trích xuất thông tin từ header IP
                    if (packet.remaining() >= 20) {
                        byte versionAndIHL = packet.get(0);
                        byte version = (byte) (versionAndIHL >> 4);
                        
                        if (version == 4) { // IPv4
                            int headerLength = (versionAndIHL & 0x0F) * 4;
                            byte protocol = packet.get(9);
                            
                            // Lấy địa chỉ IP nguồn và đích
                            byte[] sourceIP = new byte[4];
                            byte[] destIP = new byte[4];
                            packet.position(12);
                            packet.get(sourceIP);
                            packet.get(destIP);
                            
                            String sourceAddr = String.format("%d.%d.%d.%d", 
                                sourceIP[0] & 0xFF, sourceIP[1] & 0xFF, 
                                sourceIP[2] & 0xFF, sourceIP[3] & 0xFF);
                            String destAddr = String.format("%d.%d.%d.%d", 
                                destIP[0] & 0xFF, destIP[1] & 0xFF, 
                                destIP[2] & 0xFF, destIP[3] & 0xFF);
                            
                            // Reset vị trí để xử lý gói tin
                            packet.position(0);
                            
                            // Xử lý theo giao thức
                            if (protocol == 6) {  // TCP
                                processTcpPacket(packet, headerLength, sourceAddr, destAddr);
                            } else if (protocol == 17) {  // UDP
                                processUdpPacket(packet, headerLength, sourceAddr, destAddr);
                            }
                            
                            // Ghi lại gói tin để chuyển tiếp
                            packet.position(0);
                            try {
                                fos.write(data, 0, length);
                                fos.flush();
                            } catch (IOException e) {
                                Log.e(TAG, "Lỗi khi ghi dữ liệu vào VPN interface", e);
                            }
                        } else if (version == 6) {
                            // Xử lý IPv6 (có thể mở rộng sau)
                            Log.d(TAG, "Nhận được gói tin IPv6");
                        }
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Lỗi khi đọc từ VPN interface", e);
        }
    }
    
    private void processTcpPacket(ByteBuffer packet, int ipHeaderLength, String sourceAddr, String destAddr) {
        if (packet.remaining() < ipHeaderLength + 20) {
            return; // Không đủ dữ liệu cho TCP header
        }
        
        int tcpHeaderLength = ((packet.get(ipHeaderLength + 12) & 0xF0) >> 4) * 4;
        int totalHeaderLength = ipHeaderLength + tcpHeaderLength;
        
        if (packet.remaining() < totalHeaderLength) {
            return; // Không đủ dữ liệu
        }
        
        // Trích xuất cổng nguồn và đích
        int sourcePort = ((packet.get(ipHeaderLength) & 0xFF) << 8) | (packet.get(ipHeaderLength + 1) & 0xFF);
        int destPort = ((packet.get(ipHeaderLength + 2) & 0xFF) << 8) | (packet.get(ipHeaderLength + 3) & 0xFF);
        
        // Ghi log thông tin kết nối
        Log.d(TAG, String.format("TCP: %s:%d -> %s:%d", sourceAddr, sourcePort, destAddr, destPort));
        
        // Kiểm tra các cờ TCP để xác định loại gói tin (SYN, ACK, etc.)
        byte flags = packet.get(ipHeaderLength + 13);
        boolean isSyn = (flags & 0x02) != 0;
        boolean isAck = (flags & 0x10) != 0;
        
        // Tạo một HttpPacket cơ bản cho mọi kết nối TCP
        HttpPacket basicPacket = new HttpPacket();
        basicPacket.setMethod("TCP");
        basicPacket.setUrl(String.format("%s:%d -> %s:%d", sourceAddr, sourcePort, destAddr, destPort));
        
        if (isSyn && !isAck) {
            // Kết nối mới được thiết lập
            basicPacket.addHeader("Connection", "New");
            packetQueue.add(basicPacket);
        }
        
        // Kiểm tra xem đây có phải là lưu lượng HTTP hoặc HTTPS không
        if (destPort == 80 || destPort == 443 || sourcePort == 80 || sourcePort == 443) {
            // Trích xuất payload
            if (packet.remaining() > totalHeaderLength) {
                byte[] payload = new byte[packet.remaining() - totalHeaderLength];
                packet.position(totalHeaderLength);
                packet.get(payload);
                
                if (destPort == 80) {
                    // Lưu lượng HTTP
                    processHttpPayload(payload, sourceAddr, sourcePort, destAddr, destPort);
                } else if (destPort == 443) {
                    // Lưu lượng HTTPS (TLS)
                    processTlsPayload(payload, sourceAddr, sourcePort, destAddr, destPort);
                }
            }
        }
    }
    
    private void processUdpPacket(ByteBuffer packet, int ipHeaderLength, String sourceAddr, String destAddr) {
        if (packet.remaining() < ipHeaderLength + 8) {
            return; // Không đủ dữ liệu cho UDP header
        }
        
        // Trích xuất cổng nguồn và đích
        int sourcePort = ((packet.get(ipHeaderLength) & 0xFF) << 8) | (packet.get(ipHeaderLength + 1) & 0xFF);
        int destPort = ((packet.get(ipHeaderLength + 2) & 0xFF) << 8) | (packet.get(ipHeaderLength + 3) & 0xFF);
        
        // Ghi log thông tin kết nối UDP
        Log.d(TAG, String.format("UDP: %s:%d -> %s:%d", sourceAddr, sourcePort, destAddr, destPort));
        
        // Kiểm tra xem có phải là DNS không (cổng 53)
        if (destPort == 53) {
            // Tạo một gói tin DNS cơ bản
            HttpPacket dnsPacket = new HttpPacket();
            dnsPacket.setMethod("DNS");
            dnsPacket.setUrl(String.format("DNS request to %s:%d", destAddr, destPort));
            packetQueue.add(dnsPacket);
        }
        
        // Tạo một HttpPacket cơ bản cho mọi kết nối UDP
        HttpPacket basicPacket = new HttpPacket();
        basicPacket.setMethod("UDP");
        basicPacket.setUrl(String.format("%s:%d -> %s:%d", sourceAddr, sourcePort, destAddr, destPort));
        packetQueue.add(basicPacket);
    }
    
    private void processHttpPayload(byte[] payload, String sourceAddr, int sourcePort, String destAddr, int destPort) {
        try {
            String data = new String(payload, "UTF-8");
            HttpPacket packet = parseHttpRequest(data);
            if (packet != null) {
                // Thêm thông tin IP và cổng
                packet.addHeader("Source", sourceAddr + ":" + sourcePort);
                packet.addHeader("Destination", destAddr + ":" + destPort);
                packetQueue.add(packet);
                
                // Ghi log thông tin HTTP
                Log.d(TAG, "HTTP: " + packet.getMethod() + " " + packet.getUrl());
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khi xử lý payload HTTP", e);
        }
    }
    
    private void processTlsPayload(byte[] payload, String sourceAddr, int sourcePort, String destAddr, int destPort) {
        try {
            // Cố gắng trích xuất SNI (Server Name Indication) từ TLS ClientHello
            String hexPayload = bytesToHex(payload);
            if (hexPayload.contains("0100")) {
                // Có thể là ClientHello
                String sni = extractSni(payload);
                if (sni != null && !sni.isEmpty()) {
                    HttpPacket packet = new HttpPacket();
                    packet.setMethod("HTTPS");
                    packet.setUrl("https://" + sni);
                    packet.addHeader("Host", sni);
                    packet.addHeader("Source", sourceAddr + ":" + sourcePort);
                    packet.addHeader("Destination", destAddr + ":" + destPort);
                    packetQueue.add(packet);
                    
                    // Ghi log thông tin HTTPS
                    Log.d(TAG, "HTTPS: Connection to " + sni);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khi xử lý payload TLS", e);
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
        
        // Log the packet for debugging
        Log.d(TAG, "Broadcasting packet: " + packet.getMethod() + " " + packet.getUrl());
        Log.d(TAG, "CURL: " + packet.toCurl());
        
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
        Log.d(TAG, "Đang dừng dịch vụ VPN");
        isRunning = false;
        
        // Gửi broadcast thông báo VPN đã dừng
        Intent stoppedIntent = new Intent("VPNStatus");
        stoppedIntent.putExtra("status", "stopped");
        LocalBroadcastManager.getInstance(this).sendBroadcast(stoppedIntent);
        
        if (executorService != null) {
            executorService.shutdownNow();
        }
        
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
                Log.d(TAG, "Đã đóng VPN interface");
            } catch (IOException e) {
                Log.e(TAG, "Lỗi khi đóng VPN interface", e);
            }
        }
        
        if (vpnThread != null) {
            vpnThread.interrupt();
            Log.d(TAG, "Đã dừng luồng VPN");
        }
        
        Log.d(TAG, "Dịch vụ VPN đã dừng hoàn toàn");
    }
}