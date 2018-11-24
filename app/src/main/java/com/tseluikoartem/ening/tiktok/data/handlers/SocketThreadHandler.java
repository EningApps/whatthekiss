package com.tseluikoartem.ening.tiktok.data.handlers;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Pair;
import com.tseluikoartem.ening.tiktok.data.threads.SocketCreatorThread;

public class SocketThreadHandler extends Handler {

    public static final int MSG_NEW_VALUES = 1;
    public static final int MSG_NEW_CONNECTION = 2;

    private SocketCreatorThread thread;

    public SocketThreadHandler(SocketCreatorThread thread) {
        super(thread.getLooper());
        this.thread = thread;
    }

    public void sendNewFaceValues(float width, float height) {
        final Message message = Message.obtain(this, MSG_NEW_VALUES, new Pair(width, height));
        sendMessage(message);
    }

    public void sendCreateConnection(){
        post(thread.createSocketConenctionRunnable);
    }
    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_NEW_VALUES:
                final Pair<Float, Float> values = (Pair<Float, Float>) msg.obj;
                thread.onNewValues(values.first, values.second);
                break;
        }
    }
}