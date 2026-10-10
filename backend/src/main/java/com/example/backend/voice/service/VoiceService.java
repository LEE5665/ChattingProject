package com.example.backend.voice.service;
import com.example.backend.chat.dto.ChatDtos;
import com.example.backend.chat.service.ChatService;
import com.example.backend.friend.service.FriendService;
import io.livekit.server.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class VoiceService {
    private final ChatService chat;
    private final FriendService friends;
    private final String url, apiKey, apiSecret;
    public VoiceService(ChatService chat, FriendService friends, @Value("${livekit.url}") String url,
        @Value("${livekit.api-key}") String apiKey, @Value("${livekit.api-secret}") String apiSecret) {
        this.chat = chat; this.friends = friends; this.url = url; this.apiKey = apiKey; this.apiSecret = apiSecret;
    }
    public ChatDtos.Voice join(String email, Long roomId) {
        chat.requireParticipant(email, roomId);
        var member = friends.current(email);
        String roomName = "chat-" + roomId;
        AccessToken token = new AccessToken(apiKey, apiSecret);
        token.setIdentity("member-" + member.getId());
        token.setName(member.getNickname());
        token.setTtl(300_000);
        token.addGrants(new RoomJoin(true), new RoomName(roomName), new CanSubscribe(true),
            new CanPublish(true), new CanPublishData(false), new CanPublishSources(List.of("microphone")));
        return new ChatDtos.Voice(url, token.toJwt(), roomName);
    }
}
