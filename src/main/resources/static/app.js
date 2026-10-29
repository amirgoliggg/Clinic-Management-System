let currentUser = null;
const API_BASE = '';

// تبدیل خودکار اعداد فارسی و عربی به انگلیسی در تمام فیلدها
function toEnglishDigits(str) {
    if (str === null || str === undefined) return '';
    return String(str)
        .replace(/[۰-۹]/g, d => '۰۱۲۳۴۵۶۷۸۹'.indexOf(d))
        .replace(/[٠-٩]/g, d => '٠١٢٣٤٥٦٧٨٩'.indexOf(d));
}

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

// بازتاب نور تعاملی روی کارت‌ها
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
    setTimeout(() => toast.remove(), 4500);
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
    const q = toEnglishDigits(query || '').trim().toLowerCase();
    document.querySelectorAll(`#${tbodyId} tr`).forEach(r => {
        r.style.display = r.textContent.toLowerCase().includes(q) ? '' : 'none';
    });
}

function closeModal(id) {
    const el = document.getElementById(id);
    if (el) el.style.display = 'none';
}

// ==================== موتور رسم نمودارهای برداری (ChartsEngine) ====================
const ChartsEngine = {
    PALETTE: ['#38bdf8', '#34d399', '#818cf8', '#fbbf24', '#fb7185', '#a78bfa', '#2dd4bf'],

    renderDonutChart(containerId, segments, centerTitle, centerSub) {
        const container = document.getElementById(containerId);
        if (!container) return;
        container.replaceChildren();

        const total = segments.reduce((acc, s) => acc + (parseFloat(s.value) || 0), 0);
        const safeTotal = total > 0 ? total : 1;

        const layout = document.createElement('div');
        layout.className = 'donut-widget-layout';

        const svgWrap = document.createElement('div');
        svgWrap.className = 'donut-svg-wrap';

        const svgNS = 'http://www.w3.org/2000/svg';
        const svg = document.createElementNS(svgNS, 'svg');
        svg.setAttribute('viewBox', '0 0 42 42');
        svg.style.width = '100%';
        svg.style.height = '100%';

        const bgCircle = document.createElementNS(svgNS, 'circle');
        bgCircle.setAttribute('cx', '21');
        bgCircle.setAttribute('cy', '21');
        bgCircle.setAttribute('r', '15.9155');
        bgCircle.setAttribute('fill', 'transparent');
        bgCircle.setAttribute('stroke', 'rgba(148, 163, 184, 0.15)');
        bgCircle.setAttribute('stroke-width', '4.2');
        svg.appendChild(bgCircle);

        let cumulativePercent = 0;
        segments.forEach((seg, idx) => {
            const val = parseFloat(seg.value) || 0;
            if (val <= 0) return;
            const pct = (val / safeTotal) * 100;
            const circle = document.createElementNS(svgNS, 'circle');
            circle.setAttribute('cx', '21');
            circle.setAttribute('cy', '21');
            circle.setAttribute('r', '15.9155');
            circle.setAttribute('fill', 'transparent');
            circle.setAttribute('stroke', seg.color || this.PALETTE[idx % this.PALETTE.length]);
            circle.setAttribute('stroke-width', '4.5');
            circle.setAttribute('stroke-dasharray', `${pct} ${100 - pct}`);
            circle.setAttribute('stroke-dashoffset', String(25 - cumulativePercent));
            circle.setAttribute('stroke-linecap', 'round');
            svg.appendChild(circle);
            cumulativePercent += pct;
        });

        const centerLabel = document.createElement('div');
        centerLabel.className = 'donut-center-label';
        const valEl = document.createElement('div');
        valEl.className = 'donut-center-val';
        valEl.textContent = centerTitle;
        const subEl = document.createElement('div');
        subEl.className = 'donut-center-sub';
        subEl.textContent = centerSub;
        centerLabel.appendChild(valEl);
        centerLabel.appendChild(subEl);

        svgWrap.appendChild(svg);
        svgWrap.appendChild(centerLabel);

        const legend = document.createElement('div');
        legend.className = 'chart-legend';

        segments.forEach((seg, idx) => {
            const row = document.createElement('div');
            row.className = 'legend-row';
            const left = document.createElement('span');
            const dot = document.createElement('span');
            dot.className = 'legend-dot';
            dot.style.background = seg.color || this.PALETTE[idx % this.PALETTE.length];
            left.appendChild(dot);
            left.appendChild(document.createTextNode(seg.label));

            const right = document.createElement('b');
            right.textContent = seg.formattedValue !== undefined ? seg.formattedValue : seg.value;

            row.appendChild(left);
            row.appendChild(right);
            legend.appendChild(row);
        });

        layout.appendChild(svgWrap);
        layout.appendChild(legend);
        container.appendChild(layout);
    },

    renderHorizontalBars(containerId, items, unitSuffix = '') {
        const container = document.getElementById(containerId);
        if (!container) return;
        container.replaceChildren();

        const maxVal = Math.max(1, ...items.map(i => parseFloat(i.value) || 0));

        items.forEach((item) => {
            const num = parseFloat(item.value) || 0;
            const pct = Math.min(100, Math.round((num / maxVal) * 100));

            const wrap = document.createElement('div');
            wrap.style.marginBottom = '10px';

            const top = document.createElement('div');
            top.className = 'method-bar-top';
            const lbl = document.createElement('span');
            lbl.textContent = item.label;
            const val = document.createElement('b');
            val.textContent = `${item.displayValue !== undefined ? item.displayValue : num}${unitSuffix}`;
            top.appendChild(lbl);
            top.appendChild(val);

            const track = document.createElement('div');
            track.className = 'progress-track';
            const fill = document.createElement('div');
            fill.className = 'progress-fill';
            fill.style.width = `${pct}%`;
            if (item.color) fill.style.background = item.color;
            track.appendChild(fill);

            wrap.appendChild(top);
            wrap.appendChild(track);
            container.appendChild(wrap);
        });
    },

    renderHashChainVisualizer(containerId, logs) {
        const container = document.getElementById(containerId);
        if (!container) return;
        container.replaceChildren();

        const recent = logs.slice(-6);
        recent.forEach((l, idx) => {
            if (idx > 0) {
                const arrow = document.createElement('div');
                arrow.className = 'hash-connector';
                arrow.textContent = '⟵';
                container.appendChild(arrow);
            }
            const node = document.createElement('div');
            node.className = 'hash-block-node';

            const top = document.createElement('div');
            top.style.cssText = 'display:flex; justify-content:space-between; font-size:0.75rem; margin-bottom:5px;';
            const seq = document.createElement('b');
            seq.style.color = 'var(--primary)';
            seq.textContent = `بلوک #${l.sequenceNumber}`;
            const st = document.createElement('span');
            st.style.color = l.status === 'SUCCESS' ? 'var(--success)' : 'var(--danger)';
            st.textContent = l.action;
            top.appendChild(seq);
            top.appendChild(st);

            const hashLine = document.createElement('div');
            hashLine.style.cssText = 'font-family:monospace; font-size:0.71rem; color:var(--text-sub); direction:ltr; text-align:left;';
            hashLine.textContent = `Hash: ${l.entryHash.substring(0, 14)}...`;

            const prevLine = document.createElement('div');
            prevLine.style.cssText = 'font-family:monospace; font-size:0.68rem; color:var(--text-muted); direction:ltr; text-align:left;';
            prevLine.textContent = `Prev: ${l.previousHash.substring(0, 14)}...`;

            node.appendChild(top);
            node.appendChild(hashLine);
            node.appendChild(prevLine);
            container.appendChild(node);
        });
    }
};

// ==================== احراز هویت و مدیریت نشست ====================
function showAuthError(msg) {
    const box = document.getElementById('auth-error-box');
    if (!box) return;
    box.textContent = msg;
    box.style.display = 'block';
}

function fillQuickCredentials(user, pass) {
    document.getElementById('login-user').value = user;
    document.getElementById('login-pass').value = pass;
    flashInput('login-user');
}

async function checkSession() {
    try {
        const res = await apiFetch('/api/auth/me');
        if (res.ok) {
            currentUser = await res.json();
            applyUserRole();
        } else {
            document.getElementById('auth-overlay').style.display = 'flex';
        }
    } catch (e) {
        document.getElementById('auth-overlay').style.display = 'flex';
    }
}

async function submitLogin() {
    document.getElementById('auth-error-box').style.display = 'none';
    const username = toEnglishDigits(document.getElementById('login-user').value).trim();
    const password = document.getElementById('login-pass').value;
    if (!username || !password) return showAuthError('نام کاربری و رمز عبور را وارد کنید.');

    try {
        const res = await apiFetch('/api/auth/login', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({username, password})
        });
        const data = await res.json();
        if (!res.ok) return showAuthError(data.message || 'خطا در ورود');

        currentUser = data;
        document.getElementById('login-pass').value = '';
        applyUserRole();
        log(`ورود موفق به ایستگاه کاری '${currentUser.username}': ${currentUser.fullName} (${currentUser.role})`);
    } catch (err) {
        showAuthError('سرور در دسترس نیست. مطمئن شوید برنامه در حال اجراست.');
    }
}

async function submitLogout() {
    try {
        const response = await apiFetch('/api/auth/logout', {method: 'POST'});
        if (!response.ok && response.status !== 401) throw new Error('خروج از نشست ناموفق بود.');
    } catch (err) {
        log(err.message || 'ارتباط با سرور برقرار نشد.', true);
        return;
    }
    currentUser = null;
    document.getElementById('auth-overlay').style.display = 'flex';
    log('از حساب کاربری خارج شدید.');
}

function applyUserRole() {
    document.getElementById('auth-overlay').style.display = 'none';
    document.getElementById('current-user-name').textContent = currentUser.fullName;
    document.getElementById('current-user-role').textContent = `${currentUser.role} (#${currentUser.username})`;

    const role = currentUser.role.toUpperCase();
    const navIntake = document.getElementById('nav-intake');
    const navClinical = document.getElementById('nav-clinical');
    const navBilling = document.getElementById('nav-billing');
    const navStaff = document.getElementById('nav-staff');
    const navAudit = document.getElementById('nav-audit');

    [navIntake, navClinical, navBilling, navStaff, navAudit].forEach(b => {
        if (b) b.style.display = 'none';
    });

    const posBox = document.getElementById('cashier-pos-box');
    if (posBox) posBox.style.display = role.includes('CASHIER') ? 'block' : 'none';

    const refBox = document.getElementById('admin-refund-box');
    if (refBox) refBox.style.display = (role.includes('SUPER_ADMIN') || role.includes('FINANCIAL_ADMIN')) ? 'block' : 'none';

    const nurseBox = document.getElementById('nurse-triage-box');
    if (nurseBox) nurseBox.style.display = role.includes('NURSE') ? 'block' : 'none';

    if (role.includes('SUPER_ADMIN')) {
        navIntake.style.display = 'flex';
        navBilling.style.display = 'flex';
        navStaff.style.display = 'flex';
        navAudit.style.display = 'flex';
        switchNav('staff');
    } else if (role.includes('AUDITOR')) {
        navAudit.style.display = 'flex';
        switchNav('audit');
    } else if (role.includes('HR_ADMIN')) {
        navStaff.style.display = 'flex';
        switchNav('staff');
    } else if (role.includes('FINANCIAL_ADMIN')) {
        navBilling.style.display = 'flex';
        switchNav('billing');
    } else if (role.includes('DOCTOR')) {
        navClinical.style.display = 'flex';
        switchNav('clinical');
    } else if (role.includes('CASHIER')) {
        navBilling.style.display = 'flex';
        navIntake.style.display = 'flex';
        switchNav('billing');
    } else {
        navIntake.style.display = 'flex';
        switchNav('intake');
    }
}

function switchNav(panel) {
    document.querySelectorAll('.nav-btn').forEach(btn => btn.classList.remove('active'));
    document.querySelectorAll('.section-panel').forEach(p => p.classList.remove('active'));

    const targetBtn = document.getElementById(`nav-${panel}`);
    const targetPanel = document.getElementById(`panel-${panel}`);
    if (targetBtn) targetBtn.classList.add('active');
    if (targetPanel) targetPanel.classList.add('active');

    if (panel === 'intake') {
        loadQueue();
        loadReadyForDischarge();
        evaluateLiveVitals();
    }
    if (panel === 'clinical') loadDoctorQueue();
    if (panel === 'billing') loadBillingAll();
    if (panel === 'staff') {
        // پاک کردن مقادیر Autofill مرورگر در فرم ثبت کارمند جدید
        const regUser = document.getElementById('reg-user');
        const regPass = document.getElementById('reg-pass');
        if (regUser && regUser.value === 'doctor') regUser.value = '';
        if (regPass) regPass.value = '';
        loadStaffList();
    }
    if (panel === 'audit') {
        loadAuditTrail();
        verifyChainIntegrity();
    }
}

// ==================== ۱. پذیرش، تریاژ پرستار و ترخیص ====================
function evaluateLiveVitals() {
    const bpRaw = toEnglishDigits(document.getElementById('t-bp')?.value || '120/80').trim();
    const temp = parseFloat(toEnglishDigits(document.getElementById('t-temp')?.value)) || 37.0;
    const hr = parseInt(toEnglishDigits(document.getElementById('t-hr')?.value), 10) || 76;

    const parts = bpRaw.split('/');
    const sys = parseInt(parts[0], 10) || 120;
    const dia = parseInt(parts[1], 10) || 80;

    const bpBadge = document.getElementById('vital-bp-status');
    if (bpBadge) {
        if (sys >= 140 || dia >= 90 || sys < 90) {
            bpBadge.className = 'vital-status-tag badge-danger';
            bpBadge.textContent = `غیرطبیعی (${sys}/${dia})`;
        } else if (sys >= 130 || dia >= 85) {
            bpBadge.className = 'vital-status-tag badge-warning';
            bpBadge.textContent = `مرزی (${sys}/${dia})`;
        } else {
            bpBadge.className = 'vital-status-tag badge-success';
            bpBadge.textContent = `نرمال (${sys}/${dia})`;
        }
    }

    const tempBadge = document.getElementById('vital-temp-status');
    if (tempBadge) {
        if (temp >= 38.0 || temp < 35.5) {
            tempBadge.className = 'vital-status-tag badge-danger';
            tempBadge.textContent = `تب / بحرانی (${temp.toFixed(1)}°C)`;
        } else if (temp >= 37.5) {
            tempBadge.className = 'vital-status-tag badge-warning';
            tempBadge.textContent = `ساب‌فبرایل (${temp.toFixed(1)}°C)`;
        } else {
            tempBadge.className = 'vital-status-tag badge-success';
            tempBadge.textContent = `طبیعی (${temp.toFixed(1)}°C)`;
        }
    }

    const hrBadge = document.getElementById('vital-hr-status');
    if (hrBadge) {
        if (hr > 100 || hr < 60) {
            hrBadge.className = 'vital-status-tag badge-warning';
            hrBadge.textContent = (hr > 100 ? `تاکی‌کاردی (${hr} bpm)` : `برادی‌کاردی (${hr} bpm)`);
        } else {
            hrBadge.className = 'vital-status-tag badge-success';
            hrBadge.textContent = `ریتم نرمال (${hr} bpm)`;
        }
    }
}

async function registerPatient() {
    const payload = {
        nationalId: toEnglishDigits(document.getElementById('p-nid').value).trim(),
        fullName: document.getElementById('p-name').value.trim(),
        age: parseInt(toEnglishDigits(document.getElementById('p-age').value), 10),
        contactNumber: toEnglishDigits(document.getElementById('p-phone').value).trim(),
        bloodType: document.getElementById('p-blood').value
    };

    try {
        const res = await apiFetch('/api/clinical/patients/register', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (!res.ok) throw data;
        log(`بیمار '${data.fullName}' با کد ملی ${data.nationalId} پذیرش شد.`);
        document.getElementById('p-nid').value = '';
        document.getElementById('p-name').value = '';
        loadQueue();
    } catch (err) {
        log(`خطا در پذیرش: ${err.message || 'اطلاعات نامعتبر است.'}`, true);
    }
}

async function submitNurseTriage() {
    const nid = toEnglishDigits(document.getElementById('t-nid').value).trim();
    if (!nid) return showToast('کد ملی بیمار را برای تریاژ وارد کنید.', 'error');

    const payload = {
        patientNationalId: nid,
        bloodPressure: toEnglishDigits(document.getElementById('t-bp').value).trim(),
        bodyTemperatureCelsius: parseFloat(toEnglishDigits(document.getElementById('t-temp').value)),
        heartRateBpm: parseInt(toEnglishDigits(document.getElementById('t-hr').value), 10),
        chiefComplaint: document.getElementById('t-complaint').value.trim(),
        nursingNotes: document.getElementById('t-notes').value.trim()
    };

    try {
        const res = await apiFetch('/api/clinical/records/triage', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (!res.ok) throw data;
        log(`تریاژ و علائم حیاتی بیمار ${nid} با موفقیت در پرونده ثبت شد.`);
        document.getElementById('t-complaint').value = '';
        document.getElementById('t-notes').value = '';
    } catch (err) {
        log(`خطا در ثبت تریاژ: ${err.message || 'اطلاعات علائم حیاتی نامعتبر است.'}`, true);
    }
}

async function submitNursingCare() {
    const nid = toEnglishDigits(document.getElementById('nc-nid')?.value || document.getElementById('t-nid')?.value).trim();
    const proc = document.getElementById('nc-procedure').value.trim();
    const fee = parseFloat(toEnglishDigits(document.getElementById('nc-fee').value)) || 0;
    const notes = document.getElementById('nc-notes').value.trim();

    if (!nid || !proc) return showToast('کد ملی بیمار و شرح خدمت پرستاری الزامی است.', 'error');

    try {
        const res = await apiFetch('/api/clinical/records/care', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({
                patientNationalId: nid,
                careProcedure: proc,
                serviceFee: fee,
                careNotes: notes
            })
        });
        const data = await res.json();
        if (!res.ok) throw data;
        log(`خدمت پرستاری '${proc}' برای بیمار ${nid} ثبت شد (هزینه: ${fee.toFixed(2)} $).`);
        document.getElementById('nc-procedure').value = '';
        document.getElementById('nc-notes').value = '';
        loadQueue();
    } catch (err) {
        log(`خطا در ثبت خدمت پرستاری: ${err.message}`, true);
    }
}

async function lookupPatientQuick() {
    const nid = toEnglishDigits(document.getElementById('lookup-p-nid').value).trim();
    if (!nid) return showToast('کد ملی بیمار را وارد کنید.', 'error');
    try {
        const res = await apiFetch(`/api/clinical/patients/${encodeURIComponent(nid)}`);
        const data = await res.json();
        if (!res.ok) throw data;
        document.getElementById('lookup-p-result').textContent =
            `بیمار: ${data.fullName} | وضعیت: ${data.currentStatus} | بدهی: ${formatMoney(data.pendingBill)}`;
    } catch (err) {
        document.getElementById('lookup-p-result').textContent = '';
        log(`بیمار یافت نشد: ${err.message}`, true);
    }
}

async function reCheckInPatient() {
    const nid = toEnglishDigits(document.getElementById('lookup-p-nid').value).trim();
    if (!nid) return showToast('کد ملی بیمار را وارد کنید.', 'error');
    try {
        const res = await apiFetch(`/api/clinical/patients/${encodeURIComponent(nid)}/check-in`, {method: 'PUT'});
        if (!res.ok) throw await res.json();
        log(`بیمار ${nid} مجدداً به صف انتظار پزشک منتقل شد.`);
        loadQueue();
        lookupPatientQuick();
    } catch (err) {
        log(`خطا در پذیرش مجدد: ${err.message}`, true);
    }
}

async function loadQueue() {
    try {
        const [waitRes, disRes] = await Promise.all([
            apiFetch('/api/clinical/patients/status/WAITING_FOR_DOCTOR'),
            apiFetch('/api/clinical/patients/status/PAYMENT_COMPLETED')
        ]);
        const data = waitRes.ok ? await waitRes.json() : [];
        const readyList = disRes.ok ? await disRes.json() : [];

        renderIntakeAnalytics(data, readyList);

        const tbody = document.getElementById('queue-table');
        if (!tbody) return;
        tbody.replaceChildren();

        if (data.length === 0) {
            const tr = document.createElement('tr');
            const td = createCell('هیچ بیماری در صف انتظار پزشک نیست.', 'text-align:center; color:var(--text-sub);');
            td.colSpan = 7;
            tr.appendChild(td);
            tbody.appendChild(tr);
            return;
        }

        const isNurse = currentUser && currentUser.role.toUpperCase().includes('NURSE');

        data.forEach(p => {
            const tr = document.createElement('tr');
            tr.appendChild(createCell(p.patientId, 'font-family:monospace; color:var(--primary); font-weight:800;'));
            tr.appendChild(createCell(p.nationalId));
            tr.appendChild(createCell(p.fullName, 'font-weight:800;'));
            tr.appendChild(createCell(p.bloodType));

            const statusTd = document.createElement('td');
            const badge = document.createElement('span');
            badge.className = 'badge badge-warning';
            badge.textContent = p.currentStatus;
            statusTd.appendChild(badge);
            tr.appendChild(statusTd);

            tr.appendChild(createCell(formatMoney(p.pendingBill)));

            const actionTd = document.createElement('td');
            if (isNurse) {
                const triageBtn = document.createElement('button');
                triageBtn.className = 'action-btn btn-sm';
                triageBtn.textContent = '💉 انتخاب تریاژ';
                triageBtn.addEventListener('click', () => {
                    document.getElementById('t-nid').value = p.nationalId;
                    const ncNid = document.getElementById('nc-nid');
                    if (ncNid) ncNid.value = p.nationalId;
                    flashInput('t-bp');
                    showToast(`بیمار ${p.fullName} برای تریاژ انتخاب شد.`);
                });
                actionTd.appendChild(triageBtn);
            }

            const cancelBtn = document.createElement('button');
            cancelBtn.className = 'action-btn btn-danger btn-sm';
            cancelBtn.textContent = 'لغو پذیرش';
            cancelBtn.addEventListener('click', () => cancelPatientAdmission(p.nationalId));
            actionTd.appendChild(cancelBtn);

            tr.appendChild(actionTd);
            tbody.appendChild(tr);
        });
    } catch (err) {
    }
}

function renderIntakeAnalytics(waitingPatients, readyForDischarge) {
    ChartsEngine.renderDonutChart(
        'intake-pipeline-chart',
        [
            {label: 'در صف معاینه پزشک', value: waitingPatients.length, color: '#38bdf8'},
            {label: 'تسویه‌شده آماده ترخیص', value: readyForDischarge.length, color: '#34d399'}
        ],
        String(waitingPatients.length + readyForDischarge.length),
        'بیمار فعال'
    );

    const bloodCounts = {};
    waitingPatients.forEach(p => {
        const b = (p.bloodType || 'UNKNOWN').replace('_POSITIVE', '+').replace('_NEGATIVE', '-');
        bloodCounts[b] = (bloodCounts[b] || 0) + 1;
    });
    const bloodItems = Object.entries(bloodCounts).map(([label, value], i) => ({
        label: `گروه خونی ${label}`,
        value,
        color: ChartsEngine.PALETTE[i % ChartsEngine.PALETTE.length]
    }));
    if (bloodItems.length === 0) {
        bloodItems.push({label: 'بدون بیمار در صف', value: 0});
    }
    ChartsEngine.renderHorizontalBars('intake-blood-chart', bloodItems, ' نفر');
}

async function loadReadyForDischarge() {
    try {
        const res = await apiFetch('/api/clinical/patients/status/PAYMENT_COMPLETED');
        if (!res.ok) throw await res.json();
        const data = await res.json();
        const tbody = document.getElementById('discharge-table');
        if (!tbody) return;
        tbody.replaceChildren();

        if (data.length === 0) {
            const tr = document.createElement('tr');
            const td = createCell('بیماری در وضعیت آماده ترخیص نیست.', 'text-align:center; color:var(--text-sub);');
            td.colSpan = 5;
            tr.appendChild(td);
            tbody.appendChild(tr);
            return;
        }

        data.forEach(p => {
            const tr = document.createElement('tr');
            tr.appendChild(createCell(p.nationalId));
            tr.appendChild(createCell(p.fullName, 'font-weight:800;'));
            tr.appendChild(createCell(p.assignedDoctorName));

            const statusTd = document.createElement('td');
            const badge = document.createElement('span');
            badge.className = 'badge badge-success';
            badge.textContent = 'تسویه کامل ✓';
            statusTd.appendChild(badge);
            tr.appendChild(statusTd);

            const actionTd = document.createElement('td');
            const disBtn = document.createElement('button');
            disBtn.className = 'action-btn btn-success btn-sm';
            disBtn.textContent = 'ترخیص قطعی بیمار';
            disBtn.addEventListener('click', () => dischargePatient(p.nationalId));
            actionTd.appendChild(disBtn);

            tr.appendChild(actionTd);
            tbody.appendChild(tr);
        });
    } catch (err) {
    }
}

async function cancelPatientAdmission(nid) {
    try {
        const res = await apiFetch(`/api/clinical/patients/${encodeURIComponent(nid)}/cancel`, {method: 'PUT'});
        if (!res.ok) throw await res.json();
        log(`پذیرش بیمار ${nid} لغو شد.`);
        loadQueue();
    } catch (err) {
        log(`خطا در لغو پذیرش: ${err.message}`, true);
    }
}

async function dischargePatient(nid) {
    try {
        const res = await apiFetch(`/api/clinical/patients/${encodeURIComponent(nid)}/discharge`, {method: 'PUT'});
        if (!res.ok) throw await res.json();
        log(`بیمار ${nid} با موفقیت ترخیص شد.`);
        loadReadyForDischarge();
        loadQueue();
    } catch (err) {
        log(`خطا در ترخیص بیمار: ${err.message}`, true);
    }
}

// ==================== ۲. اتاق معاینه پزشک ====================
function applyClinicalTemplate(diag, rx, notes) {
    document.getElementById('c-diag').value = diag;
    document.getElementById('c-rx').value = rx;
    document.getElementById('c-notes').value = notes;
    flashInput('c-diag');
    showToast('قالب نسخه آماده جایگذاری شد.');
}

async function loadDoctorQueue() {
    try {
        const res = await apiFetch('/api/clinical/patients/status/WAITING_FOR_DOCTOR');
        if (!res.ok) throw await res.json();
        const data = await res.json();

        renderDoctorAnalytics(data);

        const tbody = document.getElementById('doctor-queue-table');
        if (!tbody) return;
        tbody.replaceChildren();

        if (data.length === 0) {
            const tr = document.createElement('tr');
            const td = createCell('هیچ بیماری در صف انتظار معاینه نیست.', 'text-align:center; color:var(--text-sub);');
            td.colSpan = 6;
            tr.appendChild(td);
            tbody.appendChild(tr);
            return;
        }

        data.forEach(p => {
            const tr = document.createElement('tr');
            tr.appendChild(createCell(p.patientId, 'font-family:monospace; color:var(--primary); font-weight:800;'));
            tr.appendChild(createCell(p.nationalId));
            tr.appendChild(createCell(p.fullName, 'font-weight:800;'));
            tr.appendChild(createCell(`${p.age} سال`));
            tr.appendChild(createCell(p.bloodType));

            const actionTd = document.createElement('td');
            const selBtn = document.createElement('button');
            selBtn.className = 'action-btn btn-sm';
            selBtn.textContent = '🩺 انتخاب جهت ویزیت';
            selBtn.addEventListener('click', () => {
                document.getElementById('c-nid').value = p.nationalId;
                flashInput('c-diag');
                showToast(`بیمار ${p.fullName} برای معاینه انتخاب شد.`);
            });

            const histBtn = document.createElement('button');
            histBtn.className = 'action-btn btn-secondary btn-sm';
            histBtn.textContent = '📂 سوابق و تریاژ';
            histBtn.addEventListener('click', () => openPatientHistoryModal(p.nationalId, p.fullName));

            actionTd.appendChild(selBtn);
            actionTd.appendChild(histBtn);
            tr.appendChild(actionTd);
            tbody.appendChild(tr);
        });
    } catch (err) {
    }
}

function renderDoctorAnalytics(patients) {
    let pediatrics = 0, adults = 0, seniors = 0;
    patients.forEach(p => {
        const age = parseInt(p.age, 10) || 30;
        if (age < 18) pediatrics++;
        else if (age < 60) adults++;
        else seniors++;
    });

    ChartsEngine.renderDonutChart(
        'doctor-demographics-chart',
        [
            {label: 'بزرگسالان (۱۸ تا ۵۹ سال)', value: adults, color: '#38bdf8'},
            {label: 'سالمندان (+۶۰ سال - اولویت)', value: seniors, color: '#fb7185'},
            {label: 'اطفال (زیر ۱۸ سال)', value: pediatrics, color: '#34d399'}
        ],
        String(patients.length),
        'منتظر ویزیت'
    );

    const dailyCap = 30;
    const remaining = Math.max(0, dailyCap - patients.length);
    ChartsEngine.renderHorizontalBars('doctor-capacity-chart', [
        {label: 'بیماران حاضر در صف معاینه', value: patients.length, color: 'var(--liquid-blue)'},
        {label: 'ظرفیت آزاد شیفت روزانه پزشک', value: remaining, color: 'var(--liquid-emerald)'}
    ], ' نفر');
}

function openPatientHistoryFromInput() {
    const nid = toEnglishDigits(document.getElementById('c-nid').value).trim();
    if (!nid) return showToast('ابتدا کد ملی بیمار را وارد یا از جدول انتخاب کنید.', 'error');
    openPatientHistoryModal(nid, nid);
}

async function openPatientHistoryModal(nid, name) {
    try {
        const res = await apiFetch(`/api/clinical/records/patient/${encodeURIComponent(nid)}`);
        if (!res.ok) throw await res.json();
        const records = await res.json();

        document.getElementById('history-modal-title').textContent = `📂 سوابق بالینی بیمار: ${name} (${nid})`;
        document.getElementById('amend-form-box').style.display = 'none';
        const listEl = document.getElementById('history-records-list');
        listEl.replaceChildren();

        if (records.length === 0) {
            const empty = document.createElement('p');
            empty.style.color = 'var(--text-sub)';
            empty.textContent = 'هیچ سابقه معاینه‌ای برای این بیمار ثبت نشده است.';
            listEl.appendChild(empty);
        } else {
            records.forEach(r => {
                const item = document.createElement('div');
                item.className = 'sub-box';
                item.style.marginTop = '12px';

                const head = document.createElement('div');
                head.style.cssText = 'display:flex; justify-content:space-between; margin-bottom:10px; flex-wrap:wrap; gap:8px;';
                const idSpan = document.createElement('b');
                idSpan.style.color = 'var(--primary)';
                idSpan.textContent = `پرونده #${r.recordId.substring(0, 8)} (${r.recordStatus})`;
                const hashSpan = document.createElement('span');
                hashSpan.className = 'badge badge-success';
                hashSpan.textContent = `HMAC: ${r.integrityHash ? r.integrityHash.substring(0, 12) : ''}...`;
                head.appendChild(idSpan);
                head.appendChild(hashSpan);

                const diagP = document.createElement('p');
                diagP.textContent = `تشخیص / خدمت: ${r.diagnosis}`;
                const rxP = document.createElement('p');
                rxP.textContent = `نسخه / اقدام: ${r.prescription}`;
                const noteP = document.createElement('p');
                noteP.style.color = 'var(--text-sub)';
                noteP.textContent = `جزئیات: ${r.notes}`;

                const amendBtn = document.createElement('button');
                amendBtn.className = 'action-btn btn-secondary btn-sm';
                amendBtn.style.marginTop = '10px';
                amendBtn.textContent = '✏️ اصلاح پرونده';
                amendBtn.addEventListener('click', () => {
                    document.getElementById('amend-record-id').value = r.recordId;
                    document.getElementById('amend-diag').value = r.diagnosis;
                    document.getElementById('amend-rx').value = r.prescription;
                    document.getElementById('amend-reason').value = '';
                    document.getElementById('amend-form-box').style.display = 'block';
                });

                item.appendChild(head);
                item.appendChild(diagP);
                item.appendChild(rxP);
                item.appendChild(noteP);
                item.appendChild(amendBtn);
                listEl.appendChild(item);
            });
        }

        document.getElementById('history-modal').style.display = 'flex';
    } catch (err) {
        log(`خطا در دریافت سوابق پزشکی: ${err.message}`, true);
    }
}

async function submitAmendRecord() {
    const recId = document.getElementById('amend-record-id').value;
    const payload = {
        updatedDiagnosis: document.getElementById('amend-diag').value.trim(),
        updatedPrescription: document.getElementById('amend-rx').value.trim(),
        reason: document.getElementById('amend-reason').value.trim()
    };
    try {
        const res = await apiFetch(`/api/clinical/records/${encodeURIComponent(recId)}/amend`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (!res.ok) throw data;
        log(`الحاقیه پرونده پزشکی ثبت شد (${data.recordId}).`);
        closeModal('history-modal');
    } catch (err) {
        log(`خطا در اصلاح پرونده: ${err.message}`, true);
    }
}

async function submitConsultation() {
    const payload = {
        patientNationalId: toEnglishDigits(document.getElementById('c-nid').value).trim(),
        diagnosis: document.getElementById('c-diag').value.trim(),
        prescription: document.getElementById('c-rx').value.trim(),
        notes: document.getElementById('c-notes').value.trim()
    };

    try {
        const res = await apiFetch('/api/clinical/records/consultation', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (!res.ok) throw data;
        log(`معاینه بیمار ${payload.patientNationalId} ثبت و به صندوق ارجاع شد.`);
        document.getElementById('c-nid').value = '';
        document.getElementById('c-diag').value = '';
        document.getElementById('c-rx').value = '';
        document.getElementById('c-notes').value = '';
        loadDoctorQueue();
    } catch (err) {
        log(`خطا در ثبت ویزیت: ${err.message || 'نقص اطلاعات'}`, true);
    }
}

// ==================== ۳. امور مالی و ترازنامه حسابداری ====================
function loadBillingAll() {
    loadFinancialSummary();
    loadBillingQueue();
    loadAllReceipts();
}

async function loadFinancialSummary() {
    try {
        const res = await apiFetch('/api/billing/summary');
        if (!res.ok) return;
        const s = await res.json();

        const gross = parseFloat(s.grossRevenue) || 0;
        const disc = parseFloat(s.totalDiscounts) || 0;
        const tax = parseFloat(s.totalTax) || 0;
        const refunded = parseFloat(s.totalRefunded) || 0;
        const netRetained = parseFloat(s.netCollectedRevenue) || 0;
        const pending = parseFloat(s.pendingReceivables) || 0;
        const billedBeforeRefund = Math.max(0, gross + tax - disc);
        const totalEconomicValue = netRetained + pending;

        const setEl = (id, val) => {
            const el = document.getElementById(id);
            if (el) el.textContent = formatMoney(val);
        };

        setEl('fin-gross', gross);
        setEl('fin-discount', disc);
        setEl('fin-tax', tax);
        setEl('fin-refunded', refunded);
        setEl('fin-net', netRetained);
        setEl('fin-pending', pending);

        setEl('ledger-gross', gross);
        setEl('ledger-discount', disc);
        setEl('ledger-tax', tax);
        setEl('ledger-billed-net', billedBeforeRefund);
        setEl('ledger-refunded', refunded);
        setEl('ledger-vault-net', netRetained);
        setEl('ledger-receivables', pending);
        setEl('ledger-total-economic', totalEconomicValue);

        const countInfo = document.getElementById('ledger-counts-badge');
        if (countInfo) {
            countInfo.textContent = `اسناد پرداخت‌شده: ${s.totalPaidReceipts} فقره | اسناد استرداد: ${s.totalRefundedReceipts} فقره`;
        }

        const intEl = document.getElementById('fin-integrity');
        if (intEl) {
            if (s.allReceiptsCryptographicallyIntact) {
                intEl.textContent = `معتبر (${s.totalPaidReceipts} رسید) ✓`;
                intEl.style.color = 'var(--success)';
            } else {
                intEl.textContent = 'مخدوش شده ⚠️';
                intEl.style.color = 'var(--danger)';
            }
        }

        const methodEntries = Object.entries(s.revenueByPaymentMethod || {});
        const donutSegments = methodEntries.map(([method, val], i) => ({
            label: method,
            value: parseFloat(val) || 0,
            formattedValue: formatMoney(val),
            color: ChartsEngine.PALETTE[i % ChartsEngine.PALETTE.length]
        }));
        ChartsEngine.renderDonutChart('billing-methods-donut', donutSegments, formatMoney(netRetained), 'خالص خزانه');

        const chartEl = document.getElementById('payment-methods-chart');
        if (chartEl) {
            chartEl.replaceChildren();
            const netTotal = netRetained > 0 ? netRetained : 1;
            methodEntries.forEach(([method, val], idx) => {
                const numVal = parseFloat(val) || 0;
                const pct = Math.min(100, Math.max(0, Math.round((numVal / netTotal) * 100)));
                const box = document.createElement('div');
                box.className = 'method-bar-item';

                const top = document.createElement('div');
                top.className = 'method-bar-top';
                const mLabel = document.createElement('span');
                mLabel.textContent = `${method} (${pct}%)`;
                const mVal = document.createElement('b');
                mVal.textContent = formatMoney(numVal);
                top.appendChild(mLabel);
                top.appendChild(mVal);

                const track = document.createElement('div');
                track.className = 'progress-track';
                const fill = document.createElement('div');
                fill.className = 'progress-fill';
                fill.style.width = `${pct}%`;
                fill.style.background = ChartsEngine.PALETTE[idx % ChartsEngine.PALETTE.length];
                track.appendChild(fill);

                box.appendChild(top);
                box.appendChild(track);
                chartEl.appendChild(box);
            });
        }
    } catch (e) {
    }
}

function updateNetPayablePreview() {
    const amt = parseFloat(toEnglishDigits(document.getElementById('b-amount').value)) || 0;
    const disc = parseFloat(toEnglishDigits(document.getElementById('b-discount').value)) || 0;
    const tax = parseFloat(toEnglishDigits(document.getElementById('b-tax').value)) || 0;
    const net = Math.max(0, amt + tax - disc).toFixed(2);
    document.getElementById('net-payable-preview').textContent = net;
}

async function lookupDebt() {
    const nid = toEnglishDigits(document.getElementById('b-nid').value).trim();
    if (!nid) return showToast('کد ملی را وارد کنید.', 'error');
    try {
        const res = await apiFetch(`/api/billing/patient/${encodeURIComponent(nid)}`);
        const data = await res.json();
        if (!res.ok) throw data;
        document.getElementById('debt-patient-name').textContent = data.fullName;
        document.getElementById('debt-patient-val').textContent = data.pendingBill;
        document.getElementById('b-amount').value = data.pendingBill;
        document.getElementById('b-discount').value = '0.00';
        document.getElementById('b-tax').value = '0.00';
        updateNetPayablePreview();
        document.getElementById('debt-info-banner').style.display = 'block';
    } catch (err) {
        document.getElementById('debt-info-banner').style.display = 'none';
        log(`خطا در استعلام بدهی: ${err.message}`, true);
    }
}

async function loadBillingQueue() {
    try {
        const res = await apiFetch('/api/billing/pending-patients');
        if (!res.ok) throw await res.json();
        const data = await res.json();
        const tbody = document.getElementById('billing-queue-table');
        if (!tbody) return;
        tbody.replaceChildren();

        if (data.length === 0) {
            const tr = document.createElement('tr');
            const td = createCell('هیچ بیماری در انتظار پرداخت صندوق نیست.', 'text-align:center; color:var(--text-sub);');
            td.colSpan = 5;
            tr.appendChild(td);
            tbody.appendChild(tr);
            return;
        }

        data.forEach(p => {
            const tr = document.createElement('tr');
            tr.appendChild(createCell(p.nationalId));
            tr.appendChild(createCell(p.fullName, 'font-weight:800;'));
            tr.appendChild(createCell(p.assignedDoctorName));
            tr.appendChild(createCell(formatMoney(p.pendingBill), 'color:var(--warning); font-weight:800;'));

            const actionTd = document.createElement('td');
            const btn = document.createElement('button');
            btn.className = 'action-btn btn-sm';
            btn.textContent = '💳 انتخاب جهت تسویه';
            btn.addEventListener('click', () => {
                document.getElementById('b-nid').value = p.nationalId;
                document.getElementById('b-amount').value = p.pendingBill;
                document.getElementById('b-discount').value = '0.00';
                document.getElementById('b-tax').value = '0.00';
                document.getElementById('debt-patient-name').textContent = p.fullName;
                document.getElementById('debt-patient-val').textContent = p.pendingBill;
                updateNetPayablePreview();
                document.getElementById('debt-info-banner').style.display = 'block';
                flashInput('b-amount');
            });
            actionTd.appendChild(btn);
            tr.appendChild(actionTd);
            tbody.appendChild(tr);
        });
    } catch (err) {
    }
}

async function submitPayment() {
    const nid = toEnglishDigits(document.getElementById('b-nid').value).trim();
    const amt = parseFloat(toEnglishDigits(document.getElementById('b-amount').value));
    const disc = parseFloat(toEnglishDigits(document.getElementById('b-discount').value)) || 0;
    const tax = parseFloat(toEnglishDigits(document.getElementById('b-tax').value)) || 0;
    if (!nid || isNaN(amt) || amt <= 0) return showToast('کد ملی و مبلغ معتبر وارد کنید.', 'error');

    const payload = {
        patientNationalId: nid,
        amount: amt,
        discountAmount: disc,
        taxAmount: tax,
        method: document.getElementById('b-method').value,
        description: document.getElementById('b-desc').value.trim() || 'تسویه در باجه صندوق',
        idempotencyKey: `${nid}-${amt}-${Date.now()}`
    };

    try {
        const res = await apiFetch('/api/billing/settle', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (!res.ok) throw data;
        log(`تسویه انجام شد. رسید: ${data.receiptId} | خالص دریافتی: ${formatMoney(data.netTotal)}`);
        document.getElementById('debt-info-banner').style.display = 'none';
        document.getElementById('b-nid').value = '';
        document.getElementById('b-amount').value = '';
        loadBillingAll();
        openPrintableReceipt(data);
    } catch (err) {
        log(`خطای تسویه: ${err.message}`, true);
    }
}

function openPrintableReceipt(r) {
    const container = document.getElementById('receipt-print-body');
    container.replaceChildren();

    const rows = [
        ['شماره رسمی سند:', r.receiptId],
        ['کد ملی بیمار:', r.patientNationalId],
        ['شناسه پزشک / مرکز هزینه:', r.doctorNationalId],
        ['کاربر مسئول صندوق:', r.cashierUsername],
        ['۱. مبلغ پایه تعرفه (Subtotal):', formatMoney(r.amount)],
        ['۲. کسر تخفیف / سهم بیمه:', `(-) ${formatMoney(r.discountAmount)}`],
        ['۳. افزوده مالیات:', `(+) ${formatMoney(r.taxAmount)}`],
        ['۴. خالص پرداخت‌شده:', formatMoney(r.netTotal)],
        ['۵. مبالغ مستردشده:', `(-) ${formatMoney(r.refundedAmount || 0)}`],
        ['۶. خالص نهایی باقی‌مانده در خزانه:', formatMoney(r.netRetainedAmount !== undefined ? r.netRetainedAmount : r.netTotal)],
        ['روش پرداخت:', r.paymentMethod],
        ['وضعیت سند:', r.receiptStatus],
        ['شرح تراکنش:', r.description || '-'],
        ['امضای دیجیتال HMAC-SHA256:', r.integrityHash]
    ];

    rows.forEach(([k, v]) => {
        const row = document.createElement('div');
        row.style.cssText = 'display:flex; justify-content:space-between; border-bottom:1px dashed var(--glass-border); padding:6px 0;';
        const kEl = document.createElement('span');
        kEl.style.color = 'var(--text-sub)';
        kEl.textContent = k;
        const vEl = document.createElement('b');
        vEl.style.fontFamily = k.includes('HMAC') ? 'monospace' : 'inherit';
        vEl.style.fontSize = k.includes('HMAC') ? '0.71rem' : '0.87rem';
        vEl.textContent = v;
        row.appendChild(kEl);
        row.appendChild(vEl);
        container.appendChild(row);
    });

    document.getElementById('receipt-modal').style.display = 'flex';
}

async function submitRefund() {
    const payload = {
        originalReceiptId: toEnglishDigits(document.getElementById('ref-receipt-id').value).trim(),
        patientNationalId: toEnglishDigits(document.getElementById('ref-nid').value).trim(),
        refundAmount: parseFloat(toEnglishDigits(document.getElementById('ref-amount').value)),
        reason: document.getElementById('ref-reason').value.trim()
    };

    try {
        const res = await apiFetch('/api/billing/refund', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (!res.ok) throw data;
        log(`استرداد وجه ثبت شد. شماره سند: ${data.receiptId}`);
        loadBillingAll();
    } catch (err) {
        log(`خطا در استرداد وجه: ${err.message}`, true);
    }
}

async function loadAllReceipts() {
    try {
        const res = await apiFetch('/api/billing/receipts');
        if (!res.ok) return;
        const data = await res.json();

        renderDoctorRevenueBreakdown(data);

        const tbody = document.getElementById('receipts-table');
        if (!tbody) return;
        tbody.replaceChildren();

        if (data.length === 0) {
            const tr = document.createElement('tr');
            const td = createCell('هنوز رسیدی صادر نشده است.', 'text-align:center; color:var(--text-sub);');
            td.colSpan = 11;
            tr.appendChild(td);
            tbody.appendChild(tr);
            return;
        }

        data.forEach(r => {
            const tr = document.createElement('tr');
            tr.appendChild(createCell(r.receiptId, 'font-family:monospace; color:var(--primary); font-weight:800;'));
            tr.appendChild(createCell(r.patientNationalId));
            tr.appendChild(createCell(r.doctorNationalId, 'font-family:monospace; font-size:0.78rem;'));
            tr.appendChild(createCell(r.cashierUsername));
            tr.appendChild(createCell(formatMoney(r.amount)));
            tr.appendChild(createCell(formatMoney(r.discountAmount)));
            tr.appendChild(createCell(formatMoney(r.refundedAmount || 0), 'color:var(--danger);'));
            tr.appendChild(createCell(formatMoney(r.netRetainedAmount !== undefined ? r.netRetainedAmount : r.netTotal), 'font-weight:800; color:var(--success);'));
            tr.appendChild(createCell(r.paymentMethod));

            const stTd = document.createElement('td');
            const badge = document.createElement('span');
            if (r.receiptStatus === 'PAID') {
                badge.className = 'badge badge-success';
            } else if (r.receiptStatus === 'PARTIALLY_REFUNDED') {
                badge.className = 'badge badge-warning';
            } else {
                badge.className = 'badge badge-danger';
            }
            badge.textContent = r.receiptStatus;
            stTd.appendChild(badge);
            tr.appendChild(stTd);

            const printTd = document.createElement('td');
            const pBtn = document.createElement('button');
            pBtn.className = 'action-btn btn-secondary btn-sm';
            pBtn.textContent = '🖨️ چاپ';
            pBtn.addEventListener('click', () => openPrintableReceipt(r));
            printTd.appendChild(pBtn);
            tr.appendChild(printTd);

            tbody.appendChild(tr);
        });
    } catch (e) {
    }
}

function renderDoctorRevenueBreakdown(receipts) {
    const byDoctor = {};
    receipts.forEach(r => {
        if (!r.receiptId || r.receiptId.startsWith('REF-') || r.receiptStatus === 'VOIDED') return;
        const doc = r.doctorNationalId || 'DOC-GENERAL';
        const retained = parseFloat(r.netRetainedAmount !== undefined ? r.netRetainedAmount : r.netTotal) || 0;
        byDoctor[doc] = (byDoctor[doc] || 0) + retained;
    });

    const items = Object.entries(byDoctor).map(([docId, val], idx) => ({
        label: `شناسه پزشک / مرکز: ${docId}`,
        value: val,
        displayValue: formatMoney(val),
        color: ChartsEngine.PALETTE[idx % ChartsEngine.PALETTE.length]
    }));

    if (items.length === 0) {
        items.push({label: 'تراکنش ثبت‌شده‌ای وجود ندارد', value: 0, displayValue: '0.00 $'});
    }
    ChartsEngine.renderHorizontalBars('doctor-revenue-chart', items, '');
}

// ==================== ۴. مدیریت پرسنل، ساخت نقش جدید و تنظیمات شبکه ====================
function toggleRoleFields() {
    const role = document.getElementById('reg-role').value;
    const docLic = document.getElementById('doc-lic-group');
    const docSpec = document.getElementById('doc-spec-group');
    const cashLim = document.getElementById('cashier-lim-group');
    if (docLic) docLic.style.display = role === 'DOCTOR' ? 'flex' : 'none';
    if (docSpec) docSpec.style.display = role === 'DOCTOR' ? 'flex' : 'none';
    if (cashLim) cashLim.style.display = role === 'CASHIER' ? 'flex' : 'none';
}

async function loadAvailableRolesDropdown() {
    try {
        const res = await apiFetch('/api/admin/staff/roles');
        if (!res.ok) return;
        const roles = await res.json();
        const selectEl = document.getElementById('reg-role');
        if (!selectEl) return;
        const previousValue = selectEl.value;
        selectEl.replaceChildren();

        roles.forEach(r => {
            const opt = document.createElement('option');
            opt.value = r.roleCode;
            opt.textContent = `${r.displayName} (${r.roleCode})${r.builtIn ? '' : ' ★ سفارشی'}`;
            selectEl.appendChild(opt);
        });
        if (previousValue && Array.from(selectEl.options).some(o => o.value === previousValue)) {
            selectEl.value = previousValue;
        }
        toggleRoleFields();
    } catch (e) {
    }
}

async function submitCreateNewRole() {
    const code = toEnglishDigits(document.getElementById('new-role-code')?.value || '').trim().toUpperCase();
    const title = (document.getElementById('new-role-title')?.value || '').trim();
    const clearance = document.getElementById('new-role-clearance')?.value || 'RECEPTIONIST';

    if (!code || !title) {
        return showToast('کد انگلیسی و عنوان فارسی نقش جدید را وارد کنید.', 'error');
    }

    try {
        const res = await apiFetch('/api/admin/staff/roles', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({
                roleCode: code,
                displayName: title,
                baseClearance: clearance,
                builtIn: false
            })
        });
        const data = await res.json();
        if (!res.ok) throw data;

        log(`نقش سازمانی جدید '${data.displayName} (${data.roleCode})' با موفقیت ساخته شد.`);
        document.getElementById('new-role-code').value = '';
        document.getElementById('new-role-title').value = '';
        await loadAvailableRolesDropdown();
        document.getElementById('reg-role').value = data.roleCode;
        toggleRoleFields();
    } catch (err) {
        log(`خطا در ساخت نقش جدید: ${err.message || 'کد انگلیسی باید حروف بزرگ و بدون فاصله باشد.'}`, true);
    }
}

async function submitRegisterStaff() {
    const username = toEnglishDigits(document.getElementById('reg-user').value).trim();
    const password = document.getElementById('reg-pass').value;
    const nationalId = toEnglishDigits(document.getElementById('reg-nid').value).trim();
    const fullName = document.getElementById('reg-name').value.trim();
    const age = parseInt(toEnglishDigits(document.getElementById('reg-age').value), 10);
    const contactNumber = toEnglishDigits(document.getElementById('reg-phone').value).trim();
    const salaryVal = parseFloat(toEnglishDigits(document.getElementById('reg-salary')?.value || '20000')) || 20000;
    const role = document.getElementById('reg-role').value;
    const specialization = document.getElementById('reg-spec').value;
    const medicalLicenseNumber = toEnglishDigits(document.getElementById('reg-med-id').value).trim();
    const maxTransactionLimit = parseFloat(toEnglishDigits(document.getElementById('reg-limit').value)) || 50000;

    if (!username || username.length < 3) {
        flashInput('reg-user');
        return showToast('نام کاربری باید حداقل ۳ کاراکتر انگلیسی باشد.', 'error');
    }
    if (!password || password.length < 6) {
        flashInput('reg-pass');
        return showToast('رمز عبور باید حداقل ۶ کاراکتر باشد.', 'error');
    }
    if (!nationalId || nationalId.length < 4) {
        flashInput('reg-nid');
        return showToast('کد ملی پرسنل معتبر نیست.', 'error');
    }
    if (!fullName) {
        flashInput('reg-name');
        return showToast('نام و نام خانوادگی کارمند را وارد کنید.', 'error');
    }

    const payload = {
        username,
        password,
        nationalId,
        fullName,
        age: isNaN(age) ? 30 : age,
        contactNumber: contactNumber || '09120000000',
        baseSalary: salaryVal,
        role,
        specialization,
        medicalLicenseNumber,
        maxTransactionLimit
    };

    try {
        const res = await apiFetch('/api/admin/staff/register', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (!res.ok) throw data;

        log(`حساب پرسنلی '${data.username}' (${data.fullName}) با نقش '${data.role}' با موفقیت ایجاد شد.`);
        document.getElementById('reg-user').value = '';
        document.getElementById('reg-pass').value = '';
        document.getElementById('reg-nid').value = '';
        document.getElementById('reg-name').value = '';
        loadStaffList();
    } catch (err) {
        log(`خطا در ایجاد پرسنل: ${err.message || 'نام کاربری یا کد ملی تکراری است.'}`, true);
    }
}

async function loadNetworkAndSslStatus() {
    try {
        const res = await apiFetch('/api/admin/staff/network-status');
        if (!res.ok) return;
        const info = await res.json();

        const protoEl = document.getElementById('net-protocol-status');
        if (protoEl) {
            protoEl.textContent = info.sslEnabled
                ? `HTTPS فعال (پورت ${info.port})`
                : `HTTP استاندارد (پورت ${info.port} - بدون هشدار مرورگر)`;
            protoEl.className = 'badge ' + (info.sslEnabled ? 'badge-success' : 'badge-role');
        }

        const urlEl = document.getElementById('net-lan-url');
        if (urlEl) urlEl.textContent = info.primaryLanUrl;

        const guidesEl = document.getElementById('net-ssl-guides');
        if (guidesEl && info.installGuides) {
            guidesEl.textContent = info.installGuides.join('\n');
        }
    } catch (e) {
    }
}

async function generateOptionalSsl() {
    try {
        const res = await apiFetch('/api/admin/staff/ssl-generate', {method: 'POST'});
        const data = await res.json();
        if (!res.ok) throw data;
        log(`گواهی SSL در مسیر ${data.certFilePath} تولید شد.`);
        const box = document.getElementById('ssl-guides-box');
        if (box) box.style.display = 'block';
        loadNetworkAndSslStatus();
    } catch (err) {
        log(`خطا در تولید گواهی SSL: ${err.message}`, true);
    }
}

async function loadStaffList() {
    loadAvailableRolesDropdown();
    loadNetworkAndSslStatus();
    try {
        const res = await apiFetch('/api/admin/staff');
        if (!res.ok) throw await res.json();
        const data = await res.json();

        renderStaffAnalytics(data);

        const tbody = document.getElementById('staff-table');
        if (!tbody) return;
        tbody.replaceChildren();

        data.forEach(s => {
            const tr = document.createElement('tr');
            tr.appendChild(createCell(s.staffId, 'font-family:monospace;'));
            tr.appendChild(createCell(s.username, 'font-weight:800; color:var(--primary);'));
            tr.appendChild(createCell(s.fullName));
            tr.appendChild(createCell(s.nationalId));

            const roleTd = document.createElement('td');
            const rBadge = document.createElement('span');
            rBadge.className = 'badge badge-role';
            rBadge.textContent = s.role;
            roleTd.appendChild(rBadge);
            tr.appendChild(roleTd);

            const stTd = document.createElement('td');
            const badge = document.createElement('span');
            badge.className = 'badge ' + (s.active ? 'badge-success' : 'badge-danger');
            badge.textContent = s.active ? 'ACTIVE' : 'SUSPENDED';
            stTd.appendChild(badge);
            tr.appendChild(stTd);

            const actTd = document.createElement('td');
            const btn = document.createElement('button');
            btn.className = 'action-btn btn-sm ' + (s.active ? 'btn-danger' : 'btn-success');
            btn.textContent = s.active ? 'تعلیق حساب' : 'فعال‌سازی';
            btn.addEventListener('click', () => toggleStaff(s.username, !s.active));
            actTd.appendChild(btn);
            tr.appendChild(actTd);

            tbody.appendChild(tr);
        });
    } catch (err) {
        log(`خطا در دریافت فهرست پرسنل: ${err.message || 'ارتباط با سرور برقرار نشد.'}`, true);
    }
}

function renderStaffAnalytics(staffList) {
    const roleCounts = {};
    let activeCount = 0;
    staffList.forEach(s => {
        if (s.active) activeCount++;
        roleCounts[s.role] = (roleCounts[s.role] || 0) + 1;
    });

    const segments = Object.entries(roleCounts).map(([role, count], idx) => ({
        label: role,
        value: count,
        formattedValue: `${count} نفر`,
        color: ChartsEngine.PALETTE[idx % ChartsEngine.PALETTE.length]
    }));

    ChartsEngine.renderDonutChart('staff-roles-donut', segments, String(staffList.length), 'کل پرسنل');
    ChartsEngine.renderHorizontalBars('staff-status-bars', [
        {label: 'پرسنل فعال در شیفت (ACTIVE)', value: activeCount, color: 'var(--liquid-emerald)'},
        {label: 'حساب‌های تعلیق‌شده (SUSPENDED)', value: staffList.length - activeCount, color: 'var(--liquid-rose)'}
    ], ' نفر');
}

async function toggleStaff(username, activeState) {
    try {
        const res = await apiFetch(`/api/admin/staff/${encodeURIComponent(username)}/status?isActive=${activeState}`, {method: 'PUT'});
        if (!res.ok) throw await res.json();
        log(`وضعیت پرسنل '${username}' تغییر کرد.`);
        loadStaffList();
    } catch (err) {
        log(`خطا در تغییر وضعیت پرسنل: ${err.message}`, true);
    }
}

// ==================== ۵. زنجیره حسابرسی امنیتی ====================
async function loadAuditTrail() {
    try {
        const res = await apiFetch('/api/admin/audit');
        if (!res.ok) throw await res.json();
        const data = await res.json();
        const tbody = document.getElementById('audit-table');
        if (!tbody) return;
        tbody.replaceChildren();

        document.getElementById('kpi-count').textContent = `${data.length} بلوک`;
        if (data.length > 0) {
            const lastEntry = data[data.length - 1];
            document.getElementById('kpi-last-hash').textContent = lastEntry.entryHash.substring(0, 18) + '...';
        }

        ChartsEngine.renderHashChainVisualizer('audit-blockchain-visualizer', data);

        let successCnt = 0, blockedCnt = 0;
        data.forEach(l => {
            if (l.status === 'SUCCESS') successCnt++;
            else blockedCnt++;
        });
        ChartsEngine.renderDonutChart(
            'audit-events-donut',
            [
                {label: 'عملیات‌های مجاز (SUCCESS)', value: successCnt, color: '#34d399'},
                {label: 'مسدود / ناموفق (BLOCKED/FAILED)', value: blockedCnt, color: '#fb7185'}
            ],
            String(data.length),
            'رخداد ثبت‌شده'
        );

        data.slice().reverse().forEach(l => {
            const tr = document.createElement('tr');
            tr.appendChild(createCell(`#${l.sequenceNumber}`, 'font-weight:800; color:var(--primary);'));
            tr.appendChild(createCell(l.timestamp, 'font-size:0.78rem; color:var(--text-sub);'));

            const actorTd = document.createElement('td');
            const userBold = document.createElement('b');
            userBold.textContent = l.actorUsername + ' ';
            const roleSpan = document.createElement('span');
            roleSpan.className = 'badge badge-role';
            roleSpan.textContent = l.actorRole || 'SYSTEM';
            actorTd.appendChild(userBold);
            actorTd.appendChild(roleSpan);
            tr.appendChild(actorTd);

            const actionTd = document.createElement('td');
            const actBold = document.createElement('b');
            actBold.textContent = l.action;
            const detSmall = document.createElement('div');
            detSmall.style.cssText = 'font-size:0.75rem; color:var(--text-sub);';
            detSmall.textContent = l.details || '';
            actionTd.appendChild(actBold);
            actionTd.appendChild(detSmall);
            tr.appendChild(actionTd);

            tr.appendChild(createCell(l.terminalOrIp || '127.0.0.1', 'font-family:monospace; font-size:0.8rem;'));

            const stTd = document.createElement('td');
            const stBadge = document.createElement('span');
            stBadge.className = 'badge ' + (l.status === 'SUCCESS' ? 'badge-success' : 'badge-danger');
            stBadge.textContent = l.status;
            stTd.appendChild(stBadge);
            tr.appendChild(stTd);

            const prevTd = createCell(l.previousHash.substring(0, 10) + '...', 'font-family:monospace; font-size:0.75rem; color:var(--text-muted);');
            prevTd.title = l.previousHash;
            tr.appendChild(prevTd);

            const currTd = createCell(l.entryHash.substring(0, 14) + '...', 'font-family:monospace; font-size:0.75rem; color:var(--primary);');
            currTd.title = l.entryHash;
            tr.appendChild(currTd);

            tbody.appendChild(tr);
        });
    } catch (err) {
        log(`خطا در دریافت زنجیره حسابرسی: ${err.message || 'ارتباط با سرور برقرار نشد.'}`, true);
    }
}

async function verifyChainIntegrity() {
    try {
        const res = await apiFetch('/api/admin/audit/verify');
        const data = await res.json();
        const kpiIntegrity = document.getElementById('kpi-integrity');
        const recoverBtn = document.getElementById('audit-recover-btn');

        if (data.intact) {
            kpiIntegrity.textContent = `معتبر (${data.chainState || 'INTACT'}) ✓`;
            kpiIntegrity.style.color = 'var(--success)';
            if (recoverBtn) recoverBtn.style.display = 'none';
            log(`صحت ریاضی زنجیره تایید شد (${data.totalBlocks} بلوک).`);
        } else {
            kpiIntegrity.textContent = `${data.chainState || 'مخدوش شده'} ⚠️`;
            kpiIntegrity.style.color = 'var(--danger)';
            if (recoverBtn && data.recoverable) {
                recoverBtn.style.display = 'inline-flex';
            }
            log(data.statusMessage || 'هشدار امنیتی: عدم تطابق در زنجیره حسابرسی!', true);
        }
    } catch (e) {
        log('خطا در بررسی زنجیره: ' + e.message, true);
    }
}

async function triggerAuditRecovery() {
    try {
        const res = await apiFetch('/api/admin/audit/recover', {method: 'POST'});
        const data = await res.json();
        if (!res.ok) throw data;
        log(`بازیابی ژورنال حسابرسی انجام شد: ${data.statusMessage}`);
        verifyChainIntegrity();
        loadAuditTrail();
    } catch (err) {
        log(`خطا در بازیابی ژورنال: ${err.message}`, true);
    }
}

setInterval(() => {
    if (!currentUser) return;
    const activePanel = document.querySelector('.section-panel.active');
    if (!activePanel) return;
    if (activePanel.id === 'panel-intake') {
        loadQueue();
        loadReadyForDischarge();
    } else if (activePanel.id === 'panel-clinical') {
        loadDoctorQueue();
    } else if (activePanel.id === 'panel-billing') {
        loadFinancialSummary();
        loadBillingQueue();
    }
}, 10000);

checkSession();