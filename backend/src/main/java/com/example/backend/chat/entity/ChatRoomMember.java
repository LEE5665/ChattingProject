package com.example.backend.chat.entity;
import com.example.backend.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "chat_room_members", uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "member_id"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "room_id")
    private ChatRoom room;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "member_id")
    private Member member;
    @Column(nullable = false)
    private Instant joinedAt;
    @Column(nullable = false)
    private Long lastReadMessageId = 0L;
    public ChatRoomMember(ChatRoom room, Member member) { this.room = room; this.member = member; joinedAt = Instant.now(); }
    public void markRead(long messageId) { lastReadMessageId = Math.max(lastReadMessageId, messageId); }
}
