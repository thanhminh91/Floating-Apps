package damjay.floating.projects.autoclicker.service;

import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import static damjay.floating.projects.autoclicker.activity.ClickerActivity.*;
import static damjay.floating.projects.bluetooth.BluetoothOperations.BluetoothOperationsConstants.*;

import android.accessibilityservice.AccessibilityService;
import android.bluetooth.BluetoothSocket;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import damjay.floating.projects.R;
import damjay.floating.projects.bluetooth.BluetoothOperations;
import damjay.floating.projects.bluetooth.BluetoothOperations.BluetoothOperationsCallback;
import damjay.floating.projects.utils.ViewsUtils;

import java.util.ArrayList;

@RequiresApi(24)
public class ClickerAccessibilityService extends AccessibilityService implements BluetoothOperationsCallback {
    private final ArrayList<View> clickPoints = new ArrayList<>();
    private View clickerLayout;
    private WindowManager windowManager;
    private LayoutParams clickerParams;

    public static BluetoothSocket bluetoothSocket;
    private BluetoothOperations btOperation;

    private boolean pendingAddButton = false;
    private boolean pendingRemoveButton = false;

    @Override
    public void onCreate() {
        super.onCreate();

        clickerLayout = LayoutInflater.from(this).inflate(R.layout.service_clicker, null);

        if (bluetoothSocket == null) {
            System.out.println("Socket is null. Destroying...");
            stopSelf();
            return;
        }
        System.out.println("Socket is not null. Continuing...");

        btOperation = new BluetoothOperations(bluetoothSocket);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        clickerParams = ViewsUtils.getFloatingLayoutParams();
        //clickerParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        windowManager.addView(clickerLayout, clickerParams);

        addClickListeners();
        btOperation.startReading(this);
    }

    private void addClickListeners() {
        clickerLayout
                .findViewById(R.id.addClicker)
                .setOnClickListener((v) -> sendToDevice(CLICKER_ADD_POINT, this));
        clickerLayout
                .findViewById(R.id.removeClicker)
                .setOnClickListener((v) -> sendToDevice(CLICKER_DELETE_POINT, this));
        clickerLayout.findViewById(R.id.closeClicker).setOnClickListener((v) -> notifyAndStop());

        ViewsUtils.addTouchListener(
                clickerLayout,
                ViewsUtils.getViewTouchListener(this, clickerLayout, windowManager, clickerParams),
                true,
                true,
                (Class[]) null);
    }

    public void sendToDevice(byte value, BluetoothOperationsCallback callback) {
        if (btOperation != null) {
            btOperation.write(value, callback);
            pendingAddButton = value == CLICKER_ADD_POINT || pendingAddButton;
            pendingRemoveButton = value == CLICKER_DELETE_POINT || pendingRemoveButton;
        } else stopSelf();
    }

    private void addNewButton() {
        pendingAddButton = false;

        View clickPoint = LayoutInflater.from(this).inflate(R.layout.clicker_point, null);
        ((TextView) clickPoint.findViewById(R.id.clickId))
                .setText(Integer.toString(clickPoints.size() + 1));

        LayoutParams pointParams;
        DisplayMetrics deviceMetrics = getResources().getDisplayMetrics();
        if (clickPoints.isEmpty() || clickPoints.get(clickPoints.size() - 1).getTag() == null) {
            // Place at almost middle of screen
            int x = deviceMetrics.widthPixels / 4;
            int y = deviceMetrics.heightPixels / 4;

            pointParams = ViewsUtils.getFloatingLayoutParams(x, y);
        } else {
            LayoutParams firstPointParams =
                    (LayoutParams) clickPoints.get(clickPoints.size() - 1).getTag();
            if (firstPointParams.x > (deviceMetrics.widthPixels * 3) / 4) {
                if (firstPointParams.y > (deviceMetrics.heightPixels * 3) / 4) {
                    pointParams = ViewsUtils.getFloatingLayoutParams(deviceMetrics.widthPixels / 8, deviceMetrics.heightPixels / 8);
                } else {
                    pointParams = ViewsUtils.getFloatingLayoutParams(deviceMetrics.widthPixels / 4, firstPointParams.y + clickPoints.get(clickPoints.size() - 1).getMeasuredHeight());
                }
            } else {
                pointParams = ViewsUtils.getFloatingLayoutParams(firstPointParams.x + clickPoints.get(clickPoints.size() - 1).getMeasuredWidth(), firstPointParams.y);
            }
        }
        
        //pointParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        // Set tag, and on touch listener
        clickPoint.setTag(pointParams);
        clickPoint.setOnTouchListener(ViewsUtils.getViewTouchListener(this, clickPoint, windowManager, pointParams));
        clickPoints.add(clickPoint);
        // Place it at a position, then add to window
        windowManager.addView(clickPoint, pointParams);
    }

    private void removeLastButton() {
        pendingRemoveButton = false;
        if (!clickPoints.isEmpty()) {
            windowManager.removeView(clickPoints.get(clickPoints.size() - 1));
            clickPoints.remove(clickPoints.size() - 1);
        }
    }

    private void clickPoint(int x, int y) {
        // TODO: Click on point x,y
        Path path = new Path();
        path.moveTo(x, y);
        
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 20));
        dispatchGesture(builder.build(), null, null);
    }

    public void onSuccess(byte type, Object value) {
        switch (type) {
            case TYPE_SUCCESS:
                if (pendingAddButton) addNewButton();
                if (pendingRemoveButton) removeLastButton();
                break;
            case TYPE_BYTE:
                byte byteValue = (byte) value;
                if (byteValue == CLICKER_ADD_POINT) {
                    addNewButton();
                } else if (byteValue == CLICKER_DELETE_POINT) {
                    removeLastButton();
                } else {
                    if (clickPoints.size() > byteValue) {
                        LayoutParams params = (LayoutParams) clickPoints.get(byteValue).getTag();
                        clickPoint(params.x, params.y);
                    }
                }
                break;
            default:
                // Should we close?
        }
    }

    public void onError(Throwable t) {
        stopSelf();
    }

    private void notifyAndStop() {
        btOperation.writeExit(this);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        windowManager.removeView(clickerLayout);
        for (View clickPoint : clickPoints) {
            windowManager.removeView(clickPoint);
        }
        clickPoints.clear();
        if (btOperation != null) btOperation.close();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        
    }

    @Override
    public void onInterrupt() {
        
    }
}
