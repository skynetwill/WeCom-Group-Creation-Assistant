package com.school.wechatgroup.service.impl;

import com.school.wechatgroup.constant.OperationStatus;
import com.school.wechatgroup.entity.GroupOperation;
import com.school.wechatgroup.entity.UploadFile;
import com.school.wechatgroup.entity.WxGroup;
import com.school.wechatgroup.repository.GroupOperationRepository;
import com.school.wechatgroup.repository.UploadFileRepository;
import com.school.wechatgroup.repository.WxGroupRepository;
import com.school.wechatgroup.service.GroupRecordService;
import com.school.wechatgroup.vo.CreateGroupResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class GroupRecordServiceImpl implements GroupRecordService {

    private static final Logger log = LoggerFactory.getLogger(GroupRecordServiceImpl.class);

    private final GroupOperationRepository operationRepo;
    private final WxGroupRepository wxGroupRepo;
    private final UploadFileRepository uploadFileRepo;

    public GroupRecordServiceImpl(GroupOperationRepository operationRepo,
                                   WxGroupRepository wxGroupRepo,
                                   UploadFileRepository uploadFileRepo) {
        this.operationRepo = operationRepo;
        this.wxGroupRepo = wxGroupRepo;
        this.uploadFileRepo = uploadFileRepo;
    }

    @Override
    @Transactional
    public void recordGroupCreation(CreateGroupResultVO result, String groupName, String ownerId,
                                     String userId, String ip, MultipartFile file) {
        String operationId = UUID.randomUUID().toString().substring(0, 8);

        // 1. 操作记录（必写）
        GroupOperation operation = new GroupOperation();
        operation.setOperationId(operationId);
        operation.setGroupName(groupName);
        operation.setOwnerId(ownerId);
        operation.setOperatorUserId(userId);
        operation.setFileName(file.getOriginalFilename());
        operation.setStatus("success".equals(result.getStatus()) ? OperationStatus.SUCCESS : OperationStatus.FAILED);
        operation.setResultMessage(result.getMessage());
        operation.setChatId("success".equals(result.getStatus()) ? result.getChatId() : null);
        operation.setClientIp(ip);
        operation.setCreatedAt(LocalDateTime.now());
        operationRepo.save(operation);

        // 2. 群聊记录（仅成功时写入）
        if ("success".equals(result.getStatus())) {
            WxGroup wxGroup = new WxGroup();
            wxGroup.setOperationId(operationId);
            wxGroup.setChatId(result.getChatId());
            wxGroup.setGroupName(groupName);
            wxGroup.setOwnerId(ownerId);
            wxGroup.setMemberList(result.getUserList());
            wxGroup.setCreatedAt(LocalDateTime.now());
            wxGroupRepo.save(wxGroup);
        }

        // 3. 文件存档（失败不影响主事务）
        try {
            UploadFile uploadFile = new UploadFile();
            uploadFile.setOperationId(operationId);
            uploadFile.setFileName(file.getOriginalFilename());
            uploadFile.setFileContent(file.getBytes());
            uploadFile.setCreatedAt(LocalDateTime.now());
            uploadFileRepo.save(uploadFile);
        } catch (Exception e) {
            log.error("文件存档失败 | operationId={} | 文件名={}", operationId, file.getOriginalFilename(), e);
        }
    }
}
