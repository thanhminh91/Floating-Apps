package damjay.floating.projects;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Random;

import damjay.floating.projects.utils.TouchState;

public class NumberRangeService extends Service {
    private WindowManager windowManager;
    private View floatingView;
    private View collapsedView;
    private View expandedView;
    private EditText minNumber;
    private EditText maxNumber;
    private TextView resultDisplay;

    private final TouchState touchState = TouchState.getInstance();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingView = LayoutInflater.from(this).inflate(R.layout.service_number_range, null);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        collapsedView = floatingView.findViewById(R.id.collapsed_view);
        expandedView = floatingView.findViewById(R.id.expanded_view);
        minNumber = floatingView.findViewById(R.id.min_number);
        maxNumber = floatingView.findViewById(R.id.max_number);
        Button generateButton = floatingView.findViewById(R.id.generate_button);
        resultDisplay = floatingView.findViewById(R.id.result_display);

        // Set up touch listener for moving the window
        expandedView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchState.setInitialPosition(event.getRawX(), event.getRawY());
                    touchState.setOriginalPosition(params.x, params.y);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    touchState.setFinalPosition(event.getRawX(), event.getRawY());
                    params.x = touchState.updatedPositionX();
                    params.y = touchState.updatedPositionY();
                    windowManager.updateViewLayout(floatingView, params);
                    return true;
            }
            return false;
        });

        generateButton.setOnClickListener(v -> {
            try {
                int min = Integer.parseInt(minNumber.getText().toString());
                int max = Integer.parseInt(maxNumber.getText().toString());
                if (min <= max) {
                    Random random = new Random();
                    int result = random.nextInt(max - min + 1) + min;
                    resultDisplay.setText(String.valueOf(result));
                } else {
                    resultDisplay.setText("Invalid range");
                }
            } catch (NumberFormatException e) {
                resultDisplay.setText("Invalid input");
            }
        });

        floatingView.findViewById(R.id.close_button).setOnClickListener(v -> stopSelf());

        collapsedView.setOnClickListener(v -> {
            collapsedView.setVisibility(View.GONE);
            expandedView.setVisibility(View.VISIBLE);
        });

        floatingView.findViewById(R.id.minimize_button).setOnClickListener(v -> {
            expandedView.setVisibility(View.GONE);
            collapsedView.setVisibility(View.VISIBLE);
        });

        windowManager.addView(floatingView, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) {
            windowManager.removeView(floatingView);
        }
    }
} 