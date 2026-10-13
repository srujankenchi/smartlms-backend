package com.smartlms.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlms.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native WebSocket Handler for SmartLMS Class Connect real-time messaging.
 * Broadcasts new messages, emoji reactions, pins, and read receipts instantly
 * to all authenticated connected clients.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    // Thread-safe collection of active WebSocket sessions
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = extractToken(session.getUri());
        if (token != null) {
            try {
                String username = jwtService.extractUsername(token);
                if (username != null && !username.isBlank()) {
                    session.getAttributes().put("userEmail", username);
                    sessions.add(session);
                    log.info("WebSocket client connected: session={}, user={}", session.getId(), username);

                    // Send connection acknowledgment
                    sendMessageToSession(session, Map.of(
                            "type", "CONNECTED",
                            "user", username,
                            "activeClients", sessions.size()
                    ));
                    return;
                }
            } catch (Exception e) {
                log.warn("Invalid JWT in WebSocket handshake: {}", e.getMessage());
            }
        }
        log.warn("Unauthorized WebSocket handshake attempt. Closing session: {}", session.getId());
        session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized: Valid JWT required"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        // Respond to heartbeat/ping
        if (payload.contains("\"PING\"") || "ping".equalsIgnoreCase(payload.trim())) {
            sendMessageToSession(session, Map.of("type", "PONG", "timestamp", System.currentTimeMillis()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        log.info("WebSocket client disconnected: session={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
        sessions.remove(session);
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException ignored) {}
    }

    /**
     * Broadcast an event to all connected sessions.
     */
    public void broadcast(String eventType, Object data) {
        if (sessions.isEmpty()) return;

        Map<String, Object> event = Map.of(
                "type", eventType,
                "data", data,
                "timestamp", System.currentTimeMillis()
        );

        try {
            String json = objectMapper.writeValueAsString(event);
            TextMessage textMessage = new TextMessage(json);

            for (WebSocketSession s : sessions) {
                if (s.isOpen()) {
                    try {
                        synchronized (s) {
                            s.sendMessage(textMessage);
                        }
                    } catch (IOException e) {
                        log.warn("Failed to send WebSocket message to {}: {}", s.getId(), e.getMessage());
                        sessions.remove(s);
                    }
                } else {
                    sessions.remove(s);
                }
            }
        } catch (Exception e) {
            log.error("Failed to serialize WebSocket event: {}", e.getMessage(), e);
        }
    }

    private void sendMessageToSession(WebSocketSession session, Object payload) {
        if (session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(payload);
                synchronized (session) {
                    session.sendMessage(new TextMessage(json));
                }
            } catch (IOException e) {
                log.warn("Could not send message to session {}: {}", session.getId(), e.getMessage());
            }
        }
    }

    private String extractToken(URI uri) {
        if (uri == null || uri.getQuery() == null) return null;
        for (String param : uri.getQuery().split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2 && ("token".equalsIgnoreCase(pair[0]) || "access_token".equalsIgnoreCase(pair[0]))) {
                return pair[1];
            }
        }
        return null;
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }
}
