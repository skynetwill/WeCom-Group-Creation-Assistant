# 企业微信批量建群 — Java 学习地图

> 一个项目掌握 Java 中高级→架构师全部核心技能

---

## 项目概览

| 项目 | 数据 |
|------|------|
| 源文件 | 165+ |
| Spring 模块 | 30+ |
| Docker 容器 | 9 |
| 设计模式 | 10 种 |
| 面试 Demo | 14 个可运行 |
| 测试 | 22 个全通过 |
| 分支 | monolithic / distributed / docker-distributed |

---

## 一、Spring 全家桶 (30 模块)

### 核心框架
| 模块 | 本项目文件 | 面试考点 |
|------|-----------|---------|
| Spring Core (IoC/DI) | 全项目 165+ 文件 | `@Autowired` vs 构造注入、`@Bean` 生命周期 |
| Spring Boot | WechatGroupApplication.java | `@SpringBootApplication` = 三个注解的合成 |
| Spring MVC | AuthController/WeChatGroupController | `@RestController` vs `@Controller`、拦截器链 |
| Spring AOP | AuthAspect/MethodSecurityAspect | JDK 代理 vs CGLIB、`@Around` 执行顺序 |

### 数据访问
| 模块 | 本项目文件 | 面试考点 |
|------|-----------|---------|
| Spring Data JPA | AppUserRepository 等 7 个 | `ddl-auto` 四种模式、懒加载 N+1 问题 |
| MyBatis-Plus | UserMapper.java | JPA vs MyBatis 选型、`BaseMapper` 动态 SQL |
| Spring Data Redis | SessionRegistryConfig/RateLimitService | StringRedisTemplate、ZSet/SETNX/INCR |
| Spring Data Elasticsearch | AuditSearchRepository.java | 全文搜索 vs SQL LIKE、倒排索引原理 |

### 消息与调度
| 模块 | 本项目文件 | 面试考点 |
|------|-----------|---------|
| Spring AMQP (RabbitMQ) | MessageProducer/Consumer | 交换机/队列/路由键、死信队列、消息确认 |
| Spring Kafka | KafkaAuditProducer/Consumer | topic/partition/consumer group、offset 管理 |
| Spring Scheduling | WeChatTokenManager @Scheduled | fixedRate vs fixedDelay、cron 表达式 |
| Spring Async | AuditService @Async | 默认线程池风险、自定义线程池配置 |
| Spring Retry | WeChatTokenManager @Retryable | backoff 策略、@Recover 兜底 |

### 安全与会话
| 模块 | 本项目文件 | 面试考点 |
|------|-----------|---------|
| Spring Security Crypto | AuthConfig BCryptPasswordEncoder | BCrypt 盐值原理、彩虹表攻击 |
| Spring Session Redis | RedisSessionConfig | Session 共享原理、JSON vs JDK 序列化 |
| 自建安全框架 | security/ 包 79 个文件 | 对标 Spring Security 全部核心模块 |

### 监控与文档
| 模块 | 本项目文件 | 面试考点 |
|------|-----------|---------|
| Spring Actuator | application-docker.properties | `/health`、`/prometheus`、`/metrics` |
| Micrometer Prometheus | JvmMetricsConfig.java | JVM 内存/GC/线程指标、Grafana 大盘 |
| SpringDoc Swagger | SwaggerConfig.java | OpenAPI 3.0、`@Operation` 注解 |
| Spring Cache | CacheConfig.java | `@Cacheable`/`@CacheEvict`、缓存穿透/击穿/雪崩 |
| Spring Validation | LoginRequest/GlobalExceptionHandler | `@Valid`/`@NotBlank`、`MethodArgumentNotValidException` |
| Spring Retry | WeChatTokenManager | `@Retryable` 重试 3 次、exponential backoff |

### 测试
| 模块 | 本项目文件 | 面试考点 |
|------|-----------|---------|
| Spring Test MockMvc | AuthControllerTest.java | 不启动服务器测试 Controller |
| Mockito | AuthServiceTest.java | `@Mock`/`when`/`verify`、`ArgumentCaptor` |

---

## 二、Java 语言基础 (5 个 Demo)

| Demo | 文件 | 面试考点 |
|------|------|---------|
| Stream API | demo/java/StreamApiDemo.java | filter/map/groupBy/reduce 等 10 个操作 |
| CompletableFuture | demo/java/CompletableFutureDemo.java | supplyAsync/thenCombine/allOf 异步编排 |
| Collections | demo/java/CollectionsDemo.java | HashMap 原理(数组+链表+红黑树)、CHM CAS |
| Modern Java | demo/java/ModernJavaDemo.java | Record/Sealed/var/Optional/TextBlock |
| ThreadPool | demo/java/ThreadPoolDemo.java | 7 参数、4 种拒绝策略、@Async 对应 |

---

## 三、分布式架构 (Docker 9 容器)

```
docker-compose.yml (9 容器):
  wg-nginx          Nginx 负载均衡（round-robin/max_fails/keepalive）
  wg-app1～3        3 个 Spring Boot 实例（独立 JVM）
  wg-mysql          MySQL 8.0（共享数据库）
  wg-redis          Redis 7（Session/限流/Token/分布式锁）
  wg-rabbitmq       RabbitMQ（异步建群队列）
  wg-elasticsearch  ES 8（审计日志全文搜索）
  wg-kafka          Kafka 3.6（审计事件流，3 消费者组并行）
```

### 分布式核心实现

| 功能 | Redis 数据结构 | 文件 |
|------|---------------|------|
| Session 共享 | Spring Session Redis | RedisSessionConfig |
| 并发会话控制 | ZSet `sessions:{userId}` | SessionRegistryConfig |
| 全局限流 | INCR `ratelimit:{ip}:{window}` | RateLimitService |
| Token 单实例刷新 | SETNX `wechat:token:lock` | WeChatTokenManager |
| 分布式锁 | SETNX + EXPIRE | WeChatTokenManager.refreshAccessToken() |

---

## 四、设计模式 (10 种，全部在项目中有实际代码)

| 模式 | 项目实际位置 | 面试怎么讲 |
|------|-------------|-----------|
| 策略模式 | FileParserStrategy/CsvParser/ExcelParser | "文件格式不固定，新增格式只加类不改老代码" |
| 模板方法 | AbstractAuthenticationToken→子类 | "认证流程固定，具体验证由子类实现" |
| 建造者 | HttpSecurity DSL 链式调用 | "用流畅 API 构建复杂配置，避免大量构造参数" |
| 代理模式 | AuthAspect @Around + CGLIB | "AOP 横切，不侵入业务代码" |
| 责任链 | ProviderManager→多个Provider | "多个认证器链式执行，匹配即停" |
| 工厂方法 | @Bean passwordEncoder()等 | "Spring 容器管理对象创建，依赖注入解耦" |
| 单例 | 所有 @Service/@Component Bean | "Spring 默认单例，避免重复创建" |
| 观察者 | HttpSessionListener→SessionRegistry | "Session 创建/销毁事件自动通知" |
| 适配器 | BCryptPasswordEncoderAdapter | "将外部 BCrypt 适配到自定义接口" |
| 装饰器 | VirtualFilterChain 包装 FilterChain | "在原链前后插入自定义 Filter" |

---

## 五、面试概念 Demo (9 个可运行/文档)

| 概念 | 文件 | 面试必问度 |
|------|------|-----------|
| Bean 生命周期 | demo/BeanLifecycleDemo.java | ⭐⭐⭐⭐⭐ |
| 事务传播 7 级 | demo/TransactionDemo.java | ⭐⭐⭐⭐⭐ |
| AOP 代理陷阱 | demo/AopProxyDemo.java | ⭐⭐⭐⭐⭐ |
| 自动配置原理 | demo/AutoConfigDemo.java | ⭐⭐⭐⭐ |
| DDD 四层架构 | demo/DddDemo.java + domain/ 包 | ⭐⭐⭐⭐ |
| 分布式锁(Redis) | demo/DistributedLockDemo.java | ⭐⭐⭐⭐⭐ |
| 幂等性 | demo/IdempotentDemo.java | ⭐⭐⭐⭐ |
| Snowflake 算法 | demo/SnowflakeIdDemo.java | ⭐⭐⭐ |
| 优雅停机 | demo/GracefulShutdownDemo.java | ⭐⭐⭐ |
| 分库分表 | demo/ShardingDemo.java | ⭐⭐⭐⭐ |
| 多数据源/读写分离 | demo/MultiDataSourceDemo.java | ⭐⭐⭐ |

---

## 六、安全框架 (自建，对标 Spring Security)

| 模块 | 对标 Spring Security |
|------|---------------------|
| AuthenticationManager | ProviderManager |
| AuthenticationProvider | DaoAuthenticationProvider + RememberMeAuthenticationProvider |
| Authentication | UsernamePasswordAuthToken + RememberMeAuthToken |
| UserDetails / GrantedAuthority | AppUserDetails + SimpleGrantedAuthority |
| SecurityContextHolder | ThreadLocal + SecurityContextFilter |
| PasswordEncoder | BCryptPasswordEncoderAdapter |
| PersistentTokenBasedRememberMeServices | RememberMeServicesImpl (SHA-256 + 浏览器指纹) |
| SessionRegistry | RedisSessionRegistry |
| @PreAuthorize / @PostAuthorize | @RequireAuth / @RequireRole + MethodSecurityAspect |
| ExceptionTranslationFilter | ExceptionTranslationFilter |
| LockedException/DisabledException | LockedException/DisabledException/CredentialsExpiredException |
| CSP/HSTS/XFO/XCTO | SecurityHeadersFilter |

---

## 七、安全增强功能

| 功能 | 实现位置 |
|------|---------|
| BCrypt 密码加密 | AuthConfig + PasswordValidator |
| 密码历史(最近5次不可重用) | PasswordHistory + AuthServiceImpl.changePassword() |
| 密码定期过期(90天) | AuthProperties.passwordExpiryDays |
| 账号锁定(5次失败锁15分钟) | AuthServiceImpl.doLogin() |
| MFA TOTP(Google Authenticator) | TOTPService + AuthController |
| Remember-Me(14天+浏览器指纹) | RememberMeServicesImpl |
| CSRF 防护 | AuthController.csrf() |
| XSS 防护 | XssUtils + SecurityHeadersFilter |
| 审计日志(10种事件) | AuditService + AuditLog |
| API 限流 | RateLimitService (Redis INCR) |
| CORS 配置 | CorsConfig + CorsFilter |
| 会话固定防护 | AuthController 登录时 destroy + newSession |
| 优雅停机 | server.shutdown=graceful |
| CI/CD | .github/workflows/ci.yml |

---

## 八、面试官可能还会问的（本项目未涵盖）

这些是纯概念或独立环境层面的，本项目加不了代码：

| 知识点 | 为什么没加 | 怎么准备 |
|--------|-----------|---------|
| JVM 调优实战 | 需要压测工具 + GC 日志分析 | 用 `jstat -gc` 看本项目的 GC |
| Kubernetes (K8s) | 需要云环境 | 用 Minikube 本地跑一次 |
| JMH 基准测试 | 独立框架，不适合项目代码 | 写个 `@Benchmark` 测 BCrypt 性能 |
| SQL 优化 | 项目有 H2 模拟，数据量不足 | 单独练习 EXPLAIN/索引/慢查询 |
| Linux 命令 | 不是 Java 代码 | 在 WSL/Docker 里练 `ps/grep/top/df` |
| 网络协议(HTTP/TCP) | 不是 Java 代码 | 抓包看一次登录请求的完整流程 |

---

## 九、学习路线建议

```
你现在的水平:  ← 已经在这

初级 Java (已学完)        中级 Java (已学完)         高级 Java (进行中)
├── Java 语法基础          ├── Spring Boot/JPA        ├── 分布式架构(✅)
├── OOP 三大特性           ├── MyBatis-Plus(✅)       ├── 消息队列(✅ RabbitMQ/Kafka)
├── 集合框架(✅)           ├── Redis/Session(✅)      ├── ES 搜索引擎(✅)
├── 异常处理               ├── Docker Compose(✅)     ├── DDD 领域驱动(✅)
└── IO/NIO                 ├── 安全框架(✅)           ├── 分库分表(✅ ShardingSphere)
                            ├── 设计模式(✅ 10种)     ├── CI/CD(✅ GitHub Actions)
                            └── 测试 MockMvc/Mockito(✅)└── JVM 调优(需要实操)

架构师 (下一步)
├── K8s + Helm
├── 领域驱动设计深入
├── 全链路压测
├── 多活容灾
└── 团队技术管理
```

---

## 十、GitHub 分支说明

| 分支 | 内容 | 架构 |
|------|------|------|
| `monolithic` | 原始版本：OAuth + 简单登录 | 单机 JAR |
| `distributed` | +安全框架 79 文件 + 全部安全功能 | 单机 JAR |
| `docker-distributed` | +Docker 9 容器 + 全部中间件 + 全部 Demo | 分布式 Docker |

---

> 这个项目涵盖了 Spring 全家桶、分布式架构、设计模式、安全框架、Java 语言特性、消息队列、搜索引擎、CI/CD 等几乎全部 Java 中高级面试范围。面试时问任何概念，你都能打开对应文件指给面试官看。
