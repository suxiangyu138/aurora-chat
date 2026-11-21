<div align="center">

# Aurora Chat

**一套模板,对话所有大模型**

极光般流畅的通用 AI 聊天客户端 · 纯本地运行 · 密钥不出设备

[![Platform](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)](#兼容性)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Version](https://img.shields.io/badge/release-v2.0.2-6366F1)](https://gitee.com/suxiangyu138/aurora-chat/releases)
[![Backend](https://img.shields.io/badge/服务端-无-0D9488)](#隐私与安全)

[下载安装](#下载安装) · [使用教程](#使用教程) · [从源码构建](#从源码构建) · [隐私与安全](#隐私与安全) · [架构](#架构)

</div>

---

## 简介

Aurora Chat 是一个把选择权交还给你的聊天客户端:接口地址、API Key、模型名、采样参数都由你决定,不绑定任何一家厂商。只要提供 OpenAI 兼容的 `/chat/completions` 接口,官方 API、聚合网关、自建服务都能接。

它没有后端。请求从手机直连模型厂商,密钥用 Android Keystore 硬件密钥加密后落盘,对话只存在本机 Room 数据库。**没有中间服务器,也就没有中间人。**

v2.0.0 起,它不再只是"能聊天的小工具":模型配置、联网搜索、深度思考、文档与多图、余额与费用、匿名会话、个性化画像、新手教程都已具备,可以当作日常主力客户端使用。

## 功能特性

### 模型接入

| 能力 | 说明 |
|---|---|
| 厂商模板 | 内置 DeepSeek、通义千问、智谱 GLM、Kimi、MiniMax、小米 MiMo 六个官方模板,模型 ID 于 2026-11-21 逐个实测;中转站统一走「自定义」,地址与 Key 由用户填写 |
| 中转站 / 自建 | 「自定义兼容接口」模板,填 Base URL 与 Key 后可直接拉取该站全部模型,自动探测 `/models` 与 `/v1/models` |
| 拉取模型 | 一键从接口读取该 Key 可用的模型,展示上下文窗口与能力;失败自动回退手动填写 |
| 连接自检 | 先查模型列表,再按需发最小请求;区分鉴权失败、地址错误、模型不存在、限流、超时、服务端错误 |
| 多套配置 | 多组地址与密钥并存,聊天页胶囊一键切换,配置名称可自定义 |
| 高级参数 | 温度、top_p、输出上限、超时、请求体模板默认收起,需要时再展开 |
| 账户余额 | DeepSeek、Kimi 支持用 API Key 查余额,胶囊实时显示;其余厂商没有公开的 Key 级余额接口,不显示入口 |

### 对话体验

- **流式打字机**:SSE 逐字输出 + 呼吸灯与光标,自动兼容非流式接口
- **完整 Markdown**:标题/列表/表格/引用/图片/链接,代码块语法高亮并支持复制
- **公式与图表**:KaTeX 渲染数学公式,mhchem 支持 `\ce{}` 化学式,希腊字母与物理公式直接可用;Mermaid 代码块渲染成图,点击可全屏缩放
- **表格不撑破**:宽表自动横向滚动,并可一键「表格全屏」
- **图表以位图呈现**:Mermaid 渲染后栅格化为 PNG 显示,尺寸稳定不依赖 WebView 的 SVG 布局;点击可全屏查看并双指缩放
- **代码全屏**:字号紧凑,全屏后可左右滑动、长按选中复制,亮色主题
- **深度思考**:按厂商下发思考参数,默认关闭,回答中的思考过程可展开
- **联网搜索**:使用厂商服务端原生工具(如 DeepSeek `deepseek_web_search` / `deepseek_read_url`),回答带引用;不支持的厂商置灰,不偷偷借用别家 Key
- **识图**:多图输入(最多 30 张),自动压缩;点开图片可全屏双指缩放
- **文档**:PDF、DOCX、TXT、Markdown、CSV、JSON 与代码文本,本地解析(最多 30 个、单篇 6 万字),随消息进入上下文
- **消息操作**:用户消息可编辑重发、删除;AI 回答底部提供复制全文、复制 Markdown、重新生成
- **历史分页**:长会话按页加载,「加载更早的消息」按需回溯

### 用量与上下文

- 每轮回答标注:输入 token、输出 token、缓存命中率、本轮费用
- 聊天字号可在 12–24px 之间调节,设置页实时预览
- 顶栏统计:本会话累计 token、累计花费、当前上下文占用(已用 / 模型上限)
- 费用按公开人民币价目表估算,DeepSeek 支持峰谷价;没有人民币价格的模型只显示 token 与「费用未收录」
- 上下文默认不限轮数,直到模型窗口上限;达到上限后再考虑压缩(排期中)

### 个性化与会话

- **个人画像**:设置页填写背景与输出偏好,开关控制是否注入每轮请求(最多 4000 字),关闭则完全不带
- **匿名会话**:首页顶栏进入,能力与普通会话一致,但不进列表、退出即删、进程被杀后下次启动兜底清理
- **会话管理**:重命名、删除、按时间留存;导出 / 导入 JSON 备份
- **外观**:深色模式三态、7 种主题色,新图标含 Android 13 单色主题图标层

### iQOO / OriginOS 深度适配

- 流式期间启动 `dataSync` 前台服务,长回答不被系统回收
- 断线指数退避自动重连;读超时下限 300s
- 中断有明确提示并保留已生成内容;失败气泡可重试

## 下载安装

前往 [Releases](https://github.com/suxiangyu138/aurora-chat/releases) 下载 `AuroraChat-v2.0.2.apk`,手机安装(需允许安装未知来源应用)。Gitee 用户可从 [Gitee 发行版](https://gitee.com/suxiangyu138/aurora-chat/releases) 获取同一安装包。

> v2.0.2 的 release 包约 12MB;debug 包包含调试信息与未裁剪代码,体积约 110MB,仅用于开发。

## 使用教程

应用内已内置完整教程:首页顶栏书本图标,或关于页作者信息下方的「使用教程」。内容覆盖申请 API Key、配置测试、对话页每个按钮、文档与图片、个性化、匿名会话、隐私边界与常见报错。

### 三步接入

1. **设置页** → 选择厂商模板 → 粘贴 API Key → 「从接口拉取模型」选一个 → 「测试连接」→ 保存
2. **首页 `+`** 新建会话(或点带斜线眼睛图标开匿名会话)
3. 开始对话;需要时切换深度思考、联网搜索、附加文档或图片

> DeepSeek、Kimi 支持在输入框上方直接查看账户余额;其余厂商的余额在云平台控制台,API Key 查不到。

## 隐私与安全

| 项 | 做法 |
|---|---|
| 服务端 | 没有。请求由设备直连你配置的模型厂商 |
| API Key | Android Keystore 硬件密钥 + AES-256-GCM 加密存储,不落明文、不上传 |
| 对话记录 | 仅存本机 Room 数据库(v8),卸载即清除 |
| 匿名会话 | 不进入列表,退出页面立刻删除整段会话,启动时兜底清理 |
| 文档解析 | PDF/DOCX/TXT 全部在本机抽取,只把正文随请求发给模型厂商 |
| 申请权限 | 仅网络、网络状态、前台服务(dataSync)、通知;无存储、无位置、无通讯录 |
| 图片选择 | 系统照片选择器,不索要相册权限 |
| WebView | 仅加载 `file:///android_asset/` 本地页;外部链接交系统浏览器;远程调试仅 debug 开启 |
| 备份文件 | 明文 JSON,由你自己保管;请勿上传公开位置 |

## 架构

### 技术栈

| 层 | 选型 |
|---|---|
| 语言 / UI | Kotlin 2.3.20 + Jetpack Compose(Material 3) |
| 架构 | MVVM + Repository + Provider 适配层 + 手写服务定位器 |
| 网络 | OkHttp + okhttp-sse(SSE 流式),按厂商下发思考/联网/流式用量参数 |
| 渲染 | WebView + markdown-it + KaTeX + mhchem + Mermaid + highlight.js(全部本地资源) |
| 存储 | Room(v8,含匿名标记、多图多文件、每轮用量)+ DataStore + Keystore AES-256-GCM |
| 文档 | PDFBox-Android 解析 PDF;DOCX 直接解压 `document.xml`;纯文本直读 |
| 构建 | Gradle 8.14.3 / AGP 8.13.2 / KSP 2.3.11 / JVM 17;release 开 R8 与资源裁剪 |
| 系统 | minSdk 30(Android 11)· targetSdk 36 |

### 渲染管线

聊天区是**单个 WebView 承载整段会话**,而非每条消息一个视图:

```
Kotlin  ──setMessages(JSON)──▶  #chat   整段重写(已入库消息)
        ──updateStreaming(δ)─▶  #stream 增量追加(约 70ms 节流)
                                   │
        代码围栏挖走 → 公式抽取 → HTML 表格清洗 → markdown-it → KaTeX 回填 → Mermaid 渲染
                                   │
        滚动/高度/贴底判定 ── 全部交给浏览器原生布局(window 滚动)
```

消息同步先比对轻量指纹再序列化,避免流式期间每帧把图片 base64 转一遍;这是长会话流畅度的关键。

### 项目结构

```
app/src/main/
├── assets/md/                     # 本地渲染页:chat.html + chat.js + markdown-it/KaTeX/mhchem/Mermaid/hljs
└── java/com/aichat/client/
    ├── MainActivity.kt            # 入口(边到边显示)
    ├── ChatApplication.kt         # 服务定位器 + 启动兜底清理匿名会话
    ├── data/
    │   ├── local/                 # Room:实体 / DAO / 数据库(v8 迁移)
    │   ├── settings/              # DataStore:配置 / 主题 / 画像 / 价格表 / 预设
    │   ├── security/              # Keystore AES-GCM 加解密
    │   ├── remote/                # 通用 API 客户端 + SSE + 图片/文档解析
    │   │   └── provider/          # 厂商能力矩阵、模型目录、余额与连接测试
    │   └── repository/            # 会话 / 聊天仓库
    ├── service/                   # 流式保活前台服务
    └── ui/
        ├── navigation/            # 路由(首页/设置/关于/教程/聊天)
        ├── theme/                 # 主题(深色三态 + 主题色)
        ├── home/                  # 会话列表 + 匿名会话入口
        ├── chat/                  # 聊天页 + WebView 桥 + 附件与用量
        ├── settings/              # 模型配置 / 个性化 / 外观 / 备份
        ├── tutorial/              # 新手教程
        └── about/                 # 关于页
```

## 从源码构建

环境要求:JDK 17+、Android SDK(compileSdk 36)、Gradle 由 wrapper 自动下载。

```bash
git clone https://gitee.com/suxiangyu138/aurora-chat.git
cd aurora-chat

# local.properties(不入库):sdk.dir 与签名配置
# sdk.dir=/path/to/android-sdk
# keystore.path=/path/to/release.jks
# keystore.alias=...
# keystore.password=...

./gradlew assembleDebug        # Debug 包(约 110MB,未裁剪)
./gradlew assembleRelease      # Release 包(R8 + 资源裁剪,约 12MB,需本机签名)
```

签名密钥路径与口令从 `local.properties` 读取,该文件已在 `.gitignore` 中,**不要提交**。丢失签名密钥将无法覆盖升级,请与备份一起保管。

## 兼容性

- **系统**:Android 11(API 30)及以上;iQOO / OriginOS 全系适配
- **接口**:任何 OpenAI 兼容的 `POST /chat/completions`,`stream: true` 走 SSE
- **中转站兼容**:以 `/api` 结尾的地址会自动补成 `/api/v1/chat/completions`;请求统一带浏览器 UA,避免 Cloudflare 403 被误判为超时
- **模型**:文本模型开箱即用;支持识图的模型可发多图;带 `reasoning_content` 的推理模型自动展示思考
- **文档**:PDF(文字版)、DOCX、TXT/Markdown/CSV/JSON/代码;扫描版 PDF 无文字层,读不出内容

## 常见问题

**测试连接失败怎么排查?**
看提示类型:401/403 是密钥或余额问题,404 多为 URL 少了 `/v1`,模型不存在就点「从接口拉取模型」重选。现在会显示服务端返回的具体原因。

**某个会话一直返回 400?**
多半是历史消息里带了服务端不接受的内容。v2.0.0 已过滤失败气泡与空 assistant 消息,并补齐 DeepSeek 工具场景要求的 `reasoning_content`;若仍失败,错误气泡会显示服务端原文。

**余额显示「查询失败」?**
只有 DeepSeek、Kimi 有 API Key 级余额接口;其余厂商只能去云平台控制台看。

**匿名会话真的不保存吗?**
不写进会话列表,退出页面即删除,启动时再清理一次残留;但如果你主动导出备份,匿名会话不在备份里。

**扫描版 PDF 读不出来?**
扫描件没有文字层,需要先 OCR。当前版本只抽取文本层。

**换手机怎么迁移?**
设置页导出备份 JSON,新机导入即可。备份含密钥,请勿经公开渠道传输。

## 许可

本项目采用 [MIT License](LICENSE) 开源,可自由使用、修改、分发,包括商业用途,仅需保留版权与许可声明。

## 作者

**苏巷雨** · 独立开发者(Android / Kotlin)

- GitHub:[@suxiangyu138](https://github.com/suxiangyu138)
- Gitee:[@suxiangyu138](https://gitee.com/suxiangyu138)
- 邮箱:[suxiangyu_dev@foxmail.com](mailto:suxiangyu_dev@foxmail.com)

<div align="center">

Kotlin · Jetpack Compose · Material 3

© 2026 苏巷雨 · MIT License

</div>
