package com.tseluikoartem.ening.tiktok;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import android.view.WindowManager;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.opentok.android.*;
import com.tseluikoartem.ening.tiktok.data.OnKissEventListener;
import com.tseluikoartem.ening.tiktok.data.threads.SocketCreatorThread;
import com.tseluikoartem.ening.tiktok.data.handlers.SocketThreadHandler;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import static com.opentok.android.Publisher.CameraCaptureFrameRate.FPS_30;

@SuppressLint({"WrongConstant"})
public class MyVideoCapturer extends BaseVideoCapturer implements Camera.PreviewCallback, BaseVideoCapturer.CaptureSwitch {
    private static final OtLog.LogToken log = new OtLog.LogToken("[camera]", false);
    private int cameraIndex = 0;
    private Camera camera;
    private Camera.CameraInfo currentDeviceInfo = null;
    public ReentrantLock previewBufferLock = new ReentrantLock();
    private static final int PIXEL_FORMAT = 17;
    PixelFormat pixelFormat = new PixelFormat();
    private boolean isCaptureStarted = false;
    private boolean isCaptureRunning = false;
    private final int numCaptureBuffers = 3;
    private int expectedFrameSize = 0;
    private int captureWidth = -1;
    private int captureHeight = -1;
    private int[] captureFpsRange;
    private Display currentDisplay;
    private SurfaceTexture surfaceTexture;
    private Context context;
    int currentRotation;
    private Publisher publisher;
    private boolean blackFrames = false;
    private boolean isCapturePaused = false;
    private Publisher.CameraCaptureResolution preferredResolution = Publisher.CameraCaptureResolution.MEDIUM;
    private Publisher.CameraCaptureFrameRate preferredFramerate = FPS_30;
    int fps = 1;
    int width = 0;
    int height = 0;
    int[] frame;
    Handler handler = new Handler();
    private Thread mProcessingThread;
    private FrameProcessingRunnable mFrameProcessor;
    private Map<byte[], ByteBuffer> mBytesToByteBuffer = new HashMap<>();

    private SocketThreadHandler socketThreadHandler;
    private OnKissEventListener kissEventListener;

    Runnable newFrame = new Runnable() {
        public void run() {
            if (MyVideoCapturer.this.isCaptureRunning) {
                if (MyVideoCapturer.this.frame == null) {
                    new VideoUtils.Size();
                    VideoUtils.Size resolution = MyVideoCapturer.this.getPreferredResolution();
                    MyVideoCapturer.this.fps = MyVideoCapturer.this.getPreferredFramerate();
                    MyVideoCapturer.this.width = resolution.width;
                    MyVideoCapturer.this.height = resolution.height;
                    MyVideoCapturer.this.frame = new int[MyVideoCapturer.this.width * MyVideoCapturer.this.height];
                }

                MyVideoCapturer.this.provideIntArrayFrame(MyVideoCapturer.this.frame, 2, MyVideoCapturer.this.width, MyVideoCapturer.this.height, 0, false);
                MyVideoCapturer.this.handler.postDelayed(MyVideoCapturer.this.newFrame, (long) (1000 / MyVideoCapturer.this.fps));
            }
        }
    };

    public MyVideoCapturer(Context context, OnKissEventListener kissEventListener, Publisher.CameraCaptureResolution resolution, Publisher.CameraCaptureFrameRate fps) {
        this.cameraIndex = getCameraIndexUsingFront(true);
        WindowManager windowManager = (WindowManager) context.getSystemService("window");
        this.currentDisplay = windowManager.getDefaultDisplay();
        this.preferredFramerate = fps;
        this.preferredResolution = resolution;
        this.context = context;
        this.kissEventListener = kissEventListener;
    }

    public synchronized void init() {
        log.w("init() enetered", new Object[0]);

        try {
            this.camera = Camera.open(this.cameraIndex);
        } catch (RuntimeException var2) {
            log.e(var2, "The camera is in use by another app", new Object[0]);
            // this.publisher.onCameraFailed(var2);
        }

        WindowManager windowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int degrees = 0;
        int rotation = windowManager.getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
            default:
        }

        this.currentDeviceInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(this.cameraIndex, this.currentDeviceInfo);
        log.w("init() exit", new Object[0]);

        int angle;
        int displayAngle;
        if (currentDeviceInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            angle = (currentDeviceInfo.orientation + degrees) % 360;
            displayAngle = (360 - angle) % 360; // compensate for it being mirrored
        } else {  // back-facing
            angle = (currentDeviceInfo.orientation - degrees + 360) % 360;
            displayAngle = angle;
        }

        // This corresponds to the rotation constants in {@link Frame}.
        currentRotation = angle / 90;


        FaceDetector detector = new FaceDetector.Builder((context))
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();
        detector.setProcessor(
                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory()).build());

        if (!detector.isOperational()) {
            Log.w("FACE_TRASH", "Face detector dependencies are not yet available.");
        }

        mFrameProcessor = new FrameProcessingRunnable(detector);
        mProcessingThread = new Thread(mFrameProcessor);
        mFrameProcessor.setActive(true);
        mProcessingThread.start();

        final SocketCreatorThread socketCreatorThread = new SocketCreatorThread("SocketCreatorThread!", kissEventListener);
        socketCreatorThread.start();
        socketThreadHandler = new SocketThreadHandler(socketCreatorThread);
        socketThreadHandler.sendCreateConnection();
    }

    public synchronized int startCapture() {
        log.w("started() entered", new Object[0]);
        if (this.isCaptureStarted) {
            return -1;
        } else {
            if (this.camera != null) {
                VideoUtils.Size resolution = this.getPreferredResolution();
                if (null == this.configureCaptureSize(resolution.width, resolution.height)) {
                    return -1;
                }

                Camera.Parameters parameters = this.camera.getParameters();
                parameters.setPreviewSize(this.captureWidth, this.captureHeight);
                parameters.setPreviewFormat(17);
                parameters.setPreviewFpsRange(this.captureFpsRange[0], this.captureFpsRange[1]);

                try {
                    this.camera.setParameters(parameters);
                } catch (RuntimeException var7) {
                    log.e(var7, "Camera.setParameters(parameters) - failed", new Object[0]);
                    //  this.publisher.onCameraFailed(var7);
                    return -1;
                }

                int bitsPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.NV21);
                long sizeInBits = this.captureWidth * this.captureHeight * bitsPerPixel;
                int bufferSize = (int) Math.ceil(sizeInBits / 8.0d) + 1;

                for (int i = 0; i < 4; ++i) {
                    byte[] byteArray = new byte[bufferSize];
                    ByteBuffer buffer = ByteBuffer.wrap(byteArray);
                    mBytesToByteBuffer.put(byteArray, buffer);
                    this.camera.addCallbackBuffer(byteArray);
                }

                try {
                    this.surfaceTexture = new SurfaceTexture(42);
                    this.camera.setPreviewTexture(this.surfaceTexture);
                } catch (Exception var6) {
                    // this.publisher.onCameraFailed(var6);
                    var6.printStackTrace();
                    return -1;
                }

                this.camera.setPreviewCallbackWithBuffer(this);
                this.camera.startPreview();
                this.previewBufferLock.lock();
                this.expectedFrameSize = bufferSize;
                this.previewBufferLock.unlock();
            } else {
                this.blackFrames = true;
                this.handler.postDelayed(this.newFrame, (long) (1000 / this.fps));
            }

            this.isCaptureRunning = true;
            this.isCaptureStarted = true;
            log.w("started() exit", new Object[0]);
            return 0;
        }
    }

    public synchronized int stopCapture() {
        if (this.camera != null) {
            this.previewBufferLock.lock();

            try {
                if (this.isCaptureRunning) {
                    this.isCaptureRunning = false;
                    this.camera.stopPreview();
                    this.camera.setPreviewCallbackWithBuffer((Camera.PreviewCallback) null);
                    this.camera.release();
                    log.d("Camera capture is stopped", new Object[0]);
                }
            } catch (RuntimeException var2) {
                log.e(var2, "Camera.stopPreview() - failed ", new Object[0]);
                // this.publisher.onCameraFailed(var2);
                return -1;
            }

            this.previewBufferLock.unlock();
        }

        this.isCaptureStarted = false;
        if (this.blackFrames) {
            this.handler.removeCallbacks(this.newFrame);
        }

        mBytesToByteBuffer.clear();

        return 0;
    }

    public void destroy() {
        mFrameProcessor.release();
    }

    public boolean isCaptureStarted() {
        return this.isCaptureStarted;
    }

    public CaptureSettings getCaptureSettings() {
        CaptureSettings settings = new CaptureSettings();
        VideoUtils.Size resolution = this.getPreferredResolution();
        int framerate = this.getPreferredFramerate();
        if (null != this.camera && null != this.configureCaptureSize(resolution.width, resolution.height)) {
            settings.fps = framerate;
            settings.width = resolution.width;
            settings.height = resolution.height;
            settings.format = 1;
            settings.expectedDelay = 0;
        } else {
            settings.fps = framerate;
            settings.width = resolution.width;
            settings.height = resolution.height;
            settings.format = 2;
        }

        return settings;
    }

    public synchronized void onPause() {
        if (this.isCaptureStarted) {
            this.isCapturePaused = true;
            this.stopCapture();
        }

    }

    public void onResume() {
        if (this.isCapturePaused) {
            this.init();
            this.startCapture();
            this.isCapturePaused = false;
        }

    }

    private int getNaturalCameraOrientation() {
        return this.currentDeviceInfo != null ? this.currentDeviceInfo.orientation : 0;
    }

    public boolean isFrontCamera() {
        if (this.currentDeviceInfo != null) {
            return this.currentDeviceInfo.facing == 1;
        } else {
            return false;
        }
    }

    public int getCameraIndex() {
        return this.cameraIndex;
    }

    public synchronized void cycleCamera() {
        this.swapCamera((this.getCameraIndex() + 1) % Camera.getNumberOfCameras());
    }

    @SuppressLint({"DefaultLocale"})
    public synchronized void swapCamera(int index) {
        boolean wasStarted = this.isCaptureStarted;
        if (this.camera != null) {
            this.stopCapture();
            this.camera.release();
            this.camera = null;
        }

        this.cameraIndex = index;
        if (wasStarted) {
            if (-1 != Build.MODEL.toLowerCase().indexOf("samsung")) {
                this.forceCameraRelease(index);
            }

            this.camera = Camera.open(index);
            this.currentDeviceInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(index, this.currentDeviceInfo);
            this.startCapture();
        }

    }

    private int compensateCameraRotation(int uiRotation) {
        int currentDeviceOrientation = 0;
        switch (uiRotation) {
            case 0:
                currentDeviceOrientation = 0;
                break;
            case 1:
                currentDeviceOrientation = 270;
                break;
            case 2:
                currentDeviceOrientation = 180;
                break;
            case 3:
                currentDeviceOrientation = 90;
        }

        int cameraOrientation = this.getNaturalCameraOrientation();
        int cameraRotation = roundRotation(currentDeviceOrientation);
        boolean usingFrontCamera = this.isFrontCamera();
        int totalCameraRotation;
        if (usingFrontCamera) {
            int inverseCameraRotation = (360 - cameraRotation) % 360;
            totalCameraRotation = (inverseCameraRotation + cameraOrientation) % 360;
        } else {
            totalCameraRotation = (cameraRotation + cameraOrientation) % 360;
        }

        return totalCameraRotation;
    }

    private static int roundRotation(int rotation) {
        return (int) (Math.round((double) rotation / 90.0D) * 90L) % 360;
    }

    private static int getCameraIndexUsingFront(boolean front) {
        for (int i = 0; i < Camera.getNumberOfCameras(); ++i) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (front && info.facing == 1) {
                return i;
            }

            if (!front && info.facing == 0) {
                return i;
            }
        }

        return 0;
    }

    public void onPreviewFrame(byte[] data, Camera camera) {
        this.previewBufferLock.lock();
        if (this.isCaptureRunning && data.length == this.expectedFrameSize) {
            int rotation = this.compensateCameraRotation(this.currentDisplay.getRotation());
            this.provideByteArrayFrame(data, 1, this.captureWidth, this.captureHeight, rotation, this.isFrontCamera());
            mFrameProcessor.setNextFrame(data, camera);
        }

        this.previewBufferLock.unlock();
    }

    public void setPublisher(Publisher publisher) {
        this.publisher = publisher;
    }

    private boolean forceCameraRelease(int cameraIndex) {
        Camera camera = null;

        boolean var4;
        try {
            camera = Camera.open(cameraIndex);
            return false;
        } catch (RuntimeException var8) {
            var4 = true;
        } finally {
            if (camera != null) {
                camera.release();
            }

        }

        return var4;
    }

    private VideoUtils.Size getPreferredResolution() {
        VideoUtils.Size resolution = new VideoUtils.Size();
        switch (this.preferredResolution) {
            case LOW:
                resolution.width = 352;
                resolution.height = 288;
                break;
            case MEDIUM:
                resolution.width = 640;
                resolution.height = 480;
                break;
            case HIGH:
                resolution.width = 1280;
                resolution.height = 720;
        }

        return resolution;
    }

    private int getPreferredFramerate() {
        int framerate = 0;
        switch (this.preferredFramerate) {
            case FPS_30:
                framerate = 30;
                break;
            case FPS_15:
                framerate = 15;
                break;
            case FPS_7:
                framerate = 7;
                break;
            case FPS_1:
                framerate = 1;
        }

        return framerate;
    }

    private VideoUtils.Size configureCaptureSize(int preferredWidth, int preferredHeight) {
        List<Camera.Size> sizes = null;
        VideoUtils.Size retSize = null;
        int preferredFramerate = this.getPreferredFramerate();

        try {
            Camera.Parameters parameters = this.camera.getParameters();
            sizes = parameters.getSupportedPreviewSizes();
            this.captureFpsRange = this.findClosestEnclosingFpsRange(preferredFramerate * 1000, parameters.getSupportedPreviewFpsRange());
            int maxw = 0;
            int maxh = 0;

            for (int i = 0; i < sizes.size(); ++i) {
                android.hardware.Camera.Size size = (android.hardware.Camera.Size) sizes.get(i);
                if (size.width >= maxw && size.height >= maxh && size.width <= preferredWidth && size.height <= preferredHeight) {
                    maxw = size.width;
                    maxh = size.height;
                }
            }

            if (maxw == 0 || maxh == 0) {
                android.hardware.Camera.Size size = (android.hardware.Camera.Size) sizes.get(0);
                int minw = size.width;
                int minh = size.height;

                for (int i = 1; i < sizes.size(); ++i) {
                    size = (android.hardware.Camera.Size) sizes.get(i);
                    if (size.width <= minw && size.height <= minh) {
                        minw = size.width;
                        minh = size.height;
                    }
                }

                maxw = minw;
                maxh = minh;
            }

            this.captureWidth = maxw;
            this.captureHeight = maxh;
            retSize = new VideoUtils.Size(maxw, maxh);
            return retSize;
        } catch (RuntimeException var16) {
            log.e(var16, "Error configuring capture size", new Object[0]);
            // MyVideoCapturer.this.publisher.onCameraFailed(var16);
            return retSize;
        } finally {
            ;
        }
    }

    private int[] findClosestEnclosingFpsRange(final int preferredFps, List<int[]> supportedFpsRanges) {
        if (supportedFpsRanges != null && supportedFpsRanges.size() != 0) {

            int[] closestRange = (int[]) Collections.min(supportedFpsRanges, new Comparator<int[]>() {
                private static final int MAX_FPS_DIFF_THRESHOLD = 5000;
                private static final int MAX_FPS_LOW_DIFF_WEIGHT = 1;
                private static final int MAX_FPS_HIGH_DIFF_WEIGHT = 3;
                private static final int MIN_FPS_THRESHOLD = 8000;
                private static final int MIN_FPS_LOW_VALUE_WEIGHT = 1;
                private static final int MIN_FPS_HIGH_VALUE_WEIGHT = 4;

                private int progressivePenalty(int value, int threshold, int lowWeight, int highWeight) {
                    return value < threshold ? value * lowWeight : threshold * lowWeight + (value - threshold) * highWeight;
                }

                private int diff(int[] range) {
                    int minFpsError = this.progressivePenalty(range[0], 8000, 1, 4);
                    int maxFpsError = this.progressivePenalty(Math.abs(preferredFps * 1000 - range[1]), 5000, 1, 3);
                    return minFpsError + maxFpsError;
                }

                public int compare(int[] lhs, int[] rhs) {
                    return this.diff(lhs) - this.diff(rhs);
                }
            });
            this.checkRangeWithWarning(preferredFps, closestRange);
            return closestRange;
        } else {
            return new int[]{0, 0};
        }
    }

    private void checkRangeWithWarning(int preferredFps, int[] range) {
        if (preferredFps < range[0] || preferredFps > range[1]) {
            log.w("Closest fps range found [%d-%d] for desired fps: %d", new Object[]{range[0] / 1000, range[1] / 1000, preferredFps / 1000});
        }

    }

    private class FrameProcessingRunnable implements Runnable {
        private Detector<?> mDetector;
        private long mStartTimeMillis = SystemClock.elapsedRealtime();

        // This lock guards all of the member variables below.
        private final Object mLock = new Object();
        private boolean mActive = true;

        // These pending variables hold the state associated with the new frame awaiting processing.
        private long mPendingTimeMillis;
        private int mPendingFrameId = 0;
        private ByteBuffer mPendingFrameData;

        FrameProcessingRunnable(Detector<?> detector) {
            mDetector = detector;
        }

        /**
         * Releases the underlying receiver.  This is only safe to do after the associated thread
         * has completed, which is managed in camera source's release method above.
         */
        @SuppressLint("Assert")
        void release() {
            assert (mProcessingThread.getState() == Thread.State.TERMINATED);
            mDetector.release();
            mDetector = null;
        }

        /**
         * Marks the runnable as active/not active.  Signals any blocked threads to continue.
         */
        void setActive(boolean active) {
            synchronized (mLock) {
                mActive = active;
                mLock.notifyAll();
            }
        }

        /**
         * Sets the frame data received from the camera.  This adds the previous unused frame buffer
         * (if present) back to the camera, and keeps a pending reference to the frame data for
         * future use.
         */
        void setNextFrame(byte[] data, Camera camera) {
            synchronized (mLock) {
                if (mPendingFrameData != null) {
                    camera.addCallbackBuffer(mPendingFrameData.array());
                    mPendingFrameData = null;
                }

                if (!mBytesToByteBuffer.containsKey(data)) {

                    return;
                }

                // Timestamp and frame ID are maintained here, which will give downstream code some
                // idea of the timing of frames received and when frames were dropped along the way.
                mPendingTimeMillis = SystemClock.elapsedRealtime() - mStartTimeMillis;
                mPendingFrameId++;
                mPendingFrameData = mBytesToByteBuffer.get(data);

                // Notify the processor thread if it is waiting on the next frame (see below).
                mLock.notifyAll();
            }
        }

        /**
         * As long as the processing thread is active, this executes detection on frames
         * continuously.  The next pending frame is either immediately available or hasn't been
         * received yet.  Once it is available, we transfer the frame info to local variables and
         * run detection on that frame.  It immediately loops back for the next frame without
         * pausing.
         * <p/>
         * If detection takes longer than the time in between new frames from the camera, this will
         * mean that this loop will run without ever waiting on a frame, avoiding any context
         * switching or frame acquisition time latency.
         * <p/>
         * If you find that this is using more CPU than you'd like, you should probably decrease the
         * FPS setting above to allow for some idle time in between frames.
         */
        @Override
        public void run() {
            Frame outputFrame;
            ByteBuffer data;

            while (true) {
                synchronized (mLock) {
                    while (mActive && (mPendingFrameData == null)) {
                        try {
                            // Wait for the next frame to be received from the camera, since we
                            // don't have it yet.
                            mLock.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }

                    if (!mActive) {
                        // Exit the loop once this camera source is stopped or released.  We check
                        // this here, immediately after the wait() above, to handle the case where
                        // setActive(false) had been called, triggering the termination of this
                        // loop.
                        return;
                    }

                    outputFrame = new Frame.Builder()
                            .setImageData(mPendingFrameData, MyVideoCapturer.this.captureWidth,
                                    MyVideoCapturer.this.captureHeight, ImageFormat.NV21)
                            .setId(mPendingFrameId)
                            .setTimestampMillis(mPendingTimeMillis)
                            .setRotation(currentRotation)
                            .build();

                    // Hold onto the frame data locally, so that we can use this for detection
                    // below.  We need to clear mPendingFrameData to ensure that this buffer isn't
                    // recycled back to the camera before we are done using that data.
                    data = mPendingFrameData;
                    mPendingFrameData = null;
                }

                // The code below needs to run outside of synchronization, because this will allow
                // the camera to add pending frame(s) while we are running detection on the current
                // frame.
                try {
                    mDetector.receiveFrame(outputFrame);
                } catch (Throwable t) {
                } finally {
                    MyVideoCapturer.this.camera.addCallbackBuffer(data.array());
                }
            }
        }
    }

    /**
     * Face tracker for each detected individual.
     */
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker();
        }
    }


    private class GraphicFaceTracker extends Tracker<Face> {

        private static final String TAG = "FACE_LOX";

        @Override
        public void onNewItem(int faceId, Face item) {
            Log.d(TAG, "onNewItem ");
        }

        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            socketThreadHandler.sendNewFaceValues(face.getWidth(), face.getHeight());
            System.out.println(TAG + " ONUPDATE : height = " + face.getHeight() + " width = " + face.getWidth() + "landmarks = " + face.getLandmarks());
        }

        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            Log.d(TAG, "onMissing ");
        }

        @Override
        public void onDone() {
            Log.d(TAG, "v ");
        }
    }
}
