package damjay.floating.projects.bible;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import android.widget.Spinner;
import damjay.floating.projects.MainActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.customadapters.BibleAdapter;
import damjay.floating.projects.utils.ViewsUtils;

public class BibleService extends Service {
    private WindowManager windowManager;
    private View view;
    private WindowManager.LayoutParams params;
    private ListView verseList;
    
    private Spinner bookList;
    private Spinner chapterList;
    private BibleAdapter bibleAdapter;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        view = LayoutInflater.from(this).inflate(R.layout.bible_layout, null);
        params = getLayoutParams();
        initializeViewItems();
        initViewSize();
        minimizeView();

        windowManager.addView(view, params);
        windowManager.updateViewLayout(view, params);
        addTouchListeners(view);
    }

    private void initializeViewItems() {
        verseList = view.findViewById(R.id.bibleVerses);
        if (bibleAdapter == null) {
            bibleAdapter = new BibleAdapter(this);
            verseList.setAdapter(bibleAdapter);
        }

        bookList = view.findViewById(R.id.bibleBookSpinner);
        chapterList = view.findViewById(R.id.bibleChapterSpinner);
        view.findViewById(R.id.minimizedBible).setOnClickListener((v) -> maximizeView());
        setArrayAdapters();
    }
    
    private void setArrayAdapters() {
        ArrayAdapter<String> bookListAdapter =
                new ArrayAdapter<>(
                        this, android.R.layout.simple_spinner_item, bibleAdapter.getBooks());
        bookListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        bookList.setAdapter(bookListAdapter);
        
        bookList.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> spinner, View view, int position, long id) {
                        if (bibleAdapter.getCurrentBookIndex() != position) {
                            bibleAdapter.makeChapter(position, 0);
                            ArrayAdapter<String> chapterListAdapter = new ArrayAdapter<>(BibleService.this, android.R.layout.simple_spinner_item, getCountTill(bibleAdapter.getNumberOfChapters()));
                            chapterListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                            chapterList.setAdapter(chapterListAdapter);
                            bibleAdapter.notifyDataSetChanged();
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> spinner) {}
                });

        ArrayAdapter<String> chapterListAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        getCountTill(bibleAdapter.getNumberOfChapters()));
        chapterListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        chapterList.setAdapter(chapterListAdapter);

        chapterList.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> spinner, View view, int position, long id) {
                        bibleAdapter.makeChapter(bibleAdapter.getCurrentBookIndex(), position);
                        bibleAdapter.notifyDataSetChanged();
                        verseList.setAdapter(bibleAdapter);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> spinner) {}
                });

    }

    private void initViewSize() {
        view.findViewById(R.id.bibleCloseView).setOnClickListener(v -> stopSelf());
        view.findViewById(R.id.bibleLauchApp)
                .setOnClickListener(v -> ViewsUtils.launchApp(this, MainActivity.class));

        view.getViewTreeObserver()
                .addOnGlobalLayoutListener(
                        new ViewTreeObserver.OnGlobalLayoutListener() {
                            @Override
                            public void onGlobalLayout() {
                                view.findViewById(R.id.bibleNavPadding).getLayoutParams().width = verseList.getLayoutParams().width = ViewsUtils.getViewWidth(350.0f);
                                verseList.getLayoutParams().height = ViewsUtils.getViewHeight(400f);
                            }
                        });
    }
    
    
    private void minimizeView() {
        verseList.setVisibility(View.GONE);
        view.findViewById(R.id.windowControls).setVisibility(View.GONE);
        view.findViewById(R.id.bibleNavPadding).setVisibility(View.GONE);
        view.findViewById(R.id.minimizedBible).setVisibility(View.VISIBLE);
    }
    
    private void maximizeView() {
        verseList.setVisibility(View.VISIBLE);
        view.findViewById(R.id.windowControls).setVisibility(View.VISIBLE);
        view.findViewById(R.id.bibleNavPadding).setVisibility(View.VISIBLE);
        view.findViewById(R.id.minimizedBible).setVisibility(View.GONE);
    }

    private String[] getCountTill(int chapters) {
        String[] chapterArray = new String[chapters];
        for (int i = 1; i <= chapters; i++) {
            chapterArray[i - 1] = i + "";
        }
        return chapterArray;
    }

    // 07040527226
    private LayoutParams getLayoutParams() {
        int type =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? LayoutParams.TYPE_APPLICATION_OVERLAY
                        : LayoutParams.TYPE_PHONE;

        LayoutParams params =
                new LayoutParams(
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

    private void addTouchListeners(View view) {
        View.OnTouchListener listener =
                ViewsUtils.getViewTouchListener(view, windowManager, params);
        ViewsUtils.addTouchListener(
                view, listener, true, true, ListView.class, Spinner.class, null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        windowManager.removeView(view);
    }
}
