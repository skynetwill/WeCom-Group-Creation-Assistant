package com.school.wechatgroup.repository;

import com.school.wechatgroup.entity.GroupOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * t_group_operation 数据访问层
 */
@Repository
public interface GroupOperationRepository extends JpaRepository<GroupOperation, Long> {
}
