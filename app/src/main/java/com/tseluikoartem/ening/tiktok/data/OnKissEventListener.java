package com.tseluikoartem.ening.tiktok.data;

public interface OnKissEventListener {
    void onEvent(EVENT_TYPE eventType);

    enum EVENT_TYPE {
        KISS_EVENT,
        SMILE_EVENT
    }
}
