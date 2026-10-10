package com.example.backend.global.config;

import com.example.backend.global.socket.ChatSocketHandler;
import com.example.backend.global.socket.SocketHub;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.*;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.util.Map;

@Configuration @EnableWebSocket @RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    private final ChatSocketHandler handler;
    private final SocketHub hub;
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/chat")
            .setAllowedOrigins("http://localhost:5173", "http://127.0.0.1:5173", "http://localhost:8081")
            .addInterceptors(new HandshakeInterceptor() {
                public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Map<String, Object> attrs) {
                    if (!(request instanceof ServletServerHttpRequest servlet) || request.getPrincipal() == null) return false;
                    HttpSession session = servlet.getServletRequest().getSession(false);
                    if (session == null) return false;
                    attrs.put("httpSession", session);
                    attrs.put("httpSessionId", session.getId());
                    return true;
                }
                public void afterHandshake(ServerHttpRequest req, ServerHttpResponse res, WebSocketHandler handler, Exception ex) {}
            });
    }
    @Bean
    public ServletListenerRegistrationBean<HttpSessionListener> socketSessionListener() {
        return new ServletListenerRegistrationBean<>(new HttpSessionListener() {
            @Override public void sessionDestroyed(HttpSessionEvent event) { hub.closeHttpSession(event.getSession().getId()); }
        });
    }
}
