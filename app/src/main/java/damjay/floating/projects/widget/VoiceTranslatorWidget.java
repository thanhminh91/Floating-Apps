package damjay.floating.projects.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import damjay.floating.projects.R;
import damjay.floating.projects.voicetranslator.VoiceTranslatorService;

public class VoiceTranslatorWidget extends AppWidgetProvider {
    private static final String ACTION_START_VOICE_TRANSLATOR = "START_VOICE_TRANSLATOR";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        // Create intent to start VoiceTranslatorService
        Intent serviceIntent = new Intent(context, VoiceTranslatorService.class);
        PendingIntent pendingIntent = PendingIntent.getService(
                context, 
                0, 
                serviceIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.voice_translator_widget);
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent);

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        
        if (ACTION_START_VOICE_TRANSLATOR.equals(intent.getAction())) {
            // Start VoiceTranslatorService
            Intent serviceIntent = new Intent(context, VoiceTranslatorService.class);
            context.startService(serviceIntent);
        }
    }
}