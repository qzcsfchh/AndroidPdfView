package io.github.qzcsfchh.android.pdf.test;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

/**
 * <p>https://blog.csdn.net/divaid/article/details/81259510</p>
 */
public class VerticalViewPager extends ViewPager {
    private boolean isChildScrollable;
    private float touchDownPosition;

    public VerticalViewPager(@NonNull Context context) {
        this(context,null);
    }

    public VerticalViewPager(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setPageTransformer(true, new PageTransformer() {
            @Override
            public void transformPage(@NonNull View view, float position) {
                if (position < -1) { // [-Infinity,-1)
                    // This page is way off-screen to the left.
                    view.setAlpha(0);
                } else if (position <= 1) { // [-1,1]
                    view.setAlpha(1);
                    // Counteract the default slide transition
                    view.setTranslationX(view.getWidth() * -position);
                    //set Y position to swipe in from top
                    float yPosition = position * view.getHeight();
                    view.setTranslationY(yPosition);
                } else { // (1,+Infinity]
                    // This page is way off-screen to the right.
                    view.setAlpha(0);
                }
            }
        });
    }

    public boolean isChildScrollable() {
        return isChildScrollable;
    }

    public void setChildScrollable(boolean childScrollable) {
        isChildScrollable = childScrollable;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
//        int currentItem = getCurrentItem();
        boolean b = super.onInterceptTouchEvent(ev);
        swapXY(ev);
        return b;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return super.onTouchEvent(swapXY(ev));
    }

    /**
     * Swaps the X and Y coordinates of your touch event.
     */
    private MotionEvent swapXY(MotionEvent ev) {
        float width = getWidth();
        float height = getHeight();
        float newX = (ev.getY() / height) * width;
        float newY = (ev.getX() / width) * height;
        ev.setLocation(newX, newY);
        return ev;
    }
}
