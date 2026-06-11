package com.school.wechatgroup.repository;

import com.school.wechatgroup.entity.WxGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WxGroupRepository extends JpaRepository<WxGroup, Long> {
}
