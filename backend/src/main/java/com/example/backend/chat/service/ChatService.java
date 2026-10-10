package com.example.backend.chat.service;

import com.example.backend.chat.dto.ChatDtos;
import com.example.backend.chat.entity.*;
import com.example.backend.chat.repository.*;
import com.example.backend.friend.service.FriendService;
import com.example.backend.global.socket.RealtimeEvent;
import com.example.backend.member.dto.MemberResponse;
import com.example.backend.member.entity.Member;
import com.example.backend.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class ChatService {
    private final ChatRoomRepository rooms;
    private final ChatRoomMemberRepository participants;
    private final ChatMessageRepository messages;
    private final MemberRepository members;
    private final FriendService friends;
    private final ApplicationEventPublisher events;
    public List<ChatDtos.Room> rooms(String email) {
        Member me = friends.current(email);
        return participants.findRooms(me.getId()).stream().map(p -> room(p.getRoom(), me.getId(), p.getLastReadMessageId())).toList();
    }
    @Transactional
    public ChatDtos.Room direct(String email, Long otherId) {
        Member me = friends.current(email);
        friends.lockPair(me.getId(), otherId);
        friends.requireFriends(me.getId(), otherId);
        String key = Math.min(me.getId(), otherId) + ":" + Math.max(me.getId(), otherId);
        ChatRoom room = rooms.findByDirectKey(key).orElse(null);
        if (room == null) {
            Member other = members.findById(otherId).orElseThrow();
            room = rooms.save(new ChatRoom(ChatRoom.Type.DIRECT, null, key, me));
            participants.save(new ChatRoomMember(room, me));
            participants.save(new ChatRoomMember(room, other));
            notifyRoom(room.getId(), "ROOMS_CHANGED", null);
        }
        return room(room, me.getId(), 0L);
    }
    @Transactional
    public ChatDtos.Room group(String email, ChatDtos.Group request) {
        Member me = friends.current(email);
        Set<Long> ids = new LinkedHashSet<>(request.memberIds());
        if (ids.size() != request.memberIds().size() || ids.contains(me.getId()) || ids.size() < 2) {
            throw new IllegalArgumentException("자신을 제외한 친구 두 명 이상을 선택해 주세요.");
        }
        List<Member> selected = ids.stream().map(id -> {
            friends.requireFriends(me.getId(), id);
            return members.findById(id).orElseThrow(() -> new IllegalArgumentException("사용자가 없습니다."));
        }).toList();
        ChatRoom room = rooms.save(new ChatRoom(ChatRoom.Type.GROUP, request.name().trim(), null, me));
        participants.save(new ChatRoomMember(room, me));
        selected.forEach(member -> participants.save(new ChatRoomMember(room, member)));
        notifyRoom(room.getId(), "ROOMS_CHANGED", null);
        return room(room, me.getId(), 0L);
    }
    public void requireParticipant(String email, Long roomId) {
        Member me = friends.current(email);
        if (!participants.existsByRoomIdAndMemberId(roomId, me.getId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 채팅방의 참여자가 아닙니다.");
    }
    public List<ChatDtos.Message> history(String email, Long roomId, Long before, Long after) {
        requireParticipant(email, roomId);
        if (before != null && after != null) throw new IllegalArgumentException("before와 after는 함께 사용할 수 없습니다.");
        if ((before != null && before < 1) || (after != null && after < 0)) throw new IllegalArgumentException("메시지 ID가 올바르지 않습니다.");
        List<ChatMessage> found = after == null
            ? messages.history(roomId, before == null ? Long.MAX_VALUE : before, PageRequest.of(0, 50))
            : messages.catchUp(roomId, after, PageRequest.of(0, 50));
        List<ChatDtos.Message> result = new ArrayList<>(found.stream().map(this::message).toList());
        if (after == null) Collections.reverse(result);
        return result;
    }
    @Transactional
    public ChatDtos.Message send(String email, ChatDtos.Send request) {
        Member me = friends.current(email);
        // Locking the sender serializes retries with the same client message ID.
        members.lockById(me.getId()).orElseThrow();
        requireParticipant(email, request.roomId());
        UUID.fromString(request.clientId());
        ChatMessage existing = messages.findBySenderIdAndClientId(me.getId(), request.clientId()).orElse(null);
        if (existing != null) {
            if (!existing.getRoom().getId().equals(request.roomId()) || !existing.getContent().equals(request.content().trim())) {
                throw new IllegalArgumentException("이미 사용된 메시지 ID입니다.");
            }
            return message(existing);
        }
        ChatRoom room = rooms.findById(request.roomId()).orElseThrow();
        ChatMessage saved = messages.saveAndFlush(new ChatMessage(room, me, request.content().trim(), request.clientId()));
        ChatDtos.Message response = message(saved);
        notifyRoom(room.getId(), "MESSAGE", response);
        return response;
    }
    @Transactional
    public void read(String email, Long roomId, Long messageId) {
        Member me = friends.current(email);
        ChatRoomMember p = participants.lockParticipant(roomId, me.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "이 채팅방의 참여자가 아닙니다."));
        if (!messages.existsByIdAndRoomId(messageId, roomId)) throw new IllegalArgumentException("이 채팅방의 메시지가 아닙니다.");
        p.markRead(messageId);
        events.publishEvent(new RealtimeEvent(List.of(email), "READ", Map.of("roomId", roomId, "messageId", messageId)));
    }
    private ChatDtos.Room room(ChatRoom room, Long viewerId, Long lastRead) {
        var people = participants.findParticipants(room.getId()).stream().map(p -> MemberResponse.from(p.getMember())).toList();
        String name = room.getType() == ChatRoom.Type.DIRECT
            ? people.stream().filter(m -> !m.id().equals(viewerId)).findFirst().map(MemberResponse::nickname).orElse("개인 채팅")
            : room.getName();
        var last = messages.findTopByRoomIdOrderByIdDesc(room.getId()).map(this::message).orElse(null);
        return new ChatDtos.Room(room.getId(), room.getType().name(), name, people, last,
            messages.countByRoomIdAndIdGreaterThanAndSenderIdNot(room.getId(), lastRead, viewerId));
    }
    private ChatDtos.Message message(ChatMessage m) {
        return new ChatDtos.Message(m.getId(), m.getRoom().getId(), MemberResponse.from(m.getSender()), m.getContent(), m.getClientId(), m.getCreatedAt());
    }
    private void notifyRoom(Long roomId, String type, Object data) {
        var recipients = participants.findParticipants(roomId).stream().map(p -> p.getMember().getEmail()).toList();
        events.publishEvent(new RealtimeEvent(recipients, type, data));
    }
}
