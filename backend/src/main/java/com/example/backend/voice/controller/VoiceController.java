package com.example.backend.voice.controller;
import com.example.backend.chat.dto.ChatDtos;
import com.example.backend.voice.service.VoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/rooms") @RequiredArgsConstructor
public class VoiceController {
    private final VoiceService service;
    @PostMapping("/{id}/voice-token")
    public ChatDtos.Voice join(Authentication auth, @PathVariable Long id) { return service.join(auth.getName(), id); }
}
