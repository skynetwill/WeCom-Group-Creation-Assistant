# 企业微信批量建群工具 — 技术详解

## 目录

- [第一章：项目概述](#第一章项目概述)
- [第二章：项目结构与各层职责](#第二章项目结构与各层职责)
- [第三章：Spring Boot 启动流程](#第三章spring-boot-启动流程)
- [第四章：配置管理](#第四章配置管理)
- [第五章：Controller 层](#第五章controller-层)
- [第六章：Service 层](#第六章service-层)
- [第七章：数据持久层（JPA + Hibernate）](#第七章数据持久层jpa--hibernate)
- [第八章：认证与安全](#第八章认证与安全)
- [第九章：文件解析（策略模式）](#第九章文件解析策略模式)
- [第十章：异常处理](#第十章异常处理)
- [第十一章：前端（Thymeleaf + Bootstrap 5）](#第十一章前端thymeleaf--bootstrap-5)
- [第十二章：定时任务](#第十二章定时任务)
- [第十三章：构建与部署](#第十三章构建与部署)

---

## 第一章：项目概述

### 1.1 这个项目解决什么问题

企业微信管理员需要为班级、部门、项目组等批量创建群聊。手动操作需要在企业微信管理后台逐个添加成员，效率极低。本工具提供：

1. **文件上传**：管理员上传一份包含成员 userid 的 CSV 或 Excel 文件
2. **自动建群**：后端解析文件，调用企业微信 API 自动创建群聊
3. **操作记录**：每次操作自动入库，可追溯谁在什么时间创建了什么群
4. **登录认证**：管理员需登录后才能使用，防止未授权操作

### 1.2 整体架构（分层架构图）

```
浏览器 (login.html / index.html)
      │
      ▼
┌──────────────────────────────────────────────────────┐
│  Controller 层 (HTTP 入口)                            │
│  PageController  AuthController  WeChatGroupController│
└──────────────────────────────────────────────────────┘
      │                        │
      ├────────────────────────┤
      ▼                        ▼
┌──────────────┐    ┌──────────────────────────┐
│ Service 层   │    │ 认证拦截器 (Interceptor)  │
│ 接口+实现   │    │ 安全过滤器 (Filter)       │
└──────────────┘    └──────────────────────────┘
      │                        │
      ├────────────┬───────────┤
      ▼            ▼           ▼
┌──────────┐ ┌──────────┐ ┌──────────────────┐
│Repository│ │TokenManager│ │FileParserStrategy│
│(JPA 接口)│ │ (定时任务) │ │ (策略模式)       │
└──────────┘ └──────────┘ └──────────────────┘
      │            │
      ▼            ▼
┌──────────────────────────────────────┐
│  MySQL 数据库          │ 企业微信 API │
│  t_group_operation     │ qyapi.weixin │
│  t_wx_group            │ .qq.com     │
│  t_upload_file         │             │
│  t_app_user            │             │
└──────────────────────────────────────┘
```

### 1.3 数据流：从浏览器点击到群聊创建的全过程

用户在前端点击"一键创建群聊"按钮后，完整数据流如下：

```
[1] 浏览器 index.html
    │  JavaScript 读取表单数据(groupName, ownerId, file)
    │  构造 FormData 对象
    │  fetch() 发 POST 到 /api/group/create
    ▼
[2] LoginInterceptor.preHandle()
    │  检查 session 中是否存在 LOGIN_USER_ID
    │  未登录 → 重定向到 /login
    │  已登录 → 放行
    ▼
[3] WeChatGroupController.createGroup()
    │  从 request 获取 userId（session 优先，fallback 到参数）
    │  校验 groupName、ownerId、file 非空
    ▼
[4] WeChatGroupServiceImpl.createGroupFromFile()
    │  提取文件扩展名 (.csv / .xlsx / .xls)
    │  从 parserMap 中选择对应的 FileParserStrategy
    │  CsvFileParser.parse() 或 ExcelFileParser.parse() → List<String> userList
    │  群主 ownerId 加入 userList 首部
    │  stream + Set 去重
    ▼
[5] WeChatGroupServiceImpl.callCreateGroupApi()
    │  从 WeChatTokenManager.getAccessToken() 获取 token
    │  构造 HttpEntity (JSON 请求体)
    │  restTemplate.exchange() 调用企业微信 API
    ▼
[6] 企业微信 API (https://qyapi.weixin.qq.com/cgi-bin/appchat/create)
    │  返回 JSON: { errcode: 0, chatid: "xxx" } 或 { errcode: 40003, errmsg: "..." }
    ▼
[7] CreateGroupResultVO.success() 或 .failure()
    │  错误码 errcode 通过 ERROR_EXPLAIN 映射翻译为用户友好消息
    ▼
[8] GroupRecordServiceImpl.recordGroupCreation()
    │  @Transactional 事务
    │  生成 operationId (UUID 前12位)
    │  写入 t_group_operation (操作记录，必写)
    │  写入 t_wx_group (仅成功时写)
    │  写入 t_upload_file (TransactionTemplate 独立事务，失败不影响主记录)
    ▼
[9] Controller 返回 Map<String, Object> JSON 给浏览器
    │  成功: { success: true, chatId: "xxx", message: "建群成功！..." }
    │  失败: { success: false, message: "错误描述", errcode: 40003 }
    ▼
[10] 浏览器 index.html
     │  解析 JSON 响应
     │  显示绿色成功卡片 或 红色错误卡片
```

---

## 第二章：项目结构与各层职责

### 2.1 完整目录树（每个文件一行说明）

```
wechat-group/
├── pom.xml                                    # Maven 构建定义（依赖、构建插件）
├── mvnw                                       # Maven Wrapper（Linux/Mac 脚本）
├── mvnw.cmd                                   # Maven Wrapper（Windows 脚本）
├── CLAUDE.md                                  # AI 编码助手上下文文件
├── TECHNICAL_GUIDE.md                         # 本文件
│
├── src/main/java/com/school/wechatgroup/
│   ├── WechatGroupApplication.java            # 启动类 + @Bean 注册
│   │
│   ├── config/
│   │   ├── AuthConfig.java                    # 条件配置：BCryptPasswordEncoder Bean
│   │   ├── AuthProperties.java                # app.auth.* 配置属性绑定
│   │   ├── WebMvcConfig.java                  # 拦截器注册配置
│   │   └── WeChatProperties.java              # wechat.work.* 配置属性绑定
│   │
│   ├── constant/
│   │   ├── OperationStatus.java               # 枚举：SUCCESS, FAILED
│   │   └── SessionKeys.java                   # Session 键名常量
│   │
│   ├── controller/
│   │   ├── AuthController.java                # 登录/登出/CSRF/状态 API
│   │   ├── PageController.java                # 页面视图返回（Thymeleaf）
│   │   └── WeChatGroupController.java         # 建群 API + OAuth2 + 模板下载
│   │
│   ├── entity/
│   │   ├── AppUser.java                       # 用户表（t_app_user）
│   │   ├── GroupOperation.java                # 操作记录表（t_group_operation）
│   │   ├── UploadFile.java                    # 上传文件表（t_upload_file）
│   │   └── WxGroup.java                       # 群聊记录表（t_wx_group）
│   │
│   ├── exception/
│   │   ├── BusinessException.java             # 自定义业务异常
│   │   └── GlobalExceptionHandler.java        # 全局异常拦截器
│   │
│   ├── filter/
│   │   └── SecurityFilter.java                # HTTP 安全响应头注入
│   │
│   ├── interceptor/
│   │   └── LoginInterceptor.java              # 登录状态检查拦截器
│   │
│   ├── repository/
│   │   ├── AppUserRepository.java             # 用户表 JPA 接口
│   │   ├── GroupOperationRepository.java      # 操作记录表 JPA 接口
│   │   ├── UploadFileRepository.java          # 上传文件表 JPA 接口
│   │   └── WxGroupRepository.java             # 群聊记录表 JPA 接口
│   │
│   ├── service/
│   │   ├── AuthService.java                   # 认证服务接口
│   │   ├── GroupRecordService.java            # 操作记录服务接口
│   │   ├── WeChatGroupService.java            # 建群服务接口
│   │   ├── impl/
│   │   │   ├── AuthServiceImpl.java           # 认证服务实现（BCrypt 验证）
│   │   │   ├── GroupRecordServiceImpl.java    # 操作记录实现（事务管理）
│   │   │   └── WeChatGroupServiceImpl.java    # 建群服务实现（API 调用）
│   │   └── parser/
│   │       ├── FileParserStrategy.java         # 文件解析策略接口
│   │       ├── CsvFileParser.java              # CSV 解析实现（OpenCSV）
│   │       └── ExcelFileParser.java            # Excel 解析实现（Apache POI）
│   │
│   ├── task/
│   │   └── WeChatTokenManager.java            # Token 管理（定时刷新）
│   │
│   ├── util/
│   │   ├── IpUtils.java                       # 客户端 IP 提取工具
│   │   └── SecurityUtils.java                 # Session 用户信息读取
│   │
│   └── vo/
│       ├── AuthStatusVO.java                  # 认证状态视图对象
│       ├── CreateGroupResultVO.java           # 建群结果视图对象
│       └── LoginResultVO.java                 # 登录结果视图对象
│
├── src/main/resources/
│   ├── application.properties                 # 公共配置 + active profile
│   ├── application-dev.properties             # 开发环境配置（H2 + debug）
│   ├── application-prod.properties            # 生产环境配置（MySQL + 连接池）
│   ├── application.properties.example         # 配置文件示例模板
│   ├── application-dev.properties.example     # 开发环境示例模板
│   ├── application-prod.properties.example    # 生产环境示例模板
│   ├── logback-spring.xml                     # 日志配置（控制台 + 文件）
│   ├── templates/
│   │   ├── login.html                         # 登录页面（Thymeleaf 模板）
│   │   └── index.html                         # 主页面（三标签：建群/指南/模板下载）
│   └── static/
│       └── templates/
│           └── member_template.csv            # 静态 CSV 模板占位文件
│
└── src/test/java/com/school/wechatgroup/
    └── service/parser/
        ├── CsvFileParserTest.java             # CSV 解析器单元测试（5 用例）
        └── ExcelFileParserTest.java           # Excel 解析器单元测试（6 用例）
```

### 2.2 分层架构解释

分层架构将软件按职责拆分为不同层，每层只与相邻层交互。这种设计的核心目的是**控制依赖方向**和**隔离变化**。

```
调用方向: Controller → Service → Repository → Entity
依赖方向: 上层依赖下层（单向）
```

**Controller 层**：接收 HTTP 请求，提取参数，调用 Service，返回 JSON/HTML。Controller 不应包含任何业务逻辑。代码示例：

```java
// PageController.java — 只负责返回视图名，不处理业务
@Controller
public class PageController {
    @GetMapping({"/login", "/login.html"})
    public String loginPage() {
        return "login";  // Thymeleaf 会找 templates/login.html
    }
}
```

**Service 层**：包含业务逻辑。接口定义行为契约，实现类执行具体操作。上层（Controller）只知道接口，不知道实现。

```java
// WeChatGroupService.java — 接口
public interface WeChatGroupService {
    CreateGroupResultVO createGroupFromFile(MultipartFile file, String groupName, String ownerId);
}

// WeChatGroupServiceImpl.java — 实现（@Service 标记，Spring 管理）
@Service
public class WeChatGroupServiceImpl implements WeChatGroupService {
    // ... 文件解析 + API 调用 + 错误翻译
}
```

**Repository 层**：数据持久化接口。使用 Spring Data JPA，只需声明方法签名，Spring 自动生成实现。

```java
// AppUserRepository.java — 无需写任何实现代码
@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);  // Spring 自动生成查询
}
```

**Entity 层**：Java 对象到数据库表的映射。每个 `@Entity` 类对应一张数据库表，每个字段对应一列。

```java
// AppUser.java — Java 类对应数据库 t_app_user 表
@Entity
@Table(name = "t_app_user")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;       // → 数据库自增主键 id
    private String username;  // → username 列
    private String password;  // → password 列
}
```

### 2.3 为什么用分层架构

**直接原因：**

1. **单一职责**：每个类只做一件事。Controller 只管接收请求和返回响应，不知道数据怎么存的。Service 只管业务逻辑，不知道 HTTP 细节。

2. **依赖倒置**：Service 接口由 Service 层定义，Controller 依赖接口而非实现。ServiceImpl 可以随时替换（比如改调用另一个建群 API），Controller 不需要修改。

3. **可测试性**：测试 Controller 时可以 mock Service 接口；测试 Service 时可以 mock Repository 接口。各层独立测试。

4. **团队协作**：前端工程师专注 Controller 层，后端工程师专注 Service 层，DBA 专注数据层。接口约定先行，并行开发。

**反例（如果不分层）：**

```
// 把 HTTP 请求处理 + 业务逻辑 + 数据库操作全写在一个方法里
@PostMapping("/api/group/create")
public Map createGroup(...) {
    // 解析文件 ...
    // 调用微信 API ...
    // 写数据库 ...
    // 返回结果 ...
    // 所有代码混在一起，修改任何一部分都可能影响其他部分
}
```

---

## 第三章：Spring Boot 启动流程

### 3.1 启动类完整分析

**文件位置**: `src/main/java/com/school/wechatgroup/WechatGroupApplication.java`

```java
package com.school.wechatgroup;

import com.school.wechatgroup.config.AuthProperties;
import com.school.wechatgroup.config.WeChatProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication                           // [注解1]
@EnableScheduling                                // [注解2]
@EnableConfigurationProperties({AuthProperties.class, WeChatProperties.class})  // [注解3]
public class WechatGroupApplication {

    public static void main(String[] args) {
        SpringApplication.run(WechatGroupApplication.class, args);  // [行4]
    }

    @Bean                                         // [注解5]
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**逐行解释：**

**第 11 行 — `@SpringBootApplication`**

这是 Spring Boot 最核心的注解。它是一个**组合注解**，等价于同时声明了以下三个注解：

```java
@SpringBootConfiguration  // 等同于 @Configuration，标记此类是配置类
@EnableAutoConfiguration  // 启动 Spring Boot 自动配置机制
@ComponentScan            // 扫描当前类所在包及其子包中的 @Component/@Service/@Repository/@Controller
```

- `@SpringBootConfiguration`：告诉 Spring 这个类是一个 Java 配置类，等同于传统的 XML 配置文件。`main()` 方法作为程序的入口。

- `@EnableAutoConfiguration`：Spring Boot 的核心自动配置能力。它会根据 classpath 中的 jar 包自动配置 Spring 应用。例如：
  - 检测到 `spring-boot-starter-data-jpa` → 自动配置 DataSource、EntityManagerFactory
  - 检测到 `spring-boot-starter-thymeleaf` → 自动配置 Thymeleaf 模板引擎
  - 检测到 `spring-boot-starter-web` → 自动配置 DispatcherServlet、HttpMessageConverter

  工作原理：`@EnableAutoConfiguration` 触发 `SpringFactoriesLoader` 加载 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中列出的所有自动配置类，每个自动配置类通过 `@ConditionalOnClass`、`@ConditionalOnMissingBean` 等条件注解决定是否生效。

- `@ComponentScan`：Spring 的组件扫描机制。默认扫描**当前类所在包**（`com.school.wechatgroup`）及其所有子包。找到 `@Component`、`@Service`、`@Repository`、`@Controller` 等注解的类，自动创建实例并纳入 Spring IoC 容器管理。

**第 12 行 — `@EnableScheduling`**

启用 Spring 的定时任务支持。没有这个注解，`@Scheduled` 注解的方法不会被执行。本项目中的定时任务包括：
- `WeChatTokenManager.refreshAccessToken()` — 每小时刷新一次企业微信 access_token
- `AuthController.cleanRateLimitCache()` — 每 5 分钟清理一次登录频率限制缓存

Spring 内部使用 `TaskScheduler` 接口管理定时任务，默认使用单线程池。所有 `@Scheduled` 方法共享这个线程池。

**第 13 行 — `@EnableConfigurationProperties`**

将配置类注册到 Spring 容器。这里注册了两个配置类：

```java
@EnableConfigurationProperties({AuthProperties.class, WeChatProperties.class})
```

- `WeChatProperties`：绑定 `application-dev.properties` 中以 `wechat.work` 为前缀的属性（corpid、corpsecret、agentid）
- `AuthProperties`：绑定 `application.properties` 中以 `app.auth` 为前缀的属性（enabled、default-username、default-password）

使用 `@EnableConfigurationProperties` 注册的类，会自动被 Spring 管理，可以在其他 Bean 中通过构造器注入使用。同时，`@ConfigurationProperties` 类会被 `spring-boot-configuration-processor` 处理，生成 IDE 元数据（`META-INF/spring-configuration-metadata.json`），使 IDE 可以在编辑 `application.properties` 时提供自动补全。

**第 17-19 行 — `main()` 方法**

```java
public static void main(String[] args) {
    SpringApplication.run(WechatGroupApplication.class, args);
}
```

`SpringApplication.run()` 是 Spring Boot 应用的启动入口。它执行以下步骤：

1. 创建 `SpringApplication` 实例，记录启动源（`WechatGroupApplication.class`）
2. 推断应用类型（SERVLET 类型，因为 classpath 中有 `DispatcherServlet`）
3. 创建 `ApplicationContext`（默认为 `AnnotationConfigServletWebServerApplicationContext`）
4. 扫描并加载所有自动配置类
5. 启动内嵌 Web 服务器（Tomcat，默认端口 8082）
6. 完成 Bean 的创建和依赖注入

**第 21-24 行 — `@Bean` 手动注册**

```java
@Bean
public RestTemplate restTemplate() {
    return new RestTemplate();
}
```

`@Bean` 注解告诉 Spring："这个方法的返回值要作为 Bean 纳入 IoC 容器管理"。这里手动创建 `RestTemplate` 是因为 Spring Boot 没有为 `RestTemplate` 提供自动配置（Spring Boot 3.x 推荐使用 `RestClient` 或 `WebClient`，但 `RestTemplate` 依然可手动注册）。

`RestTemplate` 是 Spring 提供的 HTTP 客户端工具，用于发送 REST 请求。本项目中的使用场景：
- `WeChatTokenManager`：调用 `GET /cgi-bin/gettoken` 获取 access_token
- `WeChatGroupServiceImpl`：调用 `POST /cgi-bin/appchat/create` 创建群聊
- `WeChatGroupController`：调用 `GET /cgi-bin/user/getuserinfo` 通过 OAuth code 获取 userId

**为什么用构造器注入而不共享同一个 `RestTemplate`？**

注意代码中所有使用 `RestTemplate` 的地方都是**构造器注入**：

```java
// WeChatTokenManager.java 第 22-24 行
public WeChatTokenManager(WeChatProperties properties, RestTemplate restTemplate) {
    this.properties = properties;
    this.restTemplate = restTemplate;
}

// WeChatGroupServiceImpl.java 第 92-97 行
public WeChatGroupServiceImpl(Map<String, FileParserStrategy> parserMap,
                               RestTemplate restTemplate,
                               WeChatTokenManager tokenManager) {
    this.parserMap = parserMap;
    this.restTemplate = restTemplate;
    this.tokenManager = tokenManager;
}
```

因为 `restTemplate()` 方法注册的是一个**单例 Bean**，所有地方注入的都是同一个实例。Spring 默认的 Bean 作用域（scope）就是 singleton。

**为什么不直接在代码里 `new RestTemplate()`？**

通过 Spring 管理的好处：
1. 单例复用，避免每次创建新对象的开销
2. 可以对 `RestTemplate` 添加拦截器（如日志拦截器、认证拦截器）统一处理
3. 测试时可以 mock 替换

---

## 第四章：配置管理

### 4.1 application.properties 键值对格式

Spring Boot 支持两种配置格式：`.properties` 和 `.yml`。本项目使用 `.properties`：

```properties
# 文件: application.properties
server.port=8082                          # 嵌套结构用 . 分隔：server.port
spring.profiles.active=dev                # 激活哪个环境的配置
app.base-url=https://xxx.ngrok-free.dev   # 自定义属性：app.base-url
app.auth.default-username=admin
app.auth.default-password=admin123
spring.servlet.multipart.max-file-size=10MB
spring.thymeleaf.cache=false
```

**键名规则**：`.` 表示层级关系。`spring.jpa.properties.hibernate.dialect` 在 Java 中等价于 `spring.jap.properties.hibernate.dialect`.

### 4.2 多环境配置机制

Spring Boot 使用 `spring.profiles.active` 指定激活的环境。加载顺序：

```
1. application.properties           (所有环境共享)
2. application-{profile}.properties (对应环境的配置，覆盖同名 key)
```

```properties
# application.properties
spring.profiles.active=dev           # 激活开发环境

# application-dev.properties
spring.datasource.url=jdbc:h2:mem:wechat_group   # 开发：内存数据库
spring.jpa.hibernate.ddl-auto=update              # 开发：自动建表
spring.jpa.show-sql=true                          # 开发：打印 SQL
logging.level.com.school.wechatgroup=DEBUG         # 开发：DEBUG 日志

# application-prod.properties
spring.datasource.url=jdbc:mysql://localhost:3306/wechat_group  # 生产：MySQL
spring.jpa.hibernate.ddl-auto=validate                          # 生产：只校验不建表
spring.jpa.show-sql=false                                       # 生产：不打印 SQL
logging.level.com.school.wechatgroup=INFO                       # 生产：INFO 日志
spring.datasource.hikari.maximum-pool-size=20                   # 生产：连接池优化
```

**为什么开发和生产用不同数据库？**

| 方面 | 开发环境 (H2) | 生产环境 (MySQL) |
|------|--------------|-----------------|
| 安装依赖 | 无需安装，Java 内存中运行 | 需要安装 MySQL 8.0+ |
| 数据持久化 | 进程停止数据丢失 | 数据永久保留 |
| 性能 | 内存级速度 | 磁盘 I/O |
| 适用场景 | 本地开发、测试 | 实际部署 |
| ddl-auto | `update` 自动建表 | `validate` 只验证不修改 |

### 4.3 @ConfigurationProperties 配置绑定

**文件**: `src/main/java/com/school/wechatgroup/config/WeChatProperties.java`

```java
@ConfigurationProperties(prefix = "wechat.work")   // 绑定 wechat.work.* 前缀的配置
public class WeChatProperties {
    private String corpid;      // 对应 wechat.work.corpid
    private String corpsecret;  // 对应 wechat.work.corpsecret
    private String agentid;     // 对应 wechat.work.agentid

    // getter 和 setter 是必须的，Spring 通过反射调用 setter 注入值
    public String getCorpid() { return corpid; }
    public void setCorpid(String corpid) { this.corpid = corpid; }
    // ... 其他 getter/setter
}
```

**绑定原理：**

1. 应用启动时，`@EnableConfigurationProperties(WeChatProperties.class)` 将此类注册为 Bean
2. Spring Boot 的 `ConfigurationPropertiesBindingPostProcessor` 在 Bean 初始化后，反射读取 `prefix` 值
3. 从 `Environment` 对象中找出所有 `wechat.work.*` 的配置项
4. 通过 `setCorpid()`、`setCorpsecret()`、`setAgentid()` 逐个注入值

**属性名映射规则**：`wechat.work.corpid` 中的 `corpid` 会映射到 `setCorpid()` 方法（驼峰命名）。也支持 `wechat.work.corp-id` 或 `WECHAT_WORK_CORPID`（环境变量）的宽松绑定。

**AuthProperties.java** 同理：

```java
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {
    private boolean enabled = false;            // 默认值 false
    private String defaultUsername = "admin";   // 默认值
    private String defaultPassword = "admin123"; // 默认值
    // ...
}
```

注意 Java 字段初始值即为默认值，如果 `application.properties` 中没有对应的配置项，则使用默认值。

### 4.4 @Value 注解的用法

`@ConfigurationProperties` 适合绑定**一组相关配置**。对于**单个独立配置**，用 `@Value` 更简洁：

```java
// WeChatGroupController.java 第 53-54 行
@Value("${app.base-url}")
private String baseUrl;
```

`@Value` 支持 SpEL（Spring Expression Language）语法：
- `@Value("${key}")` — 读取配置值，不存在则启动报错
- `@Value("${key:default}")` — 读取配置值，不存在使用默认值
- `@Value("#{bean.method()}")` — SpEL 表达式，调用 Bean 方法

**`@ConfigurationProperties` vs `@Value`**：

| 特性 | @ConfigurationProperties | @Value |
|------|-------------------------|--------|
| 适用场景 | 一组相关属性 | 单个属性 |
| 类型安全 | 支持（编译期检查） | 不直接支持 |
| 校验 | 支持 @Validated + JSR-303 | 不支持 |
| 宽松绑定 | 支持 | 不支持 |
| 代码量 | 需要写 getter/setter | 一行 |

### 4.5 @ConditionalOnProperty 条件装配

**文件**: `src/main/java/com/school/wechatgroup/config/AuthConfig.java`

```java
@Configuration
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public class AuthConfig {

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();  // 强度默认 10（2^10 轮哈希）
    }
}
```

**作用**：只有当 `application.properties` 中 `app.auth.enabled=true` 时，这个配置类才生效。如果设为 `false`，整个 `AuthConfig` 都不会被加载，其中的 `BCryptPasswordEncoder` Bean 也不会创建。

**为什么这样设计？**

本项目开发阶段允许关闭认证功能（`app.auth.enabled=false`），直接绕过登录直接使用。生产环境再开启（`app.auth.enabled=true`）。

**条件注解体系**（Spring Boot 自动配置的核心机制）：

| 注解 | 生效条件 |
|------|---------|
| `@ConditionalOnProperty` | 配置项存在且等于指定值 |
| `@ConditionalOnClass` | classpath 中存在指定类 |
| `@ConditionalOnMissingClass` | classpath 中不存在指定类 |
| `@ConditionalOnBean` | 容器中存在指定 Bean |
| `@ConditionalOnMissingBean` | 容器中不存在指定 Bean |
| `@ConditionalOnWebApplication` | 当前是 Web 应用 |

本项目中的使用：
- `AuthConfig`：`@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")`
- `WebMvcConfig`：同上，登录拦截器只在认证启用时注册
- `AuthServiceImpl`：`@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")`

---

## 第五章：Controller 层

### 5.1 @RestController vs @Controller

```java
// PageController.java — 返回视图名（HTML 页面）
@Controller
public class PageController {
    @GetMapping("/login")
    public String loginPage() {
        return "login";  // Thymeleaf 查找 templates/login.html
    }
}

// AuthController.java — 返回 JSON
@RestController
public class AuthController {
    @PostMapping("/api/auth/login")
    public Map<String, Object> login(...) {
        // 返回 Map 自动转 JSON
    }
}
```

**区别**：

- `@Controller`：标记一个类是 Spring MVC 控制器。方法返回值默认被解析为视图名（如 `"login"` → `templates/login.html`）。
- `@RestController` = `@Controller` + `@ResponseBody`。`@ResponseBody` 告诉 Spring 直接序列化返回值为 HTTP 响应体（Java 对象 → JSON）。

**底层原理**：Spring MVC 使用 `HttpMessageConverter` 实现序列化。当检测到 `@ResponseBody`（或 `@RestController`）时，根据请求的 `Accept` 头和返回类型选择合适的 Converter：
- `MappingJackson2HttpMessageConverter`：Java 对象 → JSON（默认）
- `StringHttpMessageConverter`：String → text/plain

### 5.2 请求映射注解

```java
// AuthController.java
@RestController
@RequestMapping("/api/auth")           // 类级别：所有方法的 URL 前缀
public class AuthController {

    @GetMapping("/csrf")               // GET /api/auth/csrf
    public Map<String, Object> csrf(...) { ... }

    @PostMapping("/login")             // POST /api/auth/login
    public Map<String, Object> login(...) { ... }

    @GetMapping("/status")             // GET /api/auth/status
    public Map<String, Object> status(...) { ... }
}
```

**Spring MVC 支持的请求映射注解**：

| 注解 | HTTP 方法 | 示例 |
|------|----------|------|
| `@RequestMapping` | 通用（默认匹配所有） | `@RequestMapping("/path")` |
| `@GetMapping` | GET | `@GetMapping("/users")` |
| `@PostMapping` | POST | `@PostMapping("/users")` |
| `@PutMapping` | PUT | `@PutMapping("/users/{id}")` |
| `@DeleteMapping` | DELETE | `@DeleteMapping("/users/{id}")` |
| `@PatchMapping` | PATCH | `@PatchMapping("/users/{id}")` |

`@RequestMapping` 也可指定方法：`@RequestMapping(value = "/login", method = RequestMethod.POST)`。`@PostMapping` 是其简写形式。

### 5.3 @RequestParam 接收请求参数

```java
// WeChatGroupController.java 第 128-135 行
@PostMapping("/api/group/create")
public Map<String, Object> createGroup(
        @RequestParam("groupName") String groupName,           // 必填参数
        @RequestParam("ownerId") String ownerId,               // 必填参数
        @RequestParam("file") MultipartFile file,              // 文件上传
        @RequestParam(value = "userId", required = false,
                      defaultValue = "本地用户") String userIdParam,  // 可选参数
        HttpServletRequest request                             // 自动注入
) { ... }
```

**参数说明**：
- `@RequestParam("groupName")`：从请求中提取名为 `groupName` 的参数
- `required = false`：参数可选，不传不会报错
- `defaultValue = "本地用户"`：不传时使用的默认值
- `MultipartFile`：Spring 自动封装上传的文件数据（通过 `StandardServletMultipartResolver` 解析 multipart/form-data 请求）
- `HttpServletRequest`：Spring MVC 自动注入当前请求对象，无需注解

### 5.4 @CrossOrigin 跨域处理

```java
@CrossOrigin(origins = "*")       // 允许所有来源的跨域请求
public class WeChatGroupController { ... }
```

**CORS（跨域资源共享）问题**：浏览器的同源策略阻止 JavaScript 向不同域名/端口发请求。开发阶段前后端分离部署时经常遇到。

**@CrossOrigin 注入的响应头**：
```
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
Access-Control-Allow-Headers: *
```

生产环境应限制 `origins` 为具体域名：`@CrossOrigin(origins = "https://your-domain.com")`。

### 5.5 构造器注入 vs 字段注入

本项目同时使用了两种注入方式：

```java
// 构造器注入 — WeChatGroupController.java 第 47-66 行
@RestController
public class WeChatGroupController {
    private final WeChatGroupService weChatGroupService;  // 字段声明为 final
    private final GroupRecordService groupRecordService;

    public WeChatGroupController(WeChatGroupService weChatGroupService,
                                  GroupRecordService groupRecordService,
                                  ...) {  // 构造器参数由 Spring 自动传入
        this.weChatGroupService = weChatGroupService;
        this.groupRecordService = groupRecordService;
        ...
    }
}

// 字段注入 — AuthController.java 第 38 行
@Autowired(required = false)
private AuthService authService;
```

**为什么构造器注入更好**（推荐方式）：

1. **不可变性**：字段可以声明为 `final`，一旦初始化不可修改
2. **显式依赖**：通过构造器参数明确列出所有依赖，避免隐藏依赖
3. **测试友好**：测试时通过构造器传入 mock 对象，不需要 Spring 容器
4. **避免 NPE**：构造时即检查依赖是否为 null

**为什么 AuthController 用字段注入？**

因为 `AuthService` 被 `@ConditionalOnProperty(name = "app.auth.enabled")` 控制，当认证关闭时这个 Bean 不存在。使用 `@Autowired(required = false)` 允许该依赖为 null，Controller 在方法中检查 `authService == null`：

```java
// AuthController.java 第 70-74 行
if (authService == null) {
    result.put("success", false);
    result.put("message", "认证功能未启用");
    return result;
}
```

### 5.6 ResponseEntity 返回文件下载

```java
// WeChatGroupController.java 第 197-207 行
@GetMapping("/api/template/csv")
public ResponseEntity<byte[]> downloadCsvTemplate() {
    String content = "userid\r\n请替换为群主的userid\r\n...";
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
    headers.setContentDispositionFormData("attachment", encodeFilename("成员导入模板.csv"));

    return ResponseEntity.ok().headers(headers).body(bytes);
}
```

**ResponseEntity 的作用**：相比直接返回 `byte[]` 或 String，`ResponseEntity` 可以精确控制 HTTP 响应的三个组成部分：
- **状态码**：`ResponseEntity.ok()` → 200，`ResponseEntity.internalServerError()` → 500
- **响应头**：`Content-Type` 指定文件类型，`Content-Disposition` 指定浏览器下载文件名
- **响应体**：`body(bytes)` 设置返回的字节数据

**Content-Disposition** 的意义：
- `inline`：浏览器内显示（如图片）
- `attachment`：强制下载，弹出保存对话框

**媒体类型对照**：

| 文件类型 | Content-Type |
|---------|-------------|
| CSV | `text/csv; charset=UTF-8` |
| Excel (.xlsx) | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| Word (.docx) | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |

### 5.7 WeChatGroupController 逐方法分析

#### 5.7.1 homePage() — OAuth2 认证流程

```java
@GetMapping("/")
public void homePage(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // 分支1：Session 中已有 userId → 直接跳转到主页面
    String sessionUserId = SecurityUtils.getCurrentUserId(request);
    if (sessionUserId != null) {
        response.sendRedirect("/index?userId=" +
            URLEncoder.encode(sessionUserId, StandardCharsets.UTF_8));
        return;
    }

    // 分支2：URL 参数中带了 userId → 跳转到主页面
    String userId = request.getParameter("userId");
    if (userId != null && !userId.isEmpty()) {
        response.sendRedirect("/index?userId=" +
            URLEncoder.encode(userId, StandardCharsets.UTF_8));
        return;
    }

    // 分支3：企业微信 OAuth 回调带了 code → 换取 userId
    String code = request.getParameter("code");
    if (code != null && !code.isEmpty()) {
        String fetchedUserId = exchangeCodeForUserId(code);
        if (fetchedUserId != null) {
            response.sendRedirect("/index?userId=" +
                URLEncoder.encode(fetchedUserId, StandardCharsets.UTF_8));
            return;
        }
        response.sendRedirect("/index?userId=本地用户");
        return;
    }

    // 分支4：都未满足 → 重定向到企业微信 OAuth 授权页
    String oauthUrl = String.format(
        "https://open.weixin.qq.com/connect/oauth2/authorize" +
        "?appid=%s&redirect_uri=%s&response_type=code&scope=snsapi_base" +
        "&agentid=%s&state=STATE#wechat_redirect",
        weChatProperties.getCorpid(),
        URLEncoder.encode(redirectUri, StandardCharsets.UTF_8),
        weChatProperties.getAgentid());
    response.sendRedirect(oauthUrl);
}
```

**三路分支逻辑**：OAuth 2.0 授权码流程在企业微信中的完整闭环：

```
[用户访问 /]
    ├─ Session 中有 userId？→ 已认证，直接进页面
    ├─ URL 参数有 userId？→ 手动传了身份，进页面
    ├─ URL 参数有 code？→ 企业微信回调，用 code 换 userId
    └─ 都没有 → 重定向到企微 OAuth 授权页
         └─ 用户授权后 → 企微带 code 回调 / → 进入分支3
```

#### 5.7.2 createGroup() — 建群 API

```java
@PostMapping("/api/group/create")
public Map<String, Object> createGroup(
        @RequestParam("groupName") String groupName,
        @RequestParam("ownerId") String ownerId,
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "userId", required = false,
                      defaultValue = "本地用户") String userIdParam,
        HttpServletRequest request) {

    // 身份确定：session 优先，fallback 到参数
    String userId = SecurityUtils.getCurrentUserId(request);
    if (userId == null) {
        userId = userIdParam;
    }

    // 三层参数校验
    if (groupName == null || groupName.trim().isEmpty()) { /* 返回错误 */ }
    if (ownerId == null || ownerId.trim().isEmpty()) { /* 返回错误 */ }
    if (file.isEmpty()) { /* 返回错误 */ }

    // 调用 Service
    try {
        CreateGroupResultVO result = weChatGroupService.createGroupFromFile(file, groupName, ownerId);
        groupRecordService.recordGroupCreation(result, groupName, ownerId, userId, IpUtils.getClientIp(request), file);
        // ... 构造响应
    } catch (BusinessException e) {
        // 业务异常：返回用户可理解的错误信息
    } catch (Exception e) {
        // 系统异常：记录日志但返回脱敏后的错误信息
    }
}
```

**校验为什么放在 Controller 层而不是 Service 层？**

Controller 层校验的是**请求格式合法性**（是否为空、是否符合格式），属于 HTTP 层面的输入验证。Service 层校验的是**业务规则**（如文件内容是否符合企业微信 API 要求）。分层校验的好处：
- Controller 层快速拒绝无效请求，减少 Service 层无效调用
- Service 层只关注业务逻辑，不关心参数怎么来的（可能来自 HTTP、也可能来自定时任务）

#### 5.7.3 模板下载方法

```java
@GetMapping("/api/template/csv")
public ResponseEntity<byte[]> downloadCsvTemplate() {
    // 直接在内存中构造 CSV 内容
    String content = "userid\r\n请替换为群主的userid\r\n请替换为成员1的userid\r\n请替换为成员2的userid\r\n";
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    // UTF-8 编码写入，确保中文字符不乱码
    ...
}

@GetMapping("/api/template/excel")
public ResponseEntity<byte[]> downloadExcelTemplate() {
    try (Workbook workbook = new XSSFWorkbook()) {  // try-with-resources 自动关闭
        Sheet sheet = workbook.createSheet("成员名单");
        // 通过 Apache POI API 构造 Excel 文件，在内存中完成（ByteArrayOutputStream）
        ...
    }
}

@GetMapping("/api/guide")
public ResponseEntity<byte[]> downloadGuide() {
    try (XWPFDocument doc = new XWPFDocument()) {
        // 通过 Apache POI XWPF API 动态生成 Word 文档
        addHeading(doc, "企业微信批量建群工具 — 操作指南", 24);
        addParagraph(doc, "一、功能概述");
        ...
    }
}
```

**为什么用 ByteArrayOutputStream 而不是写到文件？**

动态生成的文件不需要持久化，直接写入内存字节数组，作为 HTTP 响应体一次性返回。这样：
- 不产生临时文件，无需清理
- 不需要文件系统权限
- 响应速度快（无磁盘 I/O）

### 5.8 AuthController 分析

#### 5.8.1 登录频率限制

```java
// AuthController.java 第 33-51 行
private final ConcurrentHashMap<String, Integer> loginAttempts = new ConcurrentHashMap<>();
private final ConcurrentHashMap<String, Long> attemptWindowStart = new ConcurrentHashMap<>();
private static final int MAX_ATTEMPTS = 5;
private static final long WINDOW_MS = 60_000;  // 60秒窗口

private boolean isRateLimited(String ip) {
    long now = System.currentTimeMillis();
    Long windowStart = attemptWindowStart.get(ip);
    if (windowStart == null || now - windowStart > WINDOW_MS) {
        // 新窗口：重置计数
        attemptWindowStart.put(ip, now);
        loginAttempts.put(ip, 1);
        return false;
    }
    // 同一窗口内：递增计数
    int attempts = loginAttempts.merge(ip, 1, Integer::sum);
    return attempts > MAX_ATTEMPTS;  // 超过 5 次则限制
}
```

**数据结构选择 `ConcurrentHashMap` 的原因**：

- 多个 HTTP 请求可能并发访问此方法（多个用户同时登录）
- `HashMap` 在并发写入时可能导致数据丢失或死循环（JDK 7/8 的 table 扩容问题）
- `ConcurrentHashMap` 使用分段锁（JDK 7）或 CAS + synchronized（JDK 8+），保证线程安全且性能优于 `Hashtable`

**merge() 方法的线程安全性**：

```java
loginAttempts.merge(ip, 1, Integer::sum);
```

这行代码的含义：如果 key `ip` 不存在，插入 value `1`；如果已存在，使用 `Integer::sum` 将新值 `1` 与旧值相加。整个操作是**原子的**——读、计算、写在一个锁保护范围内完成，不会出现"两个线程都读到 3，都写入 4"的问题。

#### 5.8.2 Session 固定攻击防护

```java
// AuthController.java 第 96-101 行
HttpSession oldSession = request.getSession(false);  // 不创建新 session
if (oldSession != null) {
    oldSession.invalidate();  // 销毁旧 session
}
HttpSession newSession = request.getSession(true);   // 创建新 session
newSession.setAttribute(SessionKeys.LOGIN_USER_ID, loginResult.getUserId());
```

**什么是 Session 固定攻击？**

攻击者先获取一个有效的 session ID（如 `JSESSIONID=ABC123`），然后诱骗受害者使用这个 session ID 登录。登录成功后，攻击者用同一个 session ID 即可冒充受害者。

**防护方法**：登录成功后销毁旧 session，创建新 session。这样旧的 session ID 失效，攻击者无法利用。

**`getSession(false)` vs `getSession(true)`**：
- `getSession(false)`：如果当前请求没有关联 session，返回 `null`（不创建新 session）
- `getSession(true)`：如果当前请求没有关联 session，创建一个新的（相当于 `request.getSession()`）

#### 5.8.3 定时清理

```java
// AuthController.java 第 144-153 行
@Scheduled(fixedRate = 300000)  // 每 5 分钟执行一次
public void cleanRateLimitCache() {
    long now = System.currentTimeMillis();
    attemptWindowStart.forEach((ip, time) -> {
        if (now - time > WINDOW_MS * 2) {  // 超过 120 秒的条目
            loginAttempts.remove(ip);
            attemptWindowStart.remove(ip);
        }
    });
}
```

**为什么需要定时清理？**

`ConcurrentHashMap` 不会自动删除过期条目。如果不清理，恶意攻击者可能用大量不同 IP 尝试登录，导致内存占用持续增长（内存泄漏）。每 5 分钟清理超过 120 秒的窗口记录，保持内存稳定。

### 5.9 PageController 分析

```java
@Controller
public class PageController {
    @GetMapping({"/login", "/login.html"})
    public String loginPage() {
        return "login";  // → templates/login.html
    }

    @GetMapping({"/index", "/index.html"})
    public String indexPage() {
        return "index";  // → templates/index.html
    }
}
```

**Thymeleaf 视图解析过程**：

1. 方法返回字符串 `"index"`
2. Spring MVC 的 `ThymeleafViewResolver` 拦截此返回值
3. 根据 `spring.thymeleaf.prefix=classpath:/templates/` 和 `spring.thymeleaf.suffix=.html` 拼接路径
4. 查找 `classpath:/templates/index.html`
5. Thymeleaf 模板引擎解析模板，执行 `th:xxx` 属性，渲染为 HTML 字符串
6. 写入 HTTP 响应体

**`@GetMapping` 中的数组 `{"/index", "/index.html"}`** 表示两个 URL 路径都映射到同一个方法。用户访问 `/index` 和 `/index.html` 效果相同。

---

## 第六章：Service 层

### 6.1 @Service 注解的作用

```java
@Service
public class WeChatGroupServiceImpl implements WeChatGroupService { ... }

@Service
public class GroupRecordServiceImpl implements GroupRecordService { ... }

@Service
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public class AuthServiceImpl implements AuthService { ... }
```

`@Service` 是 `@Component` 的特化（specialization）。功能完全相同，都是为了被 `@ComponentScan` 扫描到并纳入 Spring 容器管理。区别仅在于语义：

| 注解 | 语义 | 本质 |
|------|------|------|
| `@Component` | 通用 Spring Bean | 基础注解 |
| `@Service` | 业务层组件 | `@Component` |
| `@Repository` | 数据访问组件 | `@Component` + 异常转换 |
| `@Controller` | Web 控制器 | `@Component` |

`@Repository` 额外提供 JPA 异常自动转换为 Spring 的 `DataAccessException` 体系的功能。

### 6.2 接口 + 实现类的设计模式

```java
// 接口
public interface WeChatGroupService {
    CreateGroupResultVO createGroupFromFile(MultipartFile file, String groupName, String ownerId);
}

// 实现
@Service
public class WeChatGroupServiceImpl implements WeChatGroupService {
    @Override
    public CreateGroupResultVO createGroupFromFile(MultipartFile file, String groupName, String ownerId) {
        // 具体实现
    }
}
```

**为什么需要接口？**

1. **依赖倒置原则**：Controller 依赖 `WeChatGroupService` 接口，不依赖实现类。实现类可以替换而不影响 Controller：
```java
// Controller 中注入的是接口类型
public WeChatGroupController(WeChatGroupService weChatGroupService, ...) { ... }
```

2. **Spring AOP 代理**：Spring 通过 JDK 动态代理拦截接口方法调用，实现 `@Transactional`、`@Cacheable` 等声明式功能。JDK 动态代理要求目标类实现接口。

3. **测试隔离**：单元测试时可以用 Mockito 创建接口的 mock 对象：
```java
WeChatGroupService mockService = Mockito.mock(WeChatGroupService.class);
```

### 6.3 WeChatGroupServiceImpl 完整分析

**文件**: `src/main/java/com/school/wechatgroup/service/impl/WeChatGroupServiceImpl.java`

#### 6.3.1 依赖注入 — Map 自动收集策略实现

```java
@Service
public class WeChatGroupServiceImpl implements WeChatGroupService {
    private final Map<String, FileParserStrategy> parserMap;

    public WeChatGroupServiceImpl(Map<String, FileParserStrategy> parserMap,
                                   RestTemplate restTemplate,
                                   WeChatTokenManager tokenManager) {
        this.parserMap = parserMap;
        ...
    }
}
```

**Spring 如何自动填充 `Map<String, FileParserStrategy>`？**

当 Spring 在构造器中看到 `Map<String, SomeInterface>` 类型的参数时，会自动收集容器中所有实现了 `SomeInterface` 的 Bean，`key` 为 Bean 名称，`value` 为 Bean 实例。

在本项目中：

```java
@Component("csvParser")       // key = "csvParser"
public class CsvFileParser implements FileParserStrategy { ... }

@Component("excelParser")     // key = "excelParser"
public class ExcelFileParser implements FileParserStrategy { ... }
```

Spring 自动构造 `Map{"csvParser" → CsvFileParser实例, "excelParser" → ExcelFileParser实例}`。

**这种模式的优势**：添加新文件格式（如 JSON、XML）时，只需新建一个 `@Component` 实现类，无需修改现有代码，符合**开闭原则**（对扩展开放，对修改关闭）。

#### 6.3.2 文件扩展名提取和解析器选择

```java
// WeChatGroupServiceImpl.java 第 102-115 行
String originalFilename = file.getOriginalFilename();
String extension = getExtension(originalFilename).toLowerCase();   // "myfile.CSV" → "csv"

FileParserStrategy parser = parserMap.values().stream()
        .filter(p -> p.supportedExtension().equalsIgnoreCase(extension)
                   || (extension.equals("xls") && p.supportedExtension().equals("xlsx")))
        .findFirst()
        .orElse(null);
```

**Stream API 分步解析：**

```java
parserMap.values()           // Collection<FileParserStrategy>  [csvParser, excelParser]
    .stream()                // Stream<FileParserStrategy>      转换为流
    .filter(p ->             // 过滤：只保留条件匹配的
        p.supportedExtension().equalsIgnoreCase(extension)  // 扩展名匹配
        || (extension.equals("xls")      // 特殊处理：.xls 文件也用 excelParser
            && p.supportedExtension().equals("xlsx")))
    .findFirst()             // 取第一个匹配的（包装为 Optional）
    .orElse(null);           // 如果 Optional 为空则返回 null
```

**为什么 `.xls` 扩展名也能被 `excelParser` 处理？**

Excel 有两种格式：
- `.xlsx` (Office Open XML，POI 使用 `XSSFWorkbook`)
- `.xls` (BIFF 二进制格式，POI 使用 `HSSFWorkbook`)

ExcelFileParser 的 `supportedExtension()` 返回 `"xlsx"`，但 `createWorkbook()` 方法内部根据文件名后缀选择对应的实现：

```java
// ExcelFileParser.java 第 63-69 行
private Workbook createWorkbook(MultipartFile file) throws Exception {
    String filename = file.getOriginalFilename();
    if (filename != null && filename.toLowerCase().endsWith(".xls")) {
        return new HSSFWorkbook(file.getInputStream());  // .xls 格式
    }
    return new XSSFWorkbook(file.getInputStream());      // .xlsx 格式
}
```

#### 6.3.3 群主自动补入和成员去重

```java
// WeChatGroupServiceImpl.java 第 122-131 行
List<String> members = new ArrayList<>();
members.add(ownerId);             // 群主必须首先是群成员
members.addAll(userList);         // 追加文件中的用户

Set<String> seen = new HashSet<>();
List<String> uniqueMembers = members.stream()
        .filter(u -> u != null && !u.isEmpty() && seen.add(u))  // 去重
        .collect(Collectors.toList());
```

**`seen.add(u)` 的巧妙用法**：

```java
// HashSet.add() 的返回值：
//   true  → 集合中原来没有，成功添加
//   false → 集合中已经存在，添加失败（不会覆盖）

// 因此：
.filter(u -> u != null && !u.isEmpty() && seen.add(u))
//      ├─ null 检查
//      ├─ 空字符串检查
//      └─ 首次出现 → seen.add() 返回 true → 通过 filter
//         重复出现 → seen.add() 返回 false → 被 filter 过滤掉
```

**为什么要去重？**

1. 群主填写的 ownerId 可能与文件中某个成员的 userid 相同，重复传参会导致企业微信 API 报错
2. 文件中可能有重复的 userid

#### 6.3.4 HTTP 请求构建

```java
// WeChatGroupServiceImpl.java 第 157-178 行
private Map<String, Object> callCreateGroupApi(String groupName, String ownerId, List<String> members) {
    String token = tokenManager.getAccessToken();
    String url = "https://qyapi.weixin.qq.com/cgi-bin/appchat/create?access_token=" + token;

    // 构造 JSON 请求体：{ "name": "...", "owner": "...", "userlist": ["...", "..."] }
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("name", groupName);
    requestBody.put("owner", ownerId);
    requestBody.put("userlist", members);

    // 设置 HTTP 请求头
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);   // Content-Type: application/json

    // 将 Header 和 Body 包装为 HttpEntity
    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

    // 类型安全的泛型反序列化
    ParameterizedTypeReference<Map<String, Object>> typeRef =
            new ParameterizedTypeReference<Map<String, Object>> {};

    // 发送 POST 请求
    Map<String, Object> response = restTemplate.exchange(url, HttpMethod.POST, entity, typeRef).getBody();
    return response != null ? response : Map.of("errcode", -1, "errmsg", "no response");
}
```

**`ParameterizedTypeReference` 的作用**：

Java 的泛型在运行时会被**擦除**（Type Erasure），`Map<String, Object>` 在运行时只是 `Map`。`RestTemplate` 需要知道完整泛型信息来正确反序列化 JSON。

```java
// 创建匿名子类，利用父类的 getType() 获取完整泛型信息
new ParameterizedTypeReference<Map<String, Object>>() {}
//                             ^^^^^^^^^^^^^^^^
//                             泛型参数在编译时被捕获，存储在匿名类的父类类型信息中
```

**为什么用 `exchange()` 而不是 `postForObject()`？**

- `postForObject(url, entity, Map.class)` — 简洁但丢失泛型，`Map.class` 无法携带 `Map<String, Object>` 的泛型信息
- `exchange(url, method, entity, typeRef)` — 通过 `ParameterizedTypeReference` 保留完整泛型

#### 6.3.5 ERROR_EXPLAIN 错误码翻译

```java
// WeChatGroupServiceImpl.java 第 36-90 行
private static final Map<Integer, String> ERROR_EXPLAIN = new HashMap<>();
static {
    ERROR_EXPLAIN.put(-1, "系统繁忙，请稍后重试");
    ERROR_EXPLAIN.put(40001, "企业微信密钥（secret）无效，请检查配置");
    ERROR_EXPLAIN.put(40003, "成员列表中包含无效的 UserID，请检查文件中填写的 userid 是否正确");
    ERROR_EXPLAIN.put(86205, "今日群聊创建数量已达到企业上限，请明天再试");
    // ... 共 30+ 条映射
}

// 使用时（第 144-146 行）
String explain = ERROR_EXPLAIN.getOrDefault(errcode,
        "建群失败（错误码 " + errcode + "），请稍后重试或联系管理员");
```

**为什么用 static 初始化块而不是在构造器中初始化？**

- `ERROR_EXPLAIN` 是类级别的常量数据，与具体实例无关
- `static` 块在类加载时执行一次，之后所有实例共享同一份数据
- 避免在每个实例创建时重复初始化 30+ 条映射

**`Map.getOrDefault()` 的作用**：如果 key 存在则返回对应 value，不存在返回默认值。企业微信 API 有 100+ 种错误码，本项目的 ERROR_EXPLAIN 只翻译了最常见的 30+ 种，未翻译的错误码显示原文和错误码数字：

```
"建群失败（错误码 12345），请稍后重试或联系管理员"
```

### 6.4 GroupRecordServiceImpl 事务管理

**文件**: `src/main/java/com/school/wechatgroup/service/impl/GroupRecordServiceImpl.java`

```java
@Service
public class GroupRecordServiceImpl implements GroupRecordService {

    private final GroupOperationRepository operationRepo;
    private final WxGroupRepository wxGroupRepo;
    private final UploadFileRepository uploadFileRepo;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Transactional
    public void recordGroupCreation(CreateGroupResultVO result, String groupName,
                                     String ownerId, String userId, String ip,
                                     MultipartFile file) {
        String operationId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        // 1. 操作记录（必写）
        GroupOperation operation = new GroupOperation();
        operation.setOperationId(operationId);
        // ... 设置各字段
        operationRepo.save(operation);   // INSERT INTO t_group_operation

        // 2. 群聊记录（仅成功）
        if ("success".equals(result.getStatus())) {
            WxGroup wxGroup = new WxGroup();
            wxGroup.setOperationId(operationId);
            // ... 设置各字段
            wxGroupRepo.save(wxGroup);   // INSERT INTO t_wx_group
        }

        // 3. 文件存档（独立事务）
        try {
            transactionTemplate.executeWithoutResult(status -> {
                UploadFile uploadFile = new UploadFile();
                uploadFile.setOperationId(operationId);
                uploadFile.setFileContent(file.getBytes());
                uploadFileRepo.save(uploadFile);
            });
        } catch (Exception e) {
            log.error("文件存档失败", e);  // 不影响主记录
        }
    }
}
```

#### 【深入理解】@Transactional 事务管理

**事务的 ACID 属性**：

| 属性 | 含义 | 本项目中的作用 |
|------|------|--------------|
| Atomicity（原子性） | 要么全部成功，要么全部回滚 | 操作记录 + 群聊记录要么都写，要么都不写 |
| Consistency（一致性） | 事务前后数据一致 | 不会出现操作记录有但群聊记录无的情况 |
| Isolation（隔离性） | 并发事务互不干扰 | 多人同时建群互不影响 |
| Durability（持久性） | 提交后数据永久保存 | 写入后不会丢失 |

**`@Transactional` 的工作原理**：

Spring 使用 AOP（面向切面编程）代理实现事务管理：

```
[调用 recordGroupCreation()]
        │
        ▼
    [代理对象拦截]
        │
        ├─► 1. 开启事务（获取数据库连接，设置 autoCommit=false）
        ├─► 2. 调用真实方法 recordGroupCreation()
        ├─► 3. 方法正常返回 → 提交事务（commit）
        └─► 4. 方法抛出 RuntimeException → 回滚事务（rollback）
```

**默认回滚规则**：
- 抛出 `RuntimeException` 及其子类 → 自动回滚
- 抛出 `Exception`（checked exception）→ **不回滚**，需要使用 `@Transactional(rollbackFor = Exception.class)`

#### 【深入理解】TransactionTemplate 独立事务

```java
// 第 76-87 行
try {
    transactionTemplate.executeWithoutResult(status -> {
        // 这是一个独立事务！
        // 即使这里抛出异常，外层 @Transactional 的事务不受影响
        UploadFile uploadFile = new UploadFile();
        uploadFile.setFileContent(file.getBytes());
        uploadFileRepo.save(uploadFile);
    });
} catch (Exception e) {
    log.error("文件存档失败", e);  // 吞掉异常
}
```

**为什么文件存档需要独立事务？**

业务语义：文件存档属于辅助功能（审计日志），即使存档失败也不应该导致建群记录写入失败。如果放在同一个事务中，文件读取错误会导致整个 `recordGroupCreation()` 回滚，操作记录也会丢失。

**`@Transactional` 嵌套限制**：在同一个类中，方法 A 调用方法 B，B 上的 `@Transactional` 不会生效（因为绕过代理）。要启动独立事务，需要使用 `TransactionTemplate` 或 `@Transactional(propagation = Propagation.REQUIRES_NEW)` 配合将方法拆分到不同类。

#### 【深入理解】UUID 生成操作 ID

```java
String operationId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
```

- `UUID.randomUUID()` 生成一个随机 UUID v4，格式：`550e8400-e29b-41d4-a716-446655440000`
- `.toString()` 转为字符串
- `.replace("-", "")` 移除连字符 → `550e8400e29b41d4a716446655440000`
- `.substring(0, 12)` 取前 12 位 → `550e8400e29b`

**为什么要手动生成 ID 而不是用数据库自增 ID？**

1. 三张表（`t_group_operation`, `t_wx_group`, `t_upload_file`）通过 `operation_id` 关联，需要一个**跨表唯一的标识**
2. 数据库自增 ID 在插入前不可知，无法在应用层关联多个表的同一条业务数据
3. UUID 前 12 位的碰撞概率极低（16^12 ≈ 2.8×10^14 种组合），对于建群操作数量级足够安全

---

## 第七章：数据持久层（JPA + Hibernate）

### 7.1 技术栈关系

```mermaid
概念层级：
    JPA (Java Persistence API)
      └── Jakarta Persistence 3.1 (规范，定义 @Entity, @Table, @Id 等注解)
            └── Hibernate 6.x (JPA 的实现，负责生成 SQL 和映射对象)
                  └── Spring Data JPA (Spring 封装，提供 Repository 自动实现)
```

- **JPA** (Java/Jakarta Persistence API)：Java 官方的 ORM（对象关系映射）标准接口。定义了一套注解（`@Entity`, `@Table`, `@Id`, `@Column` 等）和 API（`EntityManager`），但不提供具体实现。

- **Hibernate**：JPA 最流行的实现。负责将 Java 对象与数据库行互相转换、生成 SQL 语句、管理一级缓存（Session/EntityManager）。

- **Spring Data JPA**：Spring 对 JPA 的进一步封装。核心功能是**方法名解析查询**——根据 Repository 接口中的方法名自动生成 SQL。

### 7.2 实体类分析

#### 7.2.1 GroupOperation（操作记录表）

**文件**: `src/main/java/com/school/wechatgroup/entity/GroupOperation.java`

```java
@Entity
@Table(name = "t_group_operation", indexes = {      // 1. 表名映射 + 索引定义
    @Index(name = "idx_operation_id", columnList = "operation_id"),    // 普通索引
    @Index(name = "idx_operator_user_id", columnList = "operator_user_id"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
public class GroupOperation {

    @Id                                               // 2. 主键
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 数据库自增
    private Long id;

    @Column(name = "operation_id", nullable = false, length = 16)  // 3. 列映射
    private String operationId;

    @Enumerated(EnumType.STRING)                      // 4. 枚举映射为字符串
    @Column(name = "status", nullable = false, length = 16)
    private OperationStatus status;                    // SUCCESS / FAILED

    @CreationTimestamp                                 // 5. 自动时间戳
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 构造函数 + getter/setter（省略）
}
```

**注解详解**：

**`@Entity`**：标记此类为 JPA 实体类。JPA 会在启动时扫描所有 `@Entity` 类，为每个实体生成 SQL 建表/查询/更新语句。

**`@Table`**：
- `name = "t_group_operation"`：指定表名。不指定则默认使用类名（`GroupOperation` → `group_operation`）。
- `indexes`：定义数据库索引。`@Index(name = "idx_operation_id", columnList = "operation_id")` 等价于 SQL `CREATE INDEX idx_operation_id ON t_group_operation(operation_id)`。

**为什么需要索引？**

索引加速查询。`operation_id` 上建索引，查询 `WHERE operation_id = 'xxx'` 时数据库直接定位到目标行（B+树查找，O(log n)），不需要全表扫描（O(n)）。

**`@Id` + `@GeneratedValue`**：

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

主键策略 `IDENTITY` 表示主键值由数据库的 `AUTO_INCREMENT` 生成。MySQL 8+ 的等价 SQL：

```sql
CREATE TABLE t_group_operation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ...
);
```

**其他主键策略**：

| 策略 | 原理 | 适用场景 |
|------|------|---------|
| `IDENTITY` | 数据库自增列 | MySQL, H2 |
| `SEQUENCE` | 数据库 SEQUENCE 对象 | PostgreSQL, Oracle |
| `TABLE` | 用一张表模拟序列（不推荐） | 需要跨数据库兼容 |
| `UUID` | Java 生成 UUID | 分布式系统 |
| `AUTO` | JPA 自动选择（默认） | 不关心具体策略 |

**`@Column` 属性详解**：

```java
@Column(name = "operation_id",    // 数据库列名
        nullable = false,          // 列不可为 NULL
        length = 16,               // 列最大长度（VARCHAR(16)）
        unique = false,            // 列值是否唯一（默认 false）
        updatable = true)          // 列是否可被 UPDATE 更新（默认 true）
```

对应生成的 DDL：
```sql
operation_id VARCHAR(16) NOT NULL
```

**`@Enumerated`**：

```java
@Enumerated(EnumType.STRING)
private OperationStatus status;
```

- `EnumType.STRING`：保存枚举名称 → `'SUCCESS'` 或 `'FAILED'`
- `EnumType.ORDINAL`：保存枚举序号 → `0` 或 `1`（不推荐，调整枚举顺序会破坏数据）

**`@CreationTimestamp`**：

```java
@CreationTimestamp                              // Hibernate 注解
@Column(name = "created_at", nullable = false, updatable = false)
private LocalDateTime createdAt;
```

- Hibernate 在 INSERT 时自动填充当前时间
- `updatable = false` 确保 UPDATE 时不会修改此字段

#### 7.2.2 WxGroup（群聊记录表）

```java
@Entity
@Table(name = "t_wx_group", indexes = {
    @Index(name = "idx_operation_id", columnList = "operation_id"),
    @Index(name = "idx_chat_id", columnList = "chat_id")   // chat_id 查询索引
})
public class WxGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false, length = 64)
    private String chatId;                    // 企业微信返回的群聊 ID

    @Column(name = "member_list", length = 20000)  // 成员列表用逗号分隔的字符串
    private String memberList;
}
```

**为什么 `member_list` 定义为 `VARCHAR(20000)`？**

企业微信群聊最多 2000 人，每个 userid 约 10-30 字符，加上逗号分隔符，20000 足够存储。使用逗号分隔字符串而非单独的关系表是简化设计——本项目只需查询群聊信息时展示成员，不需要按成员反查群聊。

#### 7.2.3 UploadFile（上传文件表）

```java
@Entity
@Table(name = "t_upload_file", indexes = {
    @Index(name = "idx_operation_id", columnList = "operation_id")
})
public class UploadFile {
    @Lob
    @Column(name = "file_content", columnDefinition = "LONGBLOB")
    private byte[] fileContent;               // 文件二进制内容
}
```

**`@Lob` + `columnDefinition = "LONGBLOB"`**：

- `@Lob`（Large Object）告诉 JPA 此字段存储大对象数据
- `columnDefinition = "LONGBLOB"` 精确指定列类型：
  - MySQL：最大 4GB
  - BLOB vs CLOB：BLOB 存储二进制数据，CLOB 存储字符数据
- `MultipartFile.getBytes()` 返回 `byte[]`，所以使用 BLOB

#### 7.2.4 AppUser（用户表）

```java
@Entity
@Table(name = "t_app_user", indexes = {
    @Index(name = "idx_username", columnList = "username", unique = true)
})
public class AppUser {
    @Column(unique = true, nullable = false, length = 64)
    private String username;

    @Column(nullable = false, length = 128)
    private String password;    // BCrypt 哈希后的密码，长度固定 60

    @Column(nullable = false)
    private Boolean enabled = true;  // 账号是否启用
}
```

**密码列 `length = 128`**：BCrypt 哈希的输出总是 60 字符（`$2a$10$...`），128 留足余量。

### 7.3 Repository 接口

#### 7.3.1 基础接口定义

```java
// GroupOperationRepository.java
@Repository
public interface GroupOperationRepository extends JpaRepository<GroupOperation, Long> {
    // 继承后自动拥有: save(), findById(), findAll(), deleteById(), count() 等方法
}

// WxGroupRepository.java — 同上
// UploadFileRepository.java — 同上
```

**`JpaRepository<T, ID>` 泛型参数**：
- `T`：实体类型（`GroupOperation`）
- `ID`：主键类型（`Long`）

**继承后免费获得的方法**（无需实现）：

```java
// 增删改
<S extends T> S save(S entity);           // INSERT 或 UPDATE
void delete(T entity);
void deleteById(ID id);

// 查询
Optional<T> findById(ID id);
List<T> findAll();
List<T> findAll(Sort sort);
Page<T> findAll(Pageable pageable);
long count();
boolean existsById(ID id);
```

**实现时机**：这些方法在应用启动时由 Spring Data JPA 通过 JDK 动态代理（`SimpleJpaRepository`）自动生成实现，开发人员无需写任何 SQL。

#### 7.3.2 方法命名规则查询

```java
// AppUserRepository.java
@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
}
```

**方法名解析**：Spring Data JPA 解析 `findByUsername` 为 `SELECT * FROM t_app_user WHERE username = ?`。

**查询方法命名规则**：

```
find + By + [属性名] + [条件关键字] + [连接符] + [下一个条件...]
```

常用关键字对应：

| 方法关键字 | SQL 等价 | 示例 |
|-----------|---------|------|
| `findBy` | `WHERE col = ?` | `findByUsername(String)` |
| `findBy...And` | `WHERE col1 = ? AND col2 = ?` | `findByUsernameAndEnabled(String, Boolean)` |
| `findBy...Or` | `WHERE col1 = ? OR col2 = ?` | `findByUsernameOrNickname(String, String)` |
| `findBy...Between` | `WHERE col BETWEEN ? AND ?` | `findByCreatedAtBetween(LocalDateTime, LocalDateTime)` |
| `findBy...GreaterThan` | `WHERE col > ?` | `findByMemberCountGreaterThan(Integer)` |
| `findBy...Like` | `WHERE col LIKE ?` | `findByUsernameLike(String)` |
| `findBy...OrderBy...Desc` | `... ORDER BY col DESC` | `findByStatusOrderByCreatedAtDesc(OperationStatus)` |
| `countBy` | `SELECT COUNT(*) WHERE col = ?` | `countByStatus(OperationStatus)` |

**`Optional<AppUser>` 返回值**：表示可能查不到（用户名不存在），调用方必须显式处理 null 情况：

```java
// AuthServiceImpl.java 第 58 行
AppUser user = userRepository.findByUsername(username).orElse(null);
if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
    throw new BusinessException("用户名或密码错误");
}
```

### 7.4 ddl-auto 自动建表机制

```properties
# application-dev.properties
spring.jpa.hibernate.ddl-auto=update

# application-prod.properties
spring.jpa.hibernate.ddl-auto=validate
```

**`ddl-auto` 五个选项**：

| 值 | 行为 | 适用场景 |
|----|------|---------|
| `none` | 不做任何操作 | 手动管理数据库 |
| `validate` | 校验实体与表结构一致（不一致则启动失败） | **生产环境** |
| `update` | 自动更新表结构（只增加列，不删列） | **开发环境** |
| `create` | 每次启动删表重建 | 测试 |
| `create-drop` | 创建表，SessionFactory 关闭时删表 | 集成测试 |

**开发环境用 `update` 的理由**：
- 开发时频繁修改实体类，自动同步表结构省去手动执行 DDL
- 不删除已有数据，保留测试数据

**生产环境用 `validate` 的理由**：
- 防止自动修改破坏现有数据
- 发现结构不一致时启动失败，强制人工介入

---

## 第八章：认证与安全

### 8.1 BCrypt 密码哈希

**文件**: `src/main/java/com/school/wechatgroup/config/AuthConfig.java`

```java
@Configuration
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public class AuthConfig {
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();  // 强度默认 10
    }
}
```

#### 【深入理解】BCrypt 的工作原理

BCrypt 是专门为密码存储设计的哈希算法，与普通哈希（SHA-256、MD5）的核心区别：

**1. 自发加盐（Built-in Salt）**

```java
// 加密：每次生成不同的盐，产生不同的哈希
String hash1 = encoder.encode("admin123");
// → $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
String hash2 = encoder.encode("admin123");
// → $2a$10$EixZaYVK1fsbw1ZFbL4i3eX.WtBq7Jmq.dLq3JFPg.DZqEBHsiHfO
//   hash1 != hash2（因为盐不同）
```

BCrypt 的哈希输出格式：`$2a$10$[22字符盐][31字符哈希]`

- `$2a`：算法版本标识
- `$10`：工作因子（cost factor），即 2^10 = 1024 轮迭代
- 后 22 字符：随机生成的盐值
- 最后 31 字符：密码 + 盐的哈希结果

**2. 验证过程**

```java
// AuthServiceImpl.java 第 59 行
boolean matches = passwordEncoder.matches("admin123", storedHash);
```

`matches(rawPassword, encodedPassword)` 的实现逻辑：

```java
// BCryptPasswordEncoder 内部伪代码
public boolean matches(CharSequence rawPassword, String encodedPassword) {
    // 1. 从 encodedPassword 中提取盐值（$2a$10$ 后面的 22 个字符）
    String salt = extractSalt(encodedPassword);
    // 2. 用同一个盐值对 rawPassword 重新哈希
    String computed = BCrypt.hashpw(rawPassword, salt);
    // 3. 比较两个哈希是否相等（常数时间比较，防止时序攻击）
    return constantTimeEquals(computed, encodedPassword);
}
```

**3. 为什么不可逆（单向性）**

BCrypt 基于 Blowfish 加密算法改造：
- 每次迭代：`state = EksBlowfish(state, password, salt)`（用密码作为密钥加密初始状态）
- 经过 2^10 = 1024 轮迭代后，无法从最终状态反推密码
- 暴力破解的成本随 cost factor 指数增长：cost=12 时每密码需 2^12 = 4096 轮，一秒只能尝试几十个密码

**4. 成本因子选择**

```java
new BCryptPasswordEncoder(10);  // cost = 10，1024 轮，约 100ms/次
new BCryptPasswordEncoder(12);  // cost = 12，4096 轮，约 400ms/次
new BCryptPasswordEncoder(14);  // cost = 14，16384 轮，约 1.6s/次
```

cost=10 是平衡安全性和用户体验的选择：登录验证 100ms 基本无感知，同时暴力破解成本足够高。

**5. 只引入 `spring-security-crypto`，不引入完整 Spring Security**

```xml
<!-- pom.xml 第 69-73 行 -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
</dependency>
```

完整 `spring-boot-starter-security` 会自动配置：
- 所有 URL 需要登录
- 表单登录页 `/login`
- CSRF 保护
- Session 管理

这会与本项目的自定义登录流程（`AuthController` + `LoginInterceptor`）冲突。只引入 `spring-security-crypto` 仅使用 `BCryptPasswordEncoder`，不启用任何安全框架。

### 8.2 HandlerInterceptor 拦截器机制

**文件**: `src/main/java/com/school/wechatgroup/interceptor/LoginInterceptor.java`

```java
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        Object userId = request.getSession().getAttribute(SessionKeys.LOGIN_USER_ID);
        if (userId != null) {
            return true;   // 已登录，放行
        }
        response.sendRedirect("/login");  // 未登录，跳转到登录页
        return false;      // 阻止请求继续传递到 Controller
    }
}
```

**拦截器工作原理**：

```
HTTP 请求
    │
    ▼
[DispatcherServlet]
    │
    ▼
[HandlerInterceptor.preHandle()] ─── 返回 false ──► 请求被拦截，不执行后续
    │ 返回 true
    ▼
[Controller 方法执行]
    │
    ▼
[HandlerInterceptor.postHandle()]  （Controller 执行后，视图渲染前）
    │
    ▼
[视图渲染]
    │
    ▼
[HandlerInterceptor.afterCompletion()]  （视图渲染后，用于资源清理）
```

**三个时机**：

| 方法 | 执行时机 | 典型用途 |
|------|---------|---------|
| `preHandle()` | Controller 执行前 | 权限检查、登录验证 |
| `postHandle()` | Controller 执行后，视图渲染前 | 向 Model 添加公共属性 |
| `afterCompletion()` | 视图渲染后 | 资源清理、日志记录 |

**preHandle 返回值含义**：
- `true`：继续执行后续拦截器和目标 Controller
- `false`：请求终止，DispatcherServlet 认为请求已完成处理

### 8.3 拦截器注册

**文件**: `src/main/java/com/school/wechatgroup/config/WebMvcConfig.java`

```java
@Configuration
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;

    public WebMvcConfig(LoginInterceptor loginInterceptor) {
        this.loginInterceptor = loginInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/", "/index", "/index.html",
                                 "/api/group/**", "/api/template/**", "/api/guide/**")
                .excludePathPatterns("/api/auth/**", "/login", "/login.html");
    }
}
```

**路径模式语法**：

| 模式 | 含义 |
|------|------|
| `/api/group/create` | 精确匹配 |
| `/api/group/**` | 匹配 `/api/group/` 下所有路径 |
| `/api/auth/**` | 匹配 `/api/auth/` 下所有路径 |
| `/**` | 匹配所有路径 |
| `/api/*.html` | 匹配 `/api/` 下一级 `.html` 文件 |

**白名单 / 黑名单策略**：
- `addPathPatterns`：拦截这些路径（需要登录）
- `excludePathPatterns`：排除这些路径（不需要登录，如登录 API 本身、登录页面）

如果不排除 `/api/auth/**`，用户将永远无法登录（登录 API 也要求登录，形成死循环）。

### 8.4 HttpSession 会话管理

```java
// 设置 session 属性
request.getSession().setAttribute(SessionKeys.LOGIN_USER_ID, "admin");

// 读取 session 属性
Object userId = request.getSession().getAttribute(SessionKeys.LOGIN_USER_ID);

// 销毁 session
session.invalidate();
```

**Session 的工作机制**：

```
[首次请求]
浏览器 → 服务器
        服务器创建 HttpSession，生成唯一 JSESSIONID
        响应中 Set-Cookie: JSESSIONID=ABC123; Path=/; HttpOnly
浏览器 ← 服务器
        浏览器保存 Set-Cookie

[后续请求]
浏览器 → 服务器
        Cookie: JSESSIONID=ABC123
        服务器通过 JSESSIONID 找到对应的 HttpSession
        从 session.getAttribute("LOGIN_USER_ID") 读取用户信息
```

**SessionKeys 常量类**：

```java
// src/main/java/com/school/wechatgroup/constant/SessionKeys.java
public final class SessionKeys {
    public static final String LOGIN_USER_ID = "LOGIN_USER_ID";
    public static final String LOGIN_NICKNAME = "LOGIN_NICKNAME";

    private SessionKeys() {}   // 私有构造器，防止实例化
}
```

- 字符串常量避免硬编码字符串的拼写错误（写 `SessionKeys.LOGIN_USER_ID` 编译期检查，写 `"LOGIN_USER_ID"` 拼错 `"LOING_USER_ID"` 运行时才发现）
- `private` 构造器防止实例化（纯常量类不需要实例）
- `final class` 防止继承

### 8.5 CSRF 攻击防护

**文件**: `src/main/java/com/school/wechatgroup/controller/AuthController.java`

#### CSRF 攻击原理

```
[攻击场景]
1. 管理员登录了 wechat-group.com（session 有效）
2. 管理员访问恶意网站 evil.com
3. evil.com 包含隐藏表单：
   <form action="https://wechat-group.com/api/group/create" method="POST">
       <input name="groupName" value="恶意群聊">
       ...
   </form>
   <script>document.forms[0].submit();</script>
4. 浏览器自动带上 wechat-group.com 的 Cookie（JSESSIONID）
5. 服务器认为是合法请求，执行建群操作
```

#### CSRF Token 防护机制

**前端获取 Token**：

```javascript
// login.html 第 403-411 行
fetch('/api/auth/csrf')
    .then(function(r) { return r.json(); })
    .then(function(csrf) {
        return fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'X-CSRF-TOKEN': csrf.token },  // 通过自定义请求头传递
            body: formData
        });
    })
```

**后端生成和验证 Token**：

```java
// AuthController.java — 生成
@GetMapping("/csrf")
public Map<String, Object> csrf(HttpServletRequest request) {
    String token = UUID.randomUUID().toString();       // 生成随机 token
    request.getSession().setAttribute("CSRF_TOKEN", token);  // 存入 session
    result.put("token", token);
    return result;
}

// AuthController.java — 验证（第 77-83 行）
String csrfToken = request.getHeader("X-CSRF-TOKEN");          // 前端传来的 token
String sessionToken = (String) request.getSession().getAttribute("CSRF_TOKEN");  // session 中的 token
if (sessionToken == null || csrfToken == null || !sessionToken.equals(csrfToken)) {
    result.put("success", false);
    result.put("message", "无效请求");
    return result;
}
```

**为什么 CSRF Token 能防止攻击？**

- 恶意网站 evil.com 无法读取 wechat-group.com 的 `X-CSRF-TOKEN` session 值（同源策略）
- 无法获取到正确的 token，请求被拒绝
- 自定义请求头 `X-CSRF-TOKEN` 触发浏览器 CORS 预检（OPTIONS 请求），进一步增加攻击难度

### 8.6 AuthServiceImpl 登录流程

**文件**: `src/main/java/com/school/wechatgroup/service/impl/AuthServiceImpl.java`

```java
@Service
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public class AuthServiceImpl implements AuthService {

    private final AppUserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;

    @PostConstruct
    public void initDefaultAdmin() {
        if (userRepository.count() == 0) {                    // 表为空
            AppUser admin = new AppUser();
            admin.setUsername(authProperties.getDefaultUsername());  // "admin"
            admin.setPassword(passwordEncoder.encode(          // BCrypt 加密
                authProperties.getDefaultPassword()));         // "admin123"
            admin.setNickname("管理员");
            admin.setEnabled(true);
            userRepository.save(admin);                       // 插入数据库
        }
    }

    @Override
    public LoginResultVO login(String username, String password) {
        AppUser user = userRepository.findByUsername(username).orElse(null);
        if (user == null
                || !passwordEncoder.matches(password, user.getPassword())
                || !user.getEnabled()) {
            throw new BusinessException("用户名或密码错误");
        }
        return LoginResultVO.of(user.getUsername(), user.getNickname());
    }
}
```

**`@PostConstruct` 的执行时机**：

```
Spring Bean 生命周期:
    1. 构造器执行 (new AuthServiceImpl(...))
    2. 依赖注入完成 (userRepository, passwordEncoder, authProperties 已赋值)
    3. @PostConstruct 方法执行 (initDefaultAdmin())
    4. Bean 就绪，可以接受调用
```

**登录验证三步检查**：

```java
if (user == null                              // 1. 用户不存在
    || !passwordEncoder.matches(...)          // 2. 密码错误
    || !user.getEnabled()) {                  // 3. 账号已禁用
    throw new BusinessException("用户名或密码错误");  // 不区分具体原因，防止猜测
}
```

**为什么错误信息统一为"用户名或密码错误"？**

安全考虑：如果提示"用户不存在"，攻击者可以推测哪些用户名是有效的。不区分错误原因可以防止用户名枚举攻击。

### 8.7 SecurityFilter 安全头注入

**文件**: `src/main/java/com/school/wechatgroup/filter/SecurityFilter.java`

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)    // 最高优先级，确保最先执行
public class SecurityFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        httpResponse.setHeader("X-Content-Type-Options", "nosniff");      // 防 MIME 嗅探
        httpResponse.setHeader("X-Frame-Options", "DENY");                // 防点击劫持
        httpResponse.setHeader("X-XSS-Protection", "1; mode=block");      // 浏览器 XSS 过滤器
        httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin"); // Referer 策略

        chain.doFilter(request, response);  // 传递到下一个 Filter 或 Controller
    }
}
```

**各响应头的作用**：

| 响应头 | 值 | 防护目标 |
|--------|-----|---------|
| `X-Content-Type-Options` | `nosniff` | 防止浏览器根据内容猜测 MIME 类型（如把文本当作 JavaScript 执行） |
| `X-Frame-Options` | `DENY` | 禁止页面被 `<iframe>` 嵌入，防止点击劫持 |
| `X-XSS-Protection` | `1; mode=block` | 启用浏览器内置 XSS 过滤器，检测到攻击时阻止页面加载 |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | 跨域请求时只传源（不传完整路径），同源请求传完整 URL |

**Filter 和 Interceptor 的区别**：

| 维度 | Filter | Interceptor |
|------|--------|-------------|
| 规范 | Servlet 规范（Java EE） | Spring MVC 特有 |
| 作用范围 | 所有请求（包括静态资源） | 仅 DispatcherServlet 处理的请求 |
| 控制粒度 | 只能操作 Request/Response 对象 | 可访问 Controller 方法、参数 |
| 执行顺序 | Filter → Interceptor → Controller | |
| 典型用途 | 编码设置、安全头、CORS | 登录检查、权限验证、日志 |

---

## 第九章：文件解析（策略模式）

### 9.1 策略模式的定义——功能层面

策略模式（Strategy Pattern）解决的问题：**当同一个功能有多种实现方式时，将每种实现封装为独立的类，运行时按条件选择使用哪一个。**

本项目的场景：解析成员文件 → CSV 格式和 Excel 格式需要不同的解析逻辑。

### 9.2 接口定义

**文件**: `src/main/java/com/school/wechatgroup/service/parser/FileParserStrategy.java`

```java
public interface FileParserStrategy {
    List<String> parse(MultipartFile file);      // 解析文件，返回 userid 列表
    String supportedExtension();                  // 支持的扩展名（不含点号）
}
```

接口只定义契约（输入输出），不关心具体实现。

### 9.3 CsvFileParser — OpenCSV 实现

**文件**: `src/main/java/com/school/wechatgroup/service/parser/CsvFileParser.java`

```java
@Component("csvParser")                           // Bean 名称，用于 Map key
public class CsvFileParser implements FileParserStrategy {

    @Override
    public List<String> parse(MultipartFile file) {
        List<String> userList = new ArrayList<>();

        try (CSVReader csvReader = new CSVReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String[] values;
            while ((values = csvReader.readNext()) != null) {
                if (values.length > 0 && !values[0].trim().isEmpty()) {
                    if (!values[0].equalsIgnoreCase("userid")) {  // 跳过表头
                        userList.add(values[0].trim());
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("CSV文件解析失败: " + e.getMessage(), e);
        }
        return userList;
    }

    @Override
    public String supportedExtension() {
        return "csv";
    }
}
```

**逐行分析**：

```java
@Component("csvParser")
```
- 声明此类为 Spring Bean，名称为 `"csvParser"`
- `@Component` 会被 `@ComponentScan` 扫描到并创建实例

```java
try (CSVReader csvReader = new CSVReader(
        new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
```
- **try-with-resources 语句**：`CSVReader` 实现了 `Closeable` 接口，try 块结束后自动调用 `csvReader.close()`，无论是否发生异常
- `InputStreamReader`：将字节流（`InputStream`）转换为字符流（`Reader`）
- `StandardCharsets.UTF_8`：指定编码为 UTF-8。**不指定编码的话，会使用系统默认编码（Windows 上可能是 GBK），导致跨平台乱码**

```java
while ((values = csvReader.readNext()) != null) {
```
- `readNext()` 读取 CSV 的下一行，返回 `String[]` 数组。文件读完返回 `null`
- 赋值和判断可以写在一行（Java 赋值表达式返回值是被赋的值）

```java
if (!values[0].equalsIgnoreCase("userid")) {
    userList.add(values[0].trim());
}
```
- `equalsIgnoreCase`：忽略大小写比较。"UserID"、"USERID"、"userid" 都匹配
- 跳过表头行（第一行是 `userid`）

**为什么用 OpenCSV 而不是自己写字符串分割？**

CSV 格式看似简单，实际有复杂情况：
- 字段内包含逗号：`"Zhang, San",lisi`（需要用引号包裹）
- 字段内包含换行符：需要特殊处理
- 字段内包含引号：需要转义 `"He said ""Hello"""`

OpenCSV 作为成熟的 CSV 解析库，已经处理了所有这些边界情况。

### 9.4 ExcelFileParser — Apache POI 实现

**文件**: `src/main/java/com/school/wechatgroup/service/parser/ExcelFileParser.java`

```java
@Component("excelParser")
public class ExcelFileParser implements FileParserStrategy {

    @Override
    public List<String> parse(MultipartFile file) {
        List<String> userList = new ArrayList<>();

        try (Workbook workbook = createWorkbook(file)) {
            Sheet sheet = workbook.getSheetAt(0);   // 读取第一个工作表

            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell firstCell = row.getCell(0);      // 读取第一列
                if (firstCell == null) continue;

                String value = getCellValueAsString(firstCell).trim();
                if (value.isEmpty()) continue;

                if (value.equalsIgnoreCase("userid")) continue;  // 跳过表头

                userList.add(value);
            }
        } catch (Exception e) {
            throw new RuntimeException("Excel文件解析失败: " + e.getMessage(), e);
        }
        return userList;
    }

    @Override
    public String supportedExtension() {
        return "xlsx";
    }

    private Workbook createWorkbook(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".xls")) {
            return new HSSFWorkbook(file.getInputStream());  // 旧版 .xls
        }
        return new XSSFWorkbook(file.getInputStream());      // 新版 .xlsx
    }
}
```

**.xls 和 .xlsx 的区别**：

| 维度 | .xls (HSSFWorkbook) | .xlsx (XSSFWorkbook) |
|------|--------------------|--------------------|
| 底层格式 | BIFF 二进制 | Office Open XML (ZIP + XML) |
| 最大行数 | 65,536 | 1,048,576 |
| 内存占用 | 较小（流式） | 较大（DOM 模式） |
| POI 类 | `HSSFWorkbook` | `XSSFWorkbook` |

#### getCellValueAsString — 单元格类型处理

```java
// ExcelFileParser.java 第 71-92 行
private String getCellValueAsString(Cell cell) {
    switch (cell.getCellType()) {
        case STRING:
            return cell.getStringCellValue();
        case NUMERIC:
            double num = cell.getNumericCellValue();
            if (num == Math.floor(num) && !Double.isInfinite(num)) {
                return String.valueOf((long) num);  // 整数如 1001，不显示成 1001.0
            }
            return String.valueOf(num);             // 小数保持精度
        case BOOLEAN:
            return String.valueOf(cell.getBooleanCellValue());
        case FORMULA:
            try {
                return cell.getStringCellValue();   // 公式结果优先按字符串取
            } catch (Exception e) {
                return String.valueOf(cell.getNumericCellValue());  // 失败则按数字取
            }
        default:  // BLANK, ERROR, _NONE
            return "";
    }
}
```

**为什么需要区分单元格类型？**

Excel 单元格存储的不是纯文本，而是包含类型信息的结构化数据：
- `用户输入 "1001"` → Excel 可能存为 STRING 或 NUMERIC
- `用户输入公式 "=A1+B1"` → Excel 存为 FORMULA，需要计算

如果不对 NUMERIC 做特殊处理：`String.valueOf(1001.0)` → `"1001.0"`，企业微信不认这个 userid。

**整数判断逻辑**：

```java
if (num == Math.floor(num) && !Double.isInfinite(num))
//  Math.floor(1001.0) = 1001.0 → 相等 → 是整数
//  Math.floor(1001.5) = 1001.0 → 不等 → 不是整数
```

### 9.5 Spring 自动收集策略实现

回顾 Service 层的注入代码：

```java
// WeChatGroupServiceImpl.java 第 92 行
public WeChatGroupServiceImpl(Map<String, FileParserStrategy> parserMap, ...) {
    this.parserMap = parserMap;
}
```

Spring 看到构造器参数 `Map<String, FileParserStrategy>` 后：
1. 查找容器中所有 `FileParserStrategy` 类型的 Bean
2. 找到 `csvParser`（CsvFileParser）和 `excelParser`（ExcelFileParser）
3. 构造 Map：`{"csvParser" → csvParser实例, "excelParser" → excelParser实例}`

**添加新格式只需要**：

```java
@Component("jsonParser")
public class JsonFileParser implements FileParserStrategy {
    @Override
    public List<String> parse(MultipartFile file) { /* JSON 解析逻辑 */ }
    @Override
    public String supportedExtension() { return "json"; }
}
```

无需修改 WeChatGroupServiceImpl 的任何代码，新的解析器自动被注入到 `parserMap` 中。

---

## 第十章：异常处理

### 10.1 Java 异常体系

```
Throwable
├── Error (不可恢复，JVM 级错误)
│   ├── OutOfMemoryError
│   ├── StackOverflowError
│   └── ...
└── Exception (可处理的异常)
    ├── RuntimeException (非受检异常，不需要显式捕获或抛出)
    │   ├── NullPointerException
    │   ├── IllegalArgumentException
    │   ├── BusinessException  ← 本项目自定义
    │   └── ...
    └── 其他 Exception (受检异常，必须显式处理)
        ├── IOException
        ├── SQLException
        └── ...
```

**非受检异常（RuntimeException） vs 受检异常（checked Exception）**：

| 类型 | 声明要求 | 典型场景 | 设计意图 |
|------|---------|---------|---------|
| 非受检 | 无需 throws 声明或 try-catch | 程序 bug（NPE）、业务规则违反 | 调用方通常无法恢复 |
| 受检 | 必须 try-catch 或 throws | I/O 失败、网络超时 | 调用方必须考虑如何处理 |

### 10.2 BusinessException 自定义业务异常

**文件**: `src/main/java/com/school/wechatgroup/exception/BusinessException.java`

```java
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**为什么继承 RuntimeException？**

1. 非受检：调用方不需要在方法签名中声明 `throws BusinessException`
2. Spring 的 `@Transactional` 默认只对 RuntimeException 回滚
3. 两个构造器：一个只接受消息，一个接受消息+原因（异常链，保留原始堆栈信息）

### 10.3 @RestControllerAdvice 全局异常拦截

**文件**: `src/main/java/com/school/wechatgroup/exception/GlobalExceptionHandler.java`

```java
@RestControllerAdvice             // = @ControllerAdvice + @ResponseBody
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 处理业务异常
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());          // WARN 级别
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", e.getMessage());               // 直接返回原始消息
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);  // 400
    }

    // 处理文件上传大小超限
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)     // 413
                .body("上传失败: 文件大小超过限制（最大 10MB）");
    }

    // 处理所有未捕获的异常（兜底）
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("系统异常", e);                       // ERROR 级别，打印完整堆栈
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", "系统内部错误，请联系管理员");  // 脱敏：不暴露内部错误详情
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);  // 500
    }
}
```

**异常处理优先级**：Spring 按异常类型匹配，最具体的处理器优先。

```
抛出 BusinessException
  ↓
@ExceptionHandler(BusinessException.class)  ← 命中（最具体）
@ExceptionHandler(Exception.class)          ← 不执行（已被匹配）

抛出 NullPointerException
  ↓
@ExceptionHandler(BusinessException.class)  ← 不匹配
@ExceptionHandler(Exception.class)          ← 命中（兜底）
```

**信息泄露防护**：

```java
// 错误做法：
body.put("message", e.toString());  // 暴露 java.sql.SQLException: Access denied for user...

// 正确做法（本项目的实现）：
body.put("message", "系统内部错误，请联系管理员");  // 只返回通用消息
log.error("系统异常", e);  // 详细信息记录在服务端日志中
```

**HTTP 状态码的选择**：

| 状态码 | 含义 | 使用场景 |
|--------|------|---------|
| 400 BAD_REQUEST | 客户端请求有误 | 业务校验失败 |
| 413 PAYLOAD_TOO_LARGE | 请求体超限 | 文件大小超过 `max-file-size` |
| 500 INTERNAL_SERVER_ERROR | 服务器内部错误 | 未预期的异常 |
| 401 UNAUTHORIZED | 未认证 | 需要登录但未登录（本项目用 302 重定向代替） |

---

## 第十一章：前端（Thymeleaf + Bootstrap 5）

### 11.1 Thymeleaf 模板引擎

**什么是模板引擎**：模板引擎将模板文件（HTML 中的占位符）与数据组合，输出最终的 HTML。工作过程在**服务器端**完成，浏览器接收到的已经是纯 HTML。

**Thymeleaf 的核心机制**：

```
请求: GET /login
  │
  ▼
PageController.loginPage() 返回 "login"
  │
  ▼
ThymeleafViewResolver 定位到 classpath:/templates/login.html
  │
  ▼
Thymeleaf 模板引擎解析模板
  - xmlns:th="http://www.thymeleaf.org"  → 识别 th: 命名空间
  - 执行 th:xxx 属性中的表达式
  - 替换占位符为实际数据
  │
  ▼
输出纯 HTML → 写入 HTTP 响应体
```

**项目中使用的 Thymeleaf 属性**：

```html
<!-- login.html 第 2 行 -->
<html lang="zh-CN" xmlns:th="http://www.thymeleaf.org">
```

`xmlns:th` 是 Thymeleaf 的命名空间声明。有了它，IDE 才能正确识别 `th:` 前缀的属性，配置为：

```properties
# application.properties
spring.thymeleaf.prefix=classpath:/templates/
spring.thymeleaf.suffix=.html
spring.thymeleaf.encoding=UTF-8
spring.thymeleaf.cache=false   # 开发环境关闭缓存，方便热更新
```

### 11.2 Bootstrap 5 栅格系统

```html
<!-- index.html 第 679 行 -->
<div class="row justify-content-center">
    <div class="col-lg-8">
        <!-- 内容在大屏幕占 8/12 宽度，居中显示 -->
    </div>
</div>
```

Bootstrap 栅格系统将页面水平分为 12 列：

| 类名 | 含义 |
|------|------|
| `col-lg-8` | 大屏幕（>=992px）占 8/12 宽度 |
| `col-md-6` | 中等屏幕（>=768px）占 6/12 宽度 |
| `col-12` | 所有屏幕占 12/12（全宽） |
| `justify-content-center` | 水平居中 |
| `g-3` / `g-4` | 列间距（gutter），值越大间距越大 |

### 11.3 JavaScript 与后端交互

#### 11.3.1 文件拖拽上传

```javascript
// index.html 第 896-913 行
dropZone.addEventListener('dragover', function(e) {
    e.preventDefault();                                 // 阻止浏览器默认行为（打开文件）
    dropZone.classList.add('drag-over');                // 添加高亮样式
});

dropZone.addEventListener('drop', function(e) {
    e.preventDefault();
    dropZone.classList.remove('drag-over');
    const files = e.dataTransfer.files;                 // 获取拖拽的文件
    if (files.length) {
        fileInput.files = files;                        // 赋值给 <input type="file">
        showFileName(files[0].name);
    }
});
```

**为什么需要 `e.preventDefault()`？**

浏览器的默认行为是：将拖入的文件在新标签页打开。如果不阻止，页面会跳转到文件内容（或下载文件），而不是上传。

`dataTransfer.files` 返回 `FileList` 对象，包含拖拽的所有文件。

#### 11.3.2 fetch API 发送请求

```javascript
// index.html 第 920-960 行 — 表单提交
const fd = new FormData();
fd.append('groupName', groupName);
fd.append('ownerId', ownerId);
fd.append('userId', userId);
fd.append('file', file);

fetch('/api/group/create', {
    method: 'POST',
    body: fd                        // FormData → multipart/form-data
})
.then(function(r) { return r.json(); })  // 解析 JSON 响应
.then(function(result) {
    // 根据 result.success 显示成功/失败卡片
})
.catch(function(err) {
    // 网络错误处理
});
```

**fetch API 与旧式 XMLHttpRequest 的区别**：

| 特性 | fetch | XMLHttpRequest |
|------|-------|---------------|
| 语法 | Promise 链式调用 | 回调函数 |
| 错误处理 | `catch()` 统一处理 | `onerror` + `ontimeout` |
| JSON 解析 | `response.json()` | `JSON.parse(xhr.responseText)` |
| 请求取消 | `AbortController` | `xhr.abort()` |

**FormData 对象**：

```javascript
// FormData 自动设置 Content-Type 为 multipart/form-data
const fd = new FormData();
fd.append('groupName', '测试群聊');         // 文本字段
fd.append('file', fileInput.files[0]);     // 文件字段
```

浏览器自动生成请求头：
```
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW
```

#### 11.3.3 XSS 防护

```javascript
// index.html 第 835-840 行
function escapeHtml(t) {
    if (!t) return '';
    const d = document.createElement('div');
    d.textContent = t;        // textContent 自动转义 HTML 特殊字符
    return d.innerHTML;       // 读取转义后的 HTML
}
// 使用：resultBox.innerHTML = escapeHtml(result.message);
```

**为什么 `.textContent` 能防 XSS？**

```javascript
const d = document.createElement('div');

// innerHTML — 把字符串当作 HTML 解析：
d.innerHTML = "<script>alert('xss')</script>";
// 危险：脚本会执行！

// textContent — 把字符串当作纯文本：
d.textContent = "<script>alert('xss')</script>";
d.innerHTML → "&lt;script&gt;alert('xss')&lt;/script&gt;";
// 安全：特殊字符被转义为 HTML 实体，不会执行
```

所以 `escapeHtml()` 的流程是：设置 textContent（特殊字符自动转义）→ 读取 innerHTML（获得转义后的安全字符串）。

### 11.4 标签切换（Tab）实现

```javascript
// index.html 第 826-833 行
document.getElementById('tabNav').addEventListener('click', function(e) {
    const btn = e.target.closest('.tab-btn');   // 找到最近的 .tab-btn 祖先
    if (!btn) return;                           // 点击的不是标签按钮则忽略

    // 移除所有 active
    document.querySelectorAll('.tab-btn').forEach(function(b) {
        b.classList.remove('active');
    });
    document.querySelectorAll('.tab-panel').forEach(function(p) {
        p.classList.remove('active');
    });

    // 激活当前
    btn.classList.add('active');
    document.getElementById('panel-' + btn.dataset.tab).classList.add('active');
});
```

**`e.target.closest('.tab-btn')` 的作用**：事件委托（Event Delegation）。按钮内部可能包含 `<i>` 图标元素，点击图标时 `e.target` 是 `<i>` 而非 `<button>`。`closest()` 向上查找最近的匹配祖先，确保总是找到按钮元素。

```
点击 <button class="tab-btn"><i class="bi ..."></i>创建群聊</button>
     e.target = <i>
     e.target.closest('.tab-btn') = <button class="tab-btn">
```

---

## 第十二章：定时任务

### 12.1 WeChatTokenManager 完整分析

**文件**: `src/main/java/com/school/wechatgroup/task/WeChatTokenManager.java`

```java
@Component
public class WeChatTokenManager {

    private static final Logger log = LoggerFactory.getLogger(WeChatTokenManager.class);

    private final WeChatProperties properties;
    private final RestTemplate restTemplate;

    private volatile String accessToken;          // [关键] volatile 关键字

    public WeChatTokenManager(WeChatProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    @PostConstruct                                   // 启动后立即执行一次
    public void init() {
        refreshAccessToken();
    }

    @Scheduled(fixedRate = 3600000)                  // 每 3600000ms = 1 小时执行
    public void refreshAccessToken() {
        String url = String.format(
            "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=%s&corpsecret=%s",
            properties.getCorpid(), properties.getCorpsecret());

        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && safeParseErrcode(response.get("errcode")) == 0) {
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
```

### 12.2 @PostConstruct 初始化回调

`@PostConstruct` 标注的方法在依赖注入完成后自动调用。执行时机：

```
new WeChatTokenManager(properties, restTemplate)  [构造器]
    → 依赖注入 properties, restTemplate
    → @PostConstruct → init() → refreshAccessToken()  [首次获取 token]
    → Bean 就绪
```

等价于传统 Spring XML 配置中的 `init-method` 属性。Java EE 5 引入的标准注解，不仅限于 Spring 使用。

### 12.3 @Scheduled(fixedRate) 用法

```java
@Scheduled(fixedRate = 3600000)  // 60 * 60 * 1000 毫秒
```

`fixedRate` 的含义：**从上一次调用开始计时**，每隔固定时间调用一次。不管上一次是否完成：

```
时间线：    |──── 1h ────|──── 1h ────|──── 1h ────|
           ↑             ↑             ↑             ↑
        启动后立即    1小时后      2小时后      3小时后
```

**`@Scheduled` 的三种参数**：

| 参数 | 含义 | 示例 |
|------|------|------|
| `fixedRate` | 从**开始**计算间隔 | `fixedRate = 60000`：每 60 秒 |
| `fixedDelay` | 从**结束**计算间隔 | `fixedDelay = 60000`：上一次执行完 60 秒后 |
| `cron` | Cron 表达式 | `cron = "0 0 3 * * ?"`：每天凌晨 3 点 |

### 12.4 volatile 关键字的作用

```java
private volatile String accessToken;
```

**多线程场景**：
- **写线程**：定时任务线程 `refreshAccessToken()` 每小时更新 `accessToken`
- **读线程**：HTTP 请求处理线程通过 `getAccessToken()` 读取

**没有 `volatile` 的问题**：Java 内存模型允许线程将变量值缓存在 CPU 寄存器或线程私有内存中。定时任务线程更新了 `accessToken`，但 HTTP 线程可能读到旧的缓存值。

**`volatile` 保证的两个特性**：

1. **可见性**：一个线程对 volatile 变量的写入，对其他线程立即可见。原理是：写入 volatile 时强制刷新到主内存，读取时强制从主内存加载。

2. **禁止指令重排序**：JVM 和 CPU 可能对指令重新排序以优化性能。volatile 变量前后的操作不会乱序。

```java
// 假设没有 volatile：
定时任务线程：    accessToken = "new_token_abc";    // 写入 CPU 缓存
HTTP 请求线程：   String token = accessToken;       // 可能读到旧值

// 有 volatile：
定时任务线程：    accessToken = "new_token_abc";    // 写入主内存
HTTP 请求线程：   String token = accessToken;       // 从主内存读取，保证最新
```

**`volatile` 不保证原子性**：如果多个线程同时执行 `accessToken = ... + ...`（读-改-写复合操作），`volatile` 不能保证正确。但本项目只有一个写线程（定时任务线程），所以安全。

---

## 第十三章：构建与部署

### 13.1 pom.xml 结构解析

**文件**: `pom.xml`

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version>
</parent>
```

**继承 Spring Boot Parent**：`spring-boot-starter-parent` 预定义了：
- Java 编译版本（通过 `<java.version>` 属性覆盖）
- 依赖版本管理（数百个第三方库的兼容版本组合）
- Maven 插件配置（`maven-compiler-plugin`, `maven-resources-plugin` 等）
- 资源过滤和打包规则

```xml
<properties>
    <java.version>17</java.version>
</properties>
```

设置为 Java 17。Spring Boot 3.x 要求 Java 17 及以上，因为使用了 records、sealed classes、pattern matching 等 JDK 17 特性。

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <!-- ... -->
</dependencies>
```

**Starter 依赖**：Spring Boot 的核心创新之一。一个 starter 会引入一组协调好的依赖。`spring-boot-starter-web` 包含：
- `spring-webmvc`：Spring MVC 框架
- `spring-boot-starter-tomcat`：内嵌 Tomcat 服务器
- `jackson-databind`：JSON 序列化
- `hibernate-validator`：数据校验

### 13.2 Maven 依赖传递机制

Maven 的依赖解析是**传递性**的：A 依赖 B，B 依赖 C，则 A 自动获得 C。

```
本项目
├── spring-boot-starter-web
│   ├── spring-webmvc
│   │   └── spring-beans
│   ├── spring-boot-starter-tomcat
│   │   └── tomcat-embed-core
│   └── jackson-databind
├── spring-boot-starter-data-jpa
│   ├── hibernate-core
│   └── spring-data-jpa
├── mysql-connector-j (scope=runtime)
├── h2 (scope=runtime)
└── ...
```

**`scope` 属性控制传递范围**：

| scope | 含义 | 何时需要 |
|-------|------|---------|
| `compile`（默认） | 编译、测试、运行时都可用 | 大多数情况 |
| `runtime` | 编译时不需要，运行时需要 | JDBC 驱动（编译时用 JPA 接口代替） |
| `test` | 只有测试时可用 | JUnit, Mockito |
| `provided` | 编译时需要，运行时由容器提供 | Servlet API（Tomcat 已内置） |
| `optional` | 不传递 | `spring-boot-configuration-processor`（只用于 IDE 提示） |

### 13.3 spring-boot-maven-plugin

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

**三个核心功能**：

1. **Fat JAR 打包**：将所有依赖的 JAR 文件解压后重新打包到一个 JAR 中

```
mvnw clean package
  → target/wechat-group-0.0.1-SNAPSHOT.jar  (可独立运行)
```

普通 JAR：`me.jar` + 依赖的 50 个 JAR → 需要 classpath 配置
Fat JAR：一个 `me.jar` 包含所有依赖 → `java -jar me.jar` 即可启动

2. **创建可执行 JAR**：在 `META-INF/MANIFEST.MF` 中写入 `Main-Class` 和 `Start-Class`

```
Main-Class: org.springframework.boot.loader.JarLauncher    ← 启动器
Start-Class: com.school.wechatgroup.WechatGroupApplication  ← 实际入口
```

`JarLauncher` 负责创建特殊的 classloader 来加载嵌套的 JAR 依赖。

3. **`spring-boot:run` 目标**：开发时直接运行

```bash
./mvnw spring-boot:run
# 等价于 java -jar target/wechat-group-0.0.1-SNAPSHOT.jar
```

### 13.4 Maven Wrapper (mvnw)

```bash
# Windows (PowerShell)
.\mvnw.cmd clean package

# Linux / macOS
./mvnw clean package
```

**Maven Wrapper 解决的问题**：团队成员不需安装 Maven。`mvnw` 脚本自动下载指定版本的 Maven（本项目为 3.3.4），确保所有人使用相同版本，消除"在我机器上能跑"问题。

**文件组成**：
- `mvnw` / `mvnw.cmd`：启动脚本
- `.mvn/wrapper/maven-wrapper.properties`：指定 Maven 版本和下载源地址

### 13.5 启动和部署命令

```bash
# 开发环境启动
java -jar target/wechat-group-0.0.1-SNAPSHOT.jar \
     --spring.profiles.active=dev

# 生产环境启动
java -jar target/wechat-group-0.0.1-SNAPSHOT.jar \
     --spring.profiles.active=prod

# Windows PowerShell
java -jar "target/wechat-group-0.0.1-SNAPSHOT.jar" --spring.profiles.active=dev
```

**命令行参数优先级**：`--spring.profiles.active=prod` 会覆盖 `application.properties` 中的同名配置。优先级从高到低：

```
1. 命令行参数 (--key=value)
2. 操作系统环境变量 (WECHAT_WORK_CORPID)
3. application-{profile}.properties
4. application.properties
5. @ConfigurationProperties 默认值
```

### 13.6 部署依赖检查清单

部署前的必要准备：

| 检查项 | 开发环境 | 生产环境 |
|--------|---------|---------|
| JDK 版本 | Java 17+ | Java 17+ |
| 数据库 | H2（自动建表） | MySQL 8.0+（手动建库） |
| 企业微信凭据 | application-dev.properties | application-prod.properties |
| app.base-url | 本地或 ngrok | 正式域名 |
| 端口 | 8082 | 8082（或 nginx 反向代理 80→8082） |
| 文件上传大小 | 10MB | 10MB |
| ddl-auto | update | validate |
| SQL 日志 | true | false |
| 日志级别 | DEBUG | INFO |

---

## 附录：关键术语表

| 术语 | 解释 | 相关章节 |
|------|------|---------|
| IoC | 控制反转（Inversion of Control）：对象创建和依赖注入的控制权从应用代码转移到框架（Spring 容器） | 第三章 |
| DI | 依赖注入（Dependency Injection）：构造器/字段自动传入依赖 | 第五章 |
| Bean | Spring 容器管理的 Java 对象实例 | 第三章 |
| ORM | 对象关系映射（Object-Relational Mapping）：Java 对象与数据库表的对应关系 | 第七章 |
| AOP | 面向切面编程：在不修改原有代码的情况下添加新功能（如事务、日志） | 第六章 |
| DDL | 数据定义语言：CREATE TABLE, ALTER TABLE 等 | 第七章 |
| DML | 数据操作语言：SELECT, INSERT, UPDATE, DELETE | 第七章 |
| REST | 表述性状态转移：使用 HTTP 方法（GET/POST/PUT/DELETE）操作资源的 API 设计风格 | 第五章 |
| JSON | JavaScript 对象记法：本项目 API 的数据交换格式 | 第五章 |
| CSRF | 跨站请求伪造：利用已登录状态的攻击类型 | 第八章 |
| XSS | 跨站脚本攻击：注入恶意脚本的安全漏洞类型 | 第十一章 |
| OAuth 2.0 | 授权框架标准：本项目用于企业微信用户身份认证 | 第五章 |
| SHA-256 | 安全哈希算法 256 位 | 第八章 |
| BCrypt | 专门为密码存储设计的哈希算法 | 第八章 |
| UUID | 通用唯一标识符：128位随机数，用于全局唯一 ID | 第六章 |
| Maven | Java 项目管理工具：依赖管理 + 构建 | 第十三章 |

---

*本文档基于项目源代码 v0.0.1-SNAPSHOT 编写，所有代码片段均来自实际项目文件。*
