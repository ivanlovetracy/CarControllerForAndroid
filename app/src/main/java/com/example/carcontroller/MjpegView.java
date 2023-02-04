package com.example.carcontroller;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.osgi.OpenCVNativeLoader;
import org.opencv.tracking.TrackerCSRT;

import java.io.BufferedInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MjpegView extends View {
    public static final int MODE_ORIGINAL = 0;
    public static final int MODE_FIT_WIDTH = 1;
    public static final int MODE_FIT_HEIGHT = 2;
    public static final int MODE_BEST_FIT = 3;
    public static final int MODE_STRETCH = 4;

    private static final int WAIT_AFTER_READ_IMAGE_ERROR_MSEC = 5000;
    private static final int CHUNK_SIZE = 4096;
    private static final String DEFAULT_BOUNDARY_REGEX = "[_a-zA-Z0-9]*boundary";

    private final String tag = getClass().getSimpleName();
    private final Context context;
    private final Object lockBitmap = new Object();

    private String url;
    private Bitmap lastBitmap;
    private MjpegDownloader downloader;
    private Paint paint;
    private Rect dst;
    private Rect lockRect;
    private int mode = MODE_ORIGINAL;
    private int drawX,drawY, vWidth = -1, vHeight = -1;
    private int lastImgWidth, lastImgHeight;
    private boolean adjustWidth, adjustHeight;
    private int msecWaitAfterReadImageError = WAIT_AFTER_READ_IMAGE_ERROR_MSEC;
    private boolean isRecycleBitmap;
    private boolean isUserForceConfigRecycle;
    private boolean isSupportPinchZoomAndPan;
    private float lockX1,lockY1,lockX2,lockY2;//适配view后的图像的四个顶点坐标（float）
    private boolean isLocking = false;
    private boolean isTrackerOn = false;
    private Paint rectPaint;
    private TrackerCSRT tracker = null;
    private float widthRate = 0.0F;
    private float heightRate = 0.0F;
    private int lockImgX1, lockImgY1, lockImgX2, lockImgY2;//实际图片的四个顶点坐标（取证）
    private org.opencv.core.Rect trackerRect;

    public MjpegView(Context context){
        super(context);
        this.context = context;
        init();
    }

    public MjpegView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        init();
    }

    private void init(){
//        mjpeg图像paint
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);

//        追踪目标选择矩形框paint
        rectPaint = new Paint();
        rectPaint.setColor(Color.rgb(255, 0, 0));
        rectPaint.setStrokeWidth(5);
        rectPaint.setStyle(Paint.Style.STROKE);

        dst = new Rect(0,0,0,0);

//        追踪目标矩形框
        lockRect = new Rect();

//        加载opencv库并初始化trackerCSRT追踪器
        OpenCVNativeLoader openCVNativeLoader = new OpenCVNativeLoader();
        openCVNativeLoader.init();
        tracker = TrackerCSRT.create();

    }

    public void setUrl(String url){
        this.url = url;
    }

    public void startStream(){
        if(downloader != null && downloader.isRunning()){
            Log.w(tag,"Already started, stop by calling stopStream() first.");
            return;
        }

        downloader = new MjpegDownloader();
        downloader.start();
    }

    public void stopStream(){

        if(downloader != null && downloader.isRunning()) {
            downloader.cancel();
        }

//        trackerRect = null;

    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
        lastImgWidth = -1; // force re-calculate view size
        requestLayout();
    }

    public void setBitmap(Bitmap bm){
        synchronized (lockBitmap) {
            if (lastBitmap != null && isUserForceConfigRecycle && isRecycleBitmap) {
                lastBitmap.recycle();
            }

            lastBitmap = bm;
        }

        if(context instanceof Activity) {
            ((Activity) context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    invalidate();
                    requestLayout();
                }
            });
        }
        else{
            Log.e(tag,"Can not request Canvas's redraw. Context is not an instance of Activity");
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        boolean shouldRecalculateSize;
        synchronized (lockBitmap) {
            shouldRecalculateSize = lastBitmap != null && (lastImgWidth != lastBitmap.getWidth() || lastImgHeight != lastBitmap.getHeight());
            if(shouldRecalculateSize) {
                lastImgWidth = lastBitmap.getWidth();
                lastImgHeight = lastBitmap.getHeight();
            }
        }

        if (shouldRecalculateSize) {
            vWidth = MeasureSpec.getSize(widthMeasureSpec);
            vHeight = MeasureSpec.getSize(heightMeasureSpec);

            if(mode == MODE_ORIGINAL){
                drawX = (vWidth - lastImgWidth)/2;
                drawY = (vHeight - lastImgHeight)/2;

                if(adjustWidth){
                    vWidth = lastImgWidth;
                    drawX = 0;
                }

                if(adjustHeight){
                    vHeight = lastImgHeight;
                    drawY = 0;
                }
            }
            else if(mode == MODE_FIT_WIDTH){
                int newHeight = (int)(((float)lastImgHeight/(float)lastImgWidth)*vWidth);

                drawX = 0;

                if(adjustHeight){
                    vHeight = newHeight;
                    drawY = 0;
                }
                else{
                    drawY = (vHeight - newHeight)/2;
                }

                //no need to check adjustWidth because in this mode image's width is always equals view's width.

                dst.set(drawX,drawY,vWidth,drawY+newHeight);
            }
            else if(mode == MODE_FIT_HEIGHT){
                int newWidth = (int)(((float)lastImgWidth/(float)lastImgHeight)*vHeight);

                drawY = 0;

                if(adjustWidth){
                    vWidth = newWidth;
                    drawX = 0;
                }
                else{
                    drawX = (vWidth - newWidth)/2;
                }

                //no need to check adjustHeight because in this mode image's height is always equals view's height.

                dst.set(drawX,drawY,drawX+newWidth,vHeight);
            }
            else if(mode == MODE_BEST_FIT){
                if((float)lastImgWidth/(float)vWidth > (float)lastImgHeight/(float)vHeight){
                    //duplicated code
                    //fit width
                    int newHeight = (int)(((float)lastImgHeight/(float)lastImgWidth)*vWidth);

                    drawX = 0;

                    if(adjustHeight){
                        vHeight = newHeight;
                        drawY = 0;
                    }
                    else{
                        drawY = (vHeight - newHeight)/2;
                    }

                    //no need to check adjustWidth because in this mode image's width is always equals view's width.

                    dst.set(drawX,drawY,vWidth,drawY+newHeight);
                }
                else{
                    //duplicated code
                    //fit height
                    int newWidth = (int)(((float)lastImgWidth/(float)lastImgHeight)*vHeight);

                    drawY = 0;

                    if(adjustWidth){
                        vWidth = newWidth;
                        drawX = 0;
                    }
                    else{
                        drawX = (vWidth - newWidth)/2;
                    }

                    //no need to check adjustHeight because in this mode image's height is always equals view's height.

                    dst.set(drawX,drawY,drawX+newWidth,vHeight);
                }
            }
            else if(mode == MODE_STRETCH){
                dst.set(0,0,vWidth,vHeight);
                //no need to check neither adjustHeight nor adjustHeight because in this mode image's size is always equals view's size.
            }
        }
        else {
            if(vWidth == -1 || vHeight == -1){
                vWidth = MeasureSpec.getSize(widthMeasureSpec);
                vHeight = MeasureSpec.getSize(heightMeasureSpec);
            }
        }

        setMeasuredDimension(vWidth, vHeight);
    }

    @Override
    protected void onDraw(Canvas c) {
        synchronized (lockBitmap) {
            if (c != null && lastBitmap != null && !lastBitmap.isRecycled()) {
                if (mode != MODE_ORIGINAL) {
                    c.drawBitmap(lastBitmap, null, dst, paint);
                } else {
                    c.drawBitmap(lastBitmap, drawX, drawY, paint);
                }

//                启用追踪且在选定目标时，绘制目标选择框
                if (isTrackerOn && isLocking){
                    c.drawRect(lockRect,rectPaint);
                    isLocking = false;
                }
                
            } else {
                Log.d(tag, "Skip drawing, canvas is null or bitmap is not ready yet");
            }
        }
    }

    public boolean isAdjustWidth() {
        return adjustWidth;
    }

    public void setAdjustWidth(boolean adjustWidth) {
        this.adjustWidth = adjustWidth;
    }

    public boolean isAdjustHeight() {
        return adjustHeight;
    }

    public void setAdjustHeight(boolean adjustHeight) {
        this.adjustHeight = adjustHeight;
    }

    public int getMsecWaitAfterReadImageError() {
        return msecWaitAfterReadImageError;
    }

    public void setMsecWaitAfterReadImageError(int msecWaitAfterReadImageError) {
        this.msecWaitAfterReadImageError = msecWaitAfterReadImageError;
    }

    public boolean isRecycleBitmap() {
        return isRecycleBitmap;
    }

    public void setRecycleBitmap(boolean recycleBitmap) {
        isUserForceConfigRecycle = true;
        isRecycleBitmap = recycleBitmap;
    }

    public boolean getSupportPinchZoomAndPan() {
        return isSupportPinchZoomAndPan;
    }

    public void setSupportPinchZoomAndPan(boolean supportPinchZoomAndPan) {
        isSupportPinchZoomAndPan = supportPinchZoomAndPan;
    }

    private final ScaleGestureDetector.OnScaleGestureListener scaleGestureListener = new ScaleGestureDetector.OnScaleGestureListener() {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scale = detector.getScaleFactor();

            int oldW = dst.right - dst.left;
            int oldH = dst.bottom - dst.top;
            int newW = (int)(oldW * scale);
            int newH = (int)(oldH * scale);

            // TODO: also use appropriate centroid
            int screenCX = getWidth()/2;
            int screenCY = getHeight()/2;

            float CYRatio = (screenCY - dst.top)/(float)oldH;
            float CXRatio = (screenCX - dst.left)/(float)oldW;

            int newTop = (int) (dst.top - (newH - oldH) * CYRatio);
            int newLeft = (int) (dst.left - (newW - oldW) * CXRatio);
            int newBottom =  newTop + newH;
            int newRight = newLeft + newW;

            if (newH >= getHeight()) {
                // never leave a blank space
                if (newTop > 0) {
                    newTop = 0;
                    newBottom = newH;
                } else if (newBottom < getHeight()) {
                    newBottom = getHeight();
                    newTop = newBottom - newH;
                }

                dst.top = newTop;
                dst.bottom = newBottom;
            }

            if (newW >= getWidth()) {
                // never leave a blank space
                if (newLeft > 0) {
                    newLeft = 0;
                    newRight = newW;
                } else if (newRight < getWidth()) {
                    newRight = getWidth();
                    newLeft = newRight - newW;
                }

                dst.left = newLeft;
                dst.right = newRight;
            }

            // force re-render
            invalidate();

            // prevent onTouch to operate when zooming
            isTouchDown = false;

            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {

        }
    };

    private boolean isTouchDown;
    private final PointF touchStart = new PointF();
    private final Rect stateStart = new Rect();
    private final ScaleGestureDetector scaleGestureDetector = new ScaleGestureDetector(getContext(),scaleGestureListener);

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isSupportPinchZoomAndPan) {
//            没有开启追踪时，不做处理，直接返回
            if (!isTrackerOn){
                return false;
            }

//            开启了追踪时
            Log.i("mjpeg","lastBitmap w h:"+lastImgWidth+", "+lastImgHeight);

            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN){

//                选择追踪目标期间，停止获取图像流，在点击屏幕图像时最后获取到的图像作为选取目标源
                stopStream();
                Log.i("mjpegview","stopStream because onTouch to select tracking target");
                lockX1 = event.getX();
                lockY1 = event.getY();

//                屏幕上的图像经过了放大适配屏幕，需要按比例还原在原图像中的坐标
                lockImgX1 = (int)((float)lockX1*widthRate);
                lockImgY1 = (int)((float)lockY1*heightRate);

                Log.i("mjpegview","x1,y1="+lockX1+","+lockY1+" rect x1,y1="+ lockImgX1 +","+lockImgY1);

            } else if (action == MotionEvent.ACTION_MOVE) {
                Log.i("opencv","target is selected");
                lockX2 = event.getX();
                lockY2 = event.getY();

//                屏幕上的图像经过了放大适配屏幕，需要按比例还原在原图像中的坐标
                lockImgX2 = (int)((float)lockX2*widthRate);
                lockImgY2 = (int)((float)lockY2*heightRate);

                Log.i("mjpegview","x2,y2="+lockX2+","+ lockY2 +" lock x2,y2="+ lockImgX2 +","+ lockImgY2);

//                设置目标选择框矩形，用于绘制
                lockRect.set((int) lockX1, (int) lockY1, (int) lockX2, (int) lockY2);
                Log.i("mjpeg","init lockRect:"+lockRect.toString());

//                标识正在选择目标中，ondraw会根据此标识来绘制目标选择框
                isLocking = true;

                invalidate();

            } else if (action == MotionEvent.ACTION_UP) {
                if (tracker == null) {
                    tracker = TrackerCSRT.create();
                }else if(lastBitmap != null) {
                    try {
                        Log.i("mjpeg", "lastBitmap is " + lastBitmap.getWidth() + " " + lastBitmap.getHeight());

                        //                        用屏幕选取出来的目标区域来构建出原图目标追踪框矩形
                        trackerRect = new org.opencv.core.Rect(lockImgX1, lockImgY1, lockImgX2 - lockImgX1, lockImgY2 - lockImgY1);
                        Log.i("opencv", "init trackerRect:" + trackerRect.toString());
                        Mat oriMat = new Mat();
                        Mat mat = new Mat();
                        Utils.bitmapToMat(lastBitmap, oriMat);
//                    Log.i("opencv","mat type:"+mat.type()+" mat channels:"+mat.channels()+" oriMat type:"+oriMat.type()+" oriMat channels:"+oriMat.type());

//                        esp32的图像是BGR的，需要转化为RGB opencv才能追踪
                        Imgproc.cvtColor(oriMat, mat, Imgproc.COLOR_BGR2RGB);
                        Log.i("opencv", "tracker init mat type:" + mat.type() + " mat channels:" + mat.channels() + " oriMat type:" + oriMat.type() + " oriMat channels:" + oriMat.type());

//                        初始化追踪器
                        tracker.init(mat, trackerRect);

                    }catch (Exception e){
                        Log.i("opencv","tracker init exception");
                        Log.i("opencv",e.toString());
                    }

                }

//                重新开始获取图像流
                startStream();
            }
//            return true;
        } else if(event.getPointerCount() == 1) {
            int id = event.getAction();
            if(id == MotionEvent.ACTION_DOWN){
                touchStart.set(event.getX(),event.getY());
                stateStart.set(dst);
                isTouchDown = true;
            }
            else if(id == MotionEvent.ACTION_UP || id == MotionEvent.ACTION_CANCEL){
                isTouchDown = false;
            }
            else if(id == MotionEvent.ACTION_MOVE){
                if(isTouchDown){
                    int offsetLeft = (int) (stateStart.left + event.getX() - touchStart.x);
                    int offsetTop =(int) (stateStart.top + event.getY() - touchStart.y);
                    int w = dst.right - dst.left;
                    int h = dst.bottom - dst.top;

                    // keep image in the frame -- no blank space on every side
                    offsetLeft = Math.min(0,offsetLeft);
                    offsetTop = Math.min(0,offsetTop);
                    offsetLeft = Math.max(getWidth() - w,offsetLeft);
                    offsetTop = Math.max(getHeight() - h,offsetTop);

                    dst.left = offsetLeft;
                    dst.top = offsetTop;
                    dst.right = dst.left + w;
                    dst.bottom = dst.top + h;

                    invalidate();
                }
            }
        } else {
            scaleGestureDetector.onTouchEvent(event);
        }

        return true;
    }

    public void setTrackerOn(boolean b) {
        Log.i("mjpeg","setTrackerOn"+b);
        if (b){
            isTrackerOn = true;
        }else {
            isTrackerOn = false;
            trackerRect = null;//停止追踪
        }
    }

    class MjpegDownloader extends Thread{
        private boolean run = true;

        byte[] currentImageBody = new byte[(int) 1e6];
        int currentImageBodyLength = 0;

        public void cancel(){
            run = false;
        }

        public boolean isRunning(){
            return run;
        }

        @Override
        public void run() {
            while(run) {
                HttpURLConnection connection = null;
                BufferedInputStream bis = null;
                URL serverUrl;

                try {
                    serverUrl = new URL(url);

                    connection = (HttpURLConnection) serverUrl.openConnection();
                    connection.setDoInput(true);
                    connection.connect();

                    String headerBoundary = DEFAULT_BOUNDARY_REGEX;

                    try{
                        // Try to extract a boundary from HTTP header first.
                        // If the information is not presented, throw an exception and use default value instead.
                        String contentType = connection.getHeaderField("Content-Type");

//                        Log.i("contentType" , contentType);

                        if (contentType == null) {
                            throw new Exception("Unable to get content type");
                        }

                        String[] types = contentType.split(";");
                        if (types.length == 0) {
                            throw new Exception("Content type was empty");
                        }

                        String extractedBoundary = null;
                        for (String ct : types) {
                            String trimmedCt = ct.trim();
                            if (trimmedCt.startsWith("boundary=")) {
                                extractedBoundary = trimmedCt.substring(9); // Content after 'boundary='
                            }
                        }

                        if (extractedBoundary == null) {
                            throw new Exception("Unable to find mjpeg boundary.");
                        }

                        headerBoundary = extractedBoundary;

//                        Log.i("extractedBoundary" , extractedBoundary);
                    }
                    catch(Exception e){
                        Log.w(tag,"Cannot extract a boundary string from HTTP header with message: " + e.getMessage() + ". Use a default value instead.");
                    }

                    // determine boundary pattern
                    // use the whole header as separator in case boundary locate in difference chunks
                    Pattern pattern = Pattern.compile("--" + headerBoundary + "\\s+(.*)\\r\\n\\r\\n",Pattern.DOTALL);
                    Matcher matcher;

                    bis = new BufferedInputStream(connection.getInputStream());
                    byte[] read = new byte[CHUNK_SIZE];
                    int readByte, boundaryIndex;
                    String checkHeaderStr, boundary;

                    //always keep reading images from server
                    while (run) {
                        try {
                            readByte = bis.read(read);
                            if (readByte == -1) {
                                break;
                            }


                            addByte(read, 0, readByte, false);
                            checkHeaderStr = new String(currentImageBody, 0, currentImageBodyLength, "ASCII");

//                            Log.i("checkHeaderStr：" , checkHeaderStr);

                            matcher = pattern.matcher(checkHeaderStr);
                            if (matcher.find()) {
                                // delete and re-add because if body contains boundary, it means body is over one image already
                                // we want exact one image content
                                delByte(readByte);

                                boundary = matcher.group(0);
                                boundaryIndex = checkHeaderStr.indexOf(boundary);
                                boundaryIndex -= currentImageBodyLength;

                                if (boundaryIndex > 0) {
                                    addByte(read, 0, boundaryIndex, false);
                                } else {
                                    delByte(boundaryIndex);
                                }

                                Bitmap outputImg = BitmapFactory.decodeByteArray(currentImageBody, 0, currentImageBodyLength);
                                if (outputImg != null) {

                                    if (isTrackerOn){
//                                        如果开启追踪，进行追踪处理

//                                        使用view大小的图像缩放比例
                                        widthRate = (float)outputImg.getWidth()/vWidth;
                                        heightRate = (float)outputImg.getHeight()/vHeight;

                                        Log.i("mjpeg","img rate:"+widthRate+","+heightRate);
//                                    Log.i("mjpeg","width height:"+outputImg.getWidth()+ " " + outputImg.getHeight());
                                        if (tracker!=null && trackerRect != null) {
                                            try {
                                                Log.i("opencv","update tracker");
                                                //追踪代码
//                                            buffer = ByteBuffer.allocateDirect(outputImg.getByteCount());
//                                            outputImg.copyPixelsToBuffer(buffer);
//                                            Mat mat = new Mat(outputImg.getHeight(), outputImg.getWidth(), CvType.CV_8UC4, buffer);
                                                Mat oriMat = new Mat();
                                                Mat mat = new Mat();
                                                Utils.bitmapToMat(outputImg, oriMat);
//                                            Log.i("OpenCV", "mat channels:" + mat.channels() + ", cols:" + mat.cols() + ", rows:" + mat.rows());
//                                                esp32摄像头是BGR，需要转换位为RGB才能用于opencv tracker
                                                Imgproc.cvtColor(oriMat, mat, Imgproc.COLOR_BGR2RGB);
//                                            Log.i("OpenCV", "mat channels:" + mat.channels() + ", cols:" + mat.cols() + ", rows:" + mat.rows());

                                                org.opencv.core.Rect updateRect = new org.opencv.core.Rect(0,0,50,50);
                                                tracker.update(mat, trackerRect);
                                                Log.i("opencv","update trackerRect:"+trackerRect.toString());
                                                lockImgX1 = trackerRect.x;
                                                lockImgY1 = trackerRect.y;
                                                lockImgX2 = lockImgX1 + trackerRect.width;
                                                lockImgY2 = lockImgY1 + trackerRect.height;
//                                            用opencv直接把矩形框画到图像中，不需要额外绘制
//                                            lockRect.set((int) (lockImgX1/widthRate), (int) (lockImgY1/heightRate), (int) (lockImgX2/widthRate), (int) (lockImgY2/heightRate));
                                                Imgproc.rectangle(oriMat,new Point(lockImgX1,lockImgY1),new Point(lockImgX2,lockImgY2),new Scalar(255,0,0,255),1,Imgproc.LINE_4,0);
                                                Log.i("mjpeg","update lockRect:"+lockRect.toString());
//                                                mat经过转化，图像颜色有失真，用没转换过的oriMat来显示，mat仅用来给tracker追踪
                                                Utils.matToBitmap(oriMat, outputImg);
                                            }catch (Exception e){
                                                e.printStackTrace();
                                                Log.i("opencv",e.toString());
                                            }

                                        }
                                    }

                                    if(run) {
                                        newFrame(outputImg);
                                    }
                                } else {
                                    Log.e(tag, "Read image error");
                                }

                                int headerIndex = boundaryIndex + boundary.length();

                                addByte(read, headerIndex, readByte - headerIndex, true);
                            }
                        } catch (Exception e) {
                            if(e.getMessage() != null) {
                                Log.e(tag, e.getMessage());
                            }
                            break;
                        }
                    }

                } catch (Exception e) {
                    if(e.getMessage() != null) {
                        Log.e(tag, e.getMessage());
                    }
                }

                try {
                    bis.close();
                    connection.disconnect();
//                    Log.i(tag,"disconnected with " + url);
                } catch (Exception e) {
                    if(e.getMessage() != null) {
                        Log.e(tag, e.getMessage());
                    }
                }

                if(msecWaitAfterReadImageError > 0) {
                    try {
                        Thread.sleep(msecWaitAfterReadImageError);
                    } catch (InterruptedException e) {
                        if(e.getMessage() != null) {
                            Log.e(tag, e.getMessage());
                        }
                    }
                }
            }
        }

        private void addByte(byte[] src, int srcPos, int length, boolean reset) {
            if (reset) {
                System.arraycopy(src, srcPos, currentImageBody, 0, length);
                currentImageBodyLength = 0;
            } else {
                System.arraycopy(src, srcPos, currentImageBody, currentImageBodyLength, length);
            }
            currentImageBodyLength += length;
        }

        private void delByte(int del) {
            currentImageBodyLength -= del;
        }

        private void newFrame(Bitmap bitmap){
            setBitmap(bitmap);
        }
    }
}
