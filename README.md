# 企业微信批量建群工具

> Spring Boot 3.2.5 + Java 17 | 支持 CSV / Excel 上传，一键批量创建企业微信群聊

---

## 目录

- [新手入门（5 分钟上手）](#新手入门5-分钟上手)
- [项目架构](#项目架构)
- [技术栈与依赖](#技术栈与依赖)
- [代码详解](#代码详解)
  - [应用入口：WechatGroupApplication](#1-应用入口wechatgroupapplication)
  - [配置层：WeChatProperties](#2-配置层wechatproperties)
  - [Token 管理：WeChatTokenManager](#3-token-管理wechattokenmanager)
  - [文件解析策略层](#4-文件解析策略层)
  - [核心服务：WeChatGroupService](#5-核心服务wechatgroupservice)
  - [控制器：WeChatGroupController](#6-控制器wechatgroupcontroller)
  - [前端：index.html](#7-前端indexhtml)
- [API 文档](#api-文档)
- [错误码速查表](#错误码速查表)
- [日志系统](#日志系统)
- [测试](#测试)
- [部署与运维](#部署与运维)
- [常见问题 FAQ](#常见问题-faq)

---

## 新手入门（5 分钟上手）

### 这东西是干什么的？

假设你在企业微信里要创建一个群，需要手动拉人——一个人一个人地点，很慢。这个工具让你**准备好一份 Excel 或 CSV 名单，在网页上点一下按钮，群就建好了**。

### 怎么用？

**第一步：启动程序**

```bash
# 进入项目目录
cd wechat-group

# 启动（Windows PowerShell 或 CMD 都可以）
mvnw.cmd spring-boot:run
```

看到 `Started WechatGroupApplication` 和 `Token 更新成功` 就说明启动好了。

**第二步：打开网页**

浏览器访问：**http://localhost:8082**

你会看到三个标签页：

| 标签 | 做什么 |
|------|--------|
| **建群操作** | 填群名、群主ID、上传名单文件，点按钮建群 |
| **操作指南** | 使用说明 + 常见问题，可下载 Word 文档 |
| **模板下载** | 下载标准的 CSV 或 Excel 模板，照着填就行 |

**第三步：准备名单文件**

名单文件格式很简单——第一行写 `userid`（表头），后面每行写一个成员的企业微信 userid：

```
userid
chenyun
zhangsan
lisi
```

> **群主也要写进名单里！** 如果不写也没关系，程序会自动把群主补进去。

**第四步：创建群聊**

1. 输入群聊名称（比如"项目讨论组"）
2. 输入群主的企业微信 userid
3. 上传准备好的名单文件
4. 点击"一键创建群聊"

页面下方会显示结果——绿色是成功，红色是失败（会告诉你具体原因）。

### 怎么关掉？

在终端按 `Ctrl + C` 即可停止。

---

## 项目架构

### 分层架构图

```
┌─────────────────────────────────────────────┐
│                浏览器 (Browser)               │
│          index.html (三 Tab 页面)              │
└──────────────────┬──────────────────────────┘
                   │ HTTP POST /api/group/create
                   │ (multipart/form-data)
                   ▼
┌─────────────────────────────────────────────┐
│            Controller 层                      │
│     WeChatGroupController                    │
│     - 接收文件上传请求                          │
│     - IP 提取 + 操作审计日志                    │
│     - 模板下载 / 操作指南下载                    │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────┐
│             Service 层                        │
│     WeChatGroupService                       │
│     - 按文件扩展名选择解析器（策略模式）            │
│     - 自动补入群主 userid                       │
│     - 组装 JSON 请求体                        │
│     - 错误码 → 中文翻译                        │
└──────┬──────────────────────┬────────────────┘
       │                      │
       ▼                      ▼
┌──────────────┐    ┌─────────────────────────┐
│  Parser 策略层 │    │     Task 层              │
│              │    │  WeChatTokenManager      │
│ CsvFileParser│    │  - 启动时获取 Token        │
│ ExcelFileParser   │  - 每小时自动刷新          │
│ (.xlsx/.xls) │    │  - 线程安全（volatile）     │
└──────┬───────┘    └──────────┬──────────────┘
       │                       │
       │  提取 userid 列表       │  提供 access_token
       │                       │
       ▼                       ▼
┌─────────────────────────────────────────────┐
│           企业微信 Open API                    │
│   qyapi.weixin.qq.com                       │
│   - GET  /cgi-bin/gettoken                   │
│   - POST /cgi-bin/appchat/create             │
└─────────────────────────────────────────────┘
```

### 核心设计模式：策略模式（Strategy Pattern）

```
FileParserStrategy (接口)
    ├── parse(MultipartFile) → List<String>
    └── supportedExtension() → String
          │
    ┌─────┴─────┐
    │           │
CsvFileParser  ExcelFileParser
@Component     @Component
("csvParser")  ("excelParser")
    │               │
    │  .csv 文件     │  .xlsx / .xls 文件
    │  OpenCSV      │  Apache POI
    ▼               ▼
  List<String>  userid 列表
```

**好处**：以后要支持新的文件格式（比如 .txt、.json），只需要新增一个 `@Component` 类实现 `FileParserStrategy` 接口，不用改动任何现有代码——这就是"开闭原则"（对扩展开放，对修改关闭）。

Spring 会自动把所有 `FileParserStrategy` 的实例收集到一个 `Map<String, FileParserStrategy>` 中，Service 根据文件扩展名从中选取对应的解析器。

### 数据流

```
用户上传文件
  → Controller 接收 MultipartFile
    → Service.createGroupFromFile()
      → 取文件扩展名 (.csv / .xlsx / .xls)
      → 从 parserMap 中选择对应解析器
      → parser.parse(file) 提取 List<String> userList
      → 检查群主是否在名单中（不在则补入）
      → createGroupFromUserList(userList, name, owner)
        → TokenManager.getAccessToken()
        → POST qyapi.weixin.qq.com/cgi-bin/appchat/create
        → 解析 API 响应
        → 按错误码追加中文说明
      → 返回 CreateGroupResult(message, userList)
    → Controller 将 message 返回前端，userList 写入审计日志
```

---

## 技术栈与依赖

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 开发语言 |
| Spring Boot | 3.2.5 | 应用框架 |
| Spring MVC | 内嵌 | REST 接口 + 文件上传 |
| OpenCSV | 5.7.1 | CSV 文件解析 |
| Apache POI (poi-ooxml) | 5.2.5 | Excel 解析 (.xlsx/.xls) + Word 生成 (XWPF) |
| SLF4J + Logback | 内嵌 | 日志框架 |
| JUnit 5 | 内嵌 | 单元测试 |
| Maven Wrapper | - | 无需安装 Maven，`./mvnw` 即可构建 |

**为什么选 Apache POI 而不是 EasyExcel？**
- POI 同时支持 `.xlsx` 和 `.xls` 两种格式
- 自动包含 XWPF 模块，可以生成 Word 操作指南文档
- 行业标准库，社区成熟，文档丰富

---

## 代码详解

### 项目结构

```
src/main/java/com/school/wechatgroup/
├── WechatGroupApplication.java      ← 应用入口，启动类
├── config/
│   └── WeChatProperties.java        ← 配置绑定（corpid / corpsecret）
├── controller/
│   └── WeChatGroupController.java   ← REST 控制器 + 模板/指南下载
├── service/
│   ├── CreateGroupResult.java       ← 建群结果 DTO
│   ├── WeChatGroupService.java      ← 核心业务逻辑
│   └── parser/
│       ├── FileParserStrategy.java  ← 解析策略接口
│       ├── CsvFileParser.java       ← CSV 解析实现
│       └── ExcelFileParser.java     ← Excel 解析实现 (.xlsx + .xls)
└── task/
    └── WeChatTokenManager.java      ← Token 获取与定时刷新

src/main/resources/
├── application.properties           ← 配置（端口、凭证、上传限制）
├── logback-spring.xml               ← 日志配置
└── static/
    └── index.html                   ← 前端页面（三 Tab）

src/test/java/com/school/wechatgroup/service/parser/
├── CsvFileParserTest.java           ← CSV 解析器单元测试
└── ExcelFileParserTest.java         ← Excel 解析器单元测试
```

---

### 1. 应用入口：WechatGroupApplication

```java
@SpringBootApplication
@EnableScheduling                        // ← 启用定时任务（Token 每小时刷新）
@EnableConfigurationProperties(WeChatProperties.class)  // ← 启用配置属性绑定
public class WechatGroupApplication {

    @Bean
    public RestTemplate restTemplate() {  // ← 注册全局 HTTP 客户端
        return new RestTemplate();
    }

    public static void main(String[] args) {
        SpringApplication.run(WechatGroupApplication.class, args);
    }
}
```

**关键注解说明：**

| 注解 | 作用 |
|------|------|
| `@SpringBootApplication` | 组合注解 = `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan` |
| `@EnableScheduling` | 开启 Spring 定时任务支持，TokenManager 的 `@Scheduled` 需要这个 |
| `@EnableConfigurationProperties` | 将 `WeChatProperties` 注册为 Spring Bean |
| `@Bean` | `RestTemplate` 全局单例，所有需要调 HTTP 接口的地方都注入这一个实例 |

**为什么用 RestTemplate 而不是 WebClient？**
- 项目是同步阻塞模型，不需要异步
- RestTemplate 配置简单，与 Spring Boot 3.x 兼容
- 企业微信 API 调用量不大，不需要连接池优化

---

### 2. 配置层：WeChatProperties

```java
@ConfigurationProperties(prefix = "wechat.work")
public class WeChatProperties {
    private String corpid;      // 企业 ID
    private String corpsecret;  // 应用密钥
    // getter / setter ...
}
```

**工作原理：**

Spring Boot 启动时，自动将 `application.properties` 中的 `wechat.work.corpid` 和 `wechat.work.corpsecret` 读取到 `WeChatProperties` 对象的同名字段中。

**配置对应关系：**
```
application.properties              WeChatProperties
─────────────────────────           ────────────────
wechat.work.corpid=xxx      →       corpid = "xxx"
wechat.work.corpsecret=xxx  →       corpsecret = "xxx"
```

**为什么不用 `@Value`？**
- `@ConfigurationProperties` 可以一次性绑定多个属性，代码更整洁
- 支持类型安全校验
- IDE 有自动补全提示（配合 `spring-boot-configuration-processor`）

---

### 3. Token 管理：WeChatTokenManager

```java
@Component
public class WeChatTokenManager {
    private volatile String accessToken;  // ← volatile 保证多线程可见性

    @PostConstruct                        // ← 启动时立即执行
    public void init() {
        refreshAccessToken();
    }

    @Scheduled(fixedRate = 3600000)       // ← 每小时执行一次
    public void refreshAccessToken() {
        // GET https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=&corpsecret=
        // 解析响应 → 提取 access_token → 存入 accessToken 字段
    }
}
```

**设计要点：**

| 特性 | 说明 |
|------|------|
| `volatile` | 保证多线程对 `accessToken` 的读写可见性。Token 每 1 小时被定时任务线程写入一次，被 Service 工作线程读取多次 |
| `@PostConstruct` | 在 Bean 初始化后立即调用，确保应用启动时 Token 就已就绪 |
| `@Scheduled(fixedRate = 3600000)` | 固定频率执行，不受上次任务耗时影响。企业微信 Token 有效期 2 小时，1 小时刷新确保不会过期 |
| 构造器注入 | Spring 推荐的依赖注入方式，依赖清晰、不可变、便于测试 |

**Token 生命周期：**
```
应用启动 → init() 获取 Token → 存入 volatile accessToken
                                        ↓
                          每 1 小时自动刷新 ← @Scheduled
                                        ↓
                          Service 随时调用 getAccessToken()
```

---

### 4. 文件解析策略层

#### 4.1 接口定义：FileParserStrategy

```java
public interface FileParserStrategy {
    List<String> parse(MultipartFile file);   // 核心方法：文件 → userid 列表
    String supportedExtension();              // 元数据：支持什么格式
}
```

#### 4.2 CSV 解析器：CsvFileParser

```java
@Component("csvParser")  // ← Spring Bean 名称 = "csvParser"
public class CsvFileParser implements FileParserStrategy {

    public List<String> parse(MultipartFile file) {
        // 1. 用 UTF-8 编码读取（解决中文文件名乱码）
        // 2. OpenCSV 逐行读取
        // 3. 取第一列数据
        // 4. 跳过表头行（值为 "userid" 的行）
        // 5. 跳过空行
        return userList;
    }
}
```

**关键设计细节：**

- **UTF-8 编码**：`new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)`，解决 Windows 上 Excel 导出的 CSV 文件编码问题
- **跳过表头**：`values[0].equalsIgnoreCase("userid")` 不区分大小写
- **异常转义**：解析异常包装为 `RuntimeException` 抛出，由 Service 层统一处理并返回友好提示

#### 4.3 Excel 解析器：ExcelFileParser

```java
@Component("excelParser")
public class ExcelFileParser implements FileParserStrategy {

    public List<String> parse(MultipartFile file) {
        // 1. 根据扩展名选择 Workbook 实现
        //    .xlsx → XSSFWorkbook (新版 Excel)
        //    .xls  → HSSFWorkbook  (旧版 Excel)
        // 2. 读取第一个 Sheet
        // 3. 逐行读取第一列
        // 4. 跳过表头、空行、空单元格
        // 5. 处理各种单元格类型（文本、数字、布尔、公式）
        return userList;
    }

    private String getCellValueAsString(Cell cell) {
        switch (cell.getCellType()) {
            case STRING:   return cell.getStringCellValue();
            case NUMERIC:  return 格式化数字（整数无小数点）;
            case BOOLEAN:  return String.valueOf(...);
            case FORMULA:  return 取公式计算结果;
            default:       return "";
        }
    }
}
```

**支持的单元格类型：**

| 类型 | 示例 | 处理方式 |
|------|------|----------|
| 文本 | `chenyun` | 直接读取 |
| 数字 | `1001` | 转为整数字符串（避免 `1001.0`） |
| 布尔 | `TRUE` | 转为 `"true"` |
| 公式 | `=A1` | 取计算结果 |

**为什么用 `getSheetAt(0)` 只读第一个 Sheet？**
- 简化用户操作：只需关注第一个 Sheet
- 企业微信建群只需要一列 userid，多 Sheet 没有意义

---

### 5. 核心服务：WeChatGroupService

这是整个项目的**大脑**，负责调度所有组件协同工作。

```java
@Service
public class WeChatGroupService {

    // Spring 自动注入所有 FileParserStrategy 实例
    private final Map<String, FileParserStrategy> parserMap;

    public CreateGroupResult createGroupFromFile(MultipartFile file, ...) {
        // 步骤 1: 提取文件扩展名 (.csv / .xlsx / .xls)
        // 步骤 2: 从 parserMap 选择对应解析器
        //         .csv  → parserMap.get("csvParser")
        //         .xlsx → parserMap.get("excelParser")
        //         .xls  → parserMap.get("excelParser")
        // 步骤 3: 解析文件 → List<String> userList
        // 步骤 4: 自动补入群主（鲁棒性设计）
        // 步骤 5: 调用企业微信 API
    }
}
```

**解析器选择逻辑：**

```java
// .xlsx 和 .xls 都映射到 excelParser（一个解析器处理两种 Excel 格式）
if ("xls".equals(extension) || "xlsx".equals(extension)) {
    parser = parserMap.get("excelParser");
} else {
    parser = parserMap.get(extension + "Parser");  // "csv" → "csvParser"
}
```

**鲁棒性设计——自动补入群主：**

```java
if (!userList.contains(ownerId)) {
    userList.add(0, ownerId);  // 插到列表第一位
}
```

企业微信要求群聊至少 2 人且群主必须在成员列表中。这个兜底逻辑确保即使用户忘记了，群主也会自动被加入。

**企业微信 API 调用：**

```java
// 请求体
{
    "name": "项目讨论组",     // ← groupName
    "owner": "chenyun",      // ← ownerId
    "userlist": ["chenyun", "zhangsan", "lisi"]
}

// 调用
POST https://qyapi.weixin.qq.com/cgi-bin/appchat/create?access_token={token}

// 成功响应
{ "errcode": 0, "errmsg": "ok", "chatid": "wrFKkVYAAA..." }

// 失败响应
{ "errcode": 60111, "errmsg": "userid not found" }
```

**错误码翻译机制：**

```java
private static final Map<Integer, String> ERROR_EXPLAIN = new LinkedHashMap<>();
static {
    ERROR_EXPLAIN.put(60111, "名单中有无效的 userid——...");
    ERROR_EXPLAIN.put(86004, "群主 userid 不存在——...");
    // ... 共 16 个常见错误码
}

// 使用：
String explain = ERROR_EXPLAIN.getOrDefault(errcode, "");
```

返回给前端的内容同时包含**企微原始错误**（给技术人员排查）和**中文说明**（给普通用户理解）。

**三层日志体系：**

| 日志类型 | 级别 | 内容 |
|----------|------|------|
| 请求追踪 | INFO | `调用企微建群API \| 群名=xx \| 群主=xx \| 成员数=3 \| 成员=a,b,c` |
| 成功记录 | INFO | `建群成功 \| chatId=xxx \| 成员=...` |
| 失败记录 | WARN | `建群失败 \| errcode=60111 \| errmsg=... \| 成员=...` |
| 异常记录 | ERROR | `建群API调用异常 \| ...` + 完整堆栈 |

---

### 6. 控制器：WeChatGroupController

#### POST /api/group/create — 建群（核心接口）

```java
@PostMapping("/api/group/create")
public String createGroup(
    @RequestParam("groupName") String groupName,   // 群聊名称
    @RequestParam("ownerId") String ownerId,        // 群主 userid
    @RequestParam("file") MultipartFile file,       // 名单文件
    @RequestParam(value = "userId", required = false, defaultValue = "本地用户") String userId,  // 操作人
    HttpServletRequest request                      // 用于提取 IP
)
```

**处理流程：**

```
1. 记录请求日志（群名、群主、文件名）
2. 校验文件非空
3. 调用 Service.createGroupFromFile()
4. 从结果中提取成员数（正则：共导入\s*(\d+)\s*人）
5. 拼接操作审计日志
6. 返回结果文本给前端
```

**操作审计日志格式：**

```
操作人：test_user | IP：192.168.1.100 | 群名：项目组 | 群主：chenyun | 成员数：3 | 成员：chenyun,zhangsan,lisi | 文件：名单.xlsx | 结果：建群成功！
```

每个字段的含义：
- **操作人**：从 URL 参数 `userId` 获取，企业微信会自动拼接
- **IP**：优先取 `X-Forwarded-For`（支持 nginx/CDN 代理）
- **成员**：完整的 userid 列表（逗号分隔），用于事后追溯

#### 其他接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/template/csv` | GET | 下载 CSV 模板文件 |
| `/api/template/excel` | GET | 动态生成并下载 Excel 模板 |
| `/api/guide` | GET | 动态生成并下载 Word 操作指南 |

模板和指南都是**运行时动态生成**的，不依赖静态二进制文件，方便随时修改内容。

---

### 7. 前端：index.html

纯静态 HTML + 内联 CSS/JS，无需任何前端框架。

**设计特点：**

| 特点 | 实现方式 |
|------|----------|
| 三 Tab 切换 | 纯 CSS class 控制显隐 |
| 文件上传 | `fetch` + `FormData`，无需刷新页面 |
| 加载状态 | 蓝色背景 + CSS 旋转动画 |
| 结果反馈 | 绿色 = 成功，红色 = 失败，分行显示错误说明 |
| 安全 | `escapeHtml()` 防 XSS 注入 |
| 兼容性 | 使用 `var` + `function` 而非箭头函数/`const`，兼容老浏览器 |
| `userId` 获取 | 从 URL 参数自动读取，企业微信工作台打开时会自动拼接 |

**交互流程：**

```
用户点击"一键创建群聊"
  → 按钮变灰 + 旋转动画
  → 结果区域显示蓝色加载态（群名、群主、文件名预填）
  → fetch POST /api/group/create
  → 收到响应
  → 成功：绿色区域 + 群聊ID + 成员数
  → 失败：红色区域 + 错误码 + 中文说明
  → 按钮恢复
```

---

## API 文档

### POST /api/group/create

创建企业微信群聊。

**请求：**
```
Content-Type: multipart/form-data

groupName   (必填)  群聊名称
ownerId     (必填)  群主的企业微信 userid
file        (必填)  成员名单文件 (.csv / .xlsx / .xls)
userId      (可选)  操作人标识，默认"本地用户"
```

**响应（成功）：**
```
建群成功！群聊ID: wrFKkVYAAA...，共导入 3 人。
```

**响应（失败）：**
```
建群失败: [60111] invalid string value `xxx`. userid not found
说明: 名单中有无效的 userid——英文错误信息里用反引号标出了具体是哪个账号不对，请修正后重试
```

### GET /api/template/csv

下载 CSV 格式的成员导入模板。

### GET /api/template/excel

下载 Excel (.xlsx) 格式的成员导入模板。

### GET /api/guide

下载 Word (.docx) 格式的完整操作指南文档。

---

## 错误码速查表

当建群失败时，页面会显示企微原始错误码 + 下面的中文说明：

| 错误码 | 含义 | 解决方法 |
|--------|------|----------|
| `60111` | 名单中有无效的 userid | 错误信息里用反引号标出了具体是哪个账号不对 |
| `86004` | 群主 userid 不存在 | 确认群主的企业微信账号是否正确 |
| `86003` | 群聊名称不合法 | 群名不能为空，不能有特殊字符 |
| `86006` | 群成员数量不对 | 至少需要 2 人（含群主） |
| `86007` | 成员列表中有不存在的 userid | 检查名单中的账号是否都是有效账号 |
| `86201` | 群主不在成员列表中 | 程序已自动处理，正常情况下不会再出现 |
| `86202` | 成员列表中有重复的 userid | 去掉重复的账号 |
| `86207` | 群主不在成员列表中 | 同上，已自动处理 |
| `40001` | 企业微信凭证无效 | corpid 或 corpsecret 配置错误 |
| `40014` | access_token 无效 | 稍后重试，Token 会自动刷新 |
| `42001` | access_token 已过期 | Token 会自动刷新，稍后重试 |
| `60011` | 应用没有建群权限 | 在企业微信后台配置建群权限 |
| `301002` | 今日建群数达上限 | 每天最多 100 个群，请明天再试 |
| `301003` | 群成员人数超限 | 企业微信群上限 2000 人 |

---

## 日志系统

### 两份日志

| 日志 | 路径 | 内容 | 面向 |
|------|------|------|------|
| 控制台日志 | stdout | 请求追踪、API 调用详情、异常堆栈 | 开发者 |
| 操作审计日志 | `logs/group_operate.log` | 每次建群操作的完整记录 | 管理员 |

### 操作审计日志格式

```
2026-05-20 10:30:00 | 操作人：test_user | IP：192.168.1.100 | 群名：项目组 | 群主：chenyun | 成员数：3 | 成员：chenyun,zhangsan,lisi | 文件：名单.xlsx | 结果：建群成功！群聊ID: wrFKkVYAAA...
```

### Logback 配置要点

- **编码**：统一 UTF-8
- **滚动策略**：按天切割，保留 30 天
- **`GROUP_OPERATE_LOG`**：独立 Logger，只写文件不输出到控制台（`additivity="false"`）

---

## 测试

### 运行测试

```bash
./mvnw test
```

### 测试覆盖

| 测试类 | 用例数 | 覆盖场景 |
|--------|--------|----------|
| `CsvFileParserTest` | 5 | 正常 CSV、空文件、仅表头、带空行、扩展名验证 |
| `ExcelFileParserTest` | 6 | .xlsx 正常文件、.xls 正常文件、空 Sheet、仅表头、数字单元格、扩展名验证 |

### 测试技术

- **MockMultipartFile**：Spring 提供的测试工具，模拟文件上传
- **JUnit 5**：`@Test`、`assertEquals`、`assertTrue`
- **POI 内存 Workbook**：用 `XSSFWorkbook` / `HSSFWorkbook` 在内存中生成测试用的 Excel 文件

---

## 部署与运维

### 环境要求

| 项目 | 要求 |
|------|------|
| JDK | 17 或 21 |
| Maven | 无需安装（使用 Maven Wrapper `./mvnw`） |
| 网络 | 能访问 `qyapi.weixin.qq.com` |

### 配置文件

`src/main/resources/application.properties`：

```properties
server.port=8082                              # 服务端口
wechat.work.corpid=wwce6b569b8529bd53          # 企业 ID
wechat.work.corpsecret=XOYMZI7T3VHq5...        # 应用密钥
spring.servlet.multipart.max-file-size=10MB    # 上传文件大小限制
```

### 构建产物

```bash
# 打包为 JAR
./mvnw clean package

# 产物位置
target/wechat-group-0.0.1-SNAPSHOT.jar

# 运行 JAR
java -jar target/wechat-group-0.0.1-SNAPSHOT.jar
```

### 日常运维

```bash
# 启动
./mvnw spring-boot:run

# 停止
Ctrl + C

# 查看操作日志
tail -f logs/group_operate.log
```

### 注意事项

1. **Token 刷新依赖 `@EnableScheduling`**：如果启动类删了 `@EnableScheduling`，Token 只在启动时获取一次，1 小时后就会过期，建群会失败
2. **端口占用**：如果 8082 端口被占用，修改 `server.port` 配置或杀掉占用进程
3. **凭证安全**：`application.properties` 中的 `corpid` 和 `corpsecret` 不要提交到公共 Git 仓库

---

## 常见问题 FAQ

### Q: 启动时报 `Token 更新成功` 后又报错，为什么？

A: 第一次 `Token 更新成功` 是在 Bean 初始化阶段（`@PostConstruct`），第二次是应用完全启动后的定时刷新。如果第二次失败，说明网络波动，定时任务会在下一轮自动重试。

### Q: 上传 Excel 文件后提示"不支持的文件格式"？

A: 检查文件扩展名是否正确。支持的文件类型：`.csv`、`.xlsx`、`.xls`。如果是 WPS 保存的文件，确认扩展名是以上三种之一。

### Q: 建群成功但返回的"成员数"不对？

A: 不会。`getUserCount()` 方法用正则 `共导入\s*(\d+)\s*人` 精确匹配，只提取"共导入 X 人"中的数字。

### Q: 可以同时创建多个群吗？

A: 当前版本每次请求创建一个群。如果需要批量创建多个群，可以多次提交（每次用不同的群名和名单文件）。企业微信限制每个应用每天最多创建 100 个群。

### Q: 群聊建好后可以在企业微信里看到吗？

A: 是的。建群成功后，在企业微信客户端里就能看到这个群聊。群聊 ID 格式为 `wrFKkVYAAA...`。

### Q: 群主可以不在名单里吗？

A: 可以。程序会自动把群主补入成员列表。但群主的 userid 必须是有效的企业微信账号。
