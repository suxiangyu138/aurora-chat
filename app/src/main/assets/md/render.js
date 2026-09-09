/* Markdown + KaTeX + highlight.js 渲染胶水
 * 供 Android WebView 调用:
 *   window.setContent(markdownText, textColorCss, darkTheme)
 * 渲染后通过 window.MdBridge.onHeight(px) 上报内容高度。 */

var md = window.markdownit({
  html: false,          // 禁用原始 HTML,防注入
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

/* 哨兵字符(私有区,正文几乎不可能出现) */
var SENTINEL = "";
var sentinelPattern = new RegExp(SENTINEL + "(\\d+)" + SENTINEL, "g");

/* 数学公式占位:$$块级$$ 与 $行内$(代码块内容不受影响) */
function extractMath(text, items) {
  // 先保护代码块
  var codeFence = /```[^\n]*\n[\s\S]*?```/g;
  text = text.replace(codeFence, function (m) {
    items.push({ type: "code", content: m });
    return SENTINEL + (items.length - 1) + SENTINEL;
  });
  // 块级公式
  text = text.replace(/\$\$([\s\S]+?)\$\$/g, function (m, p1) {
    items.push({ type: "block", content: p1.trim() });
    return SENTINEL + (items.length - 1) + SENTINEL;
  });
  // 行内公式(不跨行)
  text = text.replace(/\$([^\$\n]+?)\$/g, function (m, p1) {
    items.push({ type: "inline", content: p1.trim() });
    return SENTINEL + (items.length - 1) + SENTINEL;
  });
  // \(...\) 与 \[...\] 兼容
  text = text.replace(/\\\[([\s\S]+?)\\\]/g, function (m, p1) {
    items.push({ type: "block", content: p1.trim() });
    return SENTINEL + (items.length - 1) + SENTINEL;
  });
  text = text.replace(/\\\(([^\\\n]+?)\\\)/g, function (m, p1) {
    items.push({ type: "inline", content: p1.trim() });
    return SENTINEL + (items.length - 1) + SENTINEL;
  });
  return text;
}

function renderCodeFence(fence) {
  // ```lang\ncode``` → 高亮代码块
  var lines = fence.split("\n");
  var lang = lines[0].replace(/^```/, "").trim();
  var code = lines.slice(1).join("\n").replace(/```\s*$/, "");
  if (lang && hljs.getLanguage(lang)) {
    try {
      return '<pre class="hljs"><code>' +
        hljs.highlight(code, { language: lang, ignoreIllegals: true }).value +
        '</code></pre>';
    } catch (e) { /* ignore */ }
  }
  return '<pre class="hljs"><code>' + md.utils.escapeHtml(code) + '</code></pre>';
}

function setContent(markdownText, textColor, darkTheme) {
  var body = document.body;
  body.style.color = textColor || (darkTheme ? "#e6e1e5" : "#1c1b1f");
  if (darkTheme) {
    body.setAttribute("data-dark", "1");
  } else {
    body.removeAttribute("data-dark");
  }

  var items = [];
  var text = extractMath(String(markdownText == null ? "" : markdownText), items);

  var html = md.render(text);

  // 还原公式与代码块
  html = html.replace(sentinelPattern, function (m, index) {
    var item = items[parseInt(index, 10)];
    if (!item) return "";
    if (item.type === "code") return renderCodeFence(item.content);
    try {
      return katex.renderToString(item.content, {
        throwOnError: false,
        displayMode: item.type === "block"
      });
    } catch (e) {
      return md.utils.escapeHtml(item.content);
    }
  });

  document.getElementById("content").innerHTML = html;
  addCopyButtons();
  reportHeight();
  // 字体/图片异步加载后高度可能变化,延迟再报一次
  setTimeout(reportHeight, 200);
}

/* 为每个代码块附加复制按钮 */
function addCopyButtons() {
  var pres = document.querySelectorAll("pre");
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
        if (window.MdBridge) {
          window.MdBridge.copyText(text);
        }
        var self = this;
        self.textContent = "已复制";
        setTimeout(function () { self.textContent = "复制"; }, 1500);
      };
    })(pre);
    pre.appendChild(btn);
  }
}

function reportHeight() {
  if (window.MdBridge) {
    window.MdBridge.onHeight(document.body.scrollHeight);
  }
}
