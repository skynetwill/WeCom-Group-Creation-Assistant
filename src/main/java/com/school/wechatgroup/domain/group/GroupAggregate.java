package com.school.wechatgroup.domain.group;

import com.school.wechatgroup.entity.WxGroup;
import com.school.wechatgroup.shared.AggregateRoot;
import com.school.wechatgroup.vo.CreateGroupResultVO;

/**
 * DDD 聚合根 — 群聊
 *
 * 不变量（Invariant）：群名不能为空，群主必须存在
 */
public class GroupAggregate implements AggregateRoot<Long> {

    private final WxGroup group;
    private GroupStatus status;

    public GroupAggregate(WxGroup group) {
        if (group.getGroupName() == null || group.getGroupName().trim().isEmpty()) {
            throw new IllegalArgumentException("群名不能为空");
        }
        this.group = group;
        this.status = GroupStatus.CREATED;
    }

    /** 领域行为：标记创建成功 */
    public void markCreatedSuccessfully(String chatId) {
        group.setChatId(chatId);
        this.status = GroupStatus.ACTIVE;
    }

    /** 领域行为：标记创建失败 */
    public void markCreationFailed(String reason) {
        this.status = GroupStatus.FAILED;
    }

    @Override
    public Long getId() { return group.getId(); }

    public WxGroup getEntity() { return group; }
    public GroupStatus getStatus() { return status; }
    public String getGroupName() { return group.getGroupName(); }
    public String getOwnerId() { return group.getOwnerId(); }

    public enum GroupStatus { CREATED, ACTIVE, FAILED, DISSOLVED }
}
