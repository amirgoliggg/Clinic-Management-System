// ==================== ۳. امور مالی، صندوق و گزارش حسابداری بدون ابهام ====================
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

        document.getElementById('fin-gross').textContent = formatMoney(gross);
        document.getElementById('fin-discount').textContent = formatMoney(disc);
        document.getElementById('fin-tax').textContent = formatMoney(tax);
        document.getElementById('fin-refunded').textContent = formatMoney(refunded);
        document.getElementById('fin-net').textContent = formatMoney(netRetained);
        document.getElementById('fin-pending').textContent = formatMoney(pending);

        // پر کردن ترازنامه ریاضی گام‌به‌گام (Zero-Ambiguity Accounting Ledger)
        const setLedger = (id, val) => {
            const el = document.getElementById(id);
            if (el) el.textContent = formatMoney(val);
        };
        setLedger('ledger-gross', gross);
        setLedger('ledger-discount', disc);
        setLedger('ledger-tax', tax);
        setLedger('ledger-billed-net', billedBeforeRefund);
        setLedger('ledger-refunded', refunded);
        setLedger('ledger-vault-net', netRetained);
        setLedger('ledger-receivables', pending);
        setLedger('ledger-total-economic', totalEconomicValue);

        const countInfo = document.getElementById('ledger-counts-badge');
        if (countInfo) {
            countInfo.textContent = `اسناد پرداخت‌شده معتبر: ${s.totalPaidReceipts} فقره | اسناد استرداد وجه: ${s.totalRefundedReceipts} فقره`;
        }

        const intEl = document.getElementById('fin-integrity');
        if (s.allReceiptsCryptographicallyIntact) {
            intEl.textContent = `معتبر (${s.totalPaidReceipts} رسید) ✓`;
            intEl.style.color = 'var(--success)';
        } else {
            intEl.textContent = 'مخدوش شده ⚠️';
            intEl.style.color = 'var(--danger)';
        }

        // ۱. رسم نمودار حلقوی روش‌های پرداخت
        const methodEntries = Object.entries(s.revenueByPaymentMethod || {});
        const donutSegments = methodEntries.map(([method, val], i) => ({
            label: method,
            value: parseFloat(val) || 0,
            formattedValue: formatMoney(val),
            color: ChartsEngine.PALETTE[i % ChartsEngine.PALETTE.length]
        }));
        ChartsEngine.renderDonutChart(
            'billing-methods-donut',
            donutSegments,
            formatMoney(netRetained),
            'خالص خزانه'
        );

        // ۲. رسم نوارهای توزیع روش پرداخت
        const chartEl = document.getElementById('payment-methods-chart');
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
    } catch (e) {
    }
}

function updateNetPayablePreview() {
    const amt = parseFloat(document.getElementById('b-amount').value) || 0;
    const disc = parseFloat(document.getElementById('b-discount').value) || 0;
    const tax = parseFloat(document.getElementById('b-tax').value) || 0;
    const net = Math.max(0, amt + tax - disc).toFixed(2);
    document.getElementById('net-payable-preview').textContent = net;
}

async function lookupDebt() {
    const nid = document.getElementById('b-nid').value.trim();
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
    const nid = document.getElementById('b-nid').value.trim();
    const amt = parseFloat(document.getElementById('b-amount').value);
    const disc = parseFloat(document.getElementById('b-discount').value) || 0;
    const tax = parseFloat(document.getElementById('b-tax').value) || 0;
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
        ['شماره رسمی سند (Receipt ID):', r.receiptId],
        ['کد ملی بیمار (Patient National ID):', r.patientNationalId],
        ['شناسه پزشک / مرکز هزینه (Clinician ID):', r.doctorNationalId],
        ['کاربر مسئول صندوق (Cashier Operator):', r.cashierUsername],
        ['۱. مبلغ پایه تعرفه کسرشده از بدهی (Subtotal):', formatMoney(r.amount)],
        ['۲. کسر تخفیف / سهم بیمه (Discount):', `(-) ${formatMoney(r.discountAmount)}`],
        ['۳. افزوده مالیات و عوارض قانونی (Tax):', `(+) ${formatMoney(r.taxAmount)}`],
        ['۴. خالص وجه دریافتی در زمان صدور (Net Collected):', formatMoney(r.netTotal)],
        ['۵. مجموع مبالغ مستردشده از این رسید (Refunded):', `(-) ${formatMoney(r.refundedAmount || 0)}`],
        ['۶. خالص نهایی باقی‌مانده در خزانه (Net Retained):', formatMoney(r.netRetainedAmount !== undefined ? r.netRetainedAmount : r.netTotal)],
        ['روش پرداخت (Payment Channel):', r.paymentMethod],
        ['وضعیت حسابداری سند (Accounting Status):', r.receiptStatus],
        ['شرح و کلید یکتای تراکنش:', r.description || '-'],
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
        originalReceiptId: document.getElementById('ref-receipt-id').value.trim(),
        patientNationalId: document.getElementById('ref-nid').value.trim(),
        refundAmount: parseFloat(document.getElementById('ref-amount').value),
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
        log(`استرداد وجه ثبت شد. شماره سند استرداد: ${data.receiptId}`);
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
        tbody.replaceChildren();

        if (data.length === 0) {
            const tr = document.createElement('tr');
            const td = createCell('هنوز رسیدی صادر نشده است.', 'text-align:center; color:var(--text-sub);');
            td.colSpan = 10;
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
            pBtn.textContent = '🖨️ جزئیات / چاپ';
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