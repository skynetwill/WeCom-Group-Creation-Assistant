package com.school.wechatgroup.repository;

import com.school.wechatgroup.entity.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    @Query("SELECT ph.passwordHash FROM PasswordHistory ph WHERE ph.username = :username ORDER BY ph.createdAt DESC")
    List<String> findRecentPasswordHashes(@Param("username") String username);

    List<PasswordHistory> findByUsernameOrderByCreatedAtDesc(String username);

    void deleteByUsername(String username);
}
