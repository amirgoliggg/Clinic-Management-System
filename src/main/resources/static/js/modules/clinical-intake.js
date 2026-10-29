// ==================== ۱. پذیرش، تریاژ هوشمند پرستار و ترخیص ====================

function evaluateLiveVitals() {
    const bpRaw = (document.getElementById('t-bp')?.value || '120/80').trim();
    const temp = parseFloat(document.getElementById('t-temp')?.value) || 37.0;
    const hr = parseInt(document.getElementById('t-hr')?.value, 10) || 76;

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
        nationalId: document.getElementById('p-nid').value.trim(),
        fullName: document.getElementById('p-name').value.trim(),
        age: parseInt(document.getElementById('p-age').value, 10),
        contactNumber: document.getElementById('p-phone').value.trim(),
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
    const nid = document.getElementById('t-nid').value.trim();
    if (!nid) return showToast('کد ملی بیمار را برای تریاژ وارد کنید.', 'error');

    const payload = {
        patientNationalId: nid,
        bloodPressure: document.getElementById('t-bp').value.trim(),
        bodyTemperatureCelsius: parseFloat(document.getElementById('t-temp').value),
        heartRateBpm: parseInt(document.getElementById('t-hr').value, 10),
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
    const nid = document.getElementById('nc-nid').value.trim() || document.getElementById('t-nid').value.trim();
    const proc = document.getElementById('nc-procedure').value.trim();
    const fee = parseFloat(document.getElementById('nc-fee').value) || 0;
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
    const nid = document.getElementById('lookup-p-nid').value.trim();
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
    const nid = document.getElementById('lookup-p-nid').value.trim();
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
                    showToast(`بیمار ${p.fullName} برای تریاژ و خدمات پرستاری انتخاب شد.`);
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

// ==================== ۲. اتاق معاینه پزشک و تحلیل بالینی ====================
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
    const nid = document.getElementById('c-nid').value.trim();
    if (!nid) return showToast('ابتدا کد ملی بیمار را وارد یا از جدول انتخاب کنید.', 'error');
    openPatientHistoryModal(nid, nid);
}

async function openPatientHistoryModal(nid, name) {
    try {
        const res = await apiFetch(`/api/clinical/records/patient/${encodeURIComponent(nid)}`);
        if (!res.ok) throw await res.json();
        const records = await res.json();

        document.getElementById('history-modal-title').textContent = `📂 سوابق بالینی و رمزنگاری‌شده بیمار: ${name} (${nid})`;
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
                idSpan.textContent = `پرونده #${r.recordId.substring(0, 8)} (${r.recordStatus}) • پزشک/ثبت‌کننده: ${r.doctorNationalId}`;
                const hashSpan = document.createElement('span');
                hashSpan.className = 'badge badge-success';
                hashSpan.textContent = `AES-256-GCM + HMAC: ${r.integrityHash ? r.integrityHash.substring(0, 12) : ''}...`;
                head.appendChild(idSpan);
                head.appendChild(hashSpan);

                const diagP = document.createElement('p');
                diagP.innerHTML = `<b>تشخیص / خدمت:</b> ${r.diagnosis}`;
                const rxP = document.createElement('p');
                rxP.innerHTML = `<b>نسخه / اقدام:</b> ${r.prescription}`;
                const noteP = document.createElement('p');
                noteP.style.color = 'var(--text-sub)';
                noteP.textContent = `جزئیات و علائم حیاتی: ${r.notes}`;

                const amendBtn = document.createElement('button');
                amendBtn.className = 'action-btn btn-secondary btn-sm';
                amendBtn.style.marginTop = '10px';
                amendBtn.textContent = '✏️ ثبت الحاقیه و اصلاح پرونده';
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
        patientNationalId: document.getElementById('c-nid').value.trim(),
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