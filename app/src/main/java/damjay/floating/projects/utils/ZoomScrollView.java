package damjay.floating.projects.utils;

public class ZoomScrollView {
}/*
import android.os.Bundle; 
import android.view.GestureDetector; 
import android.view.MotionEvent; 
import android.view.ScaleGestureDetector; 
import android.view.animation.ScaleAnimation; 
import android.widget.ScrollView; 
  
import androidx.appcompat.app.AppCompatActivity; 
  
public class MainActivity extends AppCompatActivity { 
    private static final String TAG = "MainActivity"; 
    private float mScale = 1f; 
    private ScaleGestureDetector mScaleGestureDetector; 
    GestureDetector gestureDetector; 
  
    @Override
    protected void onCreate(Bundle savedInstanceState) { 
        super.onCreate(savedInstanceState); 
        setContentView(R.layout.activity_main); 
          
        // initialising the values 
        gestureDetector = new GestureDetector(this, new GestureListener()); 
        mScaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() { 
            @Override
            public boolean onScale(ScaleGestureDetector detector) { 
                
                // firstly we will get the scale factor 
                float scale = 1 - detector.getScaleFactor(); 
                float prevScale = mScale; 
                mScale += scale; 
                  
                // we can maximise our focus to 10f only 
                if (mScale > 10f) 
                    mScale = 10f; 
  
                ScaleAnimation scaleAnimation = new ScaleAnimation(1f / prevScale, 1f / mScale, 1f / prevScale, 1f / mScale, detector.getFocusX(), detector.getFocusY()); 
                  
                // duration of animation will be 0.It will  
                // not change by self after that 
                scaleAnimation.setDuration(0); 
                scaleAnimation.setFillAfter(true); 
                 
                // initialising the scrollview 
                ScrollView layout = (ScrollView) findViewById(R.id.scrollView); 
                  
                // we are setting it as animation 
                layout.startAnimation(scaleAnimation); 
                return true; 
            } 
        }); 
    } 
  
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) { 
        super.dispatchTouchEvent(event); 
          
        // special types of touch screen events such as pinch , 
        // double tap, scrolls , long presses and flinch, 
        // onTouch event is called if found any of these 
        mScaleGestureDetector.onTouchEvent(event); 
        gestureDetector.onTouchEvent(event); 
        return gestureDetector.onTouchEvent(event); 
    } 
  
    private class GestureListener extends GestureDetector.SimpleOnGestureListener { 
        @Override
        public boolean onDown(MotionEvent e) { 
            return true; 
        } 
  
        @Override
        public boolean onDoubleTap(MotionEvent e) { 
            return true; 
        } 
    } */