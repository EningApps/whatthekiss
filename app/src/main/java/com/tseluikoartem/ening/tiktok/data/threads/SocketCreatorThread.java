package com.tseluikoartem.ening.tiktok.data.threads;

import android.os.HandlerThread;
import com.tseluikoartem.ening.tiktok.data.KissEventMessageSender;
import com.tseluikoartem.ening.tiktok.data.OnKissEventListener;
import com.tseluikoartem.ening.tiktok.data.handlers.OutputThreadHandler;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class SocketCreatorThread extends HandlerThread {

    private static Socket socket;

    private OnKissEventListener kissEventListener;

    private OutputThreadHandler outputThreadHandler;

    public SocketCreatorThread(String name, OnKissEventListener kissEventListener) {
        super(name);
        this.kissEventListener = kissEventListener;
    }

    public void onNewValues(float width, float height) {
        outputThreadHandler.sendNewFaceValues(width, height);
    }

    private void createSocketsConnections(){
        try {
            socket = new Socket("10.168.1.84", 3345);
            final DataInputStream inputStream = new DataInputStream(socket.getInputStream());
            final DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

            final ServerOutputThread outputThread = new ServerOutputThread(outputStream);
            final ServerReaderThread inputThread = new ServerReaderThread(inputStream, kissEventListener);
            outputThread.start();
            inputThread.start();
            outputThreadHandler = new OutputThreadHandler(outputThread.getLooper(), outputThread);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Runnable createSocketConenctionRunnable = new Runnable() {
        @Override
        public void run() {
            createSocketsConnections();
        }
    };
}
