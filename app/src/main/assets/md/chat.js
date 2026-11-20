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

/* Mermaid 代码块:保留语言标记,渲染时替换成 SVG 图 */
var defaultFence = md.renderer.rules.fence;
md.renderer.rules.fence = function (tokens, idx, options, env, self) {
  var info = (tokens[idx].info || "").trim().split(/\s+/)[0].toLowerCase();
  if (info === "mermaid") {
    return '<pre class="mermaid-src"><code class="language-mermaid">' +
      md.utils.escapeHtml(tokens[idx].content) + '</code></pre>';
  }
  return defaultFence(tokens, idx, options, env, self);
};

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

function sanitizeHtmlTable(html) {
  try {
    var doc = new DOMParser().parseFromString(html, "text/html");
    var src = doc.querySelector("table");
    if (!src) return "";
    var out = '<div class="table-scroll"><table>';
    var rows = src.querySelectorAll("tr");
    for (var i = 0; i < rows.length; i++) {
      out += "<tr>";
      var cells = rows[i].children;
      for (var j = 0; j < cells.length; j++) {
        var c = cells[j];
        var tag = (c.tagName || "").toLowerCase() === "th" ? "th" : "td";
        var span = "";
        var cs = parseInt(c.getAttribute("colspan") || "1", 10);
        var rs = parseInt(c.getAttribute("rowspan") || "1", 10);
        if (cs > 1) span += ' colspan="' + Math.min(cs, 50) + '"';
        if (rs > 1) span += ' rowspan="' + Math.min(rs, 50) + '"';
        out += "<" + tag + span + ">" + esc(c.textContent || "") + "</" + tag + ">";
      }
      out += "</tr>";
    }
    out += "</table></div>";
    return out;
  } catch (e) { return ""; }
}

/* 把表格包进横向滚动容器:窄屏下表格再宽也不会撑破气泡 */
function wrapTables(root) {
  var tables = root.querySelectorAll("table");
  for (var i = 0; i < tables.length; i++) {
    var t = tables[i];
    var parent = t.parentElement;
    if (parent && parent.className && parent.className.indexOf("table-scroll") >= 0) continue;
    var wrap = document.createElement("div");
    wrap.className = "table-scroll";
    t.parentNode.insertBefore(wrap, t);
    var full = document.createElement("button");
    full.className = "table-full-btn";
    full.textContent = "表格全屏";
    full.onclick = function (e) {
      e.stopPropagation();
      openTableFullscreen(t);
    };
    wrap.appendChild(full);
    wrap.appendChild(t);
  }
}

function renderMarkdownInner(text) {
  var items = [];
  var guards = [];
  var tables = [];
  var t = guardCode(String(text == null ? "" : text), guards);
  // 模型偶尔直接输出 HTML 表格:先抽走并清洗,避免被当成源码展示
  t = t.replace(/<table[\s\S]*?<\/table>/gi, function (m) {
    var clean = sanitizeHtmlTable(m);
    if (!clean) return m;
    tables.push(clean);
    return "\n\nAURORATABLE" + (tables.length - 1) + "AURORATABLE\n\n";
  });
  t = extractMath(t, items);
  // 代码原文还原,交回 markdown-it 正常渲染(围栏高亮走 highlight 回调)
  t = t.replace(guardPattern, function (m, i) {
    var g = guards[parseInt(i, 10)];
    return g == null ? "" : g;
  });
  var html = md.render(t);
  html = html.replace(/<p>AURORATABLE(\d+)AURORATABLE<\/p>/g, function (m, i) {
    return tables[parseInt(i, 10)] || "";
  });
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
    if (pre.querySelector(".code-actions")) continue;
    var actions = document.createElement("div");
    actions.className = "code-actions";

    var btn = document.createElement("button");
    btn.className = "code-copy";
    btn.textContent = "复制";
    btn.onclick = (function (p) {
      return function (e) {
        e.stopPropagation();
        var code = p.querySelector("code");
        var text = code ? code.innerText : p.innerText;
        if (window.MdBridge) window.MdBridge.copyText(text);
        var self = this;
        self.textContent = "已复制";
        setTimeout(function () { self.textContent = "复制"; }, 1500);
      };
    })(pre);
    actions.appendChild(btn);

    var full = document.createElement("button");
    full.className = "code-copy";
    full.textContent = "全屏";
    full.onclick = (function (p) {
      return function (e) {
        e.stopPropagation();
        var code = p.querySelector("code");
        openCodeFullscreen(code ? code.innerText : p.innerText);
      };
    })(pre);
    actions.appendChild(full);

    pre.appendChild(actions);
  }
}

function openTableFullscreen(tableEl) {
  var old = document.querySelector(".table-overlay");
  if (old && old.parentNode) old.parentNode.removeChild(old);
  var overlay = document.createElement("div");
  overlay.className = "table-overlay";

  var head = document.createElement("div");
  head.className = "full-head";
  var close = document.createElement("button");
  close.textContent = "关闭";
  close.onclick = function () {
    if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
  };
  head.appendChild(close);

  var scroll = document.createElement("div");
  scroll.className = "table-overlay-scroll";
  scroll.appendChild(tableEl.cloneNode(true));

  overlay.appendChild(head);
  overlay.appendChild(scroll);
  document.body.appendChild(overlay);
}

function openCodeFullscreen(text) {
  var old = document.querySelector(".full-overlay");
  if (old && old.parentNode) old.parentNode.removeChild(old);
  var overlay = document.createElement("div");
  overlay.className = "full-overlay";

  var head = document.createElement("div");
  head.className = "full-head";
  var copy = document.createElement("button");
  copy.textContent = "复制";
  copy.onclick = function () {
    if (window.MdBridge) window.MdBridge.copyText(text);
    copy.textContent = "已复制";
    setTimeout(function () { copy.textContent = "复制"; }, 1500);
  };
  var close = document.createElement("button");
  close.textContent = "关闭";
  close.onclick = function () {
    if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
  };
  head.appendChild(copy);
  head.appendChild(close);

  var pre = document.createElement("pre");
  pre.className = "full-code";
  pre.textContent = text;

  overlay.appendChild(head);
  overlay.appendChild(pre);
  document.body.appendChild(overlay);
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

function renderMarkdown(text) {
  try {
    return renderMarkdownInner(text);
  } catch (e) {
    return '<pre class="render-fallback">' + md.utils.escapeHtml(String(text == null ? "" : text)) + '</pre>';
  }
}

/* ---------- Mermaid ---------- */

var mermaidReady = typeof mermaid !== "undefined";
var mermaidSeq = 0;
if (mermaidReady) {
  try {
    mermaid.initialize({
      startOnLoad: false,
      securityLevel: "loose",
      theme: document.documentElement.classList.contains("dark") ? "dark" : "default",
      fontFamily: "inherit",
      htmlLabels: false,
      flowchart: { htmlLabels: false },
      class: { htmlLabels: false },
      state: { htmlLabels: false }
    });
  } catch (e) { mermaidReady = false; }
}

/** 中文或带空格的标签在 Mermaid 里必须加引号,否则整块图解析失败 */
function sanitizeMermaid(text) {
  var lines = String(text || "").split("\n");
  var type = "";
  function quoteLabel(label) {
    var v = String(label || "").trim();
    if (!v) return '""';
    if (/^".*"$/.test(v)) return v;
    return '"' + v.replace(/"/g, "'") + '"';
  }
  for (var i = 0; i < lines.length; i++) {
    var line = lines[i];
    var t = line.trim();
    if (!type && t && t.indexOf("%%") !== 0) type = t.split(/\s+/)[0];
    if (type === "erDiagram" && /(--|\.\.)/.test(line)) {
      line = line.replace(/:\s*([^"].*?)\s*$/, function (m, label) {
        return ": " + quoteLabel(label);
      });
    } else if (type === "classDiagram" &&
      /(<\|--|-->|\.\.>|--\|>|\.\.\|>|--\*|--o)/.test(line)) {
      line = line.replace(/:\s*([^"].*?)\s*$/, function (m, label) {
        return ": " + quoteLabel(label);
      });
    } else if (type === "quadrantChart") {
      if (/^\s*[xy]-axis\s+/.test(line)) {
        line = line.replace(/^(\s*[xy]-axis\s+)(.*?)\s*-->\s*(.*?)\s*$/,
          function (m, pre, a, b) { return pre + quoteLabel(a) + " --> " + quoteLabel(b); });
      } else if (/^\s*quadrant-\d\s+/.test(line)) {
        line = line.replace(/^(\s*quadrant-\d\s+)(.*?)\s*$/,
          function (m, pre, label) { return pre + quoteLabel(label); });
      }
    }
    lines[i] = line;
  }
  return lines.join("\n");
}

/* SVG 转 PNG:展示用 <img>,尺寸由浏览器按图片比例自动算,
 * 绕开 WebView 对 SVG 高度计算不可靠的问题;同时把 PNG 交给原生层做全屏缩放。 */
function rasterizeDiagram(div, svg, src) {
  try {
    var vb = (svg.getAttribute("viewBox") || "").split(/[\s,]+/).map(Number);
    var vbW = vb.length === 4 && vb[2] > 0 ? vb[2] : 0;
    var vbH = vb.length === 4 && vb[3] > 0 ? vb[3] : 0;
    if (!vbW || !vbH) return false;
    var clone = svg.cloneNode(true);
    var scale = Math.min(3, Math.max(1.5, 1000 / vbW));
    var w = Math.round(vbW * scale);
    var h = Math.round(vbH * scale);
    clone.setAttribute("width", w);
    clone.setAttribute("height", h);
    if (!clone.getAttribute("xmlns")) {
      clone.setAttribute("xmlns", "http://www.w3.org/2000/svg");
    }
    var xml = new XMLSerializer().serializeToString(clone);
    var blobUrl = URL.createObjectURL(new Blob([xml], { type: "image/svg+xml;charset=utf-8" }));
    var img = new Image();
    img.onload = function () {
      try {
        var canvas = document.createElement("canvas");
        canvas.width = w;
        canvas.height = h;
        var ctx = canvas.getContext("2d");
        ctx.drawImage(img, 0, 0, w, h);
        var data = canvas.toDataURL("image/png");
        var out = document.createElement("img");
        out.className = "mermaid-img";
        out.src = data;
        // 聊天里按 0.6 倍显示,避免复杂图撑得太宽;细节交给全屏放大
        var displayW = Math.max(36, Math.round(w * 0.12));
        out.style.width = displayW + "px";
        out.style.maxWidth = "none";
        out.style.height = "auto";
        out.alt = "Mermaid 图表";
        out.onclick = function () {
          if (window.MdBridge && window.MdBridge.openDiagramImage) {
            window.MdBridge.openDiagramImage(data);
          }
        };
        div.innerHTML = "";
        div.appendChild(out);
      } catch (e) {
      } finally {
        URL.revokeObjectURL(blobUrl);
      }
    };
    img.onerror = function () {
      URL.revokeObjectURL(blobUrl);
    };
    img.src = blobUrl;
    return true;
  } catch (e) {
    return false;
  }
}

/* 让图表容器跟着 SVG 真正长高:WebView 里 SVG 的百分比高度经常算成 0,
 * 容器又是一个横向滚动盒子,结果图被裁在一条很矮的框里。这里用 getBBox()
 * 取图形真实比例,写死像素宽高,并同步设置容器高度。 */
function fitDiagramBox(div, svg) {
  setTimeout(function () {
    if (!div.parentNode || !svg) return;
    var ratio = 0.6;
    try {
      var bb = svg.getBBox();
      if (bb && bb.width > 0 && bb.height > 0) ratio = bb.height / bb.width;
    } catch (e) { /* 忽略,用 viewBox 兜底 */ }
    if (ratio <= 0.05) {
      var vb = (svg.getAttribute("viewBox") || "").split(/[\s,]+/).map(Number);
      if (vb.length === 4 && vb[2] > 0 && vb[3] > 0) ratio = vb[3] / vb[2];
    }
    var cw = div.clientWidth || div.offsetWidth || 320;
    var maxW = Math.max(160, cw - 16);
    var maxH = Math.max(200, window.innerHeight * 0.65);
    var w = maxW;
    var h = w * ratio;
    if (h > maxH) {
      h = maxH;
      w = h / ratio;
    }
    w = Math.round(w);
    h = Math.round(h);
    svg.setAttribute("width", w);
    svg.setAttribute("height", h);
    svg.style.width = w + "px";
    svg.style.height = h + "px";
    svg.style.maxWidth = "100%";
    div.style.height = (h + 16) + "px";
  }, 250);
}

function renderMermaid(root) {
  if (!mermaidReady) return;
  var blocks = root.querySelectorAll("pre.mermaid-src code.language-mermaid");
  Array.prototype.forEach.call(blocks, function (code) {
    var pre = code.parentNode;
    if (!pre || pre.dataset.rendered === "1") return;
    pre.dataset.rendered = "1";
    var src = sanitizeMermaid(code.textContent);
    var id = "mmd" + (++mermaidSeq);
    try {
      Promise.resolve(mermaid.render(id, src)).then(function (res) {
        var svg = res && res.svg ? res.svg : (typeof res === "string" ? res : "");
        if (!svg) throw new Error("empty");
        var div = document.createElement("div");
        div.className = "mermaid-diagram";
        div.innerHTML = svg;
        div.querySelectorAll("text, tspan").forEach(function (el) {
          var t = el.textContent || "";
          if (t.indexOf("&") < 0) return;
          var decoded = t
            .replace(/&quot;/g, '"')
            .replace(/&apos;/g, "'")
            .replace(/&lt;/g, "<")
            .replace(/&gt;/g, ">")
            .replace(/&amp;/g, "&");
          // 为兼容 Mermaid 语法自动加的引号不必显示出来
          if (/^"[^"]*"$/.test(decoded)) decoded = decoded.slice(1, -1);
          el.textContent = decoded;
        });
        div.dataset.src = src;
        if (pre.parentNode) pre.parentNode.replaceChild(div, pre);
        var svgEl = div.querySelector("svg");
        if (!rasterizeDiagram(div, svgEl, src) && svgEl) {
          fitDiagramBox(div, svgEl);
        }
      }).catch(function (err) {
        pre.dataset.rendered = "error";
        var tip = document.createElement("div");
        tip.className = "mermaid-error";
        tip.textContent = "图表语法无法解析,已按源码展示";
        if (pre.parentNode) pre.parentNode.insertBefore(tip, pre.nextSibling);
      });
    } catch (e) { /* 忽略 */ }
  });
}

/* ---------- 图表原位缩放:点一下进入,双指缩放/单指拖动,再点一下复位 ---------- */
var activeDiagram = null;
var dState = { scale: 1, x: 0, y: 0, lastDist: 0, lastX: 0, lastY: 0 };

function diagramDistance(e) {
  return Math.hypot(
    e.touches[0].clientX - e.touches[1].clientX,
    e.touches[0].clientY - e.touches[1].clientY
  );
}

function applyDiagramTransform(host) {
  var svg = host.querySelector("svg");
  if (!svg) return;
  svg.style.transformOrigin = "center center";
  svg.style.transform = "translate(" + dState.x + "px," + dState.y + "px) scale(" +
    dState.scale + ")";
}

function resetDiagramZoom() {
  if (!activeDiagram) return;
  var host = activeDiagram;
  activeDiagram = null;
  host.classList.remove("diagram-zoomed");
  var svg = host.querySelector("svg");
  if (svg) {
    svg.style.transform = "";
    svg.style.transformOrigin = "";
  }
  var hint = host.querySelector(".diagram-zoom-hint");
  if (hint) hint.parentNode.removeChild(hint);
  if (host._zoomHandlers) {
    host.removeEventListener("touchstart", host._zoomHandlers.start);
    host.removeEventListener("touchmove", host._zoomHandlers.move);
    host.removeEventListener("touchend", host._zoomHandlers.end);
    host._zoomHandlers = null;
  }
}

function toggleDiagramZoom(host) {
  if (activeDiagram === host) {
    resetDiagramZoom();
    return;
  }
  resetDiagramZoom();
  activeDiagram = host;
  dState = { scale: 1, x: 0, y: 0, lastDist: 0, lastX: 0, lastY: 0 };
  host.classList.add("diagram-zoomed");

  var hint = document.createElement("div");
  hint.className = "diagram-zoom-hint";
  hint.textContent = "双指缩放 · 拖动平移 · 单击复位";
  host.appendChild(hint);

  function start(e) {
    if (e.touches.length === 2) {
      dState.lastDist = diagramDistance(e);
    } else if (e.touches.length === 1) {
      dState.lastX = e.touches[0].clientX;
      dState.lastY = e.touches[0].clientY;
    }
    e.preventDefault();
  }

  function move(e) {
    host._lastGestureAt = Date.now();
    if (e.touches.length === 2) {
      var dist = diagramDistance(e);
      if (dState.lastDist > 0) {
        dState.scale = Math.min(6, Math.max(1, dState.scale * dist / dState.lastDist));
        dState.lastDist = dist;
        applyDiagramTransform(host);
      }
    } else if (e.touches.length === 1 && dState.scale > 1) {
      dState.x += e.touches[0].clientX - dState.lastX;
      dState.y += e.touches[0].clientY - dState.lastY;
      dState.lastX = e.touches[0].clientX;
      dState.lastY = e.touches[0].clientY;
      applyDiagramTransform(host);
    }
    e.preventDefault();
  }

  function end(e) {
    if (e.touches.length === 0) dState.lastDist = 0;
  }

  host.addEventListener("touchstart", start, { passive: false });
  host.addEventListener("touchmove", move, { passive: false });
  host.addEventListener("touchend", end);
  host._zoomHandlers = { start: start, move: move, end: end };
}

/* ---------- 消息渲染 ---------- */

function reasoningBlock(reasoning, open) {
  return '<div class="reasoning">' +
    '<div class="reasoning-header" onclick="toggleReasoning(this)">' +
    '思考过程 <span class="arrow">' + (open ? "▾" : "▸") + '</span></div>' +
    '<div class="reasoning-body" style="' + (open ? "" : "display:none;") + '">' +
    esc(reasoning) + '</div></div>';
}

/* 用量行:AI 气泡显示输入/输出/缓存命中/费用,用户气泡只显示本轮输入 token */
function fmtTok(n) {
  n = Number(n) || 0;
  if (n >= 1000000) return (n / 1000000).toFixed(1) + "M";
  if (n >= 1000) return (n / 1000).toFixed(1) + "K";
  return String(n);
}

function usageLineHtml(m, isUser) {
  if (!isUser && m.usage) {
    try {
      var u = JSON.parse(m.usage);
      var parts = ["输入 " + fmtTok(u.prompt), "输出 " + fmtTok(u.completion)];
      if (u.prompt > 0) {
        parts.push("缓存命中 " + Math.round((u.cached || 0) * 100 / u.prompt) + "%");
      }
      if (typeof u.cost === "number") parts.push("本轮 ¥" + u.cost.toFixed(4));
      return '<div class="usage-line">' + parts.join(" · ") + '</div>';
    } catch (e) { return ""; }
  }
  if (isUser && m.promptTokens > 0) {
    return '<div class="usage-line">输入 ' + fmtTok(m.promptTokens) + ' tokens</div>';
  }
  return "";
}

function messageHtml(m) {
  var isUser = m.role === "user";
  var isError = m.status === "error";
  var inner = "";
  // 文件:优先多文件 JSON,兼容旧版单附件字段
  var files = [];
  try { files = m.filesJson ? JSON.parse(m.filesJson) : []; } catch (e) { files = []; }
  if ((!files || !files.length) && m.attachmentName) {
    files = [{ name: m.attachmentName }];
  }
  (files || []).forEach(function (f) {
    if (f && f.name) inner += '<div class="file-chip">📄 ' + esc(f.name) + '</div>';
  });

  // 图片:优先多图 JSON,兼容旧版单图字段;统一缩略图展示,点击看大图
  var imgs = [];
  try { imgs = m.imagesJson ? JSON.parse(m.imagesJson) : []; } catch (e) { imgs = []; }
  if ((!imgs || !imgs.length) && m.image) imgs = [{ base64: m.image, mime: "image/jpeg" }];
  if (imgs && imgs.length) {
    var imgsHtml = "";
    imgs.forEach(function (img, idx) {
      if (!img || !img.base64) return;
      var mime = img.mime || "image/jpeg";
      imgsHtml += '<img class="msg-image" src="data:' + mime + ';base64,' + img.base64 +
        '" alt="图片" onclick="openImage(\'' + m.id + '\', ' + idx + ')">';
    });
    if (imgsHtml) inner += '<div class="msg-images">' + imgsHtml + '</div>';
  }
  if (m.reasoning) inner += reasoningBlock(m.reasoning, false);
  if (isError) {
    inner += '<div class="content">' + esc(m.content) + '</div>';
    inner += '<div class="retry-row"><button class="retry-btn" onclick="retry(\'' +
      m.id + '\')">重试</button></div>';
  } else {
    inner += '<div class="content">' + renderMarkdown(m.content) + '</div>';
  }
  inner += usageLineHtml(m, isUser);
  if (!isUser && !isError) inner += messageActions(m);
  var html = '<div class="row ' +
    (isUser ? "user" : (isError ? "error" : "assistant")) + '" id="m' + m.id + '">';
  html += '<div class="bubble">' + inner + '</div>';
  if (isUser) html += '<button class="msg-menu" onclick="menu(\'' + m.id + '\')">⋮</button>';
  html += '</div>';
  return html;
}

/* 点击图片交给原生层放大查看 */
function openImage(id, index) {
  if (window.MdBridge && window.MdBridge.onImage) {
    window.MdBridge.onImage(String(id), String(index == null ? 0 : index));
  }
}

/* ---------- 消息操作(气泡底部按钮) ---------- */
var messageStore = {};

function stripMarkdown(text) {
  var t = String(text == null ? "" : text);
  t = t.replace(/```[^\n]*\n/g, "").replace(/```/g, "");
  t = t.replace(/!\[([^\]]*)\]\([^)]*\)/g, "$1");
  t = t.replace(/\[([^\]]+)\]\(([^)]+)\)/g, "$1 ($2)");
  t = t.replace(/^#{1,6}\s*/gm, "");
  t = t.replace(/^>\s?/gm, "");
  t = t.replace(/^\s*[-*+]\s+/gm, "· ");
  t = t.replace(/\*\*|__|~~|\*|`/g, "");
  t = t.replace(/\n{3,}/g, "\n\n");
  return t.trim();
}

function copyMessage(id, asMarkdown) {
  var m = messageStore[id];
  if (!m) return;
  var text = asMarkdown ? m.content : stripMarkdown(m.content);
  if (window.MdBridge && window.MdBridge.copyText) window.MdBridge.copyText(text);
}

function messageActions(m) {
  return '<div class="msg-actions">' +
    '<button onclick="copyMessage(\'' + m.id + '\', false)">复制全文</button>' +
    '<button onclick="copyMessage(\'' + m.id + '\', true)">复制 Markdown</button>' +
    '<button onclick="retry(\'' + m.id + '\')">重新生成</button>' +
    '</div>';
}

/* ---------- 流式 ---------- */

var streamingText = "";
var streamingReasoning = "";
/* 流式渲染代际:收尾后取消尚未执行的帧回调,防止又建出一个空流式行 */
var streamGen = 0;
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
    row.innerHTML = '<div class="bubble"></div>';
    // 挂在 #stream 里:setMessages 只重写 #chat,流式行不会被擦掉
    streamEl.appendChild(row);
  }
  var bubble = row.querySelector(".bubble");

  // 呼吸点
  var dot = bubble.querySelector(".streaming-dot");
  if (!streamPendingClear) {
    if (!dot) {
      dot = document.createElement("span");
      dot.className = "streaming-dot";
      bubble.insertBefore(dot, bubble.firstChild);
    }
  } else if (dot) {
    dot.parentNode.removeChild(dot);
  }

  // 思考过程:只更新文本,不再每帧重建
  var rBlock = bubble.querySelector(".reasoning");
  if (streamingReasoning) {
    if (!rBlock) {
      rBlock = document.createElement("div");
      rBlock.className = "reasoning";
      rBlock.innerHTML = '<div class="reasoning-header" onclick="toggleReasoning(this)">' +
        '思考过程 <span class="arrow">\u25be</span></div><div class="reasoning-body"></div>';
      bubble.appendChild(rBlock);
    }
    var body = rBlock.querySelector(".reasoning-body");
    if (body) body.textContent = streamingReasoning;
  } else if (rBlock) {
    rBlock.parentNode.removeChild(rBlock);
  }

  // 正文:只重写内容块
  var contentEl = bubble.querySelector(".content");
  if (!contentEl) {
    contentEl = document.createElement("div");
    contentEl.className = "content";
    bubble.appendChild(contentEl);
  }
  contentEl.innerHTML = renderMarkdown(streamingText);
  addCopyButtons(bubble);
  if (!streamPendingClear) appendCaret(contentEl);
  if (wasPinned) scrollToBottom(false);
  reportScroll();
}

/* 全量重置流式内容:开流、页面重载后恢复、文本回退时用(增量对不上就走这条) */
function resetStreaming(text, reasoning) {
  streamGen++;
  if (streamClearTimer) {
    clearTimeout(streamClearTimer);
    streamClearTimer = null;
  }
  streamPendingClear = false;
  streamingText = String(text == null ? "" : text);
  streamingReasoning = String(reasoning == null ? "" : reasoning);
  renderStreaming();
}

var lastStreamRenderAt = 0;
function updateStreaming(textDelta, reasoningDelta) {
  streamingText += (textDelta || "");
  streamingReasoning += (reasoningDelta || "");
  if (renderScheduled) return;
  renderScheduled = true;
  var gen = streamGen;
  // 长回答别每帧重排:文本越长间隔越大,滚动更稳
  var interval = streamingText.length > 12000 ? 160 :
    (streamingText.length > 4000 ? 110 : 60);
  var wait = Math.max(0, interval - (Date.now() - lastStreamRenderAt));
  setTimeout(function () {
    requestAnimationFrame(function () {
      renderScheduled = false;
      if (gen !== streamGen) return;   // 已收尾/已重开,丢弃过期帧
      lastStreamRenderAt = Date.now();
      renderStreaming();
    });
  }, wait);
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
  streamGen++;
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

function setFontSize(px) {
  var n = Number(px);
  if (!n || n < 10 || n > 30) return;
  document.body.style.fontSize = n + "px";
}

function setTheme(dark, userBg, assistantBg, userFg, assistantFg, errorBg, errorFg) {
  document.body.classList.toggle("dark", !!dark);
  document.body.style.color = dark ? "#E6E1E5" : "#1C1B1F";
  if (mermaidReady) {
    try {
      mermaid.initialize({
        startOnLoad: false, securityLevel: "loose",
        theme: dark ? "dark" : "default", fontFamily: "inherit",
        htmlLabels: false,
        flowchart: { htmlLabels: false },
        class: { htmlLabels: false },
        state: { htmlLabels: false }
      });
    } catch (e) { /* 忽略主题重初始化失败 */ }
  }
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

function appendMessage(payload) {
  var m;
  try {
    m = typeof payload === "string" ? JSON.parse(payload) : payload;
  } catch (e) { return; }
  if (!m || m.id == null) return;
  messageStore[m.id] = m;
  var tmp = document.createElement("div");
  tmp.innerHTML = messageHtml(m);
  var row = tmp.firstElementChild;
  if (!row) return;
  chatEl.appendChild(row);
  wrapTables(row);
  addCopyButtons(row);
  renderMermaid(row);
  removeStreamingRow();
  if (prevFirstId == null) prevFirstId = m.id;
  if (isPinned()) scrollToBottom(false);
  reportScroll();
}

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
  messageStore = {};
  for (var i = 0; i < list.length; i++) {
    messageStore[list[i].id] = list[i];
    html += messageHtml(list[i]);
  }
  chatEl.innerHTML = html;
  wrapTables(chatEl);
  addCopyButtons(chatEl);
  renderMermaid(chatEl);

  /* 流式行与正式气泡的交接:两者由 Kotlin 侧两条独立状态(messages / streamingText)
   * 推送,先后到达顺序不定。只要列表末尾已经是本次流式内容(说明已落库),
   * 就立刻收掉流式行 —— 否则会并存出现"两个回答气泡"。
   * 反之(内容还没回流)保持流式行显示,避免回答闪没了再冒出来。 */
  var tail = list.length ? list[list.length - 1] : null;
  var landed = !!tail && tail.role !== "user" && streamingText.length > 0 &&
    String(tail.content == null ? "" : tail.content) === streamingText;
  if ((tail && tail.role !== "user") || landed || streamPendingClear) removeStreamingRow();

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
