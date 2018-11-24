package com.tseluikoartem.ening.tiktok.data.threads;

import android.os.HandlerThread;
import android.os.Message;
import com.tseluikoartem.ening.tiktok.data.KissEventMessageSender;
import com.tseluikoartem.ening.tiktok.data.OnKissEventListener;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Random;

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
                kissEventListener.onKissEvent();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
