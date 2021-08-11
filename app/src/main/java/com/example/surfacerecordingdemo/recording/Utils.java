package com.example.surfacerecordingdemo.recording;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.Display;
import android.view.WindowManager;

import java.lang.reflect.Method;

public class Utils {
    public static Size getFullScreenSize(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Point size = new Point();
        Display display = wm.getDefaultDisplay();
        display.getRealSize(size);
        int width = ensureEvenSize(size.x);
        int height = ensureEvenSize(size.y);
        return new Size(width, height);
    }

    public static int ensureEvenSize(int size) {
        if (size % 2 != 0) {
            return size - 1;
        }
        return size;
    }

    private static boolean isTablet(Context c) {
        return (c.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK)
                >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    public static int getRealHeight(Context context) {
        final DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Method mGetRawH = null;

        int realHeight;

        display.getRealMetrics(metrics);
        realHeight = metrics.heightPixels;

        return realHeight;
    }

    public static int getScreenWidth(Context context) {
        if (context != null) {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            Point point = new Point();
            if (wm != null) {
                wm.getDefaultDisplay().getSize(point);
                return point.x;
            }
        }
        return 320;
    }
}
