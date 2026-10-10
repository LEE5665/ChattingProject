package com.example.backend.chat.controller;
import com.example.backend.chat.dto.ChatDtos;
import com.example.backend.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/rooms") @RequiredArgsConstructor
public class ChatController {
    private final ChatService service;
    @GetMapping public List<ChatDtos.Room> rooms(Authentication auth) { return service.rooms(auth.getName()); }
    @PostMapping("/direct") public ChatDtos.Room direct(Authentication auth, @Valid @RequestBody ChatDtos.Direct request) { return service.direct(auth.getName(), request.memberId()); }
    @PostMapping("/group") public ChatDtos.Room group(Authentication auth, @Valid @RequestBody ChatDtos.Group request) { return service.group(auth.getName(), request); }
    @GetMapping("/{id}/messages") public List<ChatDtos.Message> history(Authentication auth, @PathVariable Long id,
        @RequestParam(required = false) Long before, @RequestParam(required = false) Long after) { return service.history(auth.getName(), id, before, after); }
    @PostMapping("/{id}/read") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void read(Authentication auth, @PathVariable Long id, @Valid @RequestBody ChatDtos.Read request) { service.read(auth.getName(), id, request.messageId()); }
}
