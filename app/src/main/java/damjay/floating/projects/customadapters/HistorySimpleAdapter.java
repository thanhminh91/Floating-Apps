package damjay.floating.projects.customadapters;

import android.content.Context;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleAdapter;
import androidx.appcompat.widget.PopupMenu;
import damjay.floating.projects.R;
import java.util.ArrayList;
import java.util.HashMap;

public class HistorySimpleAdapter extends SimpleAdapter {
    private HistorySimpleAdapter.Callback callback;
    private Context context;

    public HistorySimpleAdapter(Context context, ArrayList<HashMap> list, int id, String[] entries, int[] content, Callback callback) {
        super(context, list, id, entries, content);
        this.callback = callback;
        this.context = context;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        view.findViewById(R.id.delete_button).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View p1) {
                    callback.delete(position);
                }
            });
        view.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    PopupMenu menu = new PopupMenu(context, view);
                    menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem item) {
                                switch (item.getItemId()) {
                                    case R.id.open_history_file:
                                        callback.run(position);
                                        break;
                                    case R.id.delete_history_file:
                                        callback.delete(position);
                                }
                                return true;
                            }
                        });
                    menu.inflate(R.menu.history_menu);
                    menu.show();
                    return true;
                }
            });
        view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    callback.run(position);
                }
            });
        return view;
    }

    public static interface Callback {
        void run(int position);
        void delete(int position);
    }

}
