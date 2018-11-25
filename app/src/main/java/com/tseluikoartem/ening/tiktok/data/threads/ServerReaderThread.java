package com.tseluikoartem.ening.tiktok.data.threads;

import android.os.HandlerThread;
import com.tseluikoartem.ening.tiktok.data.OnKissEventListener;

import java.io.DataInputStream;
import java.io.IOException;

public class ServerReaderThread extends HandlerThread {

    private DataInputStream inputStream;
    private OnKissEventListener kissEventListener;

    public ServerReaderThread(DataInputStream inputStream, OnKissEventListener kissEventListener) {
        super("ServerReaderThread");
        this.inputStream = inputStream;
        this.kissEventListener = kissEventListener;
    }

    @Override
    public void run() {
        try {
            while (true) {
                String in = inputStream.readUTF();
                if (in.equals("KISS")) {
                    kissEventListener.onEvent(OnKissEventListener.EVENT_TYPE.KISS_EVENT);
                } else {
                    kissEventListener.onEvent(OnKissEventListener.EVENT_TYPE.SMILE_EVENT);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
