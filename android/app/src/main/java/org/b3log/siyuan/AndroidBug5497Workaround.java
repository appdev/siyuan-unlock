package org.b3log.siyuan;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import com.blankj.utilcode.util.BarUtils;

/**
 * Android small window mode soft keyboard black occlusion <a href="https://github.com/siyuan-note/siyuan-android/pull/7">siyuan-note/siyuan-android#7</a>
 *
 * @author <a href="https://issuetracker.google.com/issues/36911528#comment100">al...@tutanota.com</a>
 * @author <a href="https://github.com/Zuoqiu-Yingyi">Yingyi</a>
 * @version 1.0.0.0, Nov 24, 2023
 * @since 2.11.0
 */
public class AndroidBug5497Workaround {

    public static void assistActivity(Activity activity) {
        new AndroidBug5497Workaround(activity);
    }

    private int windowMode = 0;
    private boolean resize = false;
    private int usableHeight = 0;
    private int rootViewHeight = 0;
    private final Activity activity;
    private final FrameLayout frameLayout;
    private final View view;
    private final FrameLayout.LayoutParams frameLayoutParams;

    private AndroidBug5497Workaround(Activity activity) {
        this.activity = activity;
        this.frameLayout = (FrameLayout) this.activity.findViewById(android.R.id.content);
        this.view = this.frameLayout.getChildAt(0);
        this.frameLayout.getViewTreeObserver().addOnGlobalLayoutListener(this::possiblyResizeChildOfContent);
        this.frameLayoutParams = (FrameLayout.LayoutParams) (this.view.getLayoutParams());
    }

    private void possiblyResizeChildOfContent() {
        final int usableHeight = this.computeUsableHeight();
        final int rootViewHeight = this.getRootViewHeight();
        // logInfo();
        if (usableHeight != this.usableHeight || rootViewHeight != this.rootViewHeight) {
            this.resize = false;

            if (this.activity.isInMultiWindowMode()) {
                // Mult-window
                this.resize = true;
                this.windowMode = 100;
                this.frameLayoutParams.height = -1;
            } else {
                // Full-window
                this.windowMode = 000;
                this.frameLayoutParams.height = -1;
            }

            this.view.requestLayout();
            this.usableHeight = usableHeight;
            this.rootViewHeight = rootViewHeight;
        } else if (this.resize) {
            switch (this.windowMode) {
                case 100:
                    if (this.frameLayoutParams.height != -1) {
                        this.frameLayoutParams.height = -1;
                        this.view.requestLayout();
                    } else {
                        this.resize = false;
                    }
                    break;
            }
        }
    }

    private void logInfo() {
        final Rect rect = this.getVisibleRect();
        Log.d("5497", "rect.top: " + rect.top + ", rect.bottom: " + rect.bottom + ", rect.height(): " + rect.height() + ", rect.width(): " + rect.width());

        Log.d("5497", "view.top: " + this.view.getTop() + ", view.bottom: " + this.view.getBottom() + ", view.height(): " + this.view.getHeight() + ", view.width(): " + this.view.getWidth());

        final int rootViewHeight = this.getRootViewHeight();
        final int rootViewWidth = this.getRootViewWidth();
        Log.d("5497", "rootViewHeight: " + rootViewHeight + ", rootViewWidth: " + rootViewWidth);

        final DisplayMetrics display = this.getDisplayMetrics();
        Log.d("5497", "display.heightPixels: " + display.heightPixels + ", display.widthPixels: " + display.widthPixels);

        Log.d("5497", "frameLayoutParams.height: " + frameLayoutParams.height);

        final int navigationBarHeight = this.getNavigationBarHeight();
        Log.d("5497", "navigationBarHeight: " + navigationBarHeight);

        Log.d("5497", "StatusBarHeight: " + BarUtils.getStatusBarHeight());
        Log.d("5497", "NavBarHeight: " + BarUtils.getNavBarHeight());
    }

    private int computeUsableHeight() {
        final Rect rect = getVisibleRect();
        return rect.height();
    }

    private Rect getVisibleRect() {
        final Rect rect = new Rect();
        this.view.getWindowVisibleDisplayFrame(rect);
        return rect;
    }

    private int getRootViewHeight() {
        return this.view.getRootView().getHeight();
    }

    private int getRootViewWidth() {
        return this.view.getRootView().getWidth();
    }

    private DisplayMetrics getDisplayMetrics() {
        final DisplayMetrics displayMetrics = new DisplayMetrics();
        this.activity.getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
        return displayMetrics;
    }

    @SuppressLint({"DiscouragedApi", "InternalInsetResource"})
    private int getNavigationBarHeight() {
        final Context context = this.view.getContext();
        final boolean hasMenuKey = ViewConfiguration.get(context).hasPermanentMenuKey();
        if (!hasMenuKey) {
            final int resourceId = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            return resourceId > 0
                    ? context.getResources().getDimensionPixelSize(resourceId)
                    : 0;
        } else {
            return 0;
        }
    }
}