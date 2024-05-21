package damjay.floating.projects.calculate;

import android.app.Service;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import damjay.floating.projects.MainActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.calculate.CompoundExpression;
import damjay.floating.projects.calculate.Expression;
import damjay.floating.projects.customadapters.CalculatorHistoryAdapter;
import damjay.floating.projects.utils.TouchState;
import damjay.floating.projects.utils.ViewsUtils;
import java.util.ArrayList;
import android.util.DisplayMetrics;

public class CalculatorService extends Service {
    private View parentLayout;
    private View collapsed;
    private View expanded;
    private ListView historyList;
    private EditText editor;

    private LayoutParams params;
    private WindowManager window;

    private ArrayList<CalcItem> calculatedItems = new ArrayList<>();

    @Override
    public IBinder onBind(Intent p1) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        getViews();
        addTouchListeners();
        minimizeView(null);

        params = getLayoutParams();
        window = (WindowManager) getSystemService(WINDOW_SERVICE);
        window.addView(parentLayout, params);

    }

    private LayoutParams getLayoutParams() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? LayoutParams.TYPE_APPLICATION_OVERLAY : LayoutParams.TYPE_PHONE;
        
        LayoutParams params = new LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            type,
            LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 0;
        params.y = 100;
        return params;
    }

    private void getViews() {
        parentLayout = LayoutInflater.from(this).inflate(R.layout.calculator_layout, null);

        editor = parentLayout.findViewById(R.id.calcField);       
        collapsed = parentLayout.findViewById(R.id.collapsedCalc);
        expanded = parentLayout.findViewById(R.id.expandedCalc);

        collapsed.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    expanded.setVisibility(View.VISIBLE);
                    collapsed.setVisibility(View.GONE);
                }         
            });

        expanded.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    DisplayMetrics metrics = new DisplayMetrics();
                    float smallestDeviceWidth;
                    if (window != null) {
                        window.getDefaultDisplay().getRealMetrics(metrics);
                        smallestDeviceWidth = (float) metrics.widthPixels / metrics.density;
                    } else {
                        metrics = Resources.getSystem().getDisplayMetrics();
                        smallestDeviceWidth = (float) metrics.widthPixels / metrics.density;
                    }
                    final float MIN_SW = 275.0f; // The minimum smallest width
                    int viewWidth;
                    if (smallestDeviceWidth / 2 < MIN_SW) {
                        // If the smallest device width is less than MIN_SW, use the device width
                        if (smallestDeviceWidth < MIN_SW) viewWidth = metrics.widthPixels;
                        // Use the MIN_SW, convert it to width pixels
                        else viewWidth = (int) (MIN_SW * metrics.density);
                    } else {
                        // Use half of the device width
                        viewWidth = metrics.widthPixels / 2;
                    }
                    expanded.getLayoutParams().width = viewWidth;
                }
            });
    }

    private void addTouchListeners() {
        View.OnTouchListener touchListener = new View.OnTouchListener() {
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
        ViewsUtils.addTouchListener(expanded, touchListener, true, true, ScrollView.class, ListView.class, null);
        ViewsUtils.addTouchListener(collapsed, touchListener, true, true);
    }

    public void launchActivity(View view) {
        Intent intent = new Intent("android.intent.category.LAUNCHER");
        String classPackage = MainActivity.class.getPackage().getName();
        String fullClassName = MainActivity.class.getCanonicalName();
        intent.setClassName(classPackage, fullClassName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public void showHistory(final View view) {
        if (parentLayout.findViewById(R.id.scrollCalcHistory).getVisibility() == View.VISIBLE) {
            hideHistory(view);
            return;
        }
        if (historyList == null) {
            historyList = parentLayout.findViewById(R.id.calcHistory);
            historyList.setAdapter(new CalculatorHistoryAdapter(this, calculatedItems));
        }

        parentLayout.findViewById(R.id.scrollCalcHistory).setVisibility(View.VISIBLE);
        parentLayout.findViewById(R.id.main_calculator).setVisibility(View.GONE);

        historyList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapter, View childView, int position, long id) {
                    CalcItem item = (CalcItem) historyList.getItemAtPosition(position);
                    // TODO: Insert the expression at caret position
                    editor.setText(item.getExpression());
                    hideHistory(view);
                }
            });
    }

    public void hideHistory(View view) {
        parentLayout.findViewById(R.id.scrollCalcHistory).setVisibility(View.GONE);
        parentLayout.findViewById(R.id.main_calculator).setVisibility(View.VISIBLE);
    }

    public void minimizeView(View view) {
        expanded.setVisibility(View.GONE);
        collapsed.setVisibility(View.VISIBLE);
    }

    public void deleteAction(View view) {
        String input = editor.getText().toString();
        if (input.toLowerCase().startsWith("invalid")) {
            editor.setText("0");
            return;
        }
        editor.setText(input.length() == 1 ? "0" : input.substring(0, input.length() - 1));
    }

    public void buttonAction(View view) {
        if (view instanceof Button) {
            Button button = (Button) view;
            String input = editor.getText().toString();
            editor.setText((input.equals("0") && !button.getText().toString().equals(".") ? "" : input) + "" + button.getText());
        }
    }

    public void computeCalculation(View view) {
        try {
            String editorText = editor.getText().toString();
            CompoundExpression firstResult = new CompoundExpression(editorText);
            Expression result = firstResult.compute();
            if (result != null) {
                calculatedItems.add(new CalcItem(editorText, result.getExact()));
            }
            editor.setText(result == null ? "Invalid syntax" : result.getExact());
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void closeView(View view) {
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (parentLayout != null) window.removeView(parentLayout);
    }

    public static class CalcItem {
        private String expression;
        private String answer;

        public CalcItem(String expression, String answer) {
            this.expression = expression;
            this.answer = answer;
        }

        public String getExpression() {
            return expression;
        }

        public String getAnswer() {
            return answer;
        }
    }

}
