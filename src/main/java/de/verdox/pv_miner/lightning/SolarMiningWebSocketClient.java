package de.verdox.pv_miner.lightning;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

@Component
public class SolarMiningWebSocketClient extends TextWebSocketHandler {
    private static final Logger LOGGER = Logger.getLogger(SolarMiningWebSocketClient.class.getName());

    private final Environment environment;
    private final LightningWalletService walletService;
    private final List<Runnable> statusListeners = new CopyOnWriteArrayList<>();
    private final String backendWebSocketUrl;
    private final String backendUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebSocketSession session;
    private boolean enabled = true;

    public SolarMiningWebSocketClient(Environment environment, LightningWalletService walletService,
                                      @Value("${solarmining.backend.url.socket:wss://localhost:8080/lightning-tunnel}") String backendWebSocketUrl,
                                      @Value("${solarmining.backend.url.rest:http://localhost:8080}") String backendUrl
    ) {
        this.environment = environment;
        this.walletService = walletService;
        this.backendWebSocketUrl = backendWebSocketUrl;
        this.backendUrl = backendUrl;
        LOGGER.info("[SolarMiningWebSocketClient] Initializing WebSocket Client for backend url: " + this.backendWebSocketUrl);
    }

    @PostConstruct
    public void connect() {
        if (!enabled) {
            notifyStatusChange();
            return;
        }

        try {
            StandardWebSocketClient client = new StandardWebSocketClient();

            if (backendWebSocketUrl.startsWith("wss://") && environment.matchesProfiles("dev")) {
                client.setUserProperties(Map.of(
                        "org.apache.tomcat.websocket.SSL_CONTEXT", createTrustAllSslContext()
                ));
            }

            client.execute(this, backendWebSocketUrl).whenComplete((session, exception) -> {
                if (exception != null) {
                    // Verbindung fehlgeschlagen
                    exception.printStackTrace();
                    notifyStatusChange();
                    scheduleReconnect();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            notifyStatusChange();
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        new Thread(() -> {
            try {
                Thread.sleep(10000);
                if (enabled) {
                    connect();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        this.session = session;
        String myNodeId = walletService.getNodeInfo().nodeId();
        sendTunnelMessage(new TunnelMessage("REGISTER", myNodeId, 0, null));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            TunnelMessage msg = objectMapper.readValue(message.getPayload(), TunnelMessage.class);
            if ("REQUEST_INVOICE".equals(msg.action())) {
                LightningTransaction invoice = walletService.createInvoice(msg.amountSat(), msg.memo(), 43200);
                if (invoice != null) {
                    TunnelMessage response = new TunnelMessage("RESPONSE_INVOICE", msg.payload(), msg.amountSat(), invoice.bolt11());
                    LOGGER.info("REQUEST_INVOICE: "+invoice.bolt11());
                    sendTunnelMessage(response);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        notifyStatusChange();
        if (enabled) {
            connect();
        }
    }

    private void sendTunnelMessage(TunnelMessage msg) {
        try {
            if (session != null && session.isOpen()) {
                String json = objectMapper.writeValueAsString(msg);
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isConnected() {
        return session != null && session.isOpen();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            connect();
        } else {
            disconnect();
            notifyStatusChange();
        }
    }

    private void disconnect() {
        try {
            if (session != null && session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public record TunnelMessage(String action, String payload, long amountSat, String memo) {
    }

    public void addStatusListener(Runnable listener) {
        statusListeners.add(listener);
    }

    public void removeStatusListener(Runnable listener) {
        statusListeners.remove(listener);
    }

    private void notifyStatusChange() {
        statusListeners.forEach(Runnable::run);
    }

    private SSLContext createTrustAllSslContext() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
        };
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        return sc;
    }

    public record ClaimResponse(String lightningAddress, String alias, String nodeId) {}

}