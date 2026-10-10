package com.example.backend.chat.dto;
import com.example.backend.member.dto.MemberResponse;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class ChatDtos {
    private ChatDtos() {}
    public record Direct(@NotNull @Positive Long memberId) {}
    public record Group(@NotBlank @Size(max = 80) String name, @NotNull @Size(min = 2, max = 49) List<@NotNull @Positive Long> memberIds) {}
    public record Send(@NotNull @Positive Long roomId, @NotBlank @Size(max = 2000) String content,
        @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String clientId) {}
    public record Read(@NotNull @Positive Long messageId) {}
    public record Message(Long id, Long roomId, MemberResponse sender, String content, String clientId, Instant createdAt) {}
    public record Room(Long id, String type, String name, List<MemberResponse> members, Message lastMessage, long unreadCount) {}
    public record Voice(String url, String token, String roomName) {}
}
