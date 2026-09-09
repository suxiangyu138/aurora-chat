# AI Chat — 自定义 API 国产 AI 大模型聊天安卓客户端

通用型 AI 聊天 APP:用户自定义接口 URL、API Key、模型参数,一套通用请求模板适配所有主流国产/海外大模型(通义千问、文心一言、讯飞星火、智谱、百川、DeepSeek、OpenAI、Claude 等)。纯本地运行,无后端服务器。

详细需求与开发方案见 [开发说明.md](开发说明.md),环境配置见 [环境配置说明.md](环境配置说明.md)。

## 技术栈

- **语言/UI**:Kotlin + Jetpack Compose(Material3)
- **架构**:MVVM + Repository
- **网络**:OkHttp + okhttp-sse(SSE 流式打字机输出,兼容非流式 JSON)
- **存储**:Room(会话/消息)+ DataStore(多套模型配置,本地加密待迭代)
- **构建**:Gradle 8.14.3 / AGP 8.13.2 / Kotlin 2.3.11 / KSP
- **最低系统**:Android 11(minSdk 30),适配 iQOO OriginOS

## 项目结构

```
app/src/main/java/com/aichat/client/
├── MainActivity.kt            # 入口
├── ChatApplication.kt         # 服务定位器
├── data/
│   ├── local/                 # Room:Entities / Daos / AppDatabase
│   ├── settings/              # DataStore:ModelConfig / SettingsRepository
│   ├── remote/                # ChatApiClient(通用请求模板 + SSE 解析)
│   └── repository/            # ChatRepository / SessionRepository
└── ui/
    ├── navigation/            # 路由
    ├── theme/                 # 主题(跟随系统深浅色)
    ├── home/                  # 会话列表页
    ├── chat/                  # 聊天页(流式输出)
    └── settings/              # 模型配置页
```

## 构建与调试

```bash
# 构建 Debug APK(首次构建需下载依赖)
gradlew assembleDebug

# 产物路径
app/build/outputs/apk/debug/app-debug.apk

# 真机安装与日志
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s AndroidRuntime:E
```

## 使用流程

1. 设置页选择**预设模板**(DeepSeek/通义千问/智谱等)→ 填 API Key → 测试连接 → 保存
2. 首页点 + 新建会话,开始对话(SSE 流式打字机 + 呼吸灯 + 思考过程可展开)
3. 支持多套配置一键切换、图片输入、参数手动微调

## 已完成 / 待迭代

- [x] MVP:自定义配置、流式对话、会话管理、参数调节、测试连接
- [x] 非流式兼容(设置页开关)、错误提示(密钥错误/超时/404)
- [x] 预设国产大模型模板(DeepSeek/通义/文心/星火/智谱/百川/Kimi/豆包等一键填充)
- [x] 自定义请求体 JSON 模板编辑(占位符替换)
- [x] 本地密钥加密存储(Keystore AES-256-GCM)、备份导入导出、一键清空
- [x] 多模态图片输入、深色模式三态切换、主题色定制
- [x] iQOO 后台保活(流式期间前台服务)、断线自动重连(指数退避)
- [x] 思考过程展开/收起(reasoning_content)、呼吸灯动画、Markdown+LaTeX 渲染
- [x] 关于页:开发者信息 + iQOO 权限引导
- [ ] 语音输入输出、对话导出、云端备份(远期规划)
