package com.mozz.htmlnative.utils;

import android.support.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author Yang Tao, 17/5/8.
 */

public class IOUtils {

    private IOUtils() {

    }

    public static void closeQuietly(@Nullable Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // do nothing
            }
        }
    }
}
