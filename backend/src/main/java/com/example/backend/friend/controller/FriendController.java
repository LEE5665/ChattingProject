package com.example.backend.friend.controller;
import com.example.backend.friend.dto.FriendDtos;
import com.example.backend.friend.service.FriendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/friends") @RequiredArgsConstructor
public class FriendController {
    private final FriendService service;
    @GetMapping public List<FriendDtos.Response> list(Authentication auth) { return service.list(auth.getName()); }
    @PostMapping("/requests") public FriendDtos.Response request(Authentication auth, @Valid @RequestBody FriendDtos.Request request) {
        return service.request(auth.getName(), request.email());
    }
    @PostMapping("/requests/{id}/accept") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void accept(Authentication auth, @PathVariable Long id) { service.decide(auth.getName(), id, true); }
    @PostMapping("/requests/{id}/reject") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void reject(Authentication auth, @PathVariable Long id) { service.decide(auth.getName(), id, false); }
}
