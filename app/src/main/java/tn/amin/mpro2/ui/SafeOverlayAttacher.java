package tn.amin.mpro2.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import androidx.core.view.ViewCompat;

import tn.amin.mpro2.debug.Logger;

public class SafeOverlayAttacher implements Runnable {
    private final View mView;
    private final WindowManager.LayoutParams mLayoutParams;
    private final int mDelay;
    private final WindowManager mWindowManager;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private OnOverlayAttachedListener mListener = null;
    private boolean shouldAttach = true;
    private int mFailedAttachAttempts = 0;


    public SafeOverlayAttacher(View view, WindowManager.LayoutParams layoutParams) {
        this(view, layoutParams, 500);
    }

    public SafeOverlayAttacher(View view, WindowManager.LayoutParams layoutParams, int delay) {
        mView = view;
        mLayoutParams = layoutParams;
        mDelay = delay;

        mWindowManager = (WindowManager) view.getContext().getSystemService(Context.WINDOW_SERVICE);
    }

    public void setOnOverlayAttachedListener(OnOverlayAttachedListener listener) {
        mListener = listener;
    }

    public void attach() {
        shouldAttach = true;
        mFailedAttachAttempts = 0;
        mHandler.post(this);
    }

    public void detach() {
        mHandler.removeCallbacks(this);

        shouldAttach = false;
        mHandler.post(this);
    }

    @Override
    public void run() {
        boolean success = false;
        try {
            if (shouldAttach) {
                if (!resolveApplicationPanelToken()) {
                    Logger.verbose("Waiting for an active Activity window token before attaching overlay");
                    return;
                }
                if (!shouldAttach) {
                    success = true;
                    return;
                }
                if (ViewCompat.isAttachedToWindow(mView)) {
                    Logger.info("Overlay already added, removing and adding again...");
                    mWindowManager.removeView(mView);
                }

                Logger.info("Adding overlay to WindowManager");
                mWindowManager.addView(mView, mLayoutParams);
                if (mListener != null)
                    mListener.onOverlayAttached();
            } else {
                if (ViewCompat.isAttachedToWindow(mView)) {
                    mWindowManager.removeView(mView);
                }
            }
            success = true;
            mFailedAttachAttempts = 0;
        } catch (WindowManager.BadTokenException e) {
            Logger.warn("Overlay add failed because the Activity window token was rejected");
            Logger.verbose(Log.getStackTraceString(e));
        } catch (IllegalStateException e) {
            Logger.info("Overlay already in WindowManager ?");
            Logger.verbose(Log.getStackTraceString(e));
            mWindowManager.removeView(mView);
        } finally {
            if (!success) {
                mFailedAttachAttempts++;
                if (shouldAttach && mFailedAttachAttempts <= 6) {
                    mHandler.postDelayed(this, mDelay);
                } else if (shouldAttach) {
                    shouldAttach = false;
                    Logger.warn("Overlay attachment stopped after repeated window-token failures");
                }
            }
        }
    }

    private boolean resolveApplicationPanelToken() {
        if (mLayoutParams.type != WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
                || mLayoutParams.token != null) {
            return true;
        }
        Context context = mView.getContext();
        if (!(context instanceof Activity)) {
            return false;
        }
        Activity activity = (Activity) context;
        if (activity.isFinishing() || activity.isDestroyed()) {
            shouldAttach = false;
            return true;
        }
        mLayoutParams.token = activity.getWindow().getDecorView().getWindowToken();
        return mLayoutParams.token != null;
    }

    public interface OnOverlayAttachedListener {
        void onOverlayAttached();
    }
}
