package com.school.wechatgroup.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;

/**
 * Spring Cache 配置
 * - dev 环境：内存 ConcurrentMapCache
 * - docker 环境：Redis CacheManager
 */
@Configuration
public class CacheConfig {

    @Bean
    @Profile("dev")
    public CacheManager devCacheManager() {
        return new ConcurrentMapCacheManager("wechat_token", "user_profile");
    }

    @Bean
    @Profile("docker")
    public CacheManager redisCacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues();
        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }
}
