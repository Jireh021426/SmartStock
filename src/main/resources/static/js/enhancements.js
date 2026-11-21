/* Sort tables whose first column is "SKU" by the number at the end (…-01, …-02)
   before pagination runs, so WC-MC-01 always comes first. */
(function () {
    function run() {
        document.querySelectorAll('main table').forEach(function (t) {
            var h = t.tHead && t.tHead.rows[0];
            if (!h || !h.cells[0] || h.cells[0].textContent.trim().toUpperCase() !== 'SKU') return;
            var tb = t.tBodies[0];
            if (!tb) return;
            var rows = Array.prototype.filter.call(tb.rows, function (r) {
                return !(r.cells.length === 1 && r.cells[0].colSpan > 1);
            });
            function num(r) {
                var m = r.cells[0].textContent.trim().match(/(\d+)\D*$/);
                return m ? parseInt(m[1], 10) : Infinity;
            }
            rows.sort(function (a, b) {
                return num(a) - num(b) ||
                    a.cells[0].textContent.localeCompare(b.cells[0].textContent);
            });
            rows.forEach(function (r) { tb.appendChild(r); });
        });
    }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', run);
    else run();
})();

/* SmartStock — shared front-end enhancements
   - Pagination + quick filter for every data table
   - Peso formatting (₱1234.5 -> ₱1,234.50) and numeric column alignment
   - Double-submit protection on POST forms
   - Skip link, "/" to focus page search, dismissible notices
   Opt-outs:  <table data-no-paginate>, <form data-no-lock>, <td data-no-format>
*/
(function () {
    'use strict';

    var SIZES = [5, 10, 25, 50];
    var MIN_ROWS = 6; // show pager/filter only when more than 5 rows

    function ready(fn) {
        if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', fn);
        else fn();
    }

    function store(key, value) {
        try {
            if (value === undefined) return window.localStorage.getItem(key);
            window.localStorage.setItem(key, value);
        } catch (e) { /* storage unavailable */ }
        return null;
    }

    /* ---------- Accessibility ---------- */
    function setupA11y() {
        var main = document.querySelector('main');
        if (main) {
            if (!main.id) main.id = 'main-content';
            var skip = document.createElement('a');
            skip.className = 'ss-skip';
            skip.href = '#' + main.id;
            skip.textContent = 'Skip to content';
            document.body.insertBefore(skip, document.body.firstChild);
        }

        document.addEventListener('keydown', function (e) {
            if (e.key !== '/' || e.ctrlKey || e.metaKey || e.altKey) return;
            var t = e.target;
            var tag = (t.tagName || '').toLowerCase();
            if (tag === 'input' || tag === 'textarea' || tag === 'select' || t.isContentEditable) return;
            var search = document.getElementById('smartstock-nav-search');
            if (search) { e.preventDefault(); search.focus(); }
        });

        document.querySelectorAll('.error-msg, .error').forEach(function (el) {
            el.setAttribute('role', 'alert');
        });
    }

    /* ---------- Dismissible notices ---------- */
    function setupNotices() {
        document.querySelectorAll('.notice-msg, .notice').forEach(function (n) {
            n.setAttribute('role', 'status');
            var b = document.createElement('button');
            b.type = 'button';
            b.className = 'ss-close';
            b.setAttribute('aria-label', 'Dismiss message');
            b.innerHTML = '&times;';
            b.addEventListener('click', function () {
                n.classList.add('ss-leaving');
                setTimeout(function () { if (n.parentNode) n.parentNode.removeChild(n); }, 260);
            });
            n.appendChild(b);
        });
    }

    /* ---------- Peso formatting ---------- */
    var PESO = /^₱\s?(-?)(\d+(?:\.\d+)?)$/;
    function formatPesos() {
        var sel = 'main td, .rem-kpi h2, .tile-value, .dc-kpi h2, .sumrow-value, .figures dd, .cat-value span';
        document.querySelectorAll(sel).forEach(function (el) {
            if (el.children.length || el.hasAttribute('data-no-format')) return;
            var m = PESO.exec(el.textContent.trim());
            if (!m) return;
            var n = parseFloat(m[2]);
            if (isNaN(n)) return;
            el.textContent = '₱' + (m[1] ? '-' : '') +
                n.toLocaleString('en-PH', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        });
    }

    /* ---------- Numeric column alignment ---------- */
    var NUM = /^[₱+\-]?\s?\d[\d,]*(\.\d+)?(\s?(kg|%))?$/i;
    var MARK = /[₱.,%]|kg/i;
    var NEUTRAL = /^(-|—|–|)$/;

    function alignNumericColumns(table, bodyRows) {
        var head = table.tHead && table.tHead.rows[0];
        if (!head) return;
        for (var c = 0; c < head.cells.length; c++) {
            var any = false, ok = true, marked = false;
            for (var r = 0; r < bodyRows.length; r++) {
                var cell = bodyRows[r].cells[c];
                if (!cell) continue;
                var txt = cell.textContent.trim();
                if (NEUTRAL.test(txt)) continue;
                any = true;
                if (!NUM.test(txt)) { ok = false; break; }
                if (MARK.test(txt)) marked = true;
            }
            if (any && ok && marked) {
                head.cells[c].classList.add('ss-num');
                for (var k = 0; k < bodyRows.length; k++) {
                    if (bodyRows[k].cells[c]) bodyRows[k].cells[c].classList.add('ss-num');
                }
            }
        }
    }

    /* ---------- Pagination + filter ---------- */
    function pageList(page, pages) {
        var out = [], last = 0;
        for (var i = 0; i < pages; i++) {
            if (i === 0 || i === pages - 1 || Math.abs(i - page) <= 1) {
                if (i - last > 1) out.push('gap');
                out.push(i);
                last = i;
            }
        }
        return out;
    }

    function btn(label, aria, disabled, onClick, active) {
        var b = document.createElement('button');
        b.type = 'button';
        b.className = 'ss-page' + (active ? ' is-active' : '');
        b.textContent = label;
        b.setAttribute('aria-label', aria);
        if (active) b.setAttribute('aria-current', 'page');
        b.disabled = !!disabled;
        if (!disabled && !active) b.addEventListener('click', onClick);
        return b;
    }

    function enhanceTable(table) {
        var tbody = table.tBodies[0];
        if (!tbody || table.dataset.ssEnhanced === 'true') return;

        var rows = Array.prototype.filter.call(tbody.rows, function (r) {
            return !(r.cells.length === 1 && r.cells[0].colSpan > 1); // skip "empty state" rows
        });
        if (!rows.length) return;

        alignNumericColumns(table, rows);

        var panel = table.closest('.panel, section, .dc-panel') || table.parentNode;
        // Existing SmartStock pagers must stay authoritative; never paginate an already paginated subset.
        var hasServerPaging = panel.querySelector('.pagination-controls, .inventory-pagination, .employees-pagination, .balance-pagination, .supplier-pagination, .ss-pager');
        if (table.hasAttribute('data-no-paginate') || hasServerPaging) return;
        if (rows.length < MIN_ROWS) return;
        // Row/column spanning makes DOM-based row paging unsafe for complex tables.
        if (rows.some(function (row) { return Array.prototype.some.call(row.cells, function (cell) { return cell.rowSpan > 1 || cell.colSpan > 1; }); })) return;
        table.dataset.ssEnhanced = 'true';

        var wrap = table.closest('.table-wrap, .dc-table-wrap, .supplier-table-wrap, .inventory-table-wrap');
        if (!wrap || !wrap.contains(table)) wrap = table.parentNode;

        rows.forEach(function (r) { r._t = r.textContent.toLowerCase(); });

        var saved = parseInt(store('ss.pageSize.v2'), 10);
        var state = { size: SIZES.indexOf(saved) > -1 ? saved : 10, page: 0, q: '' };

        /* filter box */
        var tools = document.createElement('div');
        tools.className = 'ss-table-tools';
        var search = document.createElement('input');
        search.type = 'search';
        search.placeholder = 'Filter rows…';
        search.setAttribute('aria-label', 'Filter rows in this table');
        var noMatch = document.createElement('p');
        noMatch.className = 'ss-nomatch';
        noMatch.setAttribute('role', 'status');
        noMatch.hidden = true;
        noMatch.textContent = 'No rows match your filter.';
        tools.appendChild(search);
        tools.appendChild(noMatch);
        wrap.parentNode.insertBefore(tools, wrap);

        /* pager */
        var pager = document.createElement('nav');
        pager.className = 'ss-pager';
        pager.setAttribute('aria-label', 'Table pagination');
        var info = document.createElement('div');
        info.className = 'ss-pager-info';
        info.setAttribute('aria-live', 'polite');
        var controls = document.createElement('div');
        controls.className = 'ss-pager-controls';

        var sizeLabel = document.createElement('label');
        sizeLabel.className = 'ss-pager-size';
        sizeLabel.appendChild(document.createTextNode('Rows per page'));
        var sizeSel = document.createElement('select');
        SIZES.forEach(function (s) {
            var o = document.createElement('option');
            o.value = s; o.textContent = s;
            if (s === state.size) o.selected = true;
            sizeSel.appendChild(o);
        });
        sizeLabel.appendChild(sizeSel);

        var buttons = document.createElement('div');
        buttons.className = 'ss-pager-buttons';

        controls.appendChild(sizeLabel);
        controls.appendChild(buttons);
        pager.appendChild(info);
        pager.appendChild(controls);
        wrap.parentNode.insertBefore(pager, wrap.nextSibling);

        function render(scroll) {
            var f = rows.filter(function (r) { return !state.q || r._t.indexOf(state.q) > -1; });
            var total = f.length;
            var pages = Math.max(1, Math.ceil(total / state.size));
            if (state.page >= pages) state.page = pages - 1;
            var start = state.page * state.size;
            var end = Math.min(start + state.size, total);

            rows.forEach(function (r) { r.hidden = true; });
            f.slice(start, end).forEach(function (r) { r.hidden = false; });

            noMatch.hidden = total !== 0;
            info.textContent = total
                ? 'Showing ' + (start + 1) + ' to ' + end + ' of ' + total + ' rows'
                : 'No matching rows';

            buttons.innerHTML = '';
            var go = function (p) { return function () { state.page = p; render(true); }; };
            buttons.appendChild(btn('«', 'First page', state.page === 0, go(0)));
            buttons.appendChild(btn('‹', 'Previous page', state.page === 0, go(state.page - 1)));
            pageList(state.page, pages).forEach(function (p) {
                if (p === 'gap') {
                    var g = document.createElement('span');
                    g.className = 'ss-gap';
                    g.textContent = '…';
                    buttons.appendChild(g);
                } else {
                    buttons.appendChild(btn(String(p + 1), 'Page ' + (p + 1), false, go(p), p === state.page));
                }
            });
            buttons.appendChild(btn('›', 'Next page', state.page >= pages - 1, go(state.page + 1)));
            buttons.appendChild(btn('»', 'Last page', state.page >= pages - 1, go(pages - 1)));

            if (scroll) {
                var top = tools.getBoundingClientRect().top;
                if (top < 0) tools.scrollIntoView({ block: 'start' });
            }
        }

        var timer;
        search.addEventListener('input', function () {
            clearTimeout(timer);
            timer = setTimeout(function () {
                state.q = search.value.trim().toLowerCase();
                state.page = 0;
                render(false);
            }, 120);
        });
        sizeSel.addEventListener('change', function () {
            state.size = parseInt(sizeSel.value, 10);
            state.page = 0;
            store('ss.pageSize.v2', String(state.size));
            render(false);
        });

        render(false);
    }

    /* ---------- Double-submit guard ---------- */
    function setupSubmitLock() {
        document.addEventListener('submit', function (e) {
            var f = e.target;
            if (e.defaultPrevented || !f.method || f.method.toLowerCase() !== 'post') return;
            if (f.hasAttribute('data-no-lock')) return;
            var btns = f.querySelectorAll('button[type="submit"], button:not([type]), input[type="submit"]');
            // Deferred so the clicked button's name/value is still submitted
            setTimeout(function () {
                if (e.defaultPrevented) return;
                btns.forEach(function (b) {
                    b.disabled = true;
                    b.classList.add('ss-busy');
                });
            }, 0);
        });

        // Back/forward cache: restore buttons
        window.addEventListener('pageshow', function (e) {
            if (!e.persisted) return;
            document.querySelectorAll('button.ss-busy').forEach(function (b) {
                b.disabled = false;
                b.classList.remove('ss-busy');
            });
        });
    }

    ready(function () {
        setupA11y();
        setupNotices();
        formatPesos();
        document.querySelectorAll('main table').forEach(enhanceTable);
        setupSubmitLock();
    });
})();