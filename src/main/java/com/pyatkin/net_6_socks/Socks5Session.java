package com.pyatkin.net_6_socks;

import java.io.*;
import java.net.*;
import java.util.concurrent.CountDownLatch;

public class Socks5Session {

    private final Socket client;
    private final RuleManager rules;
    private final TrafficSegmenter segmenter;
    private final String defaultStrategy;
    private final String upstreamHost;
    private final int upstreamPort;

    public Socks5Session(Socket client,
                         RuleManager rules,
                         TrafficSegmenter segmenter,
                         String defaultStrategy,
                         String upstreamHost,
                         int upstreamPort) {
        this.client = client;
        this.rules = rules;
        this.segmenter = segmenter;
        this.defaultStrategy = defaultStrategy;
        this.upstreamHost = upstreamHost;
        this.upstreamPort = upstreamPort;
    }

    public void handle() {
        try (Socket c = client;
             InputStream cin = c.getInputStream();
             OutputStream cout = c.getOutputStream()) {

            doHandshake(cin, cout);

            SocksRequest req = parseRequest(cin);
            if (req == null || req.cmd != 0x01) {
                sendReply(cout, (byte)0x07, null, 0); // unsupported command
                return;
            }

            String targetHost = req.host;
            int targetPort = req.port;

            String matched = rules.firstMatch(targetHost);
            String strategy = determineStrategy(matched);

            if ("blacklist".equals(strategy)) {
                sendReply(cout, (byte)0x02, null, 0);
                logRequest(targetHost, targetPort, strategy, 0, 0);
                return;
            }

            try (Socket remote = createRemoteSocket(strategy, targetHost, targetPort);
                 InputStream rin = remote.getInputStream();
                 OutputStream rout = remote.getOutputStream()) {

                sendReply(cout, (byte)0x00, remote.getLocalAddress(), remote.getLocalPort());

                RelayStats stats = new RelayStats();
                CountDownLatch latch = new CountDownLatch(2);

                // Поток клиент -> сервер
                Thread t1 = new Thread(() -> {
                    try {
                        if (shouldSegment(strategy, matched)) {
                            segmenter.segmentedCopy(cin, rout, stats);
                            // передаем оставшийся поток обычным копированием
                            segmenter.copyStream(cin, rout, stats, true);
                        } else {
                            segmenter.copyStream(cin, rout, stats, true);
                        }
                    } catch (IOException ignored) {}
                    finally { latch.countDown(); }
                });

                // Поток сервер -> клиент
                Thread t2 = new Thread(() -> {
                    try { segmenter.copyStream(rin, cout, stats, false); }
                    catch (IOException ignored) {}
                    finally { latch.countDown(); }
                });

                t1.start();
                t2.start();
                latch.await();

                logRequest(targetHost, targetPort, strategy, stats.bytesFromClient, stats.bytesFromServer);

            } catch (IOException e) {
                sendReply(cout, (byte)0x05, null, 0);
            }

        } catch (Exception e) {
            System.err.println("Session error: " + e.getMessage());
        }
    }

    private Socket createRemoteSocket(String strategy, String host, int port) throws IOException {
        if ("redirect".equals(strategy)) return new Socket(upstreamHost, upstreamPort);
        return new Socket(host, port);
    }

    private boolean shouldSegment(String strategy, String matched) {
        return "segment".equals(strategy) || (matched == null && "segment".equals(defaultStrategy));
    }

    private String determineStrategy(String matched) {
        if ("blacklist".equals(matched)) return "blacklist";
        if ("whitelist".equals(matched)) return "direct";
        if ("redirect".equals(matched)) return "redirect";
        if ("segment".equals(matched)) return "segment";
        return defaultStrategy;
    }

    private void logRequest(String host, int port, String strategy, long bytesFromClient, long bytesFromServer) {
        System.out.printf("[SESSION] %s:%d -> strategy=%s, bytesSent=%d, bytesReceived=%d%n",
                host, port, strategy, bytesFromClient, bytesFromServer);
    }

    // Stub-методы: нужно реализовать handshake, request parsing и отправку reply
    private void doHandshake(InputStream in, OutputStream out) throws IOException {
        // минимальный SOCKS5 handshake: клиент отправляет [VER, NMETHODS, METHODS]
        byte[] header = new byte[2];
        if (in.read(header) != 2) throw new IOException("Handshake failed");
        int nMethods = header[1];
        in.skip(nMethods); // пропускаем методы
        out.write(new byte[]{0x05, 0x00}); // вер 5, без аутентификации
        out.flush();
    }

    private SocksRequest parseRequest(InputStream in) throws IOException {
        byte[] header = new byte[4];
        if (in.read(header) != 4) return null;
        SocksRequest req = new SocksRequest();
        req.cmd = header[1];

        byte addrType = header[3];
        if (addrType == 0x01) { // IPv4
            byte[] addr = new byte[4];
            if (in.read(addr) != 4) return null;
            req.host = String.format("%d.%d.%d.%d", addr[0] & 0xff, addr[1] & 0xff, addr[2] & 0xff, addr[3] & 0xff);
        } else if (addrType == 0x03) { // domain
            int len = in.read();
            byte[] domain = new byte[len];
            if (in.read(domain) != len) return null;
            req.host = new String(domain);
        } else {
            return null; // unsupported
        }

        byte[] portBytes = new byte[2];
        if (in.read(portBytes) != 2) return null;
        req.port = ((portBytes[0] & 0xff) << 8) | (portBytes[1] & 0xff);

        return req;
    }

    private void sendReply(OutputStream out, byte rep, InetAddress bindAddr, int bindPort) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.write(0x05); // ver
        buffer.write(rep);  // reply
        buffer.write(0x00); // reserved

        if (bindAddr == null) {
            buffer.write(0x01); // IPv4
            buffer.write(new byte[]{0,0,0,0});
        } else {
            buffer.write(0x01);
            buffer.write(bindAddr.getAddress());
        }

        buffer.write((bindPort >> 8) & 0xff);
        buffer.write(bindPort & 0xff);
        out.write(buffer.toByteArray());
        out.flush();
    }

    public static class SocksRequest {
        public int cmd;
        public String host;
        public int port;
    }

    public static class RelayStats {
        public long bytesFromClient = 0;
        public long bytesFromServer = 0;
    }
}