package com.example.backend.friend.repository;
import com.example.backend.friend.entity.Friendship;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    Optional<Friendship> findByLowIdAndHighId(Long low, Long high);
    @Query("select f from Friendship f join fetch f.low join fetch f.high join fetch f.requester where f.low.id = :id or f.high.id = :id order by f.createdAt desc")
    List<Friendship> findForMember(@Param("id") Long id);
}
