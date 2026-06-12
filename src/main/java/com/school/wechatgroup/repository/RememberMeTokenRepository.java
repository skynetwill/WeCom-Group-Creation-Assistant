package com.school.wechatgroup.repository;

import com.school.wechatgroup.entity.RememberMeToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface RememberMeTokenRepository extends JpaRepository<RememberMeToken, String> {

    @Transactional
    void deleteByUsername(String username);
}
