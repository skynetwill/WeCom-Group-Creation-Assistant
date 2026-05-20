package com.school.wechatgroup.task;

import com.school.wechatgroup.config.WeChatProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class WeChatTokenManager {

    private static final Logger log = LoggerFactory.getLogger(WeChatTokenManager.class);

    private final WeChatProperties properties;
    private final RestTemplate restTemplate;
    private volatile String accessToken;

    public WeChatTokenManager(WeChatProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    public void init() {
        refreshAccessToken();
    }

    @Scheduled(fixedRate = 3600000)
    public void refreshAccessToken() {
        String url = String.format("https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=%s&corpsecret=%s",
                properties.getCorpid(), properties.getCorpsecret());

        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && Integer.parseInt(response.get("errcode").toString()) == 0) {
                this.accessToken = (String) response.get("access_token");
                log.info("Token 更新成功");
            } else {
                log.error("获取 Token 失败: {}", response);
            }
        } catch (Exception e) {
            log.error("获取 Token 发生异常: {}", e.getMessage(), e);
        }
    }

    public String getAccessToken() {
        return this.accessToken;
    }
}
