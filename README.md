<div align="center">

# Aurora Chat

**一套模板,对话所有大模型**

极光般流畅的通用 AI 聊天客户端 · 纯本地运行 · 密钥不出设备

[![Platform](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)](#兼容性)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Version](https://img.shields.io/badge/release-v1.0.0-6366F1)](https://github.com/suxiangyu138/aurora-chat/releases)
[![Backend](https://img.shields.io/badge/服务端-无-0D9488)](#隐私与安全)

[下载安装](#下载安装) · [零基础教程](#零基础使用教程) · [从源码构建](#从源码构建) · [隐私与安全](#隐私与安全) · [架构](#架构)

</div>

---

## 简介

Aurora Chat 是一款**通用型自定义大模型聊天客户端**:接口 URL、API Key、模型名、采样参数、请求体模板全部由你决定,因此它不绑定任何一家厂商 —— 凡是提供 OpenAI 兼容 `/chat/completions` 接口的模型,填上地址与密钥就能对话。

它没有后端。请求从你的手机直连模型厂商,密钥用 Android Keystore 硬件密钥加密后落盘,对话记录只存在本机 Room 数据库里。**没有中间服务器,也就没有中间人。**

## 功能特性

### 全模型通用

| 能力 | 说明 |
|---|---|
| 预设模板 | 内置 10 家厂商模板(DeepSeek、通义千问、智谱 GLM、文心一言、讯飞星火、Kimi、百川、豆包/火山方舟、OpenAI、OpenRouter),一键填充 URL 与模型名 |
| 自定义接口 | 任意 OpenAI 兼容地址;请求体模板可直接编辑,适配非标准网关 |
| 参数可调 | temperature、top_p、max_tokens(默认 8192)、上下文记忆轮数 |
| 多套配置 | 多组接口/密钥/模型并存,一键切换 |
| 连接自检 | 保存前一键测试连通性与鉴权,错误原因直给,不做无声失败 |

### 对话体验

- **流式打字机**:SSE 逐字输出 + 呼吸灯与光标,自动兼容非流式接口
- **完整 Markdown**:标题/列表/表格/引用/分割线/图片/链接,代码块语法高亮并支持一键复制
- **数学公式**:KaTeX 渲染 `$…$`、`$$…$$`、`\(…\)`、`\[…\]`,代码块内的 `$` 不会被误判成公式
- **思考过程**:自动识别 `reasoning_content`(DeepSeek-R1、GLM 等),可展开收起
- **多模态输入**:相册选图直接提问(走系统照片选择器,无需存储权限),图片随消息持久化
- **消息级操作**:复制、编辑重发、重新生成、删除;失败消息入库并就地重试
- **历史分页**:长会话按页加载,「加载更早的消息」按需回溯,不一次性堆满内存

### 会话与数据

- 多会话管理、重命名、按时间留存(Room 数据库,含版本迁移)
- 备份导出 / 导入(JSON 文件,走系统分享与文件选择器)
- 一键清空当前对话或全部数据

### 个性化

- 深色模式三态:跟随系统 / 强制浅色 / 强制深色
- 7 种主题色(靛蓝、海洋蓝、青绿、翠绿、橙、玫粉、紫),明暗两套配色自动派生

### iQOO / OriginOS 深度适配

- 流式期间启动 `dataSync` 前台服务,长回答不被系统回收
- 断线指数退避自动重连;读超时下限 300s,杜绝长回答被静默掐断
- 中断有明确提示,并保留已生成的半截内容,不做假装成功
- 手势导航条与键盘避让,边到边显示

## 快速开始

### 下载安装

前往 [Releases](https://github.com/suxiangyu138/aurora-chat/releases) 下载最新 `AuroraChat-vX.X.X.apk`,手机安装(需在系统设置里允许安装未知来源应用)。Gitee 用户可从 [Gitee 发行版](https://gitee.com/suxiangyu138/aurora-chat/releases) 获取同一安装包。

### 三步接入

1. **设置页** → 选择预设模板(或手动填写接口 URL)→ 粘贴 API Key → **测试连接** → 保存
2. **首页 `+`** → 新建会话
3. 开始对话;需要时展开思考过程、附加图片、或在设置页微调采样参数

> iQOO 用户建议顺手完成关于页里的四项后台保活设置,避免长回答被 OriginOS 中断。

### 从源码构建

环境要求:JDK 21、Android SDK(compileSdk 36)、Gradle 由 wrapper 自动下载。

```bash
git clone https://github.com/suxiangyu138/aurora-chat.git
cd aurora-chat

# 配置 local.properties(不入库,详见 环境配置说明.md)
# sdk.dir=D\:\\AndroidSdk

./gradlew assembleDebug        # Debug 包
./gradlew installDebug         # 直接装到已连接的真机
./gradlew assembleRelease      # Release 包(需本机配置签名密钥)
```

签名密钥路径与口令从 `local.properties` 读取(`keystore.path` / `keystore.alias` / `keystore.password`),该文件已在 `.gitignore` 中,**不要提交**。

详细环境搭建与真机调试见 [环境配置说明.md](环境配置说明.md),需求与设计方案见 [开发说明.md](开发说明.md)。

## 零基础使用教程

> 没接触过 AI API 也能看懂。按顺序做完,十分钟内发出第一条消息。

### 第 0 步:先弄懂三个词

- **API Key**:厂商发给你的一串密钥(常见以 `sk-` 开头),相当于一张门禁卡。App 带着它请求模型,厂商靠它识别你的账户并计费。它只属于你,不要发给别人。
- **模型名**:具体要调用的模型,比如 `deepseek-chat`、`qwen-plus`。同一家厂商通常有多个模型,能力和价格不同。
- **接口 URL**:厂商的请求地址。App 内置了常用厂商模板,选模板会自动填好,新手不用手写。

### 第 1 步:去厂商官网申请 API Key

以国内常用的 DeepSeek 为例,其他厂商流程几乎一样:

1. 浏览器打开 `https://platform.deepseek.com`,用手机号注册并登录。
2. 按页面提示完成实名认证(国内大模型平台基本都要求实名)。
3. 进入充值页面,先充少量金额试用(以官网当前活动和价格为准)。
4. 打开「API Keys」页面,点「创建 API Key」,起个名字(例如 aurora)。
5. 复制弹出的密钥。**完整密钥通常只显示这一次**,先粘贴到记事本或直接填进 App。

其他厂商的领取入口:

| 厂商 | 控制台地址 | 找 Key 的位置 | App 里选哪个模板 |
|---|---|---|---|
| DeepSeek | `platform.deepseek.com` | API Keys | DeepSeek(深度求索) |
| 阿里通义千问 | `bailian.console.aliyun.com` | API-KEY 管理 | 阿里通义千问 |
| 智谱 GLM | `bigmodel.cn` | API Keys | 智谱 GLM |
| Moonshot Kimi | `platform.moonshot.cn` | API Key 管理 | Moonshot Kimi |
| 豆包/火山方舟 | `console.volcengine.com/ark` | API Key 管理 | 豆包/火山方舟 |
| 百度文心一言 | `console.bce.baidu.com/qianfan` | API Key 管理 | 百度文心一言 |
| 讯飞星火 | `console.xfyun.cn` | 星火大模型 → 服务管理 | 讯飞星火 |
| 百川智能 | `platform.baichuan-ai.com` | API Key | 百川智能 |
| OpenAI | `platform.openai.com` | API keys | OpenAI |
| OpenRouter | `openrouter.ai/keys` | Keys | OpenRouter |

> 豆包/火山方舟的模型名要填「推理接入点 ID」(形如 `ep-xxxx`);OpenRouter 填模型全名(如 `openai/gpt-4o`)。这两家模板的模型名留空,需要手动补。
> OpenAI 在国内网络通常无法直连,新手建议先用国内厂商。

### 第 2 步:把 Key 填进 App

1. 打开 Aurora Chat,点首页右上角的齿轮图标(模型配置)。
2. 点「一键填充 URL 与模型名(密钥仍由你填写)」,选择你申请 Key 的厂商。
3. 在「API Key」框里粘贴密钥;确认「接口 URL」和「模型名」已自动填好,没有就手动补。
4. 点「测试连接」:
   - 提示成功 → 点「保存配置」。
   - 提示 401 / 403 → Key 复制不完整、余额不足或没有权限,回控制台检查。
5. 返回首页,点右下角「+」新建会话,输入问题发送即可。

### 第 3 步:按需调整参数(可选)

| 参数 | 作用 | 新手建议 |
|---|---|---|
| temperature | 随机性,越高回答越发散 | 通用场景 0.7;写代码、查资料用 0.2~0.5 |
| top_p | 另一种随机性控制,一般不用动 | 保持默认 |
| max_tokens | 单次回答的最大长度 | 默认 8192 足够,调太小回答会被截断 |
| 上下文轮数 | 每次请求携带多少条历史消息 | 10~20;越大越慢也越贵 |

### 常见报错对照

| 提示 | 大概率原因 | 处理方式 |
|---|---|---|
| 401 / 403 | Key 错误、过期、余额不足或无权限 | 重新复制 Key,检查余额与实名状态 |
| 404 | 接口 URL 多了或少了 `/v1` | 直接用 App 内置模板,不要手改路径 |
| 429 | 请求太频繁或超出额度 | 等几分钟再试,或到控制台查看限额 |
| 连接超时 | 网络不通,或厂商在当前网络不可达 | 换网络/换厂商;OpenAI 需要额外网络条件 |
| 回答中途停 | 长回答被系统后台限制中断 | 按关于页的四步说明开启自启动与后台高耗电 |

### 安全与费用

- API Key 由 Android Keystore 加密后只存本机,App 不上传;但**导出的备份 JSON 是明文**,不要发到群里或公开网盘。
- 费用按 token 在厂商控制台结算,日常文字聊天通常很便宜,长文和图片更贵;建议在控制台设置额度上限。
- 怀疑 Key 泄露:立刻在厂商控制台删除旧 Key,重新创建一个填进 App。

## 隐私与安全

| 项 | 做法 |
|---|---|
| 服务端 | 没有。请求由设备直连你配置的模型厂商 |
| API Key | Android Keystore 生成的硬件密钥 + AES-256-GCM 加密后存储,不落明文、不上传 |
| 对话记录 | 仅存本机 Room 数据库,卸载即随应用数据一并清除 |
| 申请权限 | 仅 `INTERNET`、`ACCESS_NETWORK_STATE`、`FOREGROUND_SERVICE(dataSync)`、`POST_NOTIFICATIONS` —— 无存储、无位置、无通讯录 |
| 图片选择 | 走系统照片选择器(Photo Picker),不索要相册权限 |
| WebView | 仅加载 `file:///android_asset/` 下的本地渲染页;外部链接交系统浏览器,JS 桥不对外部页面暴露;远程调试仅 Debug 包开启 |
| 备份文件 | 明文 JSON,由你自己保管与分享 —— 请勿把含密钥的备份传到公开位置 |

## 架构

### 技术栈

| 层 | 选型 |
|---|---|
| 语言 / UI | Kotlin 2.3.20 + Jetpack Compose(Material 3) |
| 架构 | MVVM + Repository + 手写服务定位器 |
| 网络 | OkHttp + okhttp-sse(SSE 流式,自动回退非流式) |
| 渲染 | WebView + markdown-it + KaTeX + highlight.js(全部本地资源,离线可用) |
| 存储 | Room(会话/消息,v4)+ DataStore(配置)+ Keystore AES-256-GCM |
| 构建 | Gradle 8.14.3 / AGP 8.13.2 / KSP 2.3.11 / JVM 17 |
| 系统 | minSdk 30(Android 11)· targetSdk 36 |

### 渲染管线

聊天区是**单个 WebView 承载整段会话**,而非每条消息一个视图:

```
Kotlin  ──setMessages(JSON)──▶  #chat   整段重写(已入库消息)
        ──updateStreaming(δ)─▶  #stream 增量追加(按帧节流)
                                   │
        代码围栏挖走 → 公式抽取占位 → markdown-it → KaTeX 回填
                                   │
        滚动/高度/贴底判定 ── 全部交给浏览器原生布局(window 滚动)
```

这样布局与高度由浏览器自己算,不存在「气泡高度上报同步」环节,从架构上消除了长内容显示不全的问题;流式只追加 DOM,不做全量重渲染。

### 项目结构

```
app/src/main/
├── assets/md/                     # 本地渲染页:chat.html + chat.js + markdown-it/KaTeX/hljs
└── java/com/aichat/client/
    ├── MainActivity.kt            # 入口(边到边显示)
    ├── ChatApplication.kt         # 服务定位器
    ├── data/
    │   ├── local/                 # Room:实体 / DAO / 数据库(含迁移)
    │   ├── settings/              # DataStore:模型配置 / 主题 / 预设模板
    │   ├── security/              # Keystore AES-GCM 加解密
    │   ├── remote/                # 通用 API 客户端 + SSE 解析 + 图片工具
    │   └── repository/            # 会话 / 聊天仓库
    ├── service/                   # 流式保活前台服务
    └── ui/
        ├── navigation/            # 路由
        ├── theme/                 # 主题(深色三态 + 主题色)
        ├── home/                  # 会话列表
        ├── chat/                  # 聊天页 + WebView 桥(ChatWebView.kt)
        ├── settings/              # 模型配置 / 参数 / 备份
        └── about/                 # 关于页
```

## 兼容性

- **系统**:Android 11(API 30)及以上;iQOO / OriginOS 全系深度适配,其余机型正常可用
- **接口**:任何 OpenAI 兼容的 `POST /chat/completions`,`stream: true` 走 SSE,不支持流式时自动整段返回
- **模型**:文本模型开箱即用;多模态模型可发图;带 `reasoning_content` 的推理模型自动展示思考过程

## 常见问题

**测试连接失败怎么排查?**
先看提示里的 HTTP 状态:401/403 多为密钥或余额问题,404 多为 URL 少写或多写了 `/v1`,超时则先确认网络与厂商可达性。

**长回答中途停了?**
读超时已放宽到 300s 并会自动重连;若仍中断,请在关于页按引导打开自启动与后台高耗电(OriginOS 的后台限制比较激进)。

**公式偶尔显示成原文?**
流式过程中公式的闭合定界符尚未到达时会暂时显示原文,内容收完即自动渲染 —— 这是增量渲染的固有过程,不是渲染失败。

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

© 2026 苏巷雨 · 保留所有权利

</div>
