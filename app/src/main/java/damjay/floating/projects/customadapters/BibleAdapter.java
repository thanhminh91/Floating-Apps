package damjay.floating.projects.customadapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;

import damjay.floating.projects.R;
import damjay.floating.projects.bible.CombinedChapterBibleSource;

public class BibleAdapter extends BaseAdapter {
    private final Context context;
    private CombinedChapterBibleSource bibleSource;

    private final ArrayList<String> versesList = new ArrayList<>();

    private int curBookIndex;

    public BibleAdapter(Context context) {
        this.context = context;
        makeChapter(0, 0);
    }

    public void makeChapter(int bookIndex, int chapterIndex) {
        curBookIndex = bookIndex;
        if (bibleSource == null) {
            try {
                bibleSource = new CombinedChapterBibleSource(context, context.getFilesDir());
            } catch (Throwable t) {
                t.printStackTrace();
                return;
            }
        }
        try {
            loadVerses(curBookIndex, chapterIndex);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void loadVerses(int bookIndex, int chapterIndex) throws IOException {
        versesList.clear();
        if (bibleSource == null) return;
        char[] verses = bibleSource.getChapter(bookIndex, chapterIndex);
        int[] verseIndices = bibleSource.getChapterIndex(bookIndex, chapterIndex);

        for (int i = 0; i < verseIndices.length / 2; i++) {
            versesList.add(
                    new String(
                            verses,
                            verseIndices[i << 1],
                            verseIndices[(i << 1) + 1] - verseIndices[i << 1]));
        }
        // System.out.println("From loadVerses(): " + versesList.get(0));
    }

    public int getCurrentBookIndex() {
        return curBookIndex;
    }

    public int getNumberOfChapters() {
        return bibleSource.getNumberOfChapters(curBookIndex);
    }

    public String[] getBooks() {
        return bibleSource.getBookNames();
    }

    @Override
    public int getCount() {
        return bibleSource == null ? 0 : versesList.size();
    }

    @Override
    public Object getItem(int position) {
        return versesList.size() > position ? versesList.get(position) : position;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View view, ViewGroup vg) {
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.bible_verse, vg, false);
        }
        TextView verseNumber = view.findViewById(R.id.bibleVerseIndex);
        TextView verseContent = view.findViewById(R.id.bibleVerseContent);
        verseNumber.setText(position + 1 + "");
        if (versesList.size() > position) verseContent.setText(versesList.get(position));
        if (position == 1) {
            System.out.println(verseContent.getText());
        }
        return view;
    }
}
