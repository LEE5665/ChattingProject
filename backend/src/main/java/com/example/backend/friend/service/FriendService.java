package com.example.backend.friend.service;

import com.example.backend.friend.dto.FriendDtos;
import com.example.backend.friend.entity.Friendship;
import com.example.backend.friend.repository.FriendshipRepository;
import com.example.backend.global.socket.RealtimeEvent;
import com.example.backend.member.dto.MemberResponse;
import com.example.backend.member.entity.Member;
import com.example.backend.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class FriendService {
    private final FriendshipRepository friendships;
    private final MemberRepository members;
    private final ApplicationEventPublisher events;

    public Member current(String email) {
        return members.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
    }
    public List<FriendDtos.Response> list(String email) {
        Member me = current(email);
        return friendships.findForMember(me.getId()).stream()
            .filter(f -> f.getStatus() != Friendship.Status.REJECTED)
            .map(f -> response(f, me.getId())).toList();
    }
    @Transactional
    public FriendDtos.Response request(String email, String targetEmail) {
        Member me = current(email);
        Member target = members.findByEmail(targetEmail.trim())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 이메일의 사용자가 없습니다."));
        if (me.getId().equals(target.getId())) throw new IllegalArgumentException("자신에게 친구 요청을 보낼 수 없습니다.");
        lockPair(me.getId(), target.getId());
        Friendship link = friendships.findByLowIdAndHighId(Math.min(me.getId(), target.getId()), Math.max(me.getId(), target.getId())).orElse(null);
        if (link == null) link = friendships.save(new Friendship(me, target));
        else if (link.getStatus() == Friendship.Status.REJECTED) link.request(me);
        else throw new IllegalStateException("이미 친구이거나 처리 중인 요청이 있습니다.");
        changed(link);
        return response(link, me.getId());
    }
    @Transactional
    public void decide(String email, Long id, boolean accepted) {
        Member me = current(email);
        Friendship link = friendships.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "친구 요청이 없습니다."));
        lockPair(link.getLow().getId(), link.getHigh().getId());
        // The entity might have been read before waiting for the member lock.
        friendships.flush();
        // Status concurrency is serialized by the pair locks and an explicit refresh.
        entityManager.refresh(link);
        if ((!link.getLow().getId().equals(me.getId()) && !link.getHigh().getId().equals(me.getId()))
                || link.getRequester().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "받은 친구 요청만 처리할 수 있습니다.");
        }
        if (link.getStatus() != Friendship.Status.PENDING) throw new IllegalStateException("이미 처리된 요청입니다.");
        link.decide(accepted);
        changed(link);
    }
    private final jakarta.persistence.EntityManager entityManager;
    public void requireFriends(Long me, Long other) {
        if (me.equals(other)) throw new IllegalArgumentException("다른 사용자를 선택해 주세요.");
        Friendship link = friendships.findByLowIdAndHighId(Math.min(me, other), Math.max(me, other))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "친구와 채팅방을 만들 수 있습니다."));
        if (link.getStatus() != Friendship.Status.ACCEPTED) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "친구 수락 후 채팅할 수 있습니다.");
    }
    public void lockPair(Long a, Long b) {
        members.lockById(Math.min(a,b)).orElseThrow(() -> new IllegalArgumentException("사용자가 없습니다."));
        members.lockById(Math.max(a,b)).orElseThrow(() -> new IllegalArgumentException("사용자가 없습니다."));
    }
    private FriendDtos.Response response(Friendship f, Long me) {
        return new FriendDtos.Response(f.getId(), MemberResponse.from(f.other(me)), f.getStatus().name(), !f.getRequester().getId().equals(me));
    }
    private void changed(Friendship link) {
        events.publishEvent(new RealtimeEvent(List.of(link.getLow().getEmail(), link.getHigh().getEmail()), "FRIENDS_CHANGED", null));
    }
}
