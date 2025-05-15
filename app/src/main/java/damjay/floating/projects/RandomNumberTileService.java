package damjay.floating.projects;

import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.N)
public class RandomNumberTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        
        // Update the tile's appearance
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.updateTile();
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        
        // Update the tile's state
        Tile tile = getQsTile();
        if (tile != null) {
            // Briefly show active state
            tile.setState(Tile.STATE_ACTIVE);
            tile.updateTile();
            
            // Launch the floating random number generator
            Intent intent = new Intent(this, NumberRangeService.class);
            startService(intent);
            
            // Return to inactive state after a short delay
            new android.os.Handler().postDelayed(() -> {
                tile.setState(Tile.STATE_INACTIVE);
                tile.updateTile();
            }, 500);
        }
    }
}