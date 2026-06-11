package com.school.wechatgroup.service;

import com.school.wechatgroup.vo.CreateGroupResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface WeChatGroupService {
    CreateGroupResultVO createGroupFromFile(MultipartFile file, String groupName, String ownerId);
}
