package com.school.wechatgroup;

import com.school.wechatgroup.config.AuthProperties;
import com.school.wechatgroup.config.WeChatProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({AuthProperties.class, WeChatProperties.class})
public class WechatGroupApplication {

    public static void main(String[] args) {
        SpringApplication.run(WechatGroupApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
