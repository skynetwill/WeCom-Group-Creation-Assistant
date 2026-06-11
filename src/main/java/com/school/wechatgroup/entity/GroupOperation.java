package com.school.wechatgroup.entity;

import com.school.wechatgroup.constant.OperationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "t_group_operation", indexes = {
    @Index(name = "idx_operation_id", columnList = "operation_id"),
    @Index(name = "idx_operator_user_id", columnList = "operator_user_id"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
public class GroupOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_id", nullable = false, length = 16)
    private String operationId;

    @Column(name = "group_name", nullable = false, length = 128)
    private String groupName;

    @Column(name = "owner_id", nullable = false, length = 64)
    private String ownerId;

    @Column(name = "operator_user_id", length = 64)
    private String operatorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OperationStatus status;

    @Column(name = "result_message", length = 2000)
    private String resultMessage;

    @Column(name = "chat_id", length = 64)
    private String chatId;

    @Column(name = "member_count")
    private Integer memberCount;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public GroupOperation() {
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

    public String getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(String operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public OperationStatus getStatus() {
        return status;
    }

    public void setStatus(OperationStatus status) {
        this.status = status;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public void setResultMessage(String resultMessage) {
        this.resultMessage = resultMessage;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public Integer getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Integer memberCount) {
        this.memberCount = memberCount;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
