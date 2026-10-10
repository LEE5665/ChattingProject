package com.example.backend.friend.dto;
import com.example.backend.member.dto.MemberResponse;
import jakarta.validation.constraints.*;

public final class FriendDtos {
    private FriendDtos() {}
    public record Request(@NotBlank @Email @Size(max = 254) String email) {}
    public record Response(Long id, MemberResponse member, String status, boolean incoming) {}
}
