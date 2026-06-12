package com.school.wechatgroup.service.impl;

import com.school.wechatgroup.exception.BusinessException;
import com.school.wechatgroup.service.WeChatGroupService;
import com.school.wechatgroup.service.parser.FileParserStrategy;
import com.school.wechatgroup.task.WeChatTokenManager;
import com.school.wechatgroup.vo.CreateGroupResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WeChatGroupServiceImpl implements WeChatGroupService {

    private static final Logger log = LoggerFactory.getLogger(WeChatGroupServiceImpl.class);

    private final Map<String, FileParserStrategy> parserMap;
    private final RestTemplate restTemplate;
    private final WeChatTokenManager tokenManager;

    private static final Map<Integer, String> ERROR_EXPLAIN = new HashMap<>();

    static {
        // 通用错误
        ERROR_EXPLAIN.put(-1, "系统繁忙，请稍后重试");
        ERROR_EXPLAIN.put(0, "请求成功");

        // 参数校验错误
        ERROR_EXPLAIN.put(40001, "企业微信密钥（secret）无效，请检查配置");
        ERROR_EXPLAIN.put(40003, "成员列表中包含无效的 UserID，请检查文件中填写的 userid 是否正确");
        ERROR_EXPLAIN.put(40004, "上传的文件格式或类型不符合要求，请上传 CSV 或 Excel 格式文件");
        ERROR_EXPLAIN.put(40013, "企业微信企业 ID（corpid）无效，请检查配置");
        ERROR_EXPLAIN.put(40014, "企业微信授权凭证（access_token）无效，请稍后重试");
        ERROR_EXPLAIN.put(40029, "企业微信授权码（code）无效或已过期，请重新授权");
        ERROR_EXPLAIN.put(40032, "群聊名称过长或含有不支持的特殊字符");
        ERROR_EXPLAIN.put(41001, "企业微信授权凭证缺失，请联系管理员检查系统配置");
        ERROR_EXPLAIN.put(41002, "企业 ID（corpid）未配置，请联系管理员检查系统配置");
        ERROR_EXPLAIN.put(41003, "企业 ID（corpid）参数缺失，请联系管理员检查系统配置");
        ERROR_EXPLAIN.put(41004, "企业微信密钥（secret）参数缺失，请联系管理员检查系统配置");

        // 权限与认证
        ERROR_EXPLAIN.put(42001, "企业微信授权凭证已过期，请刷新后重试");
        ERROR_EXPLAIN.put(42002, "企业微信刷新令牌已过期，请重新授权");
        ERROR_EXPLAIN.put(48002, "该应用没有创建群聊的权限，请在企业微信管理后台开通群聊功能");
        ERROR_EXPLAIN.put(48003, "应用未获得调用该接口的权限，请联系管理员");
        ERROR_EXPLAIN.put(50001, "企业微信 OAuth 回调域名未授权，请在企微管理后台配置可信域名");
        ERROR_EXPLAIN.put(50002, "当前用户未授权企业微信应用，请在企微工作台打开应用");
        ERROR_EXPLAIN.put(301002, "无权限执行该操作，请检查应用在企业微信管理后台的权限配置");
        ERROR_EXPLAIN.put(301005, "部分用户不在应用的可见范围内，请检查文件中的 userid");

        // 群聊相关
        ERROR_EXPLAIN.put(60011, "指定的群聊不存在（可能已被解散）");
        ERROR_EXPLAIN.put(60012, "群聊 ID 不合法，群聊可能已被删除");
        ERROR_EXPLAIN.put(60111, "成员列表中包含不存在的 UserID，请检查文件中的 userid 是否正确");
        ERROR_EXPLAIN.put(86001, "目标群聊不存在或已被删除");
        ERROR_EXPLAIN.put(86101, "群主 UserID 无效或不在应用可见范围，请检查群主 userid");
        ERROR_EXPLAIN.put(86102, "群聊名称不能为空");
        ERROR_EXPLAIN.put(86103, "成员列表不能为空，请检查上传的文件是否包含成员信息");
        ERROR_EXPLAIN.put(86104, "群聊名称已被使用，请更换群聊名称");
        ERROR_EXPLAIN.put(86105, "群聊成员数量超过限制（最多 2000 人，含群主）");
        ERROR_EXPLAIN.put(86201, "群主不在应用的可见范围内，请确认群主 userid 是否正确");
        ERROR_EXPLAIN.put(86202, "成员列表中部分用户不在应用的可见范围内");
        ERROR_EXPLAIN.put(86203, "建群请求缺少必要参数，请填写完整的群聊名称和群主信息");
        ERROR_EXPLAIN.put(86204, "建群请求参数不合法，请检查输入的群聊名称和群主 userid");
        ERROR_EXPLAIN.put(86205, "今日群聊创建数量已达到企业上限，请明天再试");
        ERROR_EXPLAIN.put(86214, "企业群聊总数已达上限，无法继续创建新群");
        ERROR_EXPLAIN.put(86215, "群聊成员已满（最多 2000 人），无法继续添加成员");
        ERROR_EXPLAIN.put(86216, "标签 ID 不合法，请联系管理员");
        ERROR_EXPLAIN.put(86217, "标签 ID 不存在或已被删除");

        // 限流
        ERROR_EXPLAIN.put(45009, "调用频率过高（每分钟超过限制），请稍后再试");
        ERROR_EXPLAIN.put(45016, "今日建群次数达到上限，请明天再试");
        ERROR_EXPLAIN.put(45033, "调用频率过高（被系统限流），请等待 1 分钟后重试");
    }

    public WeChatGroupServiceImpl(Map<String, FileParserStrategy> parserMap,
                                   RestTemplate restTemplate,
                                   WeChatTokenManager tokenManager) {
        this.parserMap = parserMap;
        this.restTemplate = restTemplate;
        this.tokenManager = tokenManager;
    }

    @Override
    public CreateGroupResultVO createGroupFromFile(MultipartFile file, String groupName, String ownerId) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new BusinessException("文件名称为空");
        }

        String extension = getExtension(originalFilename).toLowerCase();
        FileParserStrategy parser = parserMap.values().stream()
                .filter(p -> p.supportedExtension().equalsIgnoreCase(extension)
                           || (extension.equals("xls") && p.supportedExtension().equals("xlsx")))
                .findFirst()
                .orElse(null);
        if (parser == null) {
            throw new BusinessException("不支持的文件格式: " + extension + "，请使用 CSV 或 Excel 文件");
        }

        List<String> userList = parser.parse(file);
        if (userList.isEmpty()) {
            throw new BusinessException("文件中没有找到有效的成员ID");
        }

        // 自动加入群主
        List<String> members = new ArrayList<>();
        members.add(ownerId);
        members.addAll(userList);

        // 去重
        Set<String> seen = new HashSet<>();
        List<String> uniqueMembers = members.stream()
                .filter(u -> u != null && !u.isEmpty() && seen.add(u))
                .collect(Collectors.toList());

        try {
            Map<String, Object> apiResult = callCreateGroupApi(groupName, ownerId, uniqueMembers);
            Object errcodeObj = apiResult.get("errcode");
            int errcode = errcodeObj instanceof Number ? ((Number) errcodeObj).intValue() : -1;
            String errmsg = (String) apiResult.get("errmsg");

            if (errcode == 0) {
                String chatId = (String) apiResult.get("chatid");
                String userListStr = String.join(",", uniqueMembers);
                String message = "建群成功！群聊ID: " + chatId + "，成员数: " + uniqueMembers.size();
                log.info("建群成功 | chatId={} | 成员数={}", chatId, uniqueMembers.size());
                return CreateGroupResultVO.success(message, userListStr, chatId);
            } else {
                String explain = ERROR_EXPLAIN.getOrDefault(errcode,
                        "建群失败（错误码 " + errcode + "），请稍后重试或联系管理员");
                String message = explain;
                log.error("建群失败 | errcode={} | errmsg={} | 成员数={}", errcode, errmsg, uniqueMembers.size());
                return CreateGroupResultVO.failure(message, errcode, errmsg);
            }
        } catch (Exception e) {
            log.error("调用建群API异常", e);
            return CreateGroupResultVO.failure("调用企业微信接口失败，请稍后重试", -1, "api error");
        }
    }

    private Map<String, Object> callCreateGroupApi(String groupName, String ownerId, List<String> members) {
        String token = tokenManager.getAccessToken();
        if (token == null || token.isEmpty()) {
            log.error("无法获取 access_token");
            throw new BusinessException("企业微信 Token 获取失败，请稍后重试");
        }
        String url = "https://qyapi.weixin.qq.com/cgi-bin/appchat/create?access_token=" + token;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", groupName);
        requestBody.put("owner", ownerId);
        requestBody.put("userlist", members);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ParameterizedTypeReference<Map<String, Object>> typeRef =
                new ParameterizedTypeReference<Map<String, Object>>() {};
        Map<String, Object> response = restTemplate.exchange(url, HttpMethod.POST, entity, typeRef).getBody();
        return response != null ? response : Map.of("errcode", -1, "errmsg", "no response");
    }

    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return "";
        }
        return filename.substring(lastDot + 1);
    }
}
