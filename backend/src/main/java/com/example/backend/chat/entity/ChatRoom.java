package com.example.backend.chat.entity;
import com.example.backend.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "chat_rooms")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {
    public enum Type { DIRECT, GROUP }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Type type;
    @Column(length = 80)
    private String name;
    @Column(unique = true, length = 50)
    private String directKey;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member createdBy;
    @Column(nullable = false)
    private Instant createdAt;
    public ChatRoom(Type type, String name, String directKey, Member creator) {
        this.type = type; this.name = name; this.directKey = directKey; createdBy = creator; createdAt = Instant.now();
    }
}
