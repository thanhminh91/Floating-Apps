package damjay.floating.projects.calculate;

import android.app.Service;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
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
import damjay.floating.projects.utils.ViewsUtils;
import java.util.ArrayList;

public class CalculatorService extends Service {
    private View parentLayout;
    private View collapsed;
    private View expanded;
    private View mainCalculator;
    private ListView historyList;
    private EditText editor;
    
    private CalculatorHistoryAdapter historyAdapter;
    
    private int caretPosition = 1;

    private LayoutParams params;
    private WindowManager window;

    private ArrayList<CalcItem> calculatedItems = new ArrayList<>();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        getViews();
        minimizeView(null);

        params = getLayoutParams();
        window = (WindowManager) getSystemService(WINDOW_SERVICE);
        window.addView(parentLayout, params);

        addTouchListeners();
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
        mainCalculator = parentLayout.findViewById(R.id.main_calculator);
        
        if (historyList == null) {
            historyList = parentLayout.findViewById(R.id.calcHistory);
            historyAdapter = new CalculatorHistoryAdapter(this, calculatedItems);
            historyList.setAdapter(historyAdapter);
        }

        collapsed.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    expanded.setVisibility(View.VISIBLE);
                    collapsed.setVisibility(View.GONE);
                }         
            });
        
        parentLayout.findViewById(R.id.calcLaunchActivity).setOnClickListener(v -> ViewsUtils.launchApp(this, MainActivity.class));

        expanded.getViewTreeObserver().addOnGlobalLayoutListener(() -> expanded.getLayoutParams().width = ViewsUtils.getViewWidth(275.0f));
        
        ViewTreeObserver.OnGlobalLayoutListener layoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    int measuredHeight = mainCalculator.getMeasuredHeight();
                    if (measuredHeight < 1) return;
                    historyList.getLayoutParams().height = measuredHeight;
                }
                
            };
            mainCalculator.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
            historyList.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
    }

    private void addTouchListeners() {
        View.OnTouchListener touchListener = ViewsUtils.getViewTouchListener(parentLayout, window, params);
        ViewsUtils.addTouchListener(expanded, touchListener, true, true, ScrollView.class, ListView.class, null);
        ViewsUtils.addTouchListener(collapsed, touchListener, true, true);
    }

    public void showHistory(final View view) {
        if (parentLayout.findViewById(R.id.calcHistory).getVisibility() == View.VISIBLE) {
            hideHistory(view);
            return;
        }
        if (historyList == null) {
            historyList = parentLayout.findViewById(R.id.calcHistory);
            historyAdapter = new CalculatorHistoryAdapter(this, calculatedItems);
            historyList.setAdapter(historyAdapter);
        }

        View mainCalculator = parentLayout.findViewById(R.id.main_calculator);
        View calcHistory = parentLayout.findViewById(R.id.calcHistory);
 
        // Show the history views
        calcHistory.setVisibility(View.VISIBLE);
        mainCalculator.setVisibility(View.GONE);

        historyList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapter, View childView, int position, long id) {
                    CalcItem item = (CalcItem) historyList.getItemAtPosition(position);
                    // Insert at caret position
                    String editorText = editor.getText().toString();
                    int caretPosition = editor.getSelectionStart();
                    editor.setText(editorText.substring(0, caretPosition) + item.getExpression() + editorText.substring(caretPosition));
                    // Shift the caretPosition to the end of inserted expression
                    editor.setSelection(caretPosition = caretPosition + item.getExpression().length());
                    editor.requestFocus();
                    hideHistory(view);
                }
            });
    }

    public void hideHistory(View view) {
        parentLayout.findViewById(R.id.calcHistory).setVisibility(View.GONE);
        parentLayout.findViewById(R.id.main_calculator).setVisibility(View.VISIBLE);
    }

    public void minimizeView(View view) {
        expanded.setVisibility(View.GONE);
        collapsed.setVisibility(View.VISIBLE);
    }
    
    public void clearAction(View view) {
        editor.setText("0");
        editor.setSelection(caretPosition = 1);
    }

    public void deleteAction(View view) {
        String input = editor.getText().toString();
        if (input.toLowerCase().startsWith("invalid")) {
            clearAction(null);
            return;
        }
        if (caretPosition == 0) return;
        if (caretPosition == input.length()) {
            editor.setText(input.length() == 1 ? "0" : input.substring(0, input.length() - 1));
        } else {
            editor.setText(input.substring(0, caretPosition - 1) + input.substring(caretPosition));
        }
        if ("0".equals(editor.getText().toString())) {
            editor.setSelection(caretPosition = 1);
        } else {
            editor.setSelection(caretPosition = (--caretPosition < 0 ? 0 : caretPosition));
        }
        editor.requestFocus();
    }
    
    public void caretLeft(View view) {
        int textLength = editor.getText().length();
        if (editor.getText().toString().toLowerCase().startsWith("invalid")) {
            // TODO: Show the previous expression that was on the editor
            return;
        }
        editor.setCursorVisible(true);
        caretPosition = editor.getSelectionStart();
        caretPosition = --caretPosition < 0 ? textLength : caretPosition;
        editor.setSelection(caretPosition);
        editor.requestFocus();
    }
    
    public void caretRight(View view) {
        int textLength = editor.getText().length();
        if (editor.getText().toString().toLowerCase().startsWith("invalid")) {
            // TODO: Show the previous expression that was on the editor
            return;
        }
        editor.setCursorVisible(true);
        caretPosition = editor.getSelectionStart();
        caretPosition = ++caretPosition > textLength ? 0 : caretPosition;
        editor.setSelection(caretPosition);
        editor.requestFocus();
    }
    
    public void buttonAction(View view) {
        if (view instanceof Button) {
            Button button = (Button) view;
            String fieldContent = editor.getText().toString();
            String updatedContent;

            if (caretPosition == fieldContent.length()) {
                if (fieldContent.toLowerCase().startsWith("invalid")) {
                    deleteAction(null);
                    caretPosition = editor.getSelectionStart();
                }
                updatedContent = (fieldContent.equals("0") && !button.getText().toString().equals(".") ? "" : fieldContent) + "" + button.getText();
            } else {
                updatedContent = fieldContent.substring(0, caretPosition) + button.getText() + fieldContent.substring(caretPosition);
            }
            editor.setText(updatedContent);
            editor.setSelection(caretPosition = (++caretPosition > updatedContent.length() ? updatedContent.length() : caretPosition));
            editor.requestFocus();
        }
    }

    public void computeCalculation(View view) {
        try {
            String editorText = editor.getText().toString();
            CompoundExpression firstResult = new CompoundExpression(editorText);
            Expression result = firstResult.compute();
            if (result != null) {
                calculatedItems.add(new CalcItem(editorText, result.getExact()));
                if (historyAdapter != null) {
                    historyAdapter.notifyDataSetChanged();
                }
            }
            editor.setText(result == null ? "Invalid syntax" : result.getExact());
            editor.setSelection(caretPosition = editor.getText().length());
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
