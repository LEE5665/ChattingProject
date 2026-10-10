package com.example.backend.global.socket;

import com.example.backend.chat.dto.ChatDtos;
import com.example.backend.chat.service.ChatService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;

@Component @RequiredArgsConstructor @Slf4j
public class ChatSocketHandler extends TextWebSocketHandler {
    private final SocketHub hub;
    private final ChatService chat;
    private final Validator validator;
    private final ObjectMapper json;
    @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!hub.authenticated(session)) { session.close(CloseStatus.POLICY_VIOLATION); return; }
        session.setTextMessageSizeLimit(16 * 1024);
        hub.add(session);
        hub.reply(session, "READY", null);
    }
    @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (!hub.authenticated(session)) { session.close(CloseStatus.POLICY_VIOLATION); return; }
        try {
            var payload = json.readTree(message.getPayload());
            String type = payload.path("type").asText();
            if ("PING".equals(type)) { hub.reply(session, "PONG", null); return; }
            if (!"SEND_MESSAGE".equals(type)) throw new IllegalArgumentException("지원하지 않는 요청입니다.");
            ChatDtos.Send request = json.treeToValue(payload.path("data"), ChatDtos.Send.class);
            var violations = validator.validate(request);
            if (!violations.isEmpty()) throw new IllegalArgumentException("채팅방, 메시지 내용과 메시지 ID를 확인해 주세요.");
            var saved = chat.send(session.getPrincipal().getName(), request);
            // An ACK also confirms duplicate sends that did not create another database row.
            hub.reply(session, "ACK", saved);
        } catch (ResponseStatusException error) {
            hub.reply(session, "ERROR", Map.of("message", error.getReason() == null ? "요청을 처리할 수 없습니다." : error.getReason()));
        } catch (IllegalArgumentException error) {
            hub.reply(session, "ERROR", Map.of("message", "메시지 입력값 또는 요청 형식이 올바르지 않습니다."));
        } catch (Exception error) {
            log.warn("Chat send failed", error);
            hub.reply(session, "ERROR", Map.of("message", "메시지를 저장하지 못했습니다. 다시 시도해 주세요."));
        }
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { hub.remove(session.getId()); }
    @Override public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        hub.remove(session.getId()); session.close(CloseStatus.SERVER_ERROR);
    }
}
