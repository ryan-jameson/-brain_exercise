# Brain Exercise 脑部认知训练系统

> **致谢与声明 (Acknowledgment)**
> 本项目灵感及部分题库/设计模式来源于开源项目 [NeuroFlex](https://github.com/IT-NuanxinPro/NeuroFlex)，感谢原作者在脑部认知训练领域做出的优秀开源贡献！本项目基于原生 Java Socket + HTTP 协议重构了底层通信，并加入了 Python CGI 支持与 AI 报告总结功能。

这是一个基于 **Java 原生 Socket API** 开发的基础轻量 Web 服务器，完全手工实现了 HTTP/1.0 协议。前端搭配原生 HTML/JS/CSS，通过定制的 CGI 桥接技术，构建了一个小巧但完整的**脑部认知训练交互系统**。

## ✨ 核心特性

* **自主实现的轻量级 Web 容器**：无需 Tomcat / SpringBoot，通过原生 `ServerSocket` 监听，手工解析 HTTP Headers 和 Body。
* **动静分离与完整的 CGI 链路**：支持 `public/` 静态资源原生分发；访问 `/cgi-bin/` 时支持使用进程管道激活 Python / `.bat` / Java 子应用处理动态请求。
* **个性化难度**：支持简单/困难模式，根据用户的年龄或训练需求，随时调控认知负荷。
* **丰富的认知训练套件**：涵盖舒尔特方格 (Schulte Grid)、Stroop 色词、序列记忆、镜像协调等多种模式。

## 3. 部署使用说明

本项目对环境依赖门槛极低，只需配置好 `Java 11+` 和 `Python 3.x`。

### 1. 启动 Web 训练服务
Windows 系统下，可以使用 PowerShell / CMD 一键启动脚本：
```pwsh
# 自动编译所有类并拉起基于 8080 端口的内嵌 WebServer
.\scripts\start.ps1
```
启动成功后，请使用任意现代浏览器打开：`http://localhost:8080/`

*(手动启动方式)*：
```pwsh
javac -encoding UTF-8 -d out src\WebServer.java
java -cp out WebServer
```

### 2. 体验 JavaFX 桌面客户端模式
```pwsh
# 须具有网络与本地 JavaFX 环境，该脚本可自动拉取依赖并唤起客户端
.\scripts\start-client.ps1
```

## ⚙️ AI 评估配置 (Config)

如果你需要在“数据总览”页面使用 **“AI 总结分析”** 功能，你需要：
1. 进入 `cgi-bin/` 目录。
2. 将 `config.example.py` 复制/重命名为 `config.py`。
3. 填入你的大模型服务地址（如 ChatAnywhere）与 API Key！

```python
# cgi-bin/config.py
API_KEY = "sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
BASE_URL = "https://api.xxxxxxx.com/v1/chat/completions"
MODEL_NAME = "gpt-4o-mini"
PROXY_URL = "http://127.0.0.1:7897" # 如果你在国内需要走代理，写代理地址；否则留空
```

> ⚠️ 注意：本地 `config.py` 与 `.env` 文件均已被纳入 `.gitignore`，不会被提交至云端，请放心配置属于你的 API 密钥。

## 开源协议与开源主页

本项目已经在 GitHub 开源：[ryan-jameson/-brain_exercise](https://github.com/ryan-jameson/-brain_exercise)

> *再次鸣谢 [NeuroFlex](https://github.com/IT-NuanxinPro/NeuroFlex) 为本项目的场景分类与交互体验提供的思路基础。*
