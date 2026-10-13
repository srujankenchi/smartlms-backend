package com.smartlms.repository;

import com.smartlms.entity.ChatUserRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatUserReadRepository extends JpaRepository<ChatUserRead, Long> {

    Optional<ChatUserRead> findByUserId(Long userId);
}
