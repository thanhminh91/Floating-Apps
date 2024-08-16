package damjay.floating.projects.timer;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import damjay.floating.projects.MainActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.utils.ViewsUtils;

public class TimerService extends Service {
    private View view;

    private View collapsedTimer;
    private View expandedTimer;

    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressLint("InflateParams")
    @Override
    public void onCreate() {
        super.onCreate();

        view = LayoutInflater.from(this).inflate(R.layout.service_timer, null);

        initializeViews();
        setOnClickListeners();
        ViewsUtils.addTouchListener(view, ViewsUtils.getViewTouchListener(this, view, windowManager, layoutParams), true, true, (Class<?>[]) null);
    }

    private void initializeViews() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        collapsedTimer = view.findViewById(R.id.collapsedTimer);
        expandedTimer = view.findViewById(R.id.expandedTimer);

        windowManager.addView(view, layoutParams = ViewsUtils.getFloatingLayoutParams());

        hideViews();
    }

    private void hideInternalViews() {
        view.findViewById(R.id.select_timer_mode).setVisibility(View.GONE);
        view.findViewById(R.id.new_timer_expanded).setVisibility(View.GONE);
        view.findViewById(R.id.repeating_timer_expanded).setVisibility(View.GONE);
        view.findViewById(R.id.counting_timer_view).setVisibility(View.GONE);
    }

    private void hideViews() {
        hideInternalViews();
        view.findViewById(R.id.select_timer_mode).setVisibility(View.VISIBLE);
    }

    private void setOnClickListeners() {
        view.findViewById(R.id.launch_app).setOnClickListener(v -> ViewsUtils.launchApp(this, MainActivity.class));
        view.findViewById(R.id.minimizeTimer).setOnClickListener(v -> {
            expandedTimer.setVisibility(View.GONE);
            collapsedTimer.setVisibility(View.VISIBLE);
        });
        view.findViewById(R.id.closeTimer).setOnClickListener(v -> stopSelf());
        view.findViewById(R.id.minimizeTimer).callOnClick();

        collapsedTimer.setOnClickListener(v -> {
            expandedTimer.setVisibility(View.VISIBLE);
            collapsedTimer.setVisibility(View.GONE);
        });

        view.findViewById(R.id.new_timer).setOnClickListener(getShowClickListener(R.id.new_timer_expanded));
        view.findViewById(R.id.schedule_timer).setOnClickListener(getShowClickListener(R.id.repeating_timer_expanded));
        view.findViewById(R.id.back_to_timer_mode).setOnClickListener(getShowClickListener(R.id.select_timer_mode));
        view.findViewById(R.id.cancel_repeating_timer).setOnClickListener(getShowClickListener(R.id.select_timer_mode));
    }

    private View.OnClickListener getShowClickListener(int visibleView) {
        return (v) -> {
            hideInternalViews();
            view.findViewById(visibleView).setVisibility(View.VISIBLE);
        };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        windowManager.removeView(view);
    }
}
