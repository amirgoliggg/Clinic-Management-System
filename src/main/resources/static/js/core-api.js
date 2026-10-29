let currentUser = null;
const API_BASE = '';

async function apiFetch(path, options = {}) {
    const headers = new Headers(options.headers || {});
    const method = (options.method || 'GET').toUpperCase();
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
        const response = await fetch(API_BASE + '/api/auth/csrf', {credentials: 'same-origin', cache: 'no-store'});
        if (!response.ok) throw new Error('دریافت توکن امنیتی ناموفق بود. لطفاً دوباره تلاش کنید.');
        const csrf = await response.json();
        headers.set(csrf.headerName, csrf.token);
    }
    const response = await fetch(API_BASE + path, {...options, headers, credentials: 'same-origin'});
    if (response.status === 401 && currentUser) {
        currentUser = null;
        document.getElementById('auth-overlay').style.display = 'flex';
        document.getElementById('current-user-name').textContent = '-';
        document.getElementById('current-user-role').textContent = '-';
    }
    if (!response.ok && !(response.headers.get('Content-Type') || '').includes('application/json')) {
        const message = response.status === 403 ? 'مجوز دسترسی یا توکن امنیتی معتبر نیست.'
                : response.status === 401 ? 'لطفاً دوباره وارد سامانه شوید.' : 'پردازش درخواست ناموفق بود.';
        return new Response(JSON.stringify({status: response.status, message}), {
            status: response.status, headers: {'Content-Type': 'application/json'}
        });
    }
    return response;
}

// واکنش فیزیکی بازتاب نور شیشه مایع (iOS 27 Specular Spotlight) به حرکت ماوس
document.addEventListener('mousemove', (e) => {
    document.querySelectorAll('.sub-box, .kpi-card, .step-item').forEach(card => {
        const rect = card.getBoundingClientRect();
        card.style.setProperty('--mouse-x', `${e.clientX - rect.left}px`);
        card.style.setProperty('--mouse-y', `${e.clientY - rect.top}px`);
    });
});

function updateClock() {
    const now = new Date();
    const clockEl = document.getElementById('live-jalali-clock');
    if (!clockEl) return;
    try {
        const dateStr = new Intl.DateTimeFormat('fa-IR', {weekday: 'short', year: 'numeric', month: 'short', day: 'numeric'}).format(now);
        const timeStr = now.toLocaleTimeString('fa-IR');
        clockEl.textContent = `${dateStr} • ${timeStr}`;
    } catch (e) {
        clockEl.textContent = now.toLocaleTimeString();
    }
}
setInterval(updateClock, 1000);
updateClock();

function toggleTheme() {
    const root = document.documentElement;
    const next = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    root.setAttribute('data-theme', next);
    localStorage.setItem('clinic_theme', next);
    const btn = document.getElementById('theme-btn');
    if (btn) btn.textContent = next === 'dark' ? '☀️ تم روشن' : '🌙 تم تیره';
}

(function initTheme() {
    const saved = localStorage.getItem('clinic_theme') || 'dark';
    document.documentElement.setAttribute('data-theme', saved);
    const btn = document.getElementById('theme-btn');
    if (btn) btn.textContent = saved === 'dark' ? '☀️ تم روشن' : '🌙 تم تیره';
})();

function flashInput(id) {
    const el = document.getElementById(id);
    if (!el) return;
    el.classList.remove('input-flash');
    void el.offsetWidth;
    el.classList.add('input-flash');
    el.focus();
}

function showToast(msg, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;
    const toast = document.createElement('div');
    toast.className = 'toast ' + (type === 'error' ? 'toast-error' : (type === 'success' ? 'toast-success' : ''));
    const icon = type === 'error' ? '⚠️' : (type === 'success' ? '✅' : '✨');
    toast.textContent = `${icon} ${msg}`;
    container.appendChild(toast);
    setTimeout(() => toast.remove(), 4200);
}

function log(msg, isError = false) {
    showToast(msg, isError ? 'error' : 'success');
    const consoleEl = document.getElementById('console-log');
    if (!consoleEl) return;

    const line = document.createElement('div');
    line.className = 'log-line ' + (isError ? 'log-err' : 'log-ok');

    const dot = document.createElement('span');
    dot.className = 'log-status-dot';

    const timePill = document.createElement('span');
    timePill.className = 'log-time-pill';
    timePill.textContent = new Date().toLocaleTimeString('en-US', {hour12: true});

    const textSpan = document.createElement('span');
    textSpan.style.flex = '1';
    textSpan.textContent = msg;

    line.appendChild(dot);
    line.appendChild(timePill);
    line.appendChild(textSpan);
    consoleEl.prepend(line);
}

function clearConsoleLog() {
    const consoleEl = document.getElementById('console-log');
    if (consoleEl) consoleEl.replaceChildren();
}

function createCell(text, style = '') {
    const td = document.createElement('td');
    td.textContent = (text !== null && text !== undefined) ? String(text) : '-';
    if (style) td.style.cssText = style;
    return td;
}

function formatMoney(val) {
    const num = parseFloat(val);
    if (isNaN(num)) return '0.00 $';
    return num.toLocaleString('en-US', {minimumFractionDigits: 2, maximumFractionDigits: 2}) + ' $';
}

function filterTable(tbodyId, query) {
    const q = (query || '').trim().toLowerCase();
    document.querySelectorAll(`#${tbodyId} tr`).forEach(r => {
        r.style.display = r.textContent.toLowerCase().includes(q) ? '' : 'none';
    });
}

function closeModal(id) {
    const el = document.getElementById(id);
    if (el) el.style.display = 'none';
}