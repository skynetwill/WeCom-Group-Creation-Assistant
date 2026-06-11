package com.school.wechatgroup.service;

import com.school.wechatgroup.vo.CreateGroupResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface GroupRecordService {
    void recordGroupCreation(CreateGroupResultVO result, String groupName, String ownerId,
                             String userId, String ip, MultipartFile file);
}
