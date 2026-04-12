let refs = null;
let sessionCount = 0;
let currentSessionId = null;
const sessionStore = new Map(); // sessionId -> { html, status }

document.addEventListener("DOMContentLoaded", () => {
    refs = {
        chatFeed:    document.getElementById("chatFeed"),
        question:    document.getElementById("question"),
        submitBtn:   document.getElementById("submitBtn"),
        newChatBtn:  document.getElementById("newChatBtn"),
        toggleBtn:   document.getElementById("toggleBtn"),
        sidebar:     document.getElementById("sidebar"),
        historyList: document.getElementById("historyList"),
        status:      document.getElementById("status")
    };

    const required = ["chatFeed", "question", "submitBtn", "newChatBtn", "toggleBtn", "sidebar", "historyList", "status"];
    const missing = required.filter((k) => !refs[k]);
    if (missing.length) {
        console.error("ACAI UI missing required elements:", missing.join(", "));
        return;
    }

    // 在移动端默认折叠侧边栏
    if (window.innerWidth <= 760) {
        refs.sidebar.classList.add("sidebar--collapsed");
    }

    // 折叠/展开侧边栏
    refs.toggleBtn.addEventListener("click", () => {
        refs.sidebar.classList.toggle("sidebar--collapsed");
    });

    // 新对话
    refs.newChatBtn.addEventListener("click", () => {
        startNewSession();
    });

    // 发送
    refs.submitBtn.addEventListener("click", async () => {
        await sendMessage();
    });

    // 回车发送
    refs.question.addEventListener("keydown", (event) => {
        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            refs.submitBtn.click();
        }
    });

    // 初始化第一个会话
    startNewSession(true);
});

/* ── Session management ────────────────────── */

function startNewSession(silent = false) {
    // 保存当前会话内容
    if (currentSessionId !== null) {
        sessionStore.set(currentSessionId, {
            html: refs.chatFeed.innerHTML,
            status: refs.status.textContent
        });
    }

    sessionCount++;
    currentSessionId = sessionCount;

    refs.question.value = "";
    refs.chatFeed.innerHTML = "";

    if (!silent) {
        appendBubble("assistant", "已开始新对话，请输入你的问题。", "ACAI");
    } else {
        appendBubble("assistant", "你好，请输入你的数据问题。", "ACAI");
    }

    setStatus("等待输入", false);

    // 保存初始内容
    sessionStore.set(currentSessionId, {
        html: refs.chatFeed.innerHTML,
        status: "等待输入"
    });

    // 添加到历史列表
    const idx = sessionCount;
    const item = document.createElement("li");
    item.className = "history-item is-active";
    item.dataset.sessionId = idx;
    item.innerHTML = `
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
        <span class="history-item-label">对话 ${idx}</span>
    `;

    item.addEventListener("click", () => {
        const targetId = parseInt(item.dataset.sessionId, 10);
        if (targetId === currentSessionId) return; // 已在当前会话

        // 保存当前会话内容
        sessionStore.set(currentSessionId, {
            html: refs.chatFeed.innerHTML,
            status: refs.status.textContent
        });

        // 切换到目标会话
        currentSessionId = targetId;
        const saved = sessionStore.get(targetId);
        if (saved) {
            refs.chatFeed.innerHTML = saved.html;
            setStatus(saved.status, false);
            refs.chatFeed.scrollTop = refs.chatFeed.scrollHeight;
        }

        // 更新高亮
        document.querySelectorAll(".history-item").forEach(el => el.classList.remove("is-active"));
        item.classList.add("is-active");

        // 移动端点击历史后自动收起侧边栏
        if (window.innerWidth <= 760) {
            refs.sidebar.classList.add("sidebar--collapsed");
        }
    });

    // 将新会话插在顶部，并高亮
    document.querySelectorAll(".history-item").forEach(el => el.classList.remove("is-active"));
    refs.historyList.insertBefore(item, refs.historyList.firstChild);
}

function updateCurrentSessionLabel(label) {
    const active = refs.historyList.querySelector(".history-item.is-active .history-item-label");
    if (active && active.textContent.startsWith("对话")) {
        active.textContent = label.length > 20 ? label.slice(0, 20) + "…" : label;
    }
}

/* ── Send ──────────────────────────────────── */

async function sendMessage() {
    const question = refs.question.value.trim();
    if (!question) {
        setStatus("请输入问题", true);
        return;
    }

    refs.question.value = "";
    lockUi(true);
    setStatus("正在执行查询...", false);
    appendBubble("user", question, "YOU");
    updateCurrentSessionLabel(question);

    try {
        const resp = await fetch("/api/query", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ question })
        });

        const data = await resp.json();
        const summary = !resp.ok || !data.success ? (data.message || "查询失败") : "查询成功";
        appendAssistantResult(summary, data);

        if (!resp.ok || !data.success) {
            setStatus(data.message || "查询失败", true);
        } else {
            setStatus("查询成功", false);
        }
    } catch (err) {
        const msg = err instanceof Error ? err.message : String(err);
        appendBubble("assistant", `请求失败: ${msg}`, "ACAI");
        setStatus(`请求失败: ${msg}`, true);
    } finally {
        lockUi(false);
        // 更新当前会话缓存
        sessionStore.set(currentSessionId, {
            html: refs.chatFeed.innerHTML,
            status: refs.status.textContent
        });
    }
}

/* ── UI helpers ────────────────────────────── */

function lockUi(locked) {
    if (!refs?.submitBtn) return;
    refs.submitBtn.disabled = locked;
    refs.submitBtn.textContent = locked ? "查询中..." : "发送";
}

function setStatus(message, isError) {
    if (!refs?.status) return;
    refs.status.textContent = message;
    refs.status.style.color = isError ? "#c22d35" : "#476275";
}

function appendBubble(type, text, role) {
    if (!refs?.chatFeed) return;

    const bubble = document.createElement("article");
    bubble.className = `bubble ${type}`;

    const roleNode = document.createElement("p");
    roleNode.className = "bubble-role";
    roleNode.textContent = role;

    const textNode = document.createElement("p");
    textNode.textContent = text;

    bubble.appendChild(roleNode);
    bubble.appendChild(textNode);
    refs.chatFeed.appendChild(bubble);
    refs.chatFeed.scrollTop = refs.chatFeed.scrollHeight;
}

function appendAssistantResult(summary, data) {
    if (!refs?.chatFeed) return;

    const bubble = document.createElement("article");
    bubble.className = "bubble assistant";

    bubble.innerHTML = `<p class="bubble-role">ACAI</p><p>${escapeHtml(summary)}</p>`;

    const metrics = document.createElement("p");
    metrics.className = "bubble-metrics";
    metrics.textContent = `MQL ${data.mqlMs ?? 0} ms | SQL ${data.sqlMs ?? 0} ms | DB ${data.dbMs ?? 0} ms | Total ${data.totalMs ?? 0} ms`;
    bubble.appendChild(metrics);

    const mql = document.createElement("pre");
    mql.className = "bubble-code";
    mql.textContent = `MQL:\n${data.mql || "-"}`;
    bubble.appendChild(mql);

    const sql = document.createElement("pre");
    sql.className = "bubble-code";
    sql.textContent = `SQL:\n${data.sql || "-"}`;
    bubble.appendChild(sql);

    if (Array.isArray(data.columns) && data.columns.length > 0) {
        const tableWrap = document.createElement("div");
        tableWrap.className = "bubble-table-wrap";

        const table = document.createElement("table");
        const thead = document.createElement("thead");
        const tbody = document.createElement("tbody");

        const trHead = document.createElement("tr");
        data.columns.forEach((c) => {
            const th = document.createElement("th");
            th.textContent = c;
            trHead.appendChild(th);
        });
        thead.appendChild(trHead);

        (data.rows || []).forEach((row) => {
            const tr = document.createElement("tr");
            row.forEach((cell) => {
                const td = document.createElement("td");
                td.textContent = cell;
                tr.appendChild(td);
            });
            tbody.appendChild(tr);
        });

        table.appendChild(thead);
        table.appendChild(tbody);
        tableWrap.appendChild(table);
        bubble.appendChild(tableWrap);
    }

    refs.chatFeed.appendChild(bubble);
    refs.chatFeed.scrollTop = refs.chatFeed.scrollHeight;
}

function escapeHtml(text) {
    return String(text)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
}
