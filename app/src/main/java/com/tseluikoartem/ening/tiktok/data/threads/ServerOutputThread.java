package com.tseluikoartem.ening.tiktok.data.threads;

import android.os.HandlerThread;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Random;

public class ServerOutputThread extends HandlerThread {

    private DataOutputStream outputStream;

    public ServerOutputThread(DataOutputStream outputStream) {
        super("ServerOutputThread");
        this.outputStream = outputStream;
    }

    public void sendValues(float cos) {
        try {
            System.out.println("SENDIN : " + cos);
            outputStream.writeUTF(String.valueOf(cos));
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
