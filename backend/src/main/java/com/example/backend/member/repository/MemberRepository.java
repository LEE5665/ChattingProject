package com.example.backend.member.repository;

import com.example.backend.member.entity.Member;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from Member m where m.id = :id")
    Optional<Member> lockById(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<Member> findByEmail(String email);
}
