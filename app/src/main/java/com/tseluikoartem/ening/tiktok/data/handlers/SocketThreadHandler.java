package com.tseluikoartem.ening.tiktok.data.handlers;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Pair;
import com.tseluikoartem.ening.tiktok.data.threads.SocketCreatorThread;

public class SocketThreadHandler extends Handler {

    public static final int MSG_NEW_VALUES = 1;
    public static final int MSG_SMILE_VALUES = 3;

    private SocketCreatorThread thread;

    public SocketThreadHandler(SocketCreatorThread thread) {
        super(thread.getLooper());
        this.thread = thread;
    }

    public void sendNewFaceValues(float cos) {
        final Message message = Message.obtain(this, MSG_NEW_VALUES, cos);
        sendMessage(message);
    }

    public void sendSmileValues(float value) {
        final Message message = Message.obtain(this, MSG_SMILE_VALUES, value);
        sendMessage(message);
    }

    public void sendCreateConnection() {
        post(thread.createSocketConenctionRunnable);
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_NEW_VALUES:
                final float cos = (float) msg.obj;
                thread.onNewValues(cos);
                break;
            case MSG_SMILE_VALUES:
                final float value = (float) msg.obj;
                thread.onSmileValues(value);
                break;
        }
    }
}