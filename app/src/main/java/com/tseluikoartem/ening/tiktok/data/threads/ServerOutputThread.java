package com.tseluikoartem.ening.tiktok.data.threads;

import android.os.HandlerThread;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Random;

public class ServerOutputThread extends HandlerThread {

    private DataOutputStream outputStream;
    private float faceWidth;
    private float faceHeight;

    public ServerOutputThread(DataOutputStream outputStream) {
        super("ServerOutputThread");
        this.outputStream = outputStream;
    }

    public void onNewValues(float faceWidth, float faceHeight) {
        this.faceHeight = faceHeight;
        this.faceWidth = faceWidth;
    }


    public void sendValues(float faceWidth, float faceHeight) {
        try {
            System.out.println("SENDIN : " + "w:" + faceWidth + " h:" + faceHeight);
            outputStream.writeUTF("w:" + faceWidth + " h:" + faceHeight);
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
