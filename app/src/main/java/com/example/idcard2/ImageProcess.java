package com.example.idcard2;

import android.graphics.Bitmap;

/**
 * Created by xiang on 2017/7/23.
 */

public class ImageProcess {

    static {
        System.loadLibrary("idcard");
    }

    public static native Bitmap getIdNumber(Bitmap src, Bitmap.Config config);

    public static native Bitmap startProcess(Bitmap src, Bitmap.Config config, int step, int showRect);

}
