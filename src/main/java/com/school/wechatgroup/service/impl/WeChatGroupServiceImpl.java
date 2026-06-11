package com.school.wechatgroup.service.impl;

import com.school.wechatgroup.service.WeChatGroupService;
import com.school.wechatgroup.service.parser.FileParserStrategy;
import com.school.wechatgroup.vo.CreateGroupResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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

    private static final Map<Integer, String> ERROR_EXPLAIN = new HashMap<>();

    static {
        ERROR_EXPLAIN.put(-1, "系统繁忙");
        ERROR_EXPLAIN.put(0, "请求成功");
        ERROR_EXPLAIN.put(40001, "不合法的secret参数");
        ERROR_EXPLAIN.put(40003, "无效的UserID");
        ERROR_EXPLAIN.put(40004, "不合法的媒体文件类型");
        ERROR_EXPLAIN.put(40007, "不合法的媒体文件id");
        ERROR_EXPLAIN.put(40008, "不合法的消息类型");
        ERROR_EXPLAIN.put(40014, "不合法的access_token");
        ERROR_EXPLAIN.put(40029, "不合法的oauth_code");
        ERROR_EXPLAIN.put(40032, "不合法的模板id长度");
        ERROR_EXPLAIN.put(41001, "缺少access_token参数");
        ERROR_EXPLAIN.put(41002, "缺少appid参数");
        ERROR_EXPLAIN.put(42001, "access_token超时");
        ERROR_EXPLAIN.put(43002, "需要GET请求");
        ERROR_EXPLAIN.put(44001, "多媒体文件为空");
        ERROR_EXPLAIN.put(45001, "多媒体文件大小超过限制");
        ERROR_EXPLAIN.put(60011, "群聊不存在");
        ERROR_EXPLAIN.put(86001, "聊天群不存在");
        ERROR_EXPLAIN.put(86215, "群聊成员已满");
    }

    public WeChatGroupServiceImpl(Map<String, FileParserStrategy> parserMap, RestTemplate restTemplate) {
        this.parserMap = parserMap;
        this.restTemplate = restTemplate;
    }

    @Override
    public CreateGroupResultVO createGroupFromFile(MultipartFile file, String groupName, String ownerId) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            return CreateGroupResultVO.failure("文件名称为空", -1, "invalid filename");
        }

        String extension = getExtension(originalFilename).toLowerCase();
        FileParserStrategy parser = parserMap.get(extension + "Parser");
        if (parser == null) {
            return CreateGroupResultVO.failure(
                    "不支持的文件格式: " + extension + "，请使用 CSV 或 Excel 文件",
                    -1, "unsupported format");
        }

        List<String> userList = parser.parse(file);
        if (userList.isEmpty()) {
            return CreateGroupResultVO.failure("文件中没有找到有效的成员ID", -1, "empty user list");
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
            int errcode = ((Number) apiResult.get("errcode")).intValue();
            String errmsg = (String) apiResult.get("errmsg");

            if (errcode == 0) {
                String chatId = (String) apiResult.get("chatid");
                String userListStr = String.join(",", uniqueMembers);
                String message = "建群成功！群聊ID: " + chatId + "，成员数: " + uniqueMembers.size();
                log.info("建群成功 | chatId={} | 成员数={}", chatId, uniqueMembers.size());
                return CreateGroupResultVO.success(message, userListStr, chatId);
            } else {
                String explain = ERROR_EXPLAIN.getOrDefault(errcode, "未知错误");
                String message = "建群失败: " + explain + " (errcode=" + errcode + ", errmsg=" + errmsg + ")";
                log.error("建群失败 | errcode={} | errmsg={} | 成员数={}", errcode, errmsg, uniqueMembers.size());
                return CreateGroupResultVO.failure(message, errcode, errmsg);
            }
        } catch (Exception e) {
            log.error("调用建群API异常", e);
            return CreateGroupResultVO.failure("调用企业微信API失败: " + e.getMessage(), -1, "api error");
        }
    }

    private Map<String, Object> callCreateGroupApi(String groupName, String ownerId, List<String> members) {
        String url = "https://qyapi.weixin.qq.com/cgi-bin/appchat/create?access_token="
                + getAccessToken();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", groupName);
        requestBody.put("owner", ownerId);
        requestBody.put("userlist", members);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
        return response != null ? response : Map.of("errcode", -1, "errmsg", "no response");
    }

    private String getAccessToken() {
        String url = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid="
                + System.getProperty("wechat.corpid", "wwce6b569b8529bd53")
                + "&corpsecret="
                + System.getProperty("wechat.corpsecret", "");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && ((Number) response.get("errcode")).intValue() == 0) {
                return (String) response.get("access_token");
            }
        } catch (Exception e) {
            log.error("获取access_token失败", e);
        }
        return "";
    }

    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return "";
        }
        return filename.substring(lastDot + 1);
    }
}
