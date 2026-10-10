package com.example.backend.global.socket;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component @RequiredArgsConstructor @Slf4j
public class SocketHub {
    private final ObjectMapper json;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    public void add(WebSocketSession session) {
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, 5000, 128 * 1024));
    }
    public void remove(String id) { sessions.remove(id); }
    public boolean authenticated(WebSocketSession socket) {
        try {
            HttpSession http = (HttpSession) socket.getAttributes().get("httpSession");
            Object value = http == null ? null : http.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            return value instanceof SecurityContext context && context.getAuthentication() != null
                && context.getAuthentication().isAuthenticated() && socket.getPrincipal() != null
                && context.getAuthentication().getName().equals(socket.getPrincipal().getName());
        } catch (IllegalStateException expired) { return false; }
    }
    public void reply(WebSocketSession socket, String type, Object data) {
        WebSocketSession target = sessions.get(socket.getId());
        if (target == null) return;
        try {
            if (!authenticated(target)) { target.close(CloseStatus.POLICY_VIOLATION); return; }
            target.sendMessage(new TextMessage(json.writeValueAsString(new Envelope(type, data))));
        } catch (Exception error) {
            log.debug("WebSocket delivery failed: {}", target.getId(), error);
            remove(target.getId());
            try { target.close(CloseStatus.SERVER_ERROR); } catch (IOException ignored) {}
        }
    }
    @TransactionalEventListener
    public void committed(RealtimeEvent event) {
        sessions.values().forEach(socket -> {
            if (socket.getPrincipal() != null && event.recipients().contains(socket.getPrincipal().getName())) reply(socket, event.type(), event.data());
        });
    }
    public void closeHttpSession(String sessionId) {
        sessions.values().forEach(socket -> {
            if (sessionId.equals(socket.getAttributes().get("httpSessionId"))) {
                try { socket.close(CloseStatus.POLICY_VIOLATION); } catch (IOException ignored) {}
                remove(socket.getId());
            }
        });
    }
    public record Envelope(String type, Object data) {}
}
