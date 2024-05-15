package damjay.floating.projects.utils;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;

public class ViewsUtils {

    public static void addTouchListener(View parentView, View.OnTouchListener listener, boolean applyToChildren, boolean recursive, Class... allowedClasses) {
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
