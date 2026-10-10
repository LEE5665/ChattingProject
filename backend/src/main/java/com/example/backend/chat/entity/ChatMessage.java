package com.example.backend.chat.entity;
import com.example.backend.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "chat_messages", indexes = @Index(name = "idx_message_room_id", columnList = "room_id,id"),
    uniqueConstraints = @UniqueConstraint(columnNames = {"sender_id", "client_id"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "room_id")
    private ChatRoom room;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "sender_id")
    private Member sender;
    @Column(nullable = false, length = 2000)
    private String content;
    @Column(name = "client_id", nullable = false, length = 36)
    private String clientId;
    @Column(nullable = false)
    private Instant createdAt;
    public ChatMessage(ChatRoom room, Member sender, String content, String clientId) {
        this.room = room; this.sender = sender; this.content = content; this.clientId = clientId; createdAt = Instant.now();
    }
}
