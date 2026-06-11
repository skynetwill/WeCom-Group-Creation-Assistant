# 版本更迭记录

## 技术名词解释

以下是本项目中使用的所有核心技术，按用途分类介绍。

---

### 一、基础框架层

**Spring Boot**
Java 世界里最主流的应用框架。它的核心思想是"约定优于配置"——你不用手动拼装各种组件，Spring Boot 根据你引入的依赖自动猜测你要做什么，帮你配置好一切。比如引入 `spring-boot-starter-web`，它就自动启动内嵌的 Tomcat 服务器、配置 JSON 解析器、注册 REST 控制器。Spring Boot 本质上是把 Spring Framework 繁琐的 XML 配置变成了自动化的"开箱即用"。

**Maven**
Java 项目的构建和依赖管理工具。你在 `pom.xml` 里声明要用什么库（比如 OpenCSV 5.7.1），Maven 自动从中央仓库下载对应的 JAR 包。`mvn compile` 编译项目，`mvn package` 打包成可运行的 JAR。Maven Wrapper（`mvnw`）让你不用预先安装 Maven——第一次运行时会自动下载。

**Apache Tomcat**
一个 Servlet 容器（也叫 Web 服务器）。它负责接收浏览器发来的 HTTP 请求，转交给你的 Java 代码处理，再把结果返回给浏览器。Spring Boot 内嵌了 Tomcat，所以你不需要单独安装 Nginx/Apache 就能运行 Web 应用。`server.port=8082` 就是告诉 Tomcat 监听哪个端口。

---

### 二、数据持久层

**MySQL**
全球最流行的开源关系型数据库。数据以"表"的形式组织，每张表有固定的列（字段），行与行之间通过主键和外键关联。本项目用 MySQL 存储建群操作记录、群聊信息、上传的文件和用户账号。

**JPA（Java Persistence API）**
Java 官方制定的数据库操作标准规范。它定义了一套接口（`EntityManager`、`@Entity`、`@Table`），但不负责具体实现。你可以把它理解为一套"数据库操作的统一语言"。

**Hibernate**
JPA 规范最流行的实现。它的核心功能叫 ORM（对象关系映射）——把你写的 Java 类自动对应到数据库的表，把 Java 对象的字段自动对应到表的列。你操作 Java 对象，Hibernate 自动翻译成 SQL 执行。比如 `userRepository.save(user)` 会被自动翻译成 `INSERT INTO t_app_user VALUES (...)`. `ddl-auto=update` 表示 Hibernate 启动时会自动检查数据库表结构，如果 Java 类新增了字段，它会自动在表里添加对应列。

**Spring Data JPA**
Spring 对 JPA 的进一步封装。你只需写一个接口继承 `JpaRepository<Entity, Long>`，就能自动获得 `save()`、`findById()`、`findAll()`、`delete()` 等方法，不需要写一行实现代码。还能按方法名自动生成查询——比如 `findByUsername(String username)` 会自动翻译成 `SELECT * FROM t_app_user WHERE username = ?`。

**HikariCP**
目前 Java 生态最快的数据库连接池。连接池的核心思想是"复用"——数据库连接是昂贵资源，每次都新建会很慢。HikariCP 预先创建好一批连接放在池子里，谁要用了就借一个，用完了还回去，而不是销毁重建。`maximum-pool-size=20` 表示最多同时持有 20 个连接。

**H2**
一个纯 Java 实现的内存数据库。不需要安装、不需要启动服务，你的 Java 程序启动时它就自动启动，程序关闭时数据和表结构都清空。通过 `MODE=MySQL` 可以让它模拟 MySQL 的 SQL 语法，这样你在本地开发时用 H2，部署到服务器上换成 MySQL，SQL 不用改。

---

### 三、前端技术

**Thymeleaf**
Spring Boot 官方推荐的服务器端模板引擎。它的特殊之处在于：`.html` 文件本身是完全合法的 HTML，可以直接用浏览器打开预览。所有的动态逻辑通过 `th:text`、`th:if`、`th:each` 等额外属性嵌入，后端处理时才被执行。跟 JSP 不同，Thymeleaf 不需要 Servlet 容器就能渲染，Spring Boot 直接支持。

**Bootstrap 5**
Twitter 开源的前端 CSS/JS 框架。它提供了一套现成的 UI 组件——按钮、卡片、导航栏、表单、弹窗等。你只需给 HTML 标签加 `class="btn btn-primary"` 就能获得一个漂亮的蓝色按钮，不用自己写 CSS。它的栅格系统通过 `col-md-6` 这样的 class 自动处理响应式布局，一套代码同时适配电脑和手机。

**Bootstrap Icons**
Bootstrap 配套的免费图标库，包含 2000+ 个 SVG 图标。用 `<i class="bi bi-wechat"></i>` 就能在页面显示一个小图标。

---

### 四、安全技术

**BCrypt**
目前业界最推荐的密码哈希算法。它的核心特性：每次加密自动生成一个随机盐值（salt），所以同样的密码两次加密结果完全不同；计算速度故意调慢（本文使用默认 10 轮），让暴力破解变得极度缓慢。即使数据库泄露，攻击者也很难从哈希值反推出原密码。

**CSRF（跨站请求伪造）**
一种 web 攻击方式。攻击者在你登录 A 网站后，诱导你访问一个恶意页面，那个页面自动向 A 网站发请求（比如转账、修改密码），浏览器会自动带上你的登录 Cookie，A 网站以为是你在操作。CSRF Token 的原理：每次渲染表单时生成一个随机 Token，存入用户 Session，提交表单时必须带上这个 Token，服务端验证一致才放行。攻击者无法获取这个 Token（Cookie 和 Session 不跨域传递）。

**HttpSession**
Web 服务器为每个访问者维护的一个"会话盒子"。浏览器第一次访问时，服务器创建一个 Session 并给浏览器发一个 Session ID（存 Cookie 里）。之后每次请求浏览器自动带上这个 ID，服务器就能认出"这是同一个人"。本项目用 Session 存储登录状态——`LOGIN_USER_ID` 属性有值说明已登录。

**HandlerInterceptor**
Spring MVC 提供的拦截器机制。它在请求到达 Controller 之前（preHandle）、之后（postHandle）、完成之后（afterCompletion）三个时间点执行自定义逻辑。本项目用 `preHandle` 检查 Session 中是否有 `LOGIN_USER_ID`，没有就重定向到登录页。

**Filter（Servlet 过滤器）**
比 Interceptor 更底层的拦截机制，工作在 Servlet 容器层面（Tomcat）。所有 HTTP 请求和响应都会经过 Filter。本项目用 `SecurityFilter` 给每个响应添加安全头（防 XSS / 点击劫持等）。

---

### 五、文件处理

**Apache POI**
Java 操作 Microsoft Office 文档的开源库。`poi-ooxml` 包同时支持：
- Excel（`.xlsx` 用 `XSSFWorkbook`，`.xls` 用 `HSSFWorkbook`）——读取单元格内容
- Word（`XWPFDocument`）——动态生成操作指南文档

为什么不用 EasyExcel？POI 同时支持 `.xlsx` 和 `.xls` 两种格式，并且附带了 Word 生成能力，一个依赖解决两个需求。

**OpenCSV**
专门处理 CSV 文件的轻量库。CSV 本质是纯文本，但手工处理会遇到编码问题（UTF-8 BOM）、逗号转义、引号包裹等边缘情况。OpenCSV 把这些处理干净了。

---

### 六、Spring 核心概念

**IoC（控制反转）/ DI（依赖注入）**
Spring 最核心的思想。传统编程中，类自己 `new` 出它需要的对象（控制权在自己手里）。Spring IoC 反转了这个过程——你告诉 Spring "我需要一个 `WeChatGroupService`"，Spring 负责创建好并"注入"给你。好处：类与类之间解耦，方便替换实现，方便单元测试（Mock 一个假的注入进去）。

**@Component / @Service / @Repository / @Controller**
这四个注解本质是一样的——告诉 Spring"这个类请帮我管理"（放进 IoC 容器）。不同名字只是语义区分：`@Service` 标记业务逻辑层，`@Repository` 标记数据访问层，`@Controller` 标记控制器层。

**@Bean**
用在方法上，把方法的返回值注册到 Spring IoC 容器。`@Bean public RestTemplate restTemplate()` 的意思是：Spring 启动时调用这个方法，把返回的 `RestTemplate` 对象管理起来，别的地方需要时注入。

**@ConfigurationProperties**
把 `application.properties` 中的配置自动绑定到 Java 对象的字段上。`@ConfigurationProperties(prefix = "wechat.work")` 会把 `wechat.work.corpid` 的值自动赋给 `WeChatProperties.corpid` 字段。比一个个写 `@Value` 更整洁。

**@ConditionalOnProperty**
条件装配注解。当 `application.properties` 中某个属性等于指定值时，才创建这个 Bean。`@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")` 的意思是：只有当 `app.auth.enabled=true` 时才创建这个 Bean。`false` 时整个类都不会被加载，零内存开销。

**@Transactional**
声明式事务管理。加在方法上表示这个方法里的所有数据库操作要么全部成功，要么全部回滚。比如建群时保存操作记录和群聊记录——如果群聊记录保存失败，操作记录也会自动撤销。

**@Scheduled**
定时任务。`@Scheduled(fixedRate = 3600000)` 表示每 3600000 毫秒（1 小时）执行一次这个方法。`@EnableScheduling` 在启动类上全局开启定时任务支持。

**volatile**
Java 关键字，保证多线程场景下的变量可见性。`WeChatTokenManager` 中的 `accessToken` 字段被定时任务线程写入，被业务线程读取。`volatile` 确保写入后所有线程立即看到最新值，而不是读到缓存的旧值。

---

### 七、设计模式

**策略模式（Strategy Pattern）**
定义一个接口（`FileParserStrategy`），多种实现（`CsvFileParser`、`ExcelFileParser`），运行时根据条件（文件扩展名）选择使用哪个实现。新的文件格式只需增加一个新类，不修改现有代码。

**工厂模式（Factory Pattern）**
`CreateGroupResultVO.success()` 和 `CreateGroupResultVO.failure()` 是静态工厂方法。它们隐藏了对象的创建细节——调用者不需要知道构造函数有多少参数，只需要告诉它"给我一个成功/失败的结果"。

---

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
