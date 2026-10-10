package com.example.backend.chat.repository;
import com.example.backend.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Optional<ChatMessage> findBySenderIdAndClientId(Long sender, String client);
    Optional<ChatMessage> findTopByRoomIdOrderByIdDesc(Long room);
    @Query("select m from ChatMessage m join fetch m.sender where m.room.id = :room and m.id < :before order by m.id desc")
    List<ChatMessage> history(@Param("room") Long room, @Param("before") Long before, Pageable page);
    @Query("select m from ChatMessage m join fetch m.sender where m.room.id = :room and m.id > :after order by m.id")
    List<ChatMessage> catchUp(@Param("room") Long room, @Param("after") Long after, Pageable page);
    long countByRoomIdAndIdGreaterThanAndSenderIdNot(Long room, Long after, Long sender);
    boolean existsByIdAndRoomId(Long id, Long room);
}
