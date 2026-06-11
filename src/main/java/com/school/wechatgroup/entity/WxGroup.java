package com.school.wechatgroup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "t_wx_group", indexes = {
    @Index(name = "idx_operation_id", columnList = "operation_id"),
    @Index(name = "idx_chat_id", columnList = "chat_id")
})
public class WxGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_id", nullable = false, length = 16)
    private String operationId;

    @Column(name = "chat_id", nullable = false, length = 64)
    private String chatId;

    @Column(name = "group_name", nullable = false, length = 128)
    private String groupName;

    @Column(name = "owner_id", length = 64)
    private String ownerId;

    @Column(name = "member_list", length = 5000)
    private String memberList;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public WxGroup() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getMemberList() {
        return memberList;
    }

    public void setMemberList(String memberList) {
        this.memberList = memberList;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
