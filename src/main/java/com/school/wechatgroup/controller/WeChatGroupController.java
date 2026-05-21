package com.school.wechatgroup.controller;

import com.school.wechatgroup.config.WeChatProperties;
import com.school.wechatgroup.service.CreateGroupResult;
import com.school.wechatgroup.service.WeChatGroupService;
import com.school.wechatgroup.task.WeChatTokenManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class WeChatGroupController {

    private static final Logger log = LoggerFactory.getLogger(WeChatGroupController.class);
    private static final Logger OPERATE_LOG = LoggerFactory.getLogger("GROUP_OPERATE_LOG");

    @Autowired
    private WeChatGroupService weChatGroupService;
    @Autowired
    private WeChatTokenManager tokenManager;
    @Autowired
    private WeChatProperties weChatProperties;
    @Autowired
    private RestTemplate restTemplate;

    @Value("${app.base-url}")
    private String baseUrl;

    // ===================== OAuth2 身份认证 =====================

    @GetMapping("/")
    public void homePage(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 1. 已有 userId（OAuth 完成后的二次访问）→ 直接跳转到主页
        String userId = request.getParameter("userId");
        if (userId != null && !userId.isEmpty()) {
            response.sendRedirect("/index.html?userId=" + URLEncoder.encode(userId, StandardCharsets.UTF_8));
            return;
        }

        // 2. 有 OAuth code → 换取 userId
        String code = request.getParameter("code");
        if (code != null && !code.isEmpty()) {
            String fetchedUserId = exchangeCodeForUserId(code);
            if (fetchedUserId != null) {
                log.info("OAuth 认证成功 | userId={}", fetchedUserId);
                response.sendRedirect("/index.html?userId=" + URLEncoder.encode(fetchedUserId, StandardCharsets.UTF_8));
                return;
            }
            // 换取失败 → 降级到本地用户
            log.warn("OAuth code 换取 userId 失败，降级为本地用户");
            response.sendRedirect("/index.html?userId=本地用户");
            return;
        }

        // 3. 首次访问 → 跳转企业微信 OAuth 授权页
        String redirectUri = baseUrl + "/";
        String oauthUrl = String.format(
                "https://open.weixin.qq.com/connect/oauth2/authorize?appid=%s&redirect_uri=%s&response_type=code&scope=snsapi_base&agentid=%s&state=STATE#wechat_redirect",
                weChatProperties.getCorpid(),
                URLEncoder.encode(redirectUri, StandardCharsets.UTF_8),
                weChatProperties.getAgentid());
        log.info("未认证用户，跳转 OAuth 授权");
        response.sendRedirect(oauthUrl);
    }

    private String exchangeCodeForUserId(String code) {
        try {
            String token = tokenManager.getAccessToken();
            if (token == null || token.isEmpty()) {
                log.error("无有效 access_token，无法换取 userId");
                return null;
            }
            String url = "https://qyapi.weixin.qq.com/cgi-bin/user/getuserinfo?access_token=" + token + "&code=" + code;
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            if (resp != null && Integer.parseInt(resp.get("errcode").toString()) == 0) {
                return (String) resp.get("UserId");
            }
            log.error("getuserinfo 失败: {}", resp);
        } catch (Exception e) {
            log.error("getuserinfo 异常", e);
        }
        return null;
    }

    // ===================== 建群操作 =====================

    @PostMapping("/api/group/create")
    public String createGroup(
            @RequestParam("groupName") String groupName,
            @RequestParam("ownerId") String ownerId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false, defaultValue = "本地用户") String userId,
            HttpServletRequest request
    ) {
        log.info("===== 收到建群请求 =====");
        log.info("操作人：{} | 群名：{} | 群主：{} | 文件：{}", userId, groupName, ownerId, file.getOriginalFilename());

        if (file.isEmpty()) {
            String msg = "上传失败：请选择文件";
            log.error(msg);
            saveOperationLog(userId, request, groupName, ownerId, file.getOriginalFilename(), 0, "-", msg);
            return msg;
        }

        try {
            CreateGroupResult result = weChatGroupService.createGroupFromFile(file, groupName, ownerId);
            int userCount = getUserCount(result.getMessage());
            String memberIds = result.getUserList().isEmpty() ? "-" : String.join(",", result.getUserList());
            saveOperationLog(userId, request, groupName, ownerId, file.getOriginalFilename(), userCount, memberIds, result.getMessage());
            return result.getMessage();
        } catch (Exception e) {
            String errorMsg = "建群失败：" + e.getMessage();
            log.error("建群异常", e);
            saveOperationLog(userId, request, groupName, ownerId, file.getOriginalFilename(), 0, "-", errorMsg);
            return errorMsg;
        }
    }

    @GetMapping("/api/template/csv")
    public ResponseEntity<byte[]> downloadCsvTemplate() {
        String content = "userid\r\n请替换为群主的userid\r\n请替换为成员1的userid\r\n请替换为成员2的userid\r\n";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", encodeFilename("成员导入模板.csv"));

        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @GetMapping("/api/template/excel")
    public ResponseEntity<byte[]> downloadExcelTemplate() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("成员名单");

            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("userid");

            String[] samples = {"请替换为群主的userid", "请替换为成员1的userid", "请替换为成员2的userid"};
            for (int i = 0; i < samples.length; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(samples[i]);
            }

            sheet.autoSizeColumn(0);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", encodeFilename("成员导入模板.xlsx"));

            return ResponseEntity.ok().headers(headers).body(bos.toByteArray());
        } catch (Exception e) {
            log.error("生成Excel模板失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/api/guide")
    public ResponseEntity<byte[]> downloadGuide() {
        try (XWPFDocument doc = new XWPFDocument()) {
            addHeading(doc, "企业微信批量建群工具 — 操作指南", 24);

            addHeading(doc, "一、功能概述", 18);
            addParagraph(doc, "本工具支持通过上传成员名单文件（CSV 或 Excel）批量创建企业微信群聊。");

            addHeading(doc, "二、准备成员名单文件", 18);
            addParagraph(doc, "1. 文件格式：支持 .csv、.xlsx、.xls 格式。");
            addParagraph(doc, "2. 文件内容：第一列为成员的企业微信 userid，第一行必须是表头 \"userid\"。");
            addParagraph(doc, "3. 示例：");
            addParagraph(doc, "   userid");
            addParagraph(doc, "   zhangsan");
            addParagraph(doc, "   lisi");
            addParagraph(doc, "   wangwu");

            addHeading(doc, "三、操作步骤", 18);
            addParagraph(doc, "步骤 1：在企业微信工作台打开本应用（自动认证身份）。");
            addParagraph(doc, "步骤 2：输入群聊名称和群主的企业微信 userid。");
            addParagraph(doc, "步骤 3：上传准备好的成员名单文件。");
            addParagraph(doc, "步骤 4：点击 \"一键创建群聊\" 按钮。");
            addParagraph(doc, "步骤 5：查看页面返回结果，成功则显示群聊 ID 和成员数。");

            addHeading(doc, "四、常见问题", 18);
            addParagraph(doc, "Q: 建群失败，提示 Token 获取失败？");
            addParagraph(doc, "A: 请检查 application.properties 中 corpid 和 corpsecret 是否正确配置。");

            addParagraph(doc, "Q: 上传 Excel 文件后解析失败？");
            addParagraph(doc, "A: 请确保第一列是 userid，第一行是表头 \"userid\"，后续行为实际成员 ID。");

            addParagraph(doc, "Q: 支持 .xls 格式吗？");
            addParagraph(doc, "A: 支持 .xls（旧版 Excel）和 .xlsx（新版 Excel），也支持 .csv 格式。");

            addParagraph(doc, "Q: 群聊人数有限制吗？");
            addParagraph(doc, "A: 企业微信群聊最多支持 2000 人（含群主），请确保名单人数不超过此限制。");

            addHeading(doc, "五、联系方式", 18);
            addParagraph(doc, "如有问题，请联系系统管理员。");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.write(bos);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
            headers.setContentDispositionFormData("attachment", encodeFilename("企业微信批量建群操作指南.docx"));

            return ResponseEntity.ok().headers(headers).body(bos.toByteArray());
        } catch (Exception e) {
            log.error("生成操作指南失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private void addHeading(XWPFDocument doc, String text, int fontSize) {
        XWPFParagraph paragraph = doc.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(fontSize);
        run.setFontFamily("宋体");
    }

    private void addParagraph(XWPFDocument doc, String text) {
        XWPFParagraph paragraph = doc.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(12);
        run.setFontFamily("宋体");
    }

    private String encodeFilename(String filename) {
        return URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // ===================== 操作日志 =====================
    private void saveOperationLog(String userId, HttpServletRequest request, String groupName,
                                  String ownerId, String fileName, int userCount, String memberIds, String result) {
        try {
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("X-Real-IP");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }

            String logContent = String.format(
                    "操作人：%s | IP：%s | 群名：%s | 群主：%s | 成员数：%d | 成员：%s | 文件：%s | 结果：%s",
                    userId, ip, groupName, ownerId, userCount, memberIds, fileName, result
            );

            OPERATE_LOG.info(logContent);
        } catch (Exception ignored) {
        }
    }

    private int getUserCount(String result) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("共导入\\s*(\\d+)\\s*人").matcher(result);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
