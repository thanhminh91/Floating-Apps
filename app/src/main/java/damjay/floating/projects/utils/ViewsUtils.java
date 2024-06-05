package damjay.floating.projects.utils;

import android.content.Context;
import android.content.Intent;
import android.util.DisplayMetrics;
import damjay.floating.projects.MainActivity;
import android.content.res.Resources;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

public class ViewsUtils {
    public static View.OnTouchListener getViewTouchListener(
            final View parentLayout,
            final WindowManager window,
            final WindowManager.LayoutParams params) {
        return new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                TouchState touchState = TouchState.getInstance();
                TouchState.moveTolerance =
                        (int) (12.5f * Resources.getSystem().getDisplayMetrics().density);
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchState.setInitialPosition(event.getRawX(), event.getRawY());
                        touchState.setOriginalPosition(params.x, params.y);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        touchState.setFinalPosition(event.getRawX(), event.getRawY());
                        if (touchState.hasMoved()) {
                            params.x = touchState.updatedPositionX();
                            params.y = touchState.updatedPositionY();
                            window.updateViewLayout(parentLayout, params);
                            return true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if (!touchState.hasMoved()) {
                            boolean result = view.callOnClick();
                            return result;
                        }
                }
                return false;
            }
        };
    }

    public static void addTouchListener(
            View parentView,
            View.OnTouchListener listener,
            boolean applyToChildren,
            boolean recursive,
            Class... allowedClasses) {
        // Check if absent if the last class in the array is null
        boolean checkAbsent =
                allowedClasses != null
                        && allowedClasses.length > 0
                        && allowedClasses[allowedClasses.length - 1] == null;
        if (parentView instanceof ViewGroup && (applyToChildren || recursive)) {
            ViewGroup viewGroup = (ViewGroup) parentView;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                boolean present = isPresent(child.getClass(), allowedClasses);
                present = checkAbsent ? !present : present;
                if (recursive && present) {
                    addTouchListener(child, listener, applyToChildren, recursive, allowedClasses);
                } else {
                    if (present) {
                        child.setOnTouchListener(listener);
                    }
                }
            }
        }
        boolean present = isPresent(parentView.getClass(), allowedClasses);
        present = checkAbsent ? !present : present;
        if (present) {
            parentView.setOnTouchListener(listener);
        }
    }

    // Check if viewClass is present
    private static boolean isPresent(Class viewClass, Class[] classes) {
        if (classes != null && viewClass != null) {
            for (Class clazz : classes) {
                if (viewClass == clazz) {
                    return true;
                }
            }
        }
        // If nothing was specified, match all classes
        return classes == null || classes.length == 0;
    }

    public static void launchApp(Context context, Class mainActivity) {
        Intent intent = new Intent("android.intent.category.LAUNCHER");
        String classPackage = mainActivity.getPackage().getName();
        String fullClassName = mainActivity.getCanonicalName();
        intent.setClassName(classPackage, fullClassName);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }

    public static int getViewWidth(float minSmallestWidth) {
        float smallestDeviceWidth;
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        smallestDeviceWidth = (float) metrics.widthPixels / metrics.density;

        int viewWidth;
        if (smallestDeviceWidth / 2 < minSmallestWidth) {
            // If the smallest device width is less than min smallest width, use the device width
            if (smallestDeviceWidth < minSmallestWidth) viewWidth = metrics.widthPixels;
            // Use the min smallest width, convert it to width pixels
            else viewWidth = (int) (minSmallestWidth * metrics.density);
        } else {
            // Use half of the device width
            viewWidth = metrics.widthPixels / 2;
        }
        return viewWidth;
    }
    
    public static int getViewHeight(float minSmallestHeight) {
        float smallestDeviceHeight;
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        smallestDeviceHeight = (float) metrics.heightPixels / metrics.density;

        int viewHeight;
        if (smallestDeviceHeight / 2 < minSmallestHeight) {
            // If the smallest device height is less than min smallest height, use the device height
            if (smallestDeviceHeight < minSmallestHeight) viewHeight = metrics.heightPixels;
            // Use the min smallest height, convert it to height pixels
            else viewHeight = (int) (minSmallestHeight * metrics.density);
        } else {
            // Use half of the device height
            viewHeight = metrics.widthPixels / 2;
        }
        return viewHeight;
    }

}
