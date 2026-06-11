# 企业微信批量建群工具

> Spring Boot 3.2.5 + Java 17 + Maven | Thymeleaf + Bootstrap 5 | 批量创建企业微信群聊

---

## 目录

- [快速开始](#快速开始)
- [项目简介](#项目简介)
- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [系统架构](#系统架构)
- [配置指南](#配置指南)
- [API 文档](#api-文档)
- [错误码速查](#错误码速查)
- [部署指南](#部署指南)
- [开发指南](#开发指南)
- [安全措施](#安全措施)

---

## 快速开始

**前提条件：** JDK 17+，无需安装 Maven（已包含 Maven Wrapper）。

```bash
# 1. 配置
cp src/main/resources/application.properties.example src/main/resources/application.properties
cp src/main/resources/application-dev.properties.example src/main/resources/application-dev.properties
# 编辑 application-dev.properties，填入企业微信 corpid / corpsecret / agentid

# 2. 构建
./mvnw clean package

# 3. 运行
./mvnw spring-boot:run
```

浏览器访问 `http://localhost:8082`，使用默认账号 `admin` 登录即可。

---

## 项目简介

本工具面向**企业微信管理员和教师**，通过上传成员名单文件（CSV 或 Excel），一键批量创建企业微信群聊。解决了手动逐一拉人建群的低效问题，适用于学校班级群、部门群、项目讨论组等场景。

**工作流程：**

```
准备成员名单 (CSV/Excel)  →  上传表单  →  一键建群  →  返回结果 + 数据库记录
```

---

## 功能特性

- **一键批量建群** -- 上传 CSV 或 Excel 名单，自动调用企业微信 API 创建群聊
- **多种文件格式** -- 支持 `.csv`、`.xlsx`、`.xls`，策略模式扩展新格式
- **42 种错误码翻译** -- 企业微信原始错误码自动翻译为中文说明，覆盖通用、权限、群聊、限流等类别
- **OAuth2 身份认证** -- 企业微信工作台自动获取用户身份，操作可追溯
- **登录认证系统** -- 基于 Session  + BCrypt 密码加密，可通过 `app.auth.enabled` 开关启用/禁用
- **CSRF 防护** -- 登录接口 Token 校验，防止跨站请求伪造
- **登录限流** -- 同一 IP 每分钟最多 5 次尝试，内存缓存 + 定时清理
- **安全响应头** -- 自动注入 `X-Content-Type-Options`、`X-Frame-Options`、`X-XSS-Protection`、`Referrer-Policy`
- **数据库持久化** -- Spring Data JPA + MySQL（生产）/ H2（开发），三张业务表记录操作历史
- **文件日志轮转** -- Logback 按天切割日志，保留 30 天，审计日志独立文件
- **多环境配置** -- `dev` / `prod` profile 自动切换数据库、日志级别、认证行为
- **模板下载** -- 动态生成 CSV / Excel 成员模板、Word 操作指南文档
- **专业前端** -- Thymeleaf + Bootstrap 5 浅色主题，三页签 UI（建群、指南、模板）
- **企业级分层架构** -- Controller / Service / Repository / Entity 清晰分层，VO/DTO/Util 工具类齐全

---

## 技术栈

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| **语言** | Java | 17 | 开发语言 |
| **框架** | Spring Boot | 3.2.5 | 应用框架 |
| **模板引擎** | Thymeleaf | 3.x | 服务端渲染页面 |
| **前端** | Bootstrap 5 | 5.x | 响应式 UI 框架 |
| **持久化** | Spring Data JPA | 3.2.5 | ORM 数据库操作 |
| **数据库** | MySQL / H2 | 8.0+ / 2.x | 生产数据库 / 开发内置数据库 |
| **连接池** | HikariCP | 内嵌 | 数据库连接池（生产配置） |
| **安全** | spring-security-crypto | 6.x | BCrypt 密码加密 |
| **CSV** | OpenCSV | 5.7.1 | CSV 文件解析 |
| **Excel/Word** | Apache POI | 5.2.5 | Excel 解析 + Word 文档生成 |
| **日志** | SLF4J + Logback | 内嵌 | 日志记录与文件轮转 |
| **构建** | Maven Wrapper | 3.3.4 | 无需预装 Maven |
| **测试** | JUnit 5 | 内嵌 | 单元测试 |

---

## 项目结构

```
wechat-group/
├── pom.xml
├── mvnw / mvnw.cmd                      ← Maven Wrapper
├── README.md
├── DEPLOY.md
├── nginx.conf
│
├── src/main/java/com/school/wechatgroup/
│   ├── WechatGroupApplication.java      ← 应用入口（@EnableScheduling）
│   │
│   ├── config/
│   │   ├── WeChatProperties.java        ← 企微配置绑定 (wechat.work.*)
│   │   ├── AuthProperties.java          ← 认证配置绑定 (app.auth.*)
│   │   ├── AuthConfig.java              ← BCryptPasswordEncoder Bean（按需启用）
│   │   └── WebMvcConfig.java            ← 拦截器注册（登录保护，按需启用）
│   │
│   ├── controller/
│   │   ├── WeChatGroupController.java   ← REST API：建群、模板下载、指南下载、OAuth 入口
│   │   ├── AuthController.java          ← 登录/登出/CSRF Token/状态查询/限流
│   │   └── PageController.java          ← Thymeleaf 页面路由
│   │
│   ├── service/
│   │   ├── WeChatGroupService.java      ← 建群业务接口
│   │   ├── AuthService.java             ← 认证业务接口
│   │   ├── GroupRecordService.java      ← 操作记录业务接口
│   │   ├── impl/
│   │   │   ├── WeChatGroupServiceImpl.java  ← 建群核心逻辑 + 42 条错误码翻译
│   │   │   ├── AuthServiceImpl.java         ← BCrypt 密码校验 + Session 管理
│   │   │   └── GroupRecordServiceImpl.java  ← 三表事务写入
│   │   └── parser/
│   │       ├── FileParserStrategy.java      ← 解析策略接口
│   │       ├── CsvFileParser.java           ← CSV 解析（OpenCSV）
│   │       └── ExcelFileParser.java         ← Excel 解析（.xlsx/.xls，Apache POI）
│   │
│   ├── repository/
│   │   ├── GroupOperationRepository.java    ← t_group_operation
│   │   ├── WxGroupRepository.java           ← t_wx_group
│   │   ├── UploadFileRepository.java        ← t_upload_file
│   │   └── AppUserRepository.java           ← t_app_user（认证用户）
│   │
│   ├── entity/
│   │   ├── GroupOperation.java          ← 建群操作记录
│   │   ├── WxGroup.java                 ← 成功创建的群聊
│   │   ├── UploadFile.java              ← 上传文件存档（LONGBLOB）
│   │   └── AppUser.java                 ← 认证用户（BCrypt 密码）
│   │
│   ├── vo/
│   │   ├── CreateGroupResultVO.java     ← 建群结果
│   │   ├── LoginResultVO.java           ← 登录结果
│   │   └── AuthStatusVO.java            ← 认证状态
│   │
│   ├── constant/
│   │   ├── OperationStatus.java         ← SUCCESS / FAILED 枚举
│   │   └── SessionKeys.java             ← Session 属性键常量
│   │
│   ├── exception/
│   │   ├── BusinessException.java       ← 业务异常（由全局处理器捕获）
│   │   └── GlobalExceptionHandler.java  ← @RestControllerAdvice 全局异常处理
│   │
│   ├── filter/
│   │   └── SecurityFilter.java          ← 安全响应头注入（最高优先级）
│   │
│   ├── interceptor/
│   │   └── LoginInterceptor.java        ← 登录拦截（未登录重定向 /login）
│   │
│   ├── util/
│   │   ├── IpUtils.java                 ← 客户端 IP 提取（X-Forwarded-For）
│   │   └── SecurityUtils.java           ← Session 中获取当前用户
│   │
│   └── task/
│       └── WeChatTokenManager.java      ← Token 获取 + 每小时自动刷新
│
├── src/main/resources/
│   ├── application.properties           ← 公共配置（激活 profile、端口等）
│   ├── application.properties.example   ← 公共配置模板
│   ├── application-dev.properties       ← 开发环境（H2 + debug日志 + 自动建表）
│   ├── application-dev.properties.example
│   ├── application-prod.properties      ← 生产环境（MySQL + 连接池）
│   ├── application-prod.properties.example
│   ├── logback-spring.xml               ← 日志配置（按天轮转，保留 30 天）
│   ├── templates/
│   │   ├── index.html                   ← 主页面（Bootstrap 5 三页签）
│   │   └── login.html                   ← 登录页面（渐变色背景）
│   └── static/templates/
│       └── member_template.csv          ← 静态模板备份
│
└── src/test/java/com/school/wechatgroup/service/parser/
    ├── CsvFileParserTest.java           ← CSV 解析器测试（5 用例）
    └── ExcelFileParserTest.java         ← Excel 解析器测试（6 用例）
```

---

## 系统架构

```
┌──────────────────────────────────────────────────────────┐
│                    浏览器 (Browser)                        │
│        Thymeleaf + Bootstrap 5 (index / login)            │
└─────────────────────┬────────────────────────────────────┘
                      │
          ┌───────────┼───────────┐
          ▼           ▼           ▼
   ┌───────────┐ ┌───────┐ ┌──────────┐
   │ Security  │ │ Login │ │   Page   │
   │  Filter   │ │ Inter │ │Controller│  ← 拦截器/过滤器层
   └───────────┘ └───────┘ └──────────┘
          │           │           │
          ▼           ▼           ▼
   ┌─────────────────────────────────────┐
   │           Controller 层              │
   │  AuthController  │  WeChatGroup     │
   │  (登录/登出/限流)  │  Controller     │
   │                  │  (建群/OAuth)    │
   └────────┬─────────┴────────┬─────────┘
            │                  │
            ▼                  ▼
   ┌──────────────┐  ┌─────────────────┐
   │ AuthService  │  │ WeChatGroup     │
   │ (BCrypt校验)  │  │ Service          │
   │              │  │ (文件解析+建群)    │
   └──────┬───────┘  └────┬────────────┘
          │               │
          │               ├──► FileParserStrategy  ← 策略模式
          │               │    ├── CsvFileParser   (OpenCSV)
          │               │    └── ExcelFileParser (Apache POI)
          │               │
          │               ├──► WeChatTokenManager  ← Token 管理
          │               │    (volatile + @Scheduled)
          │               │
          │               └──► ERROR_EXPLAIN Map   ← 42 种错误翻译
          │
          ▼
   ┌─────────────────────────────────────┐
   │         Repository 层 (JPA)          │
   │  AppUserRepo  │  GroupOperationRepo │
   │  WxGroupRepo  │  UploadFileRepo     │
   └───────────────┬─────────────────────┘
                   │
          ┌────────┴────────┐
          ▼                 ▼
   ┌──────────┐     ┌──────────────┐
   │  MySQL   │     │     H2        │
   │ (生产环境) │     │ (开发环境/内存) │
   └──────────┘     └──────────────┘
                   │
                   ▼
   ┌─────────────────────────────────────┐
   │       企业微信 Open API               │
   │  qyapi.weixin.qq.com               │
   │  GET /cgi-bin/gettoken              │
   │  POST /cgi-bin/appchat/create       │
   │  GET /cgi-bin/user/getuserinfo      │
   └─────────────────────────────────────┘
```

---

## 配置指南

### 多环境切换

项目通过 Spring Profile 区分环境，主配置文件 `application.properties` 中指定：

```properties
spring.profiles.active=dev    # 开发环境（H2数据库）
# spring.profiles.active=prod   # 生产环境（MySQL数据库）
```

所有环境共用的配置（端口、基础 URL、默认管理员账号、文件上传限制）在 `application.properties` 中；环境差异配置（数据库、日志级别、企微凭证）在各自的 `application-[profile].properties` 中。

### 认证开关

通过 `app.auth.enabled` 控制登录认证是否启用：

```properties
# 开发/生产环境默认启用
app.auth.enabled=true

# 如设为 false，所有页面无需登录即可访问，拦截器不会加载
app.auth.enabled=false
```

默认管理员账号密码：

```properties
app.auth.default-username=admin
app.auth.default-password=你的密码
```

密码以 **BCrypt** 加密存储在 `t_app_user` 表中。应用首次启动时若表为空，会自动创建默认用户。

### 数据库配置

**开发环境（H2 内存数据库）：**

```properties
spring.datasource.url=jdbc:h2:mem:wechat_group;MODE=MySQL;DB_CLOSE_DELAY=-1
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=update              # 自动建表
spring.jpa.show-sql=true                          # 打印 SQL
spring.h2.console.enabled=true                    # H2 Web 控制台
```

浏览器访问 `http://localhost:8082/h2-console` 可查看内存数据库。

**生产环境（MySQL 8.0+）：**

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/wechat_group?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
spring.datasource.username=wechat_app
spring.datasource.password=数据库密码
spring.jpa.hibernate.ddl-auto=validate            # 仅校验，不自动变更
spring.jpa.show-sql=false

# HikariCP 连接池优化
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
```

### 企业微信配置

在企业微信管理后台获取以下信息并配置：

```properties
wechat.work.corpid=你的企业ID
wechat.work.corpsecret=应用Secret
wechat.work.agentid=你的AgentId
app.base-url=https://你的应用访问地址
```

OAuth2 回调需要将 `app.base-url` 的域名配置到企业微信管理后台的应用设置 -- 网页授权及 JS-SDK -- 可信域名中。

---

## API 文档

### 页面路由

| 方法 | 路径 | 说明 | 需要登录 |
|------|------|------|----------|
| GET | `/` | OAuth2 认证入口，三路分支（已认证 / 有 code / 发起授权） | 否 |
| GET | `/login` | 登录页面（Thymeleaf 渲染） | 否 |
| GET | `/index` | 主功能页面（Thymeleaf 渲染） | 是 |

### 建群 API

**POST `/api/group/create`**

```
Content-Type: multipart/form-data

groupName  (必填)  String    群聊名称
ownerId    (必填)  String    群主企业微信 UserID
file       (必填)  File      成员名单文件 (.csv / .xlsx / .xls)
userId     (可选)  String    操作人标识，默认"本地用户"
```

响应（JSON）：

```json
// 成功
{ "success": true, "chatId": "wrFKkVYAAA...", "message": "建群成功！群聊ID: xxx，成员数: 3" }

// 失败
{ "success": false, "message": "错误中文说明", "errcode": 60111, "errmsg": "原始错误信息" }
```

### 认证 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/auth/csrf` | 获取 CSRF Token（登录前调用） |
| POST | `/api/auth/login` | 登录（参数: username, password, Header: X-CSRF-TOKEN） |
| POST | `/api/auth/logout` | 登出 |
| GET | `/api/auth/status` | 查询当前登录状态 |

### 下载 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/template/csv` | 下载 CSV 成员导入模板 |
| GET | `/api/template/excel` | 动态生成并下载 Excel (.xlsx) 模板 |
| GET | `/api/guide` | 动态生成并下载 Word (.docx) 操作指南 |

---

## 错误码速查

建群失败时，系统将企业微信原始错误码自动翻译为中文说明。目前覆盖 42 种错误码，按类别分为：

| 类别 | 错误码范围 | 示例 |
|------|-----------|------|
| **通用** | -1, 0 | 系统繁忙 / 请求成功 |
| **参数校验** | 40001-41004 | `40001`: 企业微信密钥无效 / `40014`: access_token 无效 / `41001`: 凭证缺失 |
| **权限与认证** | 42001-50002 | `42001`: 凭证已过期 / `50001`: OAuth 回调域名未授权 |
| **群聊操作** | 60011-86217 | `60111`: 成员包含不存在的 UserID / `86104`: 群聊名称已被使用 / `86205`: 今日建群数达上限 |
| **限流** | 45009-45033 | `45009`: 调用频率过高 / `45016`: 今日次数达上限 |

完整翻译表见 `WeChatGroupServiceImpl.ERROR_EXPLAIN`。

---

## 部署指南

### 打包

```bash
./mvnw clean package
# 产物：target/wechat-group-0.0.1-SNAPSHOT.jar
```

### 生产环境启动

```bash
# profile 已在 application.properties 中指定为 prod
java -jar target/wechat-group-0.0.1-SNAPSHOT.jar

# 或指定外部配置文件
java -jar target/wechat-group-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

### Nginx 反向代理

项目提供 `nginx.conf` 模板，将 80 端口请求代理到 `127.0.0.1:8082`：

```bash
cp nginx.conf /etc/nginx/sites-available/wechat-group
ln -s /etc/nginx/sites-available/wechat-group /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx
```

### 环境要求

| 项目 | 要求 |
|------|------|
| JDK | 17 或 21 |
| MySQL | 8.0+（生产环境） |
| 网络 | 能访问 `qyapi.weixin.qq.com` |
| 端口 | 默认 8082（可在 `server.port` 修改） |

---

## 开发指南

### 本地运行（使用 H2 数据库，无需 MySQL）

```bash
# 1. 确保 spring.profiles.active=dev
# 2. 配置企业微信凭证（application-dev.properties）
# 3. 启动
./mvnw spring-boot:run
```

- 数据库：H2 内存模式，无需安装 MySQL
- H2 控制台：`http://localhost:8082/h2-console`（JDBC URL: `jdbc:h2:mem:wechat_group`）
- 日志级别：`DEBUG`，SQL 日志可见

### 测试

```bash
./mvnw test
```

测试覆盖范围：

| 测试类 | 用例数 | 覆盖场景 |
|--------|--------|----------|
| `CsvFileParserTest` | 5 | 正常解析、空文件、仅表头、含空行、扩展名验证 |
| `ExcelFileParserTest` | 6 | .xlsx 解析、.xls 解析、空 Sheet、仅表头、数字单元格、扩展名验证 |

---

## 安全措施

| 措施 | 实现方式 |
|------|---------|
| **BCrypt 密码加密** | `spring-security-crypto` 对管理员密码进行 BCrypt 哈希存储 |
| **Session 管理** | 登录后创建新 Session（防 Session Fixation），登出时销毁 |
| **CSRF 防护** | 登录接口需携带一次性 CSRF Token，Token 由 `/api/auth/csrf` 下发 |
| **登录限流** | 同一 IP 每分钟最多 5 次登录尝试，内存限流 + 每 5 分钟定时清理 |
| **安全响应头** | `SecurityFilter` 自动注入 `X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`X-XSS-Protection: 1; mode=block`、`Referrer-Policy: strict-origin-when-cross-origin` |
| **登录保护** | `LoginInterceptor` 拦截受保护路径，未登录重定向 `/login` |
| **认证可开关** | `app.auth.enabled=false` 时拦截器不加载，适用于无需认证的场景 |
| **凭证不入库** | `application.properties` 已加入 `.gitignore`，密钥不提交到 Git |
| **业务异常统一处理** | `GlobalExceptionHandler` 捕获 `BusinessException` 和其他未处理异常 |
| **文件日志隔离** | 审计日志写入独立文件（`logs/group_operate.log`），按天轮转保留 30 天 |
