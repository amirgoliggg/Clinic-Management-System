/**
 * موتور بومی و بدون وابستگی رسم نمودارهای SVG به سبک Apple Health / iOS 27 Widgets
 */
const ChartsEngine = {
    PALETTE: ['#38bdf8', '#34d399', '#818cf8', '#fbbf24', '#fb7185', '#a78bfa', '#2dd4bf'],

    /**
     * رسم نمودار حلقوی چندبخشی (Multi-Segment SVG Donut Chart) به همراه راهنما
     */
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

        // حلقه پس‌زمینه
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
            right.textContent = seg. formattedValue !== undefined ? seg.formattedValue : seg.value;

            row.appendChild(left);
            row.appendChild(right);
            legend.appendChild(row);
        });

        layout.appendChild(svgWrap);
        layout.appendChild(legend);
        container.appendChild(layout);
    },

    /**
     * رسم نوارهای توزیع افقی (Horizontal Distribution Bars)
     */
    renderHorizontalBars(containerId, items, unitSuffix = '') {
        const container = document.getElementById(containerId);
        if (!container) return;
        container.replaceChildren();

        const maxVal = Math.max(1, ...items.map(i => parseFloat(i.value) || 0));

        items.forEach((item, idx) => {
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

    /**
     * رسم گراف زنجیره بلوکی هش برای پنل حسابرسی امنیتی
     */
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