package com.tseluikoartem.ening.tiktok.data;

import android.os.Handler;
import android.os.Message;

public class KissEventMessageSender extends Handler {

    private OnKissEventListener kissEventListener;

    public KissEventMessageSender(OnKissEventListener kissEventListener) {
        this.kissEventListener = kissEventListener;
    }

    @Override
    public void handleMessage(Message msg) {
        kissEventListener.onKissEvent();
    }
}
