package com.pyatkin.net_6_socks.session;

import com.pyatkin.net_6_socks.rules.RuleManager;
import com.pyatkin.net_6_socks.traffic.TrafficSegmenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Handles a single SOCKS5 session.
 * Implements SOCKS5 handshake, request parsing, and traffic relaying.
 */
public class Socks5Session {
    private static final Logger log = LoggerFactory.getLogger(Socks5Session.class);

    private static final int SOCKS_VERSION = 0x05;
    private static final byte CMD_CONNECT = 0x01;
    private static final byte ATYP_IPV4 = 0x01;
    private static final byte ATYP_DOMAIN = 0x03;
    private static final byte ATYP_IPV6 = 0x04;

    // SOCKS5 reply codes
    private static final byte REPLY_SUCCESS = 0x00;
    private static final byte REPLY_GENERAL_FAILURE = 0x01;
    private static final byte REPLY_CONNECTION_NOT_ALLOWED = 0x02;
    private static final byte REPLY_NETWORK_UNREACHABLE = 0x03;
    private static final byte REPLY_HOST_UNREACHABLE = 0x04;
    private static final byte REPLY_CONNECTION_REFUSED = 0x05;
    private static final byte REPLY_TTL_EXPIRED = 0x06;
    private static final byte REPLY_COMMAND_NOT_SUPPORTED = 0x07;
    private static final byte REPLY_ADDRESS_TYPE_NOT_SUPPORTED = 0x08;

    private final Socket client;
    private final RuleManager rules;
    private final TrafficSegmenter segmenter;
    private final String defaultStrategy;
    private final String upstreamHost;
    private final int upstreamPort;
    private final SessionStats stats;

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
        this.stats = new SessionStats();
    }

    /**
     * Main session handling method.
     */
    public void handle() {
        String clientAddr = client.getRemoteSocketAddress().toString();
        log.info("Starting SOCKS5 session for client: {}", clientAddr);

        try (Socket c = client;
             InputStream cin = c.getInputStream();
             OutputStream cout = c.getOutputStream()) {

            // SOCKS5 handshake
            performHandshake(cin, cout);

            // Parse connection request
            SocksRequest request = parseRequest(cin);
            if (request == null) {
                log.error("Failed to parse SOCKS5 request from {}", clientAddr);
                sendReply(cout, REPLY_GENERAL_FAILURE, null, 0);
                return;
            }

            if (request.cmd != CMD_CONNECT) {
                log.warn("Unsupported SOCKS5 command: 0x{} from {}",
                        Integer.toHexString(request.cmd), clientAddr);
                sendReply(cout, REPLY_COMMAND_NOT_SUPPORTED, null, 0);
                return;
            }

            String targetHost = request.host;
            int targetPort = request.port;

            log.info("Connection request: {}:{} from {}", targetHost, targetPort, clientAddr);

            // Determine strategy based on rules
            String matchedRule = rules.firstMatch(targetHost);
            String strategy = determineStrategy(matchedRule);

            log.info("Applying strategy '{}' for {}:{} (matched rule: {})",
                    strategy, targetHost, targetPort, matchedRule != null ? matchedRule : "none");

            // Handle blacklist
            if ("blacklist".equals(strategy)) {
                log.warn("Connection blocked by blacklist: {}:{}", targetHost, targetPort);
                sendReply(cout, REPLY_CONNECTION_NOT_ALLOWED, null, 0);
                logSession(targetHost, targetPort, strategy, 0, 0);
                return;
            }

            // Establish remote connection
            Socket remote = null;
            try {
                remote = createRemoteSocket(strategy, targetHost, targetPort);
                log.info("Connected to remote: {}", remote.getRemoteSocketAddress());

                sendReply(cout, REPLY_SUCCESS, remote.getLocalAddress(), remote.getLocalPort());

                // Relay traffic
                relayTraffic(cin, cout, remote, strategy, matchedRule);

                stats.markEnd();
                logSession(targetHost, targetPort, strategy,
                        stats.getBytesFromClient(), stats.getBytesFromServer());

            } catch (UnknownHostException e) {
                log.error("Unknown host: {}", targetHost);
                sendReply(cout, REPLY_HOST_UNREACHABLE, null, 0);
            } catch (ConnectException e) {
                log.error("Connection refused: {}:{}", targetHost, targetPort);
                sendReply(cout, REPLY_CONNECTION_REFUSED, null, 0);
            } catch (IOException e) {
                log.error("Failed to connect to {}:{} - {}", targetHost, targetPort, e.getMessage());
                sendReply(cout, REPLY_GENERAL_FAILURE, null, 0);
            } finally {
                if (remote != null && !remote.isClosed()) {
                    try {
                        remote.close();
                    } catch (IOException e) {
                        log.debug("Error closing remote socket: {}", e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            log.error("Session error for {}: {}", clientAddr, e.getMessage());
        } finally {
            log.info("Session ended for client: {} - {}", clientAddr, stats);
        }
    }

    /**
     * Relays traffic between client and remote server.
     */
    private void relayTraffic(InputStream cin, OutputStream cout, Socket remote,
                              String strategy, String matchedRule) throws IOException {

        try (InputStream rin = remote.getInputStream();
             OutputStream rout = remote.getOutputStream()) {

            CountDownLatch latch = new CountDownLatch(2);

            // Client -> Server thread
            Thread clientToServer = new Thread(() -> {
                try {
                    boolean shouldSegment = shouldApplySegmentation(strategy, matchedRule);

                    if (shouldSegment) {
                        log.debug("Applying traffic segmentation");
                        long bytes = segmenter.segmentedCopy(cin, rout, 0);
                        stats.addClientBytes(bytes);
                        // Continue with normal copy for remaining data
                        bytes = segmenter.copyStream(cin, rout, bytes);
                        stats.addClientBytes(bytes - stats.getBytesFromClient());
                    } else {
                        long bytes = segmenter.copyStream(cin, rout, 0);
                        stats.addClientBytes(bytes);
                    }
                } catch (IOException e) {
                    log.trace("Client->Server relay ended: {}", e.getMessage());
                } finally {
                    closeQuietly(rout);
                    latch.countDown();
                }
            }, "ClientToServer");

            // Server -> Client thread
            Thread serverToClient = new Thread(() -> {
                try {
                    long bytes = segmenter.copyStream(rin, cout, 0);
                    stats.addServerBytes(bytes);
                } catch (IOException e) {
                    log.trace("Server->Client relay ended: {}", e.getMessage());
                } finally {
                    closeQuietly(cout);
                    latch.countDown();
                }
            }, "ServerToClient");

            clientToServer.start();
            serverToClient.start();

            // Wait for both relay threads to finish (with timeout)
            try {
                boolean finished = latch.await(5, TimeUnit.MINUTES);
                if (!finished) {
                    log.warn("Relay timeout - interrupting threads");
                    clientToServer.interrupt();
                    serverToClient.interrupt();
                }
            } catch (InterruptedException e) {
                log.warn("Relay interrupted");
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Creates socket for remote connection based on strategy.
     */
    private Socket createRemoteSocket(String strategy, String host, int port) throws IOException {
        if ("redirect".equals(strategy)) {
            log.debug("Redirecting to upstream proxy: {}:{}", upstreamHost, upstreamPort);
            return new Socket(upstreamHost, upstreamPort);
        }

        log.debug("Direct connection to: {}:{}", host, port);
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 10000); // 10 second timeout
        return socket;
    }

    /**
     * Determines if traffic segmentation should be applied.
     */
    private boolean shouldApplySegmentation(String strategy, String matchedRule) {
        return "segment".equals(strategy) ||
                (matchedRule == null && "segment".equals(defaultStrategy));
    }

    /**
     * Determines strategy based on matched rule.
     */
    private String determineStrategy(String matchedRule) {
        if ("blacklist".equals(matchedRule)) return "blacklist";
        if ("whitelist".equals(matchedRule)) return "direct";
        if ("redirect".equals(matchedRule)) return "redirect";
        if ("segment".equals(matchedRule)) return "segment";
        return defaultStrategy;
    }

    /**
     * Logs session information.
     */
    private void logSession(String host, int port, String strategy, long bytesSent, long bytesReceived) {
        log.info("[SESSION] {}:{} -> strategy={}, bytesSent={}, bytesReceived={}, duration={}ms",
                host, port, strategy, bytesSent, bytesReceived, stats.getDurationMs());
    }

    /**
     * Performs SOCKS5 handshake.
     */
    private void performHandshake(InputStream in, OutputStream out) throws IOException {
        // Client sends: [VER(1), NMETHODS(1), METHODS(1-255)]
        byte[] header = new byte[2];
        if (in.read(header) != 2) {
            throw new IOException("Failed to read handshake header");
        }

        int version = header[0] & 0xff;
        if (version != SOCKS_VERSION) {
            throw new IOException("Unsupported SOCKS version: " + version);
        }

        int nMethods = header[1] & 0xff;
        if (nMethods < 1) {
            throw new IOException("No authentication methods provided");
        }

        // Read and discard authentication methods
        byte[] methods = new byte[nMethods];
        if (in.read(methods) != nMethods) {
            throw new IOException("Failed to read authentication methods");
        }

        // Server responds: [VER(1), METHOD(1)]
        // We only support NO_AUTHENTICATION (0x00)
        out.write(new byte[]{SOCKS_VERSION, 0x00});
        out.flush();

        log.debug("SOCKS5 handshake completed");
    }

    /**
     * Parses SOCKS5 connection request.
     */
    private SocksRequest parseRequest(InputStream in) throws IOException {
        // Request: [VER(1), CMD(1), RSV(1), ATYP(1), DST.ADDR(var), DST.PORT(2)]
        byte[] header = new byte[4];
        if (in.read(header) != 4) {
            log.error("Failed to read request header");
            return null;
        }

        int version = header[0] & 0xff;
        if (version != SOCKS_VERSION) {
            log.error("Invalid SOCKS version in request: {}", version);
            return null;
        }

        SocksRequest request = new SocksRequest();
        request.cmd = header[1];
        byte addrType = header[3];

        // Parse destination address
        if (addrType == ATYP_IPV4) {
            byte[] addr = new byte[4];
            if (in.read(addr) != 4) {
                log.error("Failed to read IPv4 address");
                return null;
            }
            request.host = String.format("%d.%d.%d.%d",
                    addr[0] & 0xff, addr[1] & 0xff, addr[2] & 0xff, addr[3] & 0xff);

        } else if (addrType == ATYP_DOMAIN) {
            int len = in.read();
            if (len < 1) {
                log.error("Invalid domain length");
                return null;
            }

            byte[] domain = new byte[len];
            if (in.read(domain) != len) {
                log.error("Failed to read domain name");
                return null;
            }
            request.host = new String(domain);

        } else if (addrType == ATYP_IPV6) {
            // IPv6 support
            byte[] addr = new byte[16];
            if (in.read(addr) != 16) {
                log.error("Failed to read IPv6 address");
                return null;
            }
            try {
                request.host = InetAddress.getByAddress(addr).getHostAddress();
            } catch (UnknownHostException e) {
                log.error("Invalid IPv6 address");
                return null;
            }
        } else {
            log.error("Unsupported address type: 0x{}", Integer.toHexString(addrType));
            return null;
        }

        // Parse destination port
        byte[] portBytes = new byte[2];
        if (in.read(portBytes) != 2) {
            log.error("Failed to read port");
            return null;
        }
        request.port = ((portBytes[0] & 0xff) << 8) | (portBytes[1] & 0xff);

        log.debug("Parsed request: cmd=0x{}, host={}, port={}",
                Integer.toHexString(request.cmd), request.host, request.port);

        return request;
    }

    /**
     * Sends SOCKS5 reply to client.
     */
    private void sendReply(OutputStream out, byte replyCode, InetAddress bindAddr, int bindPort)
            throws IOException {

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        buffer.write(SOCKS_VERSION);  // VER
        buffer.write(replyCode);       // REP
        buffer.write(0x00);            // RSV (reserved)

        // BND.ADDR and BND.PORT
        if (bindAddr == null) {
            buffer.write(ATYP_IPV4);   // ATYP
            buffer.write(new byte[]{0, 0, 0, 0}); // 0.0.0.0
        } else {
            byte[] addr = bindAddr.getAddress();
            if (addr.length == 4) {
                buffer.write(ATYP_IPV4);
            } else {
                buffer.write(ATYP_IPV6);
            }
            buffer.write(addr);
        }

        buffer.write((bindPort >> 8) & 0xff);
        buffer.write(bindPort & 0xff);

        out.write(buffer.toByteArray());
        out.flush();

        log.debug("Sent reply: code=0x{}", Integer.toHexString(replyCode));
    }

    /**
     * Closes stream quietly without throwing exceptions.
     */
    private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * SOCKS5 request data structure.
     */
    public static class SocksRequest {
        public int cmd;
        public String host;
        public int port;
    }
}