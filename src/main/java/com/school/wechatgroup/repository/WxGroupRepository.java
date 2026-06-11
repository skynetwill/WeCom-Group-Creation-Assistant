package com.school.wechatgroup.repository;

import com.school.wechatgroup.entity.WxGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WxGroupRepository extends JpaRepository<WxGroup, Long> {
    Optional<WxGroup> findByChatId(String chatId);
}
