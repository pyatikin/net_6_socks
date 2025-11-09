package com.pyatkin.net_6_socks.traffic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TrafficSegmenter {
    private static final Logger log = LoggerFactory.getLogger(TrafficSegmenter.class);

    private final int segmentBlockSize;
    private final int segmentSize;
    private final int segmentDelayMs;

    public TrafficSegmenter() {
        this(200, 50, 30);
    }

    public TrafficSegmenter(int segmentBlockSize, int segmentSize, int segmentDelayMs) {
        if (segmentBlockSize <= 0) {
            throw new IllegalArgumentException("segmentBlockSize must be positive");
        }
        if (segmentSize <= 0 || segmentSize > segmentBlockSize) {
            throw new IllegalArgumentException("segmentSize must be positive and <= blockSize");
        }
        if (segmentDelayMs < 0) {
            throw new IllegalArgumentException("segmentDelayMs cannot be negative");
        }

        this.segmentBlockSize = segmentBlockSize;
        this.segmentSize = segmentSize;
        this.segmentDelayMs = segmentDelayMs;

        log.debug("TrafficSegmenter initialized: blockSize={}, segmentSize={}, delayMs={}",
                segmentBlockSize, segmentSize, segmentDelayMs);
    }

    public long segmentedCopy(InputStream in, OutputStream out, long bytesTransferred) throws IOException {
        byte[] buffer = new byte[segmentBlockSize];
        long totalBytes = bytesTransferred;

        // Read up to blockSize bytes
        int bytesRead = in.read(buffer, 0, segmentBlockSize);
        if (bytesRead <= 0) {
            return totalBytes;
        }

        log.debug("Segmenting {} bytes into segments of {} bytes with {}ms delay",
                bytesRead, segmentSize, segmentDelayMs);

        int offset = 0;
        int segmentCount = 0;

        while (offset < bytesRead) {
            int chunkSize = Math.min(segmentSize, bytesRead - offset);

            try {
                out.write(buffer, offset, chunkSize);
                out.flush();

                offset += chunkSize;
                totalBytes += chunkSize;
                segmentCount++;

                // Add delay between segments (except after the last one)
                if (offset < bytesRead && segmentDelayMs > 0) {
                    Thread.sleep(segmentDelayMs);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Segmentation interrupted");
                throw new IOException("Segmentation interrupted", e);
            } catch (IOException e) {
                log.error("Error writing segment {} at offset {}: {}", segmentCount, offset, e.getMessage());
                throw e;
            }
        }

        log.debug("Segmentation complete: {} bytes in {} segments", bytesRead, segmentCount);
        return totalBytes;
    }

    public long copyStream(InputStream in, OutputStream out, long bytesTransferred) throws IOException {
        byte[] buffer = new byte[8192];
        long totalBytes = bytesTransferred;
        int bytesRead;

        try {
            while ((bytesRead = in.read(buffer)) > 0) {
                out.write(buffer, 0, bytesRead);
                out.flush();
                totalBytes += bytesRead;
            }
        } catch (IOException e) {
            // Connection closed or error - this is normal for proxy connections
            log.trace("Stream copy ended: {}", e.getMessage());
        }

        return totalBytes;
    }

    public int getSegmentBlockSize() {
        return segmentBlockSize;
    }

    public int getSegmentSize() {
        return segmentSize;
    }

    public int getSegmentDelayMs() {
        return segmentDelayMs;
    }
}