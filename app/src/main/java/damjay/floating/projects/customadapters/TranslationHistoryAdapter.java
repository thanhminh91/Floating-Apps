package damjay.floating.projects.customadapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

import damjay.floating.projects.R;
import damjay.floating.projects.models.TranslationHistory;

public class TranslationHistoryAdapter extends BaseAdapter {
    private Context context;
    private List<TranslationHistory> historyList;
    private LayoutInflater inflater;

    public TranslationHistoryAdapter(Context context, List<TranslationHistory> historyList) {
        this.context = context;
        this.historyList = historyList;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return historyList.size();
    }

    @Override
    public Object getItem(int position) {
        return historyList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.translation_history_item, parent, false);
            holder = new ViewHolder();
            holder.languagePair = convertView.findViewById(R.id.languagePair);
            holder.timestamp = convertView.findViewById(R.id.timestamp);
            holder.originalText = convertView.findViewById(R.id.originalTextHistory);
            holder.translatedText = convertView.findViewById(R.id.translatedTextHistory);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        TranslationHistory history = historyList.get(position);
        holder.languagePair.setText(history.getLanguagePair());
        holder.timestamp.setText(history.getFormattedTimestamp());
        holder.originalText.setText(history.getOriginalText());
        holder.translatedText.setText(history.getTranslatedText());

        return convertView;
    }

    public void updateHistory(List<TranslationHistory> newHistoryList) {
        this.historyList = newHistoryList;
        notifyDataSetChanged();
    }

    private static class ViewHolder {
        TextView languagePair;
        TextView timestamp;
        TextView originalText;
        TextView translatedText;
    }
}