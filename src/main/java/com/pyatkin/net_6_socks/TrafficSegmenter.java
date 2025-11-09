package com.pyatkin.net_6_socks;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TrafficSegmenter {

    private final int segmentBlockSize;
    private final int segmentSize;
    private final int segmentDelayMs;

    public TrafficSegmenter() {
        this.segmentBlockSize = 200; // по умолчанию
        this.segmentSize = 50;
        this.segmentDelayMs = 30;
    }

    public TrafficSegmenter(int segmentBlockSize, int segmentSize, int segmentDelayMs) {
        this.segmentBlockSize = segmentBlockSize;
        this.segmentSize = segmentSize;
        this.segmentDelayMs = segmentDelayMs;
    }

    /**
     * Сегментированная передача данных
     */
    public void segmentedCopy(InputStream in, OutputStream out, Socks5Session.RelayStats stats) throws IOException {
        byte[] buffer = new byte[segmentBlockSize];
        int read = in.read(buffer, 0, segmentBlockSize);
        if (read <= 0) return;

        int offset = 0;
        while (offset < read) {
            int len = Math.min(segmentSize, read - offset);
            out.write(buffer, offset, len);
            out.flush();
            offset += len;
            stats.bytesFromClient += len; // считаем трафик
            try { Thread.sleep(segmentDelayMs); } catch (InterruptedException ignored) {}
        }
    }

    /**
     * Копирование потока без сегментации
     */
    public void copyStream(InputStream in, OutputStream out, Socks5Session.RelayStats stats, boolean fromClient) throws IOException {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
            out.flush();
            if (fromClient) stats.bytesFromClient += n;
            else stats.bytesFromServer += n;
        }
    }
}