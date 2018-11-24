package com.tseluikoartem.ening.tiktok.data.handlers;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Pair;
import com.tseluikoartem.ening.tiktok.data.threads.ServerOutputThread;

public class OutputThreadHandler extends Handler {

    public static final int MSG_NEW_VALUES = 1;

    private ServerOutputThread thread;

    public OutputThreadHandler(Looper looper, ServerOutputThread thread) {
        super(looper);
        this.thread = thread;
    }

    public void sendNewFaceValues(float cos) {
        final Message message = Message.obtain(this, MSG_NEW_VALUES, cos);
        sendMessage(message);
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_NEW_VALUES:
                final float cos = (float) msg.obj;
                thread.sendValues(cos);
                break;
        }
    }
}