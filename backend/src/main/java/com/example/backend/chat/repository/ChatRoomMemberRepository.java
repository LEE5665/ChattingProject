package com.example.backend.chat.repository;
import com.example.backend.chat.entity.ChatRoomMember;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {
    @Query("select p from ChatRoomMember p join fetch p.room where p.member.id = :id order by p.room.id desc")
    List<ChatRoomMember> findRooms(@Param("id") Long id);
    @Query("select p from ChatRoomMember p join fetch p.member where p.room.id = :id order by p.id")
    List<ChatRoomMember> findParticipants(@Param("id") Long roomId);
    boolean existsByRoomIdAndMemberId(Long room, Long member);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ChatRoomMember p where p.room.id = :room and p.member.id = :member")
    Optional<ChatRoomMember> lockParticipant(@Param("room") Long room, @Param("member") Long member);
}
