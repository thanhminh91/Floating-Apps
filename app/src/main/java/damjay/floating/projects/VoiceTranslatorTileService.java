package damjay.floating.projects;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import damjay.floating.projects.voicetranslator.VoiceTranslatorService;

public class VoiceTranslatorTileService extends TileService {

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        updateTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        
        // Start VoiceTranslatorService
        Intent serviceIntent = new Intent(this, VoiceTranslatorService.class);
        startService(serviceIntent);
        
        // Update tile state
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setIcon(Icon.createWithResource(this, R.drawable.voice_translator_logo));
            tile.setLabel("Video Voice Translator");
            tile.setContentDescription("Start Video Voice Translator");
            tile.setState(Tile.STATE_INACTIVE);
            tile.updateTile();
        }
    }
}