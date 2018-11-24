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

    public void sendValues(float[] values) {
        try {

            String stringUTF = new StringBuilder()
                    .append(values[0]).append(" ")
                    .append(values[1]).append(" ")
                    .append(values[2]).append(" ")
                    .append(values[3]).append(" ")
                    .toString();

            System.out.println("SENDIN : " + stringUTF);
            outputStream.writeUTF(stringUTF);
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
