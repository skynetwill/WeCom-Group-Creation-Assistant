# 部署指南

本文介绍将企业微信批量建群工具部署到服务器上的三种方式，根据你的使用场景选择合适的即可。

---

## 快速对照

| 方式 | 适合场景 | 需要公网IP | 需要域名 | 难度 |
|------|----------|-----------|---------|------|
| Ngrok 隧道 | 临时演示、测试 | 否 | 否 | 低 |
| Nginx 反向代理 | 长期生产运行 | 是 | 建议有 | 中 |
| 直接运行 | 仅自己使用 | 否 | 否 | 低 |

---

## 方式一：Ngrok 内网穿透（推荐新手）

适合没有公网 IP 的服务器，几分钟就能让外网用户访问。

### 前提

- 服务器上已安装 Java 17+
- 注册 ngrok 账号，下载 ngrok 客户端

### 步骤

**1. 上传并启动应用**

```bash
# 上传 JAR 到服务器，然后启动
java -jar wechat-group-0.0.1-SNAPSHOT.jar
```

看到 `Started WechatGroupApplication` 和 `Token 更新成功` 表示启动完成。

**2. 启动 ngrok 隧道**

另开一个终端窗口：

```bash
ngrok http 8082
```

执行后会显示类似以下内容：

```
Forwarding  https://abc123.ngrok-free.app -> http://localhost:8082
```

**3. 分发地址**

把 `https://abc123.ngrok-free.app` 发给用户即可。

### 注意事项

- 免费版 ngrok 每次重启地址会变，适合临时使用
- 付费版支持固定域名
- ngrok 服务商在国内访问可能较慢，可换用国内的内网穿透工具（如 frp、natapp 等）

### 架构示意

```
外网用户 ──→ ngrok 服务器 ──→ 你的服务器:8082
                  ↑
           https://xxx.ngrok-free.app
```

---

## 方式二：Nginx 反向代理（推荐生产环境）

适合有公网 IP（或云服务器）的正式部署，性能最好。

### 前提

- 服务器已安装 Nginx
- 服务器有公网 IP，防火墙已开放 80 / 443 端口
- （可选）已备案域名

### 步骤

**1. 上传并启动应用**

```bash
java -jar wechat-group-0.0.1-SNAPSHOT.jar
```

**2. 配置 Nginx**

将项目中的 `nginx.conf` 复制到 Nginx 配置目录：

```bash
# Linux
cp nginx.conf /etc/nginx/conf.d/wechat-group.conf

# 如果使用 HTTPS，修改 nginx.conf 中的 listen 为 443，
# 并添加 SSL 证书配置（certbot 可免费申请 Let's Encrypt 证书）
```

**3. 测试并重载 Nginx**

```bash
nginx -t          # 测试配置
nginx -s reload   # 重载生效
```

**4. 访问**

浏览器打开 `http://你的服务器IP` 或 `https://你的域名`。

### 使用 HTTPS（推荐）

```bash
# 用 certbot 免费申请 SSL 证书
certbot --nginx -d your-domain.com
```

### 架构示意

```
外网用户 ──→ Nginx (80/443) ──→ Spring Boot (127.0.0.1:8082)
    ↑                              ↑
  只暴露 Nginx                  只监听本地，安全
```

### 为什么前面加一层 Nginx？

- **安全**：后端只监听 `127.0.0.1`，外部无法直接访问
- **HTTPS**：Nginx 统一处理 SSL，后端不需要管证书
- **性能**：Nginx 可以缓存静态文件、做负载均衡
- **日志**：Nginx 记录访问日志，配合后端的操作日志形成完整链路

---

## 方式三：直接运行（仅自己使用）

如果你只是在本地用，或者只在服务器上自己访问：

```bash
java -jar wechat-group-0.0.1-SNAPSHOT.jar
```

浏览器打开 `http://localhost:8082` 即可。

如果需要让局域网内其他电脑访问，确保防火墙放行 8082 端口，用 `http://你的内网IP:8082` 访问。

---

## 补充说明

### 文件上传大小

`application.properties` 中已设置 10MB 限制。如果使用 Nginx，`nginx.conf` 也配置了相同的 `client_max_body_size 10m`，两端保持一致即可。

### 开机自启（Linux）

创建 systemd 服务文件 `/etc/systemd/system/wechat-group.service`：

```ini
[Unit]
Description=企业微信批量建群工具
After=network.target

[Service]
Type=simple
User=your-user
WorkingDirectory=/opt/wechat-group
ExecStart=/usr/bin/java -jar /opt/wechat-group/wechat-group-0.0.1-SNAPSHOT.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

然后：

```bash
systemctl daemon-reload
systemctl enable wechat-group
systemctl start wechat-group
```

### 查看日志

```bash
# 应用日志（控制台输出）
journalctl -u wechat-group -f

# 操作审计日志
tail -f logs/group_operate.log
```
