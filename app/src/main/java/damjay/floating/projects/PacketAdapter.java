package damjay.floating.projects;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class PacketAdapter extends RecyclerView.Adapter<PacketAdapter.PacketViewHolder> {
    private List<String> packetList;
    private Context context;

    public PacketAdapter(Context context, List<String> packetList) {
        this.context = context;
        this.packetList = packetList;
    }

    @NonNull
    @Override
    public PacketViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.packet_item, parent, false);
        return new PacketViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PacketViewHolder holder, int position) {
        String packet = packetList.get(position);
        holder.packetTextView.setText(packet);

        holder.copyCurlButton.setOnClickListener(v -> {
            String curlCommand = convertToCurl(packet);
            copyToClipboard(curlCommand);
            Toast.makeText(context, "CURL command copied to clipboard", Toast.LENGTH_SHORT).show();
        });
    }

    private String convertToCurl(String packetData) {
        // The packet data already contains the curl command in the last line
        // Format is: METHOD URL\ncurl command
        String[] parts = packetData.split("\\n", 2);
        if (parts.length > 1) {
            return parts[1]; // Return the curl part
        }
        
        // Fallback if format is unexpected
        return packetData;
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("CURL Command", text);
        clipboard.setPrimaryClip(clip);
    }

    @Override
    public int getItemCount() {
        return packetList.size();
    }

    static class PacketViewHolder extends RecyclerView.ViewHolder {
        TextView packetTextView;
        Button copyCurlButton;

        PacketViewHolder(View itemView) {
            super(itemView);
            packetTextView = itemView.findViewById(R.id.packetTextView);
            copyCurlButton = itemView.findViewById(R.id.copyCurlButton);
        }
    }
} 