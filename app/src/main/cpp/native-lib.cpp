#include <jni.h>
#include <android/bitmap.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <opencv2/opencv.hpp>
#include <android/log.h>
#include "opencv2/imgproc/types_c.h"

#define DEFAULT_CARD_WIDTH 640
#define DEFAULT_CARD_HEIGHT 400
#define FIX_IDCARD_SIZE Size(DEFAULT_CARD_WIDTH,DEFAULT_CARD_HEIGHT)

extern "C" {

using namespace cv;
using namespace std;

extern JNIEXPORT void JNICALL Java_org_opencv_android_Utils_nBitmapToMat2
        (JNIEnv *env, jclass, jobject bitmap, jlong m_addr, jboolean needUnPremultiplyAlpha);
extern JNIEXPORT void JNICALL Java_org_opencv_android_Utils_nMatToBitmap
        (JNIEnv *env, jclass, jlong m_addr, jobject bitmap);

jobject createBitmap(JNIEnv *env, Mat srcData, jobject config) {
    // Image Details
    int imgWidth = srcData.cols;
    int imgHeight = srcData.rows;
    // int numPix = imgWidth * imgHeight;
    jclass bmpCls = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmapMid = env->GetStaticMethodID(
            bmpCls,
            "createBitmap",
            "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;"
    );
    jobject jBmpObj = env->CallStaticObjectMethod(
            bmpCls,
            createBitmapMid,
            imgWidth,
            imgHeight,
            config
    );
    Java_org_opencv_android_Utils_nMatToBitmap(env, nullptr, (jlong) &srcData, jBmpObj);
    // mat2Bitmap(env, srcData, jBmpObj);
    return jBmpObj;
}

void addRect(Mat dst) {
    // 轮廓检测
    vector<vector<Point> > contours;
    vector<Rect> rects;
    findContours(dst, contours, RETR_TREE, CHAIN_APPROX_SIMPLE, Point(0, 0));
    for (auto &contour: contours) {
        Rect rect = boundingRect(contour);
        rectangle(dst, rect, Scalar(0, 255, 0));  // 在dst 图片上显示 rect 矩形
    }
}


JNIEXPORT jobject JNICALL
Java_com_example_idcard2_ImageProcess_getIdNumber(JNIEnv *env, jclass type, jobject src,
                                                  jobject config) {
    Mat src_img;
    Mat dst_img;
    //imshow("src_", src_img);
    //讲bitmap转换为Mat型格式数据
    Java_org_opencv_android_Utils_nBitmapToMat2(env, type, src, (jlong) &src_img, 0);

    Mat dst;
    //无损压缩//640*400
    resize(src_img, src_img, FIX_IDCARD_SIZE);
    //imshow("dst", src_img);
    //灰度化
    cvtColor(src_img, dst, COLOR_BGR2GRAY);
    //imshow("gray", dst);

    //二值化
    threshold(dst, dst, 100, 255, CV_THRESH_OTSU);//CV_THRESH_BINARY);
    //imshow("threshold", dst);

    //加水膨胀，发酵
    Mat erodeElement = getStructuringElement(MORPH_RECT, Size(20, 10));
    erode(dst, dst, erodeElement);
    //imshow("erode", dst);

    ////轮廓检测 // arraylist
    vector<vector<Point> > contours;
    vector<Rect> rects;

    findContours(dst, contours, RETR_TREE, CHAIN_APPROX_SIMPLE, Point(0, 0));

    for (auto &contour: contours) {
        Rect rect = boundingRect(contour);
        //rectangle(dst, rect, Scalar(0, 0, 255));  // 在dst 图片上显示 rect 矩形
        if (rect.width > rect.height * 9) {
            rects.push_back(rect);
            rectangle(dst, rect, Scalar(0, 255, 255));
            dst_img = src_img(rect);
        }
    }
    // imshow("轮廓检测", dst);

    if (rects.size() == 1) {
        Rect rect = rects.at(0);
        dst_img = src_img(rect);
    } else {
        int lowPoint = 0;
        Rect finalRect;
        for (const auto &rect: rects) {
            Point();
            if (rect.tl().y > lowPoint) {
                lowPoint = rect.tl().y;
                finalRect = rect;
            }
        }
        rectangle(dst, finalRect, Scalar(255, 255, 0));
        //imshow("contours", dst);
        dst_img = src_img(finalRect);
    }

    jobject bitmap = createBitmap(env, dst_img, config);

    src_img.release();
    dst_img.release();
    dst.release();

    return bitmap;

//    if (!dst_img.empty()) {
//        imshow("target", dst_img);
//    }
}

JNIEXPORT jobject JNICALL
Java_com_example_idcard2_ImageProcess_startProcess(JNIEnv *env, jclass type, jobject src,
                                                   jobject config, int step, int showRect) {
    Mat src_img;
    //bitmap转换为Mat型格式数据
    Java_org_opencv_android_Utils_nBitmapToMat2(env, type, src, (jlong) &src_img, 0);

    Mat dst;
    //无损压缩//640*400
    resize(src_img, src_img, FIX_IDCARD_SIZE);
    if (step >= 1) {
        //灰度化
        cvtColor(src_img, dst, COLOR_BGR2GRAY);
    }
    if (step >= 2) {
        //二值化
        threshold(dst, dst, 100, 255, CV_THRESH_BINARY);
        if (showRect == 1 && step == 2) {
            addRect(dst);
        }
    }
    if (step >= 3) {
        //加水膨胀，发酵
        Mat erodeElement = getStructuringElement(MORPH_RECT, Size(20, 10));
        erode(dst, dst, erodeElement);
        if (showRect == 1) {
            addRect(dst);
        }
    }
    jobject bitmap = createBitmap(env, dst, config);
    src_img.release();
    dst.release();
    return bitmap;
}
}
