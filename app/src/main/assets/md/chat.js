/* Aurora Chat 单页聊天渲染
 * 整个会话渲染在一个页面里:布局/高度/滚动全部由浏览器原生管理,
 * 不再存在"气泡高度上报同步"环节,从根上杜绝气泡显示不全问题。
 * 流式采用 DOM 增量追加(每帧节流),不做全量重渲染。 */

var md = window.markdownit({
  html: false,
  linkify: true,
  breaks: true,
  highlight: function (str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return '<pre class="hljs"><code>' +
          hljs.highlight(str, { language: lang, ignoreIllegals: true }).value +
          '</code></pre>';
      } catch (e) { /* 忽略高亮失败 */ }
    }
    return '<pre class="hljs"><code>' + md.utils.escapeHtml(str) + '</code></pre>';
  }
});

/* ---------- Markdown + 数学公式 ---------- */

/* 占位符用私用区字符:markdown-it 不会解析它们,可安全穿过 md.render()。
 * SENTINEL 包公式(渲染后替换成 KaTeX),GUARD 包代码(渲染前还原给 markdown-it)。 */
var SENTINEL = "";
var GUARD = "";
var sentinelPattern = new RegExp(SENTINEL + "(\\d+)" + SENTINEL, "g");
var guardPattern = new RegExp(GUARD + "(\\d+)" + GUARD, "g");

/* 先把代码围栏与行内代码挖走:里面的 $ 和 \( 是代码不是公式,
 * 不挖走的话 `$PATH` / `printf("$%d")` 之类会被公式规则吃掉。 */
function guardCode(text, guards) {
  function take(m) {
    guards.push(m);
    return GUARD + (guards.length - 1) + GUARD;
  }
  return text
    .replace(/```[\s\S]*?```/g, take)
    .replace(/~~~[\s\S]*?~~~/g, take)
    .replace(/(`+)[\s\S]*?\1/g, take);
}

function extractMath(text, items) {
  function take(type) {
    return function (m, p1) {
      items.push({ type: type, content: String(p1).trim() });
      return SENTINEL + (items.length - 1) + SENTINEL;
    };
  }
  // 先块级、再行内;$…$ 放最后(它最容易误判,让其它写法先落袋)
  text = text.replace(/\$\$([\s\S]+?)\$\$/g, take("block"));
  text = text.replace(/\\\[([\s\S]+?)\\\]/g, take("block"));
  // 内层必须允许反斜杠,否则 \(\frac{a}{b}\) 这类带命令的公式一个都匹配不上
  text = text.replace(/\\\(([\s\S]+?)\\\)/g, take("inline"));
  /* 行内 $…$ 的约束:开头 $ 未被转义(\$ 不算)、其后不接空白,
   * 内容不跨行、结尾非空白,闭合 $ 后不接数字 —— 避免 "$5 到 $10" 被当成公式。 */
  text = text.replace(
    /(^|[^\\$])\$(?!\s)((?:\\.|[^$\n\\])*?[^\s$\\])\$(?!\d)/g,
    function (m, pre, body) {
      items.push({ type: "inline", content: String(body).trim() });
      return pre + SENTINEL + (items.length - 1) + SENTINEL;
    }
  );
  return text;
}

function renderMarkdown(text) {
  var items = [];
  var guards = [];
  var t = guardCode(String(text == null ? "" : text), guards);
  t = extractMath(t, items);
  // 代码原文还原,交回 markdown-it 正常渲染(围栏高亮走 highlight 回调)
  t = t.replace(guardPattern, function (m, i) {
    var g = guards[parseInt(i, 10)];
    return g == null ? "" : g;
  });
  var html = md.render(t);
  html = html.replace(sentinelPattern, function (m, index) {
    var item = items[parseInt(index, 10)];
    if (!item) return "";
    try {
      return katex.renderToString(item.content, {
        throwOnError: false,
        displayMode: item.type === "block"
      });
    } catch (e) {
      // KaTeX 解析不了就退回原样展示,不能让整条消息渲染失败
      return md.utils.escapeHtml(item.content);
    }
  });
  return html;
}

/* ---------- 工具 ---------- */

function esc(s) {
  return String(s)
    .replace(/&/g, "&amp;").replace(/</g, "&lt;")
    .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function addCopyButtons(root) {
  var pres = (root || document).querySelectorAll("pre");
  for (var i = 0; i < pres.length; i++) {
    var pre = pres[i];
    if (pre.querySelector(".code-copy")) continue;
    var btn = document.createElement("button");
    btn.className = "code-copy";
    btn.textContent = "复制";
    btn.onclick = (function (p) {
      return function () {
        var code = p.querySelector("code");
        var text = code ? code.innerText : p.innerText;
        if (window.MdBridge) window.MdBridge.copyText(text);
        var self = this;
        self.textContent = "已复制";
        setTimeout(function () { self.textContent = "复制"; }, 1500);
      };
    })(pre);
    pre.appendChild(btn);
  }
}

/* ---------- 滚动 ---------- */

var chatEl = document.getElementById("chat");
var streamEl = document.getElementById("stream");

/* 一律走文档(window)滚动:WebView 里 body / documentElement 谁是滚动元素并不确定,
 * 写死 document.body 会让贴底判定恒假(body.clientHeight 为 0)。 */
function scrollTopNow() {
  return window.pageYOffset || document.documentElement.scrollTop ||
    document.body.scrollTop || 0;
}

function contentHeight() {
  return Math.max(
    document.documentElement.scrollHeight || 0,
    document.body.scrollHeight || 0
  );
}

function viewportHeight() {
  return window.innerHeight || document.documentElement.clientHeight || 0;
}

function isPinned() {
  return contentHeight() - scrollTopNow() - viewportHeight() < 80;
}

function scrollToBottom(smooth) {
  var top = contentHeight();
  if (smooth) {
    window.scrollTo({ top: top, behavior: "smooth" });
  } else {
    window.scrollTo(0, top);
  }
}

function reportScroll() {
  if (window.MdBridge) window.MdBridge.onScrollState(isPinned());
}

window.addEventListener("scroll", reportScroll);
window.addEventListener("resize", reportScroll);

/* ---------- 消息渲染 ---------- */

function reasoningBlock(reasoning, open) {
  return '<div class="reasoning">' +
    '<div class="reasoning-header" onclick="toggleReasoning(this)">' +
    '思考过程 <span class="arrow">' + (open ? "▾" : "▸") + '</span></div>' +
    '<div class="reasoning-body" style="' + (open ? "" : "display:none;") + '">' +
    esc(reasoning) + '</div></div>';
}

function messageHtml(m) {
  var isUser = m.role === "user";
  var isError = m.status === "error";
  var inner = "";
  if (m.image) {
    inner += '<img class="msg-image" src="data:image/jpeg;base64,' + m.image + '" alt="图片">';
  }
  if (m.reasoning) inner += reasoningBlock(m.reasoning, false);
  if (isError) {
    inner += '<div class="content">' + esc(m.content) + '</div>';
    inner += '<div class="retry-row"><button class="retry-btn" onclick="retry(\'' +
      m.id + '\')">重试</button></div>';
  } else {
    inner += '<div class="content">' + renderMarkdown(m.content) + '</div>';
  }
  var html = '<div class="row ' +
    (isUser ? "user" : (isError ? "error" : "assistant")) + '" id="m' + m.id + '">';
  if (!isUser) html += '<div class="avatar">✦</div>';
  html += '<div class="bubble">' + inner + '</div>';
  html += '<button class="msg-menu" onclick="menu(\'' + m.id + '\')">⋮</button>';
  html += '</div>';
  return html;
}

/* ---------- 流式 ---------- */

var streamingText = "";
var streamingReasoning = "";
var renderScheduled = false;
/* 流式已结束、等正式消息入库回来后再摘掉流式行(见 clearStreaming) */
var streamPendingClear = false;
var streamClearTimer = null;

/* 光标插进最后一个行内可承载的元素里,否则它会成为独立块另起一行,
 * 每来一帧就抖一下,看起来像回答被截断。 */
function appendCaret(contentEl) {
  if (!contentEl) return;
  var target = contentEl;
  for (var i = 0; i < 8; i++) {
    var last = target.lastElementChild;
    if (!last) break;
    var tag = last.tagName;
    if (tag === "PRE" || tag === "TABLE" || tag === "HR" ||
      tag === "IMG" || tag === "BR" || tag === "BLOCKQUOTE") break;
    if (String(last.className || "").indexOf("katex") >= 0) break;
    target = last;
  }
  var caret = document.createElement("span");
  caret.className = "caret";
  caret.textContent = "▌";
  target.appendChild(caret);
}

function renderStreaming() {
  // 贴底判定必须在改 DOM 之前取:改完再判,内容一涨就会判成"用户上翻了"而停止跟随
  var wasPinned = isPinned();
  var row = document.getElementById("streaming-row");
  if (!row) {
    row = document.createElement("div");
    row.id = "streaming-row";
    row.className = "row assistant";
    row.innerHTML = '<div class="avatar">✦</div><div class="bubble"></div>';
    // 挂在 #stream 里:setMessages 只重写 #chat,流式行不会被擦掉
    streamEl.appendChild(row);
  }
  var inner = "";
  if (!streamPendingClear) inner += '<span class="streaming-dot"></span>';
  if (streamingReasoning) inner += reasoningBlock(streamingReasoning, true);
  inner += '<div class="content">' + renderMarkdown(streamingText) + '</div>';
  var bubble = row.querySelector(".bubble");
  bubble.innerHTML = inner;
  addCopyButtons(bubble);
  if (!streamPendingClear) appendCaret(bubble.querySelector(".content"));
  if (wasPinned) scrollToBottom(false);
  reportScroll();
}

/* 全量重置流式内容:开流、页面重载后恢复、文本回退时用(增量对不上就走这条) */
function resetStreaming(text, reasoning) {
  if (streamClearTimer) {
    clearTimeout(streamClearTimer);
    streamClearTimer = null;
  }
  streamPendingClear = false;
  streamingText = String(text == null ? "" : text);
  streamingReasoning = String(reasoning == null ? "" : reasoning);
  renderStreaming();
}

function updateStreaming(textDelta, reasoningDelta) {
  streamingText += (textDelta || "");
  streamingReasoning += (reasoningDelta || "");
  if (renderScheduled) return;
  renderScheduled = true;
  requestAnimationFrame(function () {
    renderScheduled = false;
    renderStreaming();
  });
}

/* 流式结束:内容此刻才写库,Room 的 Flow 回到界面还有几十毫秒延迟。
 * 立刻删掉流式行会出现"回答闪没了又回来";这里先摘掉呼吸灯与光标,
 * 等下一次 setMessages(已含正式消息)再真正移除,并留超时兜底。 */
function clearStreaming() {
  var row = document.getElementById("streaming-row");
  if (!row) {
    removeStreamingRow();
    return;
  }
  streamPendingClear = true;
  renderStreaming();
  if (streamClearTimer) clearTimeout(streamClearTimer);
  streamClearTimer = setTimeout(removeStreamingRow, 1500);
}

function removeStreamingRow() {
  if (streamClearTimer) {
    clearTimeout(streamClearTimer);
    streamClearTimer = null;
  }
  streamPendingClear = false;
  streamingText = "";
  streamingReasoning = "";
  var row = document.getElementById("streaming-row");
  if (row && row.parentNode) row.parentNode.removeChild(row);
}

/* ---------- 主题 ---------- */

var themeStyle = document.createElement("style");
themeStyle.id = "theme-style";
document.head.appendChild(themeStyle);

/* 裸 RRGGBB 在 CSS 里是非法值,整条声明会被丢弃(气泡就没背景色了),
 * 这里统一补上 # —— 兼容 Kotlin 侧传 "#RRGGBB" 与 "RRGGBB" 两种写法。 */
function cssColor(v) {
  var s = String(v == null ? "" : v).trim();
  if (!s) return "inherit";
  if (/^[0-9a-fA-F]{3,8}$/.test(s)) return "#" + s;
  return s;
}

function setTheme(dark, userBg, assistantBg, userFg, assistantFg, errorBg, errorFg) {
  document.body.style.color = dark ? "#E6E1E5" : "#1C1B1F";
  themeStyle.textContent =
    ".row.user .bubble { background:" + cssColor(userBg) +
    "; color:" + cssColor(userFg) + "; }" +
    ".row.assistant .bubble { background:" + cssColor(assistantBg) +
    "; color:" + cssColor(assistantFg) + "; }" +
    ".row.error .bubble { background:" + cssColor(errorBg) +
    "; color:" + cssColor(errorFg) + "; }";
}

/* ---------- 交互 → Android 桥 ---------- */

function toggleReasoning(header) {
  var body = header.nextElementSibling;
  var arrow = header.querySelector(".arrow");
  if (body.style.display === "none") {
    body.style.display = "";
    arrow.textContent = "▾";
  } else {
    body.style.display = "none";
    arrow.textContent = "▸";
  }
}

function loadEarlier() {
  if (window.MdBridge) window.MdBridge.onLoadEarlier();
}

function menu(id) {
  if (window.MdBridge) window.MdBridge.onMenu(String(id));
}

function retry(id) {
  if (window.MdBridge) window.MdBridge.onRetry(String(id));
}

/* ---------- 诊断:页面标题回传状态(Chrome DevTools 可读) ---------- */
window.onerror = function (msg) {
  document.title = "ERR:" + msg;
};

/* 上次渲染的首条消息 id:用来区分"上方插入历史"与"下方追加新消息" ——
 * 只有前者需要按锚点补偿 scrollTop,对后者补偿会把用户视口无故拽走。 */
var prevFirstId = null;

function setMessages(payload, hasMore) {
  // Kotlin 侧用 JSONObject.quote 传进来的是 JSON 字符串,必须解析成数组;
  // 直接当数组遍历会退化成"逐字符渲染",整个会话变成一堆空气泡。
  var list;
  try {
    list = typeof payload === "string" ? JSON.parse(payload) : (payload || []);
  } catch (e) {
    document.title = "ERR:setMessages " + e;
    return;
  }
  if (!list || typeof list.length !== "number") list = [];

  var wasPinned = isPinned();
  var prevScrollTop = scrollTopNow();
  var anchorId = prevFirstId;
  var anchorEl = anchorId == null ? null : document.getElementById("m" + anchorId);
  var anchorTop = anchorEl ? anchorEl.offsetTop : 0;

  var html = "";
  if (hasMore) {
    html += '<div id="load-more"><button class="load-more-btn" onclick="loadEarlier()">' +
      '加载更早的消息</button></div>';
  }
  for (var i = 0; i < list.length; i++) html += messageHtml(list[i]);
  chatEl.innerHTML = html;
  addCopyButtons(chatEl);

  /* 流式行与正式气泡的交接:两者由 Kotlin 侧两条独立状态(messages / streamingText)
   * 推送,先后到达顺序不定。只要列表末尾已经是本次流式内容(说明已落库),
   * 就立刻收掉流式行 —— 否则会并存出现"两个回答气泡"。
   * 反之(内容还没回流)保持流式行显示,避免回答闪没了再冒出来。 */
  var tail = list.length ? list[list.length - 1] : null;
  var landed = !!tail && tail.role !== "user" && streamingText.length > 0 &&
    String(tail.content == null ? "" : tail.content) === streamingText;
  if (landed || streamPendingClear) removeStreamingRow();

  if (wasPinned) {
    scrollToBottom(false);
  } else if (anchorEl) {
    var newAnchor = document.getElementById("m" + anchorId);
    if (newAnchor) window.scrollTo(0, prevScrollTop + (newAnchor.offsetTop - anchorTop));
  }
  prevFirstId = list.length ? list[0].id : null;
  reportScroll();
  document.title = "OK:" + list.length;
}
