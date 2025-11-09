package com.pyatkin.net_6_socks.session;

/**
 * Tracks statistics for a SOCKS5 session.
 */
public class SessionStats {
    private long bytesFromClient = 0;
    private long bytesFromServer = 0;
    private final long startTime;
    private long endTime;

    public SessionStats() {
        this.startTime = System.currentTimeMillis();
    }

    public synchronized void addClientBytes(long bytes) {
        bytesFromClient += bytes;
    }

    public synchronized void addServerBytes(long bytes) {
        bytesFromServer += bytes;
    }

    public synchronized long getBytesFromClient() {
        return bytesFromClient;
    }

    public synchronized long getBytesFromServer() {
        return bytesFromServer;
    }

    public void markEnd() {
        this.endTime = System.currentTimeMillis();
    }

    public long getDurationMs() {
        long end = endTime > 0 ? endTime : System.currentTimeMillis();
        return end - startTime;
    }

    @Override
    public String toString() {
        return String.format("SessionStats{sent=%d bytes, received=%d bytes, duration=%d ms}",
                bytesFromClient, bytesFromServer, getDurationMs());
    }
}