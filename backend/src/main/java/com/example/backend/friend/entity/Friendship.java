package com.example.backend.friend.entity;

import com.example.backend.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "friendships", uniqueConstraints = @UniqueConstraint(columnNames = {"low_id", "high_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friendship {
    public enum Status { PENDING, ACCEPTED, REJECTED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "low_id")
    private Member low;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "high_id")
    private Member high;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member requester;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Status status;
    @Column(nullable = false)
    private Instant createdAt;
    public Friendship(Member a, Member b) {
        low = a.getId() < b.getId() ? a : b;
        high = a.getId() < b.getId() ? b : a;
        request(a);
    }
    public void request(Member sender) { requester = sender; status = Status.PENDING; createdAt = Instant.now(); }
    public void decide(boolean accepted) { status = accepted ? Status.ACCEPTED : Status.REJECTED; }
    public Member other(Long id) { return low.getId().equals(id) ? high : low; }
}
