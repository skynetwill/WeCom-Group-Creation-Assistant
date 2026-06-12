# 企业微信批量建群管理工具

Spring Boot 3.2.5 + 自建安全框架的企业微信（WeChat Work）批量建群后端代理服务。无需完整 Spring Security 依赖，手写对标其所有核心模块。

---

## 功能概述

| 模块 | 功能 | 说明 |
|------|------|------|
| 批量建群 | CSV/Excel 用户列表 | 调用企业微信 API 自动创建群聊 |
| OAuth2 登录 | 企业微信 OAuth2 | snsapi_base 静默授权 |
| 表单登录 | 用户名/密码 + BCrypt | 可开关（`app.auth.enabled`） |
| **安全框架** | 对标 Spring Security 全能力 | 手写认证/授权/会话/密码/MFA/审计/JWT |
| 操作记录 | MySQL 三表持久化 | 群操作/群记录/文件存档 |

---

## 快速启动

### 环境要求

- JDK 17+
- MySQL 8.0+（生产）/ H2 内置（开发）
- 企业微信管理后台应用

### 配置

```bash
cp src/main/resources/application.properties.example \
   src/main/resources/application.properties
```

编辑填入：
- `app.base-url` — 公网访问地址（OAuth 回调）
- `app.auth.default-password` — 管理员密码
- profile 文件中的 `wechat.work.*` 企业微信凭据

### 运行

```bash
./mvnw spring-boot:run
# → http://localhost:8082
# → 默认管理员 admin / 配置的密码（首次登录强制改密）
```

---

## 安全框架架构

本项目实现 **完整自建安全框架**，对标 Spring Security 全部核心模块，仅依赖 `spring-security-crypto`（BCrypt）。

```
请求 → SecurityContextFilter → ExceptionTranslationFilter
    → AuthorizationFilter → UsernamePasswordAuthFilter
    → BearerTokenAuthFilter → MVC Interceptor → Controller
```

### 安全接口层 (`security/`)

| 接口 | 对标 Spring Security |
|------|---------------------|
| `Authentication` | Authentication |
| `AuthenticationManager` | AuthenticationManager |
| `AuthenticationProvider` | AuthenticationProvider |
| `UserDetails` | UserDetails |
| `GrantedAuthority` | GrantedAuthority |
| `PasswordEncoder` | PasswordEncoder |
| `SecurityContext` | SecurityContext |
| `SecurityContextHolder` | ThreadLocal 持有者 |
| `AuthenticationEntryPoint` | 未认证入口 |
| `AccessDeniedHandler` | 权限拒绝处理 |
| `LogoutHandler` | 登出处理器 |

### 认证实现 (`security/impl/`)

| 组件 | 对标 | 职责 |
|------|------|------|
| `ProviderManager` | AuthenticationManager | 遍历 Provider 链 |
| `DaoAuthenticationProvider` | DaoAuthenticationProvider | 用户名/密码 + 锁定/禁用/过期检查 |
| `AppUserDetails` | UserDetails | 权限 ROLE_ADMIN/ROLE_USER |
| `SimpleGrantedAuthority` | GrantedAuthority | 权限字符串 |
| `RememberMeServicesImpl` | PersistentTokenBasedRememberMeServices | Cookie 登录 + 浏览器指纹 |
| `SecurityContextFilter` | SecurityContextPersistenceFilter | ThreadLocal 加载/清理 |

### 过滤器链 (`security/filter/`)

| 过滤器 | 职责 |
|--------|------|
| `ExceptionTranslationFilter` | AuthException → 401 / AccessDenied → 403 |
| `AuthorizationFilter` | URL 规则匹配 |
| `UsernamePasswordAuthFilter` | 表单登录处理 |
| `BearerTokenAuthFilter` | JWT 无状态认证 |

### 安全功能全清单

| 功能 | 实现方式 |
|------|---------|
| **账号锁定** | 连续 5 次失败 → 锁定 15 分钟（`LockedException`） |
| **账号禁用** | `enabled=false` → 拒绝（`DisabledException`） |
| **密码过期** | 首次 + 定期 90 天（`CredentialsExpiredException`） |
| **密码策略** | ≥6 位，含字母+数字，最近 5 次不可重用 |
| **Remember-Me** | SHA-256 + 浏览器指纹 + 一次一用 token 刷新 |
| **并发会话** | 每账号 max 5 会话，超标踢最旧 |
| **MFA TOTP** | Google Authenticator 兼容，6 位 30 秒 |
| **JWT** | HS256，access/refresh token |
| **审计日志** | 10 种安全事件异步入库 |
| **方法安全** | `@PreAuthorize` / `@PostAuthorize` / `@RequireAuth` / `@RequireRole` |
| **安全头** | CSP / HSTS / XFO / XCTO / XSS-Protection |
| **CSRF** | Session UUID token |
| **限流** | 每分钟 5 次 / IP |
| **XSS** | HTML 实体转义 |
| **CORS** | 可配置策略 |
| **会话固定** | `changeSessionId()` + 旧 session 销毁 |
| **DSL** | `HttpSecurity` 声明式配置 |

### 审计事件

`LOGIN_SUCCESS` / `LOGIN_FAILURE` / `LOGOUT` / `PASSWORD_CHANGE` / `ACCOUNT_LOCKED` / `MFA_VERIFY` / `REMEMBER_ME_LOGIN` / `SESSION_KICKED`

---

## API

### `/api/auth` — 认证

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/csrf` | CSRF Token |
| POST | `/login` | 登录（rememberMe, mfaRequired） |
| POST | `/logout` | 登出 |
| GET | `/status` | 认证状态 |
| GET | `/profile` | 用户详情（权限/密码状态/MFA） |
| POST | `/change-password` | 改密 |
| GET | `/mfa/setup` | MFA 密钥 |
| POST | `/mfa/enable` | 启用 MFA |
| POST | `/mfa/verify` | 验证 MFA |

### `/api/admin` — 管理（需 ADMIN）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/sessions` | 活跃会话 |
| DELETE | `/sessions/{user}` | 踢出用户 |
| GET | `/audit` | 审计日志（分页） |
| GET | `/audit/user/{user}` | 用户日志 |
| GET | `/audit/event/{type}` | 事件日志 |

---

## 各分支对比

| 分支 | 架构 | 安全框架 | 分布式 |
|------|------|---------|--------|
| `monolithic` | 单机 Jar | 简单表单登录 | ❌ |
| `distributed` | 单机 Jar | 79 文件安全框架 | ❌ |
| `docker-distributed` | Docker Compose 6 容器 | 完整安全 + 全 Redis 状态共享 | ✅ |

---

## Docker 分布式部署（docker-distributed 分支）

### 架构

```
            Docker Host（1 台机器模拟多实例）
┌──────────────────────────────────────────────────────────┐
│                    ┌─→ wg-app1 (JVM 1) ─┐               │
│  Browser → nginx ─┼─→ wg-app2 (JVM 2) ─┼→ Redis ─┐     │
│    :80           └─→ wg-app3 (JVM 3) ─┘   MySQL  │     │
│                                                    │     │
│  Redis 存储（跨实例共享）：                            │     │
│    sessions:{user}  → ZSet    会话注册表             │     │
│    ratelimit:{ip}:* → INCR    限流计数器             │     │
│    wechat:access_token → SETNX 分布式锁             │     │
└──────────────────────────────────────────────────────────┘
```

### 什么是"实例"

一个实例 = 一个独立的 JVM 进程。3 个实例虽然在同一台机器上，但拥有独立的内存空间，互不可见。所有状态通过 Redis 共享，与真正多机器的行为完全一致。

### 一键启动

```bash
git checkout docker-distributed
cp .env.example .env          # 填入企业微信凭证
docker compose up -d           # 6 容器：nginx + 3x app + mysql + redis

# 查看日志（Nginx 轮询到哪个实例）
docker compose logs -f app1 app2 app3
```

### 验证分布式

```bash
# 1. 登录 → Nginx 路由到 app1
curl -X POST http://localhost/api/auth/login \
  -d "username=admin&password=你的密码" -c cookies.txt

# 2. 停掉 app1
docker stop wg-app1

# 3. 同一 cookie 访问 → Nginx 路由到 app2/app3
curl http://localhost/index -b cookies.txt
# ✅ 仍然登录（Session 从 Redis 恢复）

# 4. 验证限流跨实例
for i in $(seq 1 6); do
  curl -X POST http://localhost/api/auth/login \
    -d "username=admin&password=wrong" -H "X-CSRF-TOKEN: xxx"
done
# ✅ 第 6 次请求被限流（3 个实例共享计数器）
```

### 服务清单

| 服务 | 容器名 | 端口 | 资源限制 | 说明 |
|------|--------|------|---------|------|
| nginx | wg-nginx | 80 | 0.5CPU/128M | 负载均衡（max_fails=3, keepalive=32） |
| app1~3 | wg-app1/2/3 | 8082 | 1CPU/512M | Spring Boot 实例（独立 JVM） |
| mysql | wg-mysql | 3306 | 1CPU/512M | 持久化数据（healthcheck） |
| redis | wg-redis | 6379 | 0.5CPU/256M | 共享状态（AOF 持久化） |

### 分布式改造清单

| 组件 | 单机版 | 分布式版 |
|------|--------|---------|
| HttpSession | JVM 内存 | Redis + Spring Session |
| SessionRegistry | ConcurrentHashMap | Redis ZSet（全局并发会话限制） |
| RateLimitService | ConcurrentHashMap | Redis INCR + EXPIRE（全局限流） |
| WeChatTokenManager | volatile String（互踢） | Redis SETNX 分布式锁（单实例刷新） |
| JWT Secret | 每实例随机 | 共享环境变量 |

---

## 项目结构

```
src/main/java/com/school/wechatgroup/
├── annotation/        @RequireAuth / @RequireRole
├── config/            Auth/WebMvc/Security/CORS/Session
├── constant/          SessionKeys / OperationStatus
├── controller/        Auth / WeChatGroup / Page / Admin / Audit
├── entity/            13 个 JPA 实体
├── exception/         6 种 Auth 异常 + GlobalExceptionHandler
├── filter/            SecurityContext / SecurityHeaders / HSTS
├── interceptor/       Login / RememberMe
├── repository/        6 个 DAO
├── security/          🆕 自建安全框架
│   ├── annotation/    @PreAuthorize/@PostAuthorize/@PreFilter/@PostFilter
│   ├── config/        HttpSecurity DSL + SecurityFilterChain
│   ├── expression/    SpEL 表达式求值
│   ├── filter/        ExceptionTranslation/Authorization/Auth
│   ├── handler/       Success/Failure 处理器
│   ├── impl/          Provider / Token / RememberMe
│   ├── jwt/           JWT + BearerToken Filter
│   ├── matcher/       AntPathRequestMatcher
│   ├── password/      DelegatingPasswordEncoder {bcrypt}/{noop}/{sha256}
│   └── web/           RequestCache / SavedRequest
├── service/           Auth / GroupRecord / WeChatGroup / Audit / TOTP / RateLimit
├── task/              WeChatTokenManager（定时刷新 token）
├── util/              IpUtils / XssUtils / PasswordValidator / RateLimiter
└── vo/                LoginResult / AuthStatus / CreateGroupResult
```

---

## 配置参数 (`app.auth.*`)

| 参数 | 默认 | 说明 |
|------|------|------|
| `enabled` | false | 启用表单登录（false=纯 OAuth2） |
| `default-username` | admin | 默认管理员 |
| `default-password` | (随机生成) | 未配置时生成随机密码，日志中打印 |
| `lock-threshold` | 5 | 失败锁定阈值 |
| `lock-duration` | 15 | 锁定分钟数 |
| `max-sessions` | 5 | 最大并发会话 |
| `remember-me-validity-days` | 14 | Cookie 有效期 |
| `password-expiry-days` | 90 | 密码过期天数 |
| `password-history-size` | 5 | 最近不可重用数 |
| `mfa.enabled` | false | MFA TOTP 开关 |

### 数据库表

| 表 | 用途 |
|----|------|
| `t_app_user` | 用户（含锁定/审计/MFA 字段） |
| `t_group_operation` | 建群操作 |
| `t_wx_group` | 群聊记录 |
| `t_upload_file` | 文件存档 |
| `t_remember_me_token` | Remember-Me Token |
| `t_password_history` | 密码历史 |
| `t_audit_log` | 安全审计 |

---

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 运行环境 |
| Spring Boot | 3.2.5 | 框架 |
| Spring Data JPA | 3.2.5 | ORM |
| Spring AOP | 3.2.5 | 方法安全 |
| Spring Security Crypto | 6.2.4 | BCrypt |
| Thymeleaf | 3.x | 模板 |
| Apache POI | 5.3.0 | Excel/Word |
| OpenCSV | 5.7.1 | CSV |
| Spring Session | 3.2.x | Redis Session 共享 |
| Spring Data Redis | 3.2.x | Redis 数据访问 |
| Spring Boot Actuator | 3.2.5 | Docker 健康检查 |
| H2 | 2.x | 开发库 |
| MySQL | 8.0 | 生产库 |
| Redis | 7 | 共享状态 |
| Nginx | alpine | 负载均衡 |
| Docker Compose | 3.8 | 容器编排 |

---

## 运行时验证

本地 `./mvnw spring-boot:run` 实测 14 项全部通过：

```
✅ 登录(正确密码)          → success:true, mustChangePassword:true
✅ 认证状态                 → authenticated:true, role:ADMIN
✅ 用户 Profile             → 含 authorities/密码到期/账户状态/审计字段
✅ 修改密码                 → 密码修改成功，请重新登录
✅ 新密码登录               → mustChangePassword:false
✅ 错误密码                 → 用户名或密码错误
✅ CSRF 防护               → 无 token 返回 无效请求
✅ 管理员会话查询           → 返回活跃会话列表
✅ 首页(已登录)             → HTTP 200
✅ 登出                     → success:true
✅ 登出后状态               → authenticated:false
✅ 锁定测试(1-4次错误)      → 用户名或密码错误
✅ 锁定测试(第5次)          → 账号已被锁定，请15分钟后重试
✅ 限流测试(第6次)          → 登录尝试次数过多
```

---

## 部署到服务器

### 1. 打包

```bash
./mvnw clean package -DskipTests
# 产出: target/wechat-group-0.0.1-SNAPSHOT.jar
```

### 2. 上传到服务器

```bash
scp target/wechat-group-0.0.1-SNAPSHOT.jar root@你的服务器IP:/app/
```

### 3. 配置生产环境

编辑 `application-prod.properties`，填入真实密码：

```properties
spring.datasource.password=你的MySQL密码
app.base-url=https://你的穿透域名
```

### 4. 启动

```bash
ssh root@你的服务器IP
cd /app

# 用生产配置启动（后台运行 + 崩溃自动重启）
nohup java -jar wechat-group-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod > logs/app.log 2>&1 &

# 或者配成 systemd 服务开机自启
```

### 5. 开发 vs 生产

| 命令 | 数据库 | 用途 |
|------|--------|------|
| `./mvnw spring-boot:run` | H2 内存 | 本地开发 |
| `java -jar ...` | H2 内存 | 本地开发 |
| `java -jar ... --spring.profiles.active=prod` | MySQL | 服务器部署 |

### 6. 查日志

```bash
tail -f logs/wechat-group.log          # 实时日志
tail -100 logs/wechat-group.log        # 最近100行
grep "ERROR" logs/wechat-group.log     # 只看错误
```

### 7. 查数据库

**开发环境（H2）**：浏览器打开 `http://localhost:8082/h2-console`

```
JDBC URL: jdbc:h2:mem:wechat_group
用户名: sa  密码: (空)
```

**生产环境（MySQL）**：用 DBeaver 远程连接

```
主机: 服务器IP
端口: 3306
用户: wechat_app
密码: wechat_pass_2024
```

### 8. 远程查数据的原理

```
你的笔记本（DBeaver）
    ↓ TCP 连接 → 服务器IP:3306
服务器上的 MySQL
    ↓ 返回数据
你的笔记本上看到表和数据
```

---

## 许可证

Apache 2.0
