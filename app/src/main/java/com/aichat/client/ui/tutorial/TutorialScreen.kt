package com.aichat.client.ui.tutorial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 新手教程:软件是什么、怎么开始、每个按钮干什么、隐私边界在哪。
 * 写给人看,不用术语堆砌;文案与当前版本功能一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("使用教程") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Aurora Chat 是一个把选择权交还给你的聊天客户端:模型、接口、密钥都由你自己填," +
                    "对话记录只存在这台手机上,没有中间服务器。",
                style = MaterialTheme.typography.bodyMedium
            )

            Section(
                "三分钟上手",
                listOf(
                    "1. 去模型厂商官网注册,创建 API Key(通常以 sk- 开头),复制下来。",
                    "2. 打开设置页,点「选择厂商或中转站模板」,选你申请的厂商,URL 与模型名会自动填好。",
                    "3. 把 API Key 粘进去,点「从接口拉取模型」可列出这个 Key 能用的全部模型。",
                    "4. 点「测试连接」,看到「连接成功」再点「保存」。",
                    "5. 返回首页,点右下角 + 新建会话,输入问题发送。",
                    "6. 第一轮回答后,气泡下方会出现输入/输出 token 与费用,顶栏显示本会话累计与上下文占用。"
                )
            )

            Section(
                "对话页的每个按钮",
                listOf(
                    "模型胶囊(最左):切换已保存的配置,只影响之后的请求,历史消息不会改写。",
                    "深度思考:开启后模型先推理再回答,过程可展开;默认关闭,适合难题。",
                    "联网搜索:开启后由厂商在服务端检索网页并给出来源;日期与新闻类问题建议开。",
                    "余额胶囊:仅 DeepSeek、Kimi、OpenRouter 支持,点一下刷新,回答完一轮也会自动刷新。",
                    "回形针:上传 PDF、Word(docx)、TXT 等文档,正文在本地抽取后随消息发给模型。",
                    "图片按钮:从相册选图,点击已发送的图片可全屏放大、双指缩放。",
                    "发送/停止:生成过程中按钮变成停止,已生成的内容会保留。"
                )
            )

            Section(
                "个性化画像",
                listOf(
                    "设置页最下方可以写一段关于你自己的说明,例如专业、目标、回答偏好。",
                    "开关打开后,这段文字会附加到每轮请求的系统提示里;关闭则完全不带。",
                    "画像只影响回答风格与详略,不会上传到任何第三方服务器。"
                )
            )

            Section(
                "文档与图片",
                listOf(
                    "支持 PDF、DOCX、TXT、Markdown、CSV、JSON 与常见代码文件,单次最多抽取 6 万字。",
                    "旧版 .doc 请先在 Word 里另存为 .docx;扫描版 PDF 没有文字层,读不出内容。",
                    "附件正文会进入该会话的历史,后续每轮都会重复携带,长文档会明显增加 token 消耗。"
                )
            )

            Section(
                "匿名会话",
                listOf(
                    "首页顶栏的带斜线眼睛图标就是匿名会话入口。",
                    "匿名会话同样能用联网、识图、文件、个性化与记忆,但不会出现在会话列表。",
                    "退出该页面时整段会话立刻删除,进程被系统结束后下次启动也会兜底清理。",
                    "适合问一些不希望留在手机里的问题;注意导出备份不含匿名会话。"
                )
            )

            Section(
                "隐私与数据",
                listOf(
                    "没有服务端,请求由手机直连你填写的接口地址。",
                    "API Key 用 Android Keystore 硬件密钥加密后存储,不落明文。",
                    "对话只存在本机数据库,卸载应用即消失;换机迁移请在设置页导出备份。",
                    "备份文件是明文 JSON,包含密钥,不要发到公开场合。"
                )
            )

            Faq(
                listOf(
                    "测试连接提示 401/403" to "密钥错误、过期、余额不足或无权限,回厂商控制台重新复制。",
                    "提示 404" to "接口地址多了或少了 /v1,直接用模板里的地址,不要手改路径。",
                    "提示模型不存在" to "模型名写错或这个 Key 没开通该模型,点「从接口拉取模型」选一个。",
                    "长回答中途停" to "在关于页按引导打开自启动与后台高耗电,OriginOS 的后台限制比较激进。",
                    "余额显示查询失败" to "该厂商没有 Key 级余额接口,或 OpenRouter 需要管理密钥;不影响聊天。",
                    "文档读不出内容" to "可能是扫描件或加密 PDF;换文字版 PDF 或先复制成 TXT。"
                )
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, lines: List<String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            }
            lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Faq(items: List<Pair<String, String>>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("常见问题", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            items.forEach { (q, a) ->
                Column {
                    Text(q, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Text(
                        a,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
