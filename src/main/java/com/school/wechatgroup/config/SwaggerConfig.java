package com.school.wechatgroup.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 3 配置
 * 访问地址：http://localhost:8082/swagger-ui.html
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("企业微信批量建群 API")
                        .version("3.0")
                        .description("Spring Boot 3.2.5 + 自建安全框架 + Docker 分布式部署")
                        .contact(new Contact()
                                .name("WeChat Group Team")
                                .email("admin@example.com")))
                .addSecurityItem(new SecurityRequirement().addList("JSESSIONID"))
                .components(new Components()
                        .addSecuritySchemes("JSESSIONID", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")));
    }
}
