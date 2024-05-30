package damjay.floating.projects.utils;

import android.content.res.Resources;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

public class ViewsUtils {
    public static View.OnTouchListener getViewTouchListener(final View parentLayout, final WindowManager window, final WindowManager.LayoutParams params) {
        return new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                TouchState touchState = TouchState.getInstance();
                touchState.moveTolerance = (int) (12.5f * Resources.getSystem().getDisplayMetrics().density);
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

    public static void addTouchListener(View parentView, View.OnTouchListener listener, boolean applyToChildren, boolean recursive, Class... allowedClasses) {
        // Check if absent if the last class in the array is null
        boolean checkAbsent = allowedClasses != null && allowedClasses.length > 0 && allowedClasses[allowedClasses.length - 1] == null;
        if (parentView instanceof ViewGroup && (applyToChildren || recursive)) {
            ViewGroup viewGroup = (ViewGroup) parentView;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (recursive) {
                    addTouchListener(child, listener, applyToChildren, recursive, allowedClasses);
                } else {
                    boolean present = isPresent(child.getClass(), allowedClasses);
                    present = checkAbsent ? !present : present;
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
}
