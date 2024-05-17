package damjay.floating.projects.customadapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import damjay.floating.projects.R;
import damjay.floating.projects.calculate.CalculatorService;
import java.util.ArrayList;

public class CalculatorHistoryAdapter extends BaseAdapter {
    private Context context;
    private ArrayList<CalculatorService.CalcItem> list;

    public CalculatorHistoryAdapter(Context context, ArrayList<CalculatorService.CalcItem> list) {
        this.list = list;
        this.context = context;
    }

    @Override
    public int getCount() {
        return list != null ? list.size() : 0;
    }

    @Override
    public Object getItem(int position) {
        return list != null && list.size() > position ? list.get(position) : null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View view, ViewGroup vg) {
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.calculator_history, null);
//            view.setTag(list.get(position));
        }
        TextView expressionView = view.findViewById(R.id.calc_history_expression);
        expressionView.setText(list.get(position).getExpression());
        TextView solutionView = view.findViewById(R.id.calc_history_solution);
        solutionView.setText(list.get(position).getAnswer());
        return view;
    }

}
