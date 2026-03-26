package tn.amin.mpro2.ui.touch;

import android.os.CountDownTimer;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import tn.amin.mpro2.util.Range;

public class LongPressDetector {
    private static final int DELAY = 1000;
    private static final int MOVE_THRESHOLD = 30;

    private CountDownTimer mCountDown;
    private final Range mXRange;
    private final Range mYRange;
    private float mDownX, mDownY;

    private LongPressListener mListener = null;

    public LongPressDetector(@Nullable Range xRange, @Nullable Range yRange) {
        mXRange = xRange;
        mYRange = yRange;

        mCountDown = new CountDownTimer(DELAY, DELAY) {
            @Override
            public void onTick(long ignored) {}

            @Override
            public void onFinish() {
                if (mListener != null)
                    mListener.onLongPress();
            }
        };
    }

    public void setLongPressListener(LongPressListener mListener) {
        this.mListener = mListener;
    }


    public void handleTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if ((mXRange == null || mXRange.contains(event.getX())) &&
                    (mYRange == null || mYRange.contains(event.getY()))) {
                    mDownX = event.getX();
                    mDownY = event.getY();
                    mCountDown.start();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - mDownX;
                float dy = event.getY() - mDownY;
                if (dx * dx + dy * dy > MOVE_THRESHOLD * MOVE_THRESHOLD) {
                    mCountDown.cancel();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mCountDown.cancel();
                break;
        }
    }

    public interface LongPressListener {
        void onLongPress();
    }
}
