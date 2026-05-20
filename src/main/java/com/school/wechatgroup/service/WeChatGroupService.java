package com.school.wechatgroup.service;

import com.school.wechatgroup.service.parser.FileParserStrategy;
import com.school.wechatgroup.task.WeChatTokenManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WeChatGroupService {

    private static final Logger log = LoggerFactory.getLogger(WeChatGroupService.class);

    private static final Map<Integer, String> ERROR_EXPLAIN = new LinkedHashMap<>();
    static {
        ERROR_EXPLAIN.put(86001, "群主不在成员列表中——群主自己的 userid 也需要写进 Excel/CSV 名单里");
        ERROR_EXPLAIN.put(86003, "群聊名称不合法——群名可能包含特殊字符或为空，请检查");
        ERROR_EXPLAIN.put(86004, "群主 userid 不存在——请确认群主的企业微信账号是否正确");
        ERROR_EXPLAIN.put(86006, "群成员数量不对——至少需要2人（含群主），请确保名单人数 ≥ 2，且群主也在名单中");
        ERROR_EXPLAIN.put(86007, "成员列表中有不存在的 userid——请检查名单中的账号是否都是有效企业微信账号");
        ERROR_EXPLAIN.put(86201, "群主不在成员列表中——群主也必须出现在名单里");
        ERROR_EXPLAIN.put(86202, "成员列表中有重复的 userid——请去掉重复的账号");
        ERROR_EXPLAIN.put(40001, "企业微信凭证无效——corpid 或 corpsecret 配置错误");
        ERROR_EXPLAIN.put(40014, "access_token 无效或已过期——请刷新后重试");
        ERROR_EXPLAIN.put(41001, "缺少 access_token 参数——系统内部错误，请联系管理员");
        ERROR_EXPLAIN.put(42001, "access_token 已过期——系统将自动刷新，请稍后重试");
        ERROR_EXPLAIN.put(60011, "application 权限不足——当前应用没有建群权限，需在企业微信后台配置");
        ERROR_EXPLAIN.put(60111, "名单中有无效的 userid——英文错误信息里用反引号标出了具体是哪个账号不对，请修正后重试");
        ERROR_EXPLAIN.put(40003, "群主 userid 格式不合法——userid 应为英文字母、数字或下划线组成");
        ERROR_EXPLAIN.put(60003, "部门不存在——部分成员所属部门可能已被删除");
        ERROR_EXPLAIN.put(86008, "群聊名已存在或有成员离线——请更换群名或确认所有成员在线");
        ERROR_EXPLAIN.put(301002, "建群总数已达今日上限——每个应用每天最多创建 100 个群聊");
        ERROR_EXPLAIN.put(301003, "群成员人数超限——企业微信群聊上限为 2000 人");
    }

    private final WeChatTokenManager tokenManager;
    private final RestTemplate restTemplate;
    private final Map<String, FileParserStrategy> parserMap;

    public WeChatGroupService(WeChatTokenManager tokenManager, RestTemplate restTemplate,
                              Map<String, FileParserStrategy> parserMap) {
        this.tokenManager = tokenManager;
        this.restTemplate = restTemplate;
        this.parserMap = parserMap;
    }

    /**
     * 根据文件扩展名选择解析器，提取userid列表后创建群聊
     */
    public CreateGroupResult createGroupFromFile(MultipartFile file, String groupName, String ownerId) {
        // 1. 确定文件扩展名
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.contains(".")) {
            return new CreateGroupResult("文件解析失败: 无法识别文件类型，请上传 .csv 或 .xlsx/.xls 文件", List.of());
        }
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();

        // 2. 选择解析策略: .xlsx 和 .xls 都使用 excelParser
        FileParserStrategy parser;
        if ("xls".equals(extension) || "xlsx".equals(extension)) {
            parser = parserMap.get("excelParser");
        } else {
            parser = parserMap.get(extension + "Parser");
        }

        if (parser == null) {
            return new CreateGroupResult("文件解析失败: 不支持的文件格式 ." + extension + "，请使用 .csv 或 .xlsx/.xls", List.of());
        }

        // 3. 解析文件
        List<String> userList;
        try {
            userList = parser.parse(file);
        } catch (Exception e) {
            return new CreateGroupResult("文件解析失败: " + e.getMessage(), List.of());
        }

        if (userList.isEmpty()) {
            return new CreateGroupResult("名单为空，无法建群", List.of());
        }

        // 4. 鲁棒性：如果群主不在名单中，自动补入（群主是群聊的必要成员）
        if (!userList.contains(ownerId)) {
            userList.add(0, ownerId);
        }

        // 5. 创建群聊
        return createGroupFromUserList(userList, groupName, ownerId);
    }

    /**
     * 调用企业微信API创建群聊
     */
    private CreateGroupResult createGroupFromUserList(List<String> userList, String groupName, String ownerId) {
        String token = tokenManager.getAccessToken();
        if (token == null || token.isEmpty()) {
            return new CreateGroupResult("获取Token失败，请检查企业微信配置", userList);
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", groupName);
        requestBody.put("owner", ownerId);
        requestBody.put("userlist", userList);

        String url = "https://qyapi.weixin.qq.com/cgi-bin/appchat/create?access_token=" + token;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        log.info("调用企微建群API | 群名={} | 群主={} | 成员数={} | 成员={}", groupName, ownerId, userList.size(), String.join(",", userList));
        try {
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            if (response != null && Integer.parseInt(response.get("errcode").toString()) == 0) {
                String chatId = (String) response.get("chatid");
                log.info("建群成功 | chatId={} | 成员数={} | 成员={}", chatId, userList.size(), String.join(",", userList));
                return new CreateGroupResult("建群成功！群聊ID: " + chatId + "，共导入 " + userList.size() + " 人。", userList);
            } else {
                int errcode = Integer.parseInt(response.get("errcode").toString());
                String errmsg = (String) response.get("errmsg");
                log.warn("建群失败 | errcode={} | errmsg={} | 成员={}", errcode, errmsg, String.join(",", userList));
                String explain = ERROR_EXPLAIN.getOrDefault(errcode, "");
                if (!explain.isEmpty()) {
                    return new CreateGroupResult("建群失败: [" + errcode + "] " + errmsg + "\n说明: " + explain, userList);
                }
                return new CreateGroupResult("建群失败: [" + errcode + "] " + errmsg, userList);
            }
        } catch (Exception e) {
            log.error("建群API调用异常 | 群名={} | 群主={}", groupName, ownerId, e);
            return new CreateGroupResult("接口调用异常: " + e.getMessage(), userList);
        }
    }
}
