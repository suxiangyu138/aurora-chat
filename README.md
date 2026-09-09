# Aurora Chat

> 极光般流畅的通用 AI 聊天客户端 —— 一套模板,对话所有大模型

Aurora Chat 是一款通用型自定义 AI 大模型聊天安卓客户端:用户自定义接口 URL、API Key、模型参数,兼容 DeepSeek、通义千问、文心一言、讯飞星火、智谱、百川、Kimi、豆包、OpenAI、Claude 等所有主流国产/海外大模型的 HTTP API。**纯本地运行,无后端服务器,密钥不离开你的设备。**

## 功能特性

- **全模型通用**:通用 JSON 请求模板,预设 9 大厂商模板一键填充 URL(密钥自填),自定义请求体模板编辑
- **极致对话体验**:SSE 流式打字机 + 呼吸灯动画、Markdown/LaTeX(KaTeX)渲染、代码语法高亮、思考过程可展开收起、多模态图片输入
- **会话管理**:多会话、重命名、历史留存(Room 数据库)、上下文记忆轮数可调
- **多套配置**:一键切换,API Key 使用 Android Keystore AES-256-GCM 硬件级加密存储
- **数据安全**:纯本地运行、备份导入导出(JSON 文件分享/导入)、一键清空
- **个性化**:深色模式三态(跟随系统/浅色/深色)+ 7 种主题色
- **iQOO 专属适配**:OriginOS 后台保活(流式期间前台服务)、断线自动重连(指数退避)、手势导航条避让

## 快速开始

### 下载安装

前往 [Releases](https://github.com/suxiangyu138/aurora-chat/releases) 下载最新 `AuroraChat-vX.X.X.apk`,手机安装(需允许安装未知来源应用)。

### 使用

1. 设置页选择预设模板(或手动填写接口 URL)→ 填入 API Key → 测试连接 → 保存
2. 首页 `+` 新建会话,开始对话
3. 支持图片输入(多模态模型)、参数手动微调、思考过程展开

### 从源码构建

环境要求:JDK 21、Android SDK(compileSdk 36)、Gradle 8.14+(wrapper 自动下载)

```bash
git clone https://github.com/suxiangyu138/aurora-chat.git
cd aurora-chat

# 配置 local.properties(参考 环境配置说明.md)
# sdk.dir=D\:\\AndroidSdk

./gradlew assembleDebug        # Debug 包
./gradlew assembleRelease      # Release 包(需本机配置签名密钥,见 app/build.gradle.kts)
```

详细环境搭建与真机调试指南见 [环境配置说明.md](环境配置说明.md),需求与方案见 [开发说明.md](开发说明.md)。

## 技术栈

| 层 | 技术 |
|---|---|
| 语言/UI | Kotlin + Jetpack Compose(Material3) |
| 架构 | MVVM + Repository |
| 网络 | OkHttp + okhttp-sse(SSE 流式,兼容非流式) |
| 渲染 | WebView + markdown-it + KaTeX + highlight.js |
| 存储 | Room + DataStore,Keystore AES-256-GCM 加密 |
| 构建 | Gradle 8.14.3 / AGP 8.13.2 / Kotlin 2.3.20 / KSP |
| 最低系统 | Android 11(minSdk 30) |

## 项目结构

```
app/src/main/java/com/aichat/client/
├── MainActivity.kt            # 入口(边到边显示)
├── ChatApplication.kt         # 服务定位器
├── data/
│   ├── local/                 # Room:实体/DAO/数据库(含迁移)
│   ├── settings/              # DataStore:模型配置/主题/预设模板
│   ├── security/              # Keystore AES-GCM 加密
│   ├── remote/                # 通用 API 客户端 + SSE 解析 + 图片工具
│   └── repository/            # 会话/聊天仓库
├── service/                   # 对话保活前台服务
└── ui/
    ├── navigation/            # 路由
    ├── theme/                 # 主题(深色三态 + 主题色)
    ├── home/                  # 会话列表页
    ├── chat/                  # 聊天页(Markdown 渲染/呼吸灯/思考过程/图片)
    ├── settings/              # 模型配置页(预设/参数/备份)
    └── about/                 # 关于页(开发者信息)
```

## 许可

个人项目,保留所有权利。代码仅供学习交流。

## 关于作者

- 开发者:苏巷雨
- 联系:1368614311@qq.com
