package damjay.floating.projects;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.Switch;
import androidx.appcompat.app.AppCompatActivity;
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(packetReceiver, new IntentFilter("PacketData"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(packetReceiver);
    }

    private BroadcastReceiver packetReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String method = intent.getStringExtra("method");
            String url = intent.getStringExtra("url");
            String curl = intent.getStringExtra("curl");
            
            // Add to list and update UI
            String displayText = String.format("%s %s\n%s", method, url, curl);
            runOnUiThread(() -> updatePacketData(displayText));
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
        if (resultCode == RESULT_OK) {
            Intent serviceIntent = new Intent(this, NetworkMonitorVPNService.class);
            startService(serviceIntent);
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