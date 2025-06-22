package damjay.floating.projects;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.widget.Switch;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class NetworkMonitorActivity extends AppCompatActivity {
    private Switch networkSwitch;
    private RecyclerView networkList;
    private PacketAdapter packetAdapter;
    private List<String> packetDataList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.network_monitor_layout);

        networkSwitch = findViewById(R.id.networkSwitch);
        networkList = findViewById(R.id.networkList);

        // Set up RecyclerView
        networkList.setLayoutManager(new LinearLayoutManager(this));
        packetAdapter = new PacketAdapter(this, packetDataList);
        networkList.setAdapter(packetAdapter);

        networkSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startNetworkMonitoring();
            } else {
                stopNetworkMonitoring();
            }
        });
        
        // Set up test button
        findViewById(R.id.testButton).setOnClickListener(v -> {
            if (networkSwitch.isChecked()) {
                // Send a test intent to the VPN service
                Intent testIntent = new Intent(this, NetworkMonitorVPNService.class);
                testIntent.setAction("TEST");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(this, testIntent);
                } else {
                    startService(testIntent);
                }
                
                // Also add a test entry directly to the UI
                updatePacketData("MANUAL TEST https://manual.test.com\ncurl -X GET 'https://manual.test.com'");
            } else {
                // Inform user to enable monitoring first
                android.widget.Toast.makeText(this, 
                    "Please enable network monitoring first", 
                    android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Đăng ký nhận thông báo gói tin
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(packetReceiver, new IntentFilter("PacketData"));
            
        // Đăng ký nhận thông báo trạng thái VPN
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(vpnStatusReceiver, new IntentFilter("VPNStatus"));
            
        // Kiểm tra trạng thái hiện tại của VPN
        if (isVpnServiceRunning()) {
            networkSwitch.setChecked(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(packetReceiver);
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(vpnStatusReceiver);
    }
    
    // Kiểm tra xem dịch vụ VPN có đang chạy không
    private boolean isVpnServiceRunning() {
        android.app.ActivityManager manager = 
            (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (android.app.ActivityManager.RunningServiceInfo service : 
                manager.getRunningServices(Integer.MAX_VALUE)) {
            if (NetworkMonitorVPNService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private BroadcastReceiver packetReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String method = intent.getStringExtra("method");
            String url = intent.getStringExtra("url");
            String curl = intent.getStringExtra("curl");
            
            // Thêm vào danh sách và cập nhật UI
            String displayText = String.format("%s %s\n%s", method, url, curl);
            runOnUiThread(() -> updatePacketData(displayText));
        }
    };
    
    private BroadcastReceiver vpnStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra("status");
            
            if (status != null) {
                switch (status) {
                    case "starting":
                        android.widget.Toast.makeText(NetworkMonitorActivity.this, 
                            "Đang khởi động VPN...", 
                            android.widget.Toast.LENGTH_SHORT).show();
                        break;
                    case "ready":
                        android.widget.Toast.makeText(NetworkMonitorActivity.this, 
                            "VPN đã sẵn sàng và đang theo dõi lưu lượng mạng", 
                            android.widget.Toast.LENGTH_SHORT).show();
                        networkSwitch.setChecked(true);
                        break;
                    case "error":
                        String message = intent.getStringExtra("message");
                        android.widget.Toast.makeText(NetworkMonitorActivity.this, 
                            "Lỗi VPN: " + message, 
                            android.widget.Toast.LENGTH_LONG).show();
                        networkSwitch.setChecked(false);
                        break;
                    case "stopped":
                        android.widget.Toast.makeText(NetworkMonitorActivity.this, 
                            "VPN đã dừng", 
                            android.widget.Toast.LENGTH_SHORT).show();
                        networkSwitch.setChecked(false);
                        break;
                }
            }
        }
    };

    private void startNetworkMonitoring() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, 0);
        } else {
            onActivityResult(0, RESULT_OK, null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            Intent serviceIntent = new Intent(this, NetworkMonitorVPNService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, serviceIntent);
            } else {
                startService(serviceIntent);
            }
            
            // Add a test packet to verify UI is working
            String testPacket = "TEST GET https://example.com/test\ncurl -X GET 'https://example.com/test'";
            updatePacketData(testPacket);
        } else {
            // If user denied VPN permission, uncheck the switch
            networkSwitch.setChecked(false);
        }
    }

    private void stopNetworkMonitoring() {
        stopService(new Intent(this, NetworkMonitorVPNService.class));
    }

    // Add method to update packet data
    public void updatePacketData(String packetData) {
        packetDataList.add(packetData);
        packetAdapter.notifyDataSetChanged();
    }
} 