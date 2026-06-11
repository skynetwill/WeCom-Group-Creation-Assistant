# 版本更迭记录

## v2.0 — 生产就绪 (2026-06)

### 全新前端：Thymeleaf + Bootstrap 5

抛弃原先纯静态 HTML 方案，全面升级为 Thymeleaf 模板引擎 + Bootstrap 5 专业 UI 框架。

| 维度 | 旧方案 | 新方案 |
|------|--------|--------|
| 模板引擎 | 无（纯 HTML） | Thymeleaf（Spring Boot 官方推荐） |
| CSS 框架 | 内联手写 | Bootstrap 5 CDN |
| 图标 | 无 | Bootstrap Icons |
| 页面缓存 | 浏览器缓存 | 开发环境禁用缓存，实时生效 |
| 登录页 | 无 | 左右分栏专业布局 |
| 文件上传 | 原生选择器 | 拖拽上传 + 虚线框预览 |
| 响应式 | 无 | Bootstrap 栅格自动适配 |

**Thymeleaf**：Spring Boot 官方推荐的服务器端模板引擎。HTML 文件本身在浏览器和 IDE 中可以直接预览，模板语法以 `th:` 属性嵌入，不影响原生 HTML 结构。`spring.thymeleaf.cache=false` 确保开发时修改模板立即生效，无需重启。

**Bootstrap 5**：全球使用最广泛的前端框架。栅格系统自动处理 PC/平板/手机适配；`btn`、`card`、`alert` 等组件开箱即用。通过 CDN 引入，无构建工具依赖。

### 多环境配置

从单一 `application.properties` 拆分为三层：

```
application.properties        ← 公共配置 + spring.profiles.active
application-dev.properties    ← 开发环境（H2 + 调试日志 + auth=enabled）
application-prod.properties   ← 生产环境（MySQL + INFO 日志 + HikariCP）
```

Spring Boot 的 Profile 机制按 `spring.profiles.active` 自动加载对应配置文件，同名属性后者覆盖前者。

### 安全管理

全局安全响应头 Filter，拦截所有 HTTP 响应注入防护头：

| 头 | 值 | 防护 |
|------|------|------|
| `X-Content-Type-Options` | `nosniff` | 防止浏览器 MIME 嗅探 |
| `X-Frame-Options` | `DENY` | 防点击劫持 |
| `X-XSS-Protection` | `1; mode=block` | 启用浏览器 XSS 过滤 |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | 限制跨域 Referer |

### 数据库优化

**HikariCP 连接池**：生产环境配置了完整的连接池参数——最大 20 连接、最小 5 空闲、连接超时 30 秒、连接最大存活 30 分钟、归还时健康检查。Spring Boot 2+ 默认使用 HikariCP，无需额外依赖。

**H2 开发数据库**：本地开发不再需要安装 MySQL。H2 是 Java 嵌入式内存数据库，启动时自动建表，进程结束数据清空。通过 `MODE=MySQL` 参数模拟 MySQL 语法，开发和生产环境 SQL 完全兼容。

### 规范化架构

增加 4 个关键包：

| 包 | 职责 |
|------|------|
| `util/IpUtils.java` | IP 提取（多级代理穿透） |
| `util/SecurityUtils.java` | Session 认证工具 |
| `filter/SecurityFilter.java` | 全局安全响应头 |
| `exception/BusinessException.java` | Service 层业务异常 |

`BusinessException` 落地使用：Service 层校验失败（空文件名、不支持格式、空成员列表、密码错误）全部抛 `BusinessException`，由 `GlobalExceptionHandler` 统一捕获返回 JSON 错误。

---

## v1.3 — 架构规范化 (2026-06)

### 依赖注入统一为构造器注入

所有 `@Autowired` 字段注入改为 `private final` + 构造器注入：

```java
// 旧：字段注入（Spring 不推荐的隐藏依赖）
@Autowired
private WeChatGroupService service;

// 新：构造器注入（显式依赖、不可变、可测试）
private final WeChatGroupService service;
public Controller(WeChatGroupService service) {
    this.service = service;
}
```

Spring 4.3+ 对单构造器自动注入，无需 `@Autowired`。`private final` 保证依赖不可变，消除 NPE 风险。

### API 响应 JSON 化

`/api/group/create` 从返回纯字符串改为标准 JSON：

```json
{"success": true, "chatId": "wrFKkV...", "message": "建群成功..."}
{"success": false, "errcode": 60111, "errmsg": "...", "message": "成员不存在..."}
```

### 错误码翻译系统

从 17 条技术描述扩充至 43 条用户友好的中文解释，按类别组织：

| 类别 | 数量 | 示例 |
|------|------|------|
| 参数校验 | 10 | `40003 → "成员列表中包含无效UserID，请检查文件"` |
| 权限认证 | 8 | `48002 → "该应用没有创建群聊权限"` |
| 群聊相关 | 19 | `86104 → "群聊名称已被使用，请更换"` |
| 限流 | 3 | `45009 → "调用频率过高，请稍后重试"` |
| 通用 | 3 | `-1 → "系统繁忙，请稍后重试"` |

原始 `errcode`/`errmsg` 只在日志中记录，不暴露给用户。

---

## v1.2 — 安全加固 (2026-06)

### CSRF 防护

登录流程增加跨站请求伪造防护：

```
1. 浏览器 GET /api/auth/csrf → 获得随机 Token
2. 浏览器 POST /api/auth/login + Header: X-CSRF-TOKEN
3. 服务端比对 Session 中的 Token → 一致才放行
```

### 登录频率限制

基于 IP 的登录频率控制：

```
窗口：60 秒
上限：5 次
超过 → 返回"登录尝试次数过多，请稍后重试"
成功 → 清除计数
@Scheduled 定时清理（每 5 分钟清理过期条目）
```

### Session 固定攻击防护

登录成功后立即销毁旧 Session，创建新 Session：

```java
HttpSession oldSession = request.getSession(false);
oldSession.invalidate();                    // 销毁旧会话
HttpSession newSession = request.getSession(true);  // 创建新会话
newSession.setAttribute("LOGIN_USER_ID", userId);   // 在新会话中设置属性
```

### 异常信息脱敏

全局异常处理器不再返回 `e.getMessage()`，改为固定提示"系统内部错误，请联系管理员"，防止泄露数据库路径、API 密钥等敏感信息。

---

## v1.1 — 登录认证 (2026-06)

### BCrypt 密码哈希

引入 `spring-security-crypto`（仅加密模块，非完整 Spring Security 框架）：

| 方案 | 依赖 | 复杂度 | 适用场景 |
|------|------|--------|----------|
| Spring Security | 全套框架 | 高 | 大型企业应用 |
| spring-security-crypto | 仅 BCrypt | 低 | 本项目 |

BCrypt 特点：自动加盐（同一个密码每次加密结果不同）、可调节计算强度（默认 10 轮）、CPU 密集型抗暴力破解。

### 条件装配机制

整个认证系统通过 `app.auth.enabled` 一键开关：

```
app.auth.enabled=true → AuthConfig / AuthServiceImpl / LoginInterceptor / WebMvcConfig 全部加载
app.auth.enabled=false → 上述 Bean 全部不创建，退回 OAuth 流程
```

核心注解：`@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")`。Spring Boot 启动时读取配置决定是否创建 Bean，`false` 时零开销。

### HandlerInterceptor + HttpSession

放弃 Spring Security 的完整认证链，使用更轻量的拦截器方案：

```java
public boolean preHandle(HttpServletRequest request, ...) {
    if (request.getSession().getAttribute("LOGIN_USER_ID") != null) {
        return true;  // 已登录，放行
    }
    response.sendRedirect("/login");  // 未登录，跳转
    return false;
}
```

### 账号系统

- `t_app_user` 表存储用户信息（username / password / nickname / enabled）
- 首次启动自动创建管理员（`app.auth.default-username` / `app.auth.default-password`）
- 默认密码 `admin123` 启动时有 WARN 日志提醒修改
- 用户名不存在和密码错误统一返回"用户名或密码错误"（防用户枚举）

---

## v1.0 — 数据库持久化 (2026-05)

### 从无数据库到 JPA + MySQL

| 维度 | 旧方案 | 新方案 |
|------|--------|--------|
| 操作记录 | 分散的日志文件 | 统一的 `t_group_operation` 表 |
| 群聊记录 | 无持久化 | `t_wx_group` 表 |
| 文件存档 | 无 | `t_upload_file` 表（LONGBLOB） |
| 用户管理 | 无 | `t_app_user` 表 |
| ORM | 无 | Hibernate 6（JPA 标准实现） |

**Spring Data JPA**：只需定义接口继承 `JpaRepository`，无需写 SQL。Spring 自动生成 `save()` / `findAll()` / `findById()` 等 CRUD 方法。`findByUsername()` 等方法名即查询语义。

**Hibernate**：Java 生态最成熟的 ORM。`ddl-auto=update` 开发阶段自动建表、自动增加新列（不删除旧列）。生产环境改为 `validate`，只校验不修改。

**`@CreationTimestamp`**：Hibernate 的专用注解，实体新建时自动填充 `createdAt` 字段为当前时间。替代手动 `setCreatedAt(LocalDateTime.now())`。

### 数据库事务

`@Transactional` 声明式事务：`GroupRecordServiceImpl.recordGroupCreation()` 内保存 `GroupOperation` 和 `WxGroup` 共享一个事务。文件存档用 `TransactionTemplate` 在独立事务中执行，失败不回滚主记录。

### 数据库索引

| 表 | 索引 | 用途 |
|------|------|------|
| `t_group_operation` | `idx_operation_id` / `idx_created_at` | 按操作 ID 查询 / 按时间排序 |
| `t_wx_group` | `idx_chat_id` | 按群聊 ID 查群信息 |
| `t_app_user` | `idx_username` (UNIQUE) | 登录时查用户 |

---

## v0.1 — 项目起点 (2025)

**技术栈**：Spring Boot 3.2.5 + Java 17 + Apache POI + OpenCSV。

**核心能力**：
- CSV / Excel 文件解析（策略模式）
- 企业微信 OAuth2 认证
- `WeChatTokenManager` 定时刷新 Token（`@Scheduled` + `volatile`）
- `RestTemplate` 调用企业微信建群 API
- 纯 HTML 前端（内联 CSS/JS，无框架）

**策略模式**（Strategy Pattern）：

```
FileParserStrategy (接口)
    ├── parse(MultipartFile)
    └── supportedExtension()

CsvFileParser("csvParser")   ExcelFileParser("excelParser")
    OpenCSV 解析                Apache POI 解析 (.xlsx/.xls)
```

Spring 自动收集所有 `@Component` 标注的 `FileParserStrategy` 到 `Map<beanName, instance>`。Service 根据文件扩展名从中选取解析器。新增格式只需添加一个 `@Component` 类，无需改现有代码。

---

## 技术决策记录

### 为什么不用 Spring Security？

| 考量 | Spring Security | 本项目方案 |
|------|----------------|------------|
| 项目规模 | 适合 | 21 个 Java 文件，过重 |
| 学习成本 | 高（Filter Chain / Security Context / GrantedAuthority） | 低（HandlerInterceptor 就够了） |
| 认证需求 | 角色/权限/OAuth2/Remember-Me | 仅用户名密码登录 |
| 启动速度 | 加载大量自动配置 | 零额外启动开销 |

### 为什么用 JPA 而不是 MyBatis？

| 考量 | JPA | MyBatis |
|------|-----|---------|
| 本项目表关系 | 4 张表，无复杂关联查询 | - |
| CRUD 代码量 | `JpaRepository` 零实现 | 每个方法写 XML |
| DDL | `ddl-auto=update` 自动建表 | 需手动管理 |
| Spring Boot 集成 | `spring-boot-starter-data-jpa` 开箱即用 | 需额外配置 |

### 为什么文件存数据库而不是文件系统？

| 方案 | 优点 | 缺点 |
|------|------|------|
| 数据库 LONGBLOB | 备份 DB 即备份文件、权限统一 | 数据库体积增长 |
| 文件系统 | 数据库轻量、备份分离 | 需单独管理文件权限和备份 |

本项目选择数据库方案——内部工具、文件量小、运维简单优先。

### 为什么用 H2 做开发数据库？

| 方案 | 优点 | 缺点 |
|------|------|------|
| 本地 MySQL | 与生产环境一致 | 需安装、配置、启动 |
| H2 内存模式 | 零安装、启动即用 | 进程结束数据清空 |

H2 通过 `MODE=MySQL` 参数模拟 MySQL SQL 方言，开发和生产 SQL 完全兼容。测试数据不需要持久化，内存模式反而是优点。
