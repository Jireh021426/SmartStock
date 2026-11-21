/*
 * Normalizes the existing Employees and Stock History SERVER-SIDE page links.
 * Original hrefs / pageSize are preserved. No fetching, row hiding or data writes.
 * Other pagers retain their existing pagination engines and use the shared CSS.
 */
(function () {
    'use strict';

    function pageWindow(current, total) {
        var pages = new Set([1, total, current - 1, current, current + 1]);
        if (current <= 3) {
            for (var a = 1; a <= Math.min(4, total); a++) pages.add(a);
        }
        if (current >= total - 2) {
            for (var b = Math.max(1, total - 3); b <= total; b++) pages.add(b);
        }
        return Array.from(pages).filter(function (p) {
            return p >= 1 && p <= total;
        }).sort(function (a, b) { return a - b; });
    }

    function enhance(controls) {
        if (controls.dataset.ssPaginationUnified === 'true') return;
        var links = Array.from(controls.querySelectorAll('.page-button'));
        var numbered = links.filter(function (a) {
            return /^\d+$/.test(a.textContent.trim()) && a.tagName === 'A';
        });
        if (!numbered.length) return;

        var active = numbered.find(function (a) {
            return a.classList.contains('active') || a.getAttribute('aria-current') === 'page';
        });
        var current = active ? Number(active.textContent.trim()) : Number(numbered[0].textContent.trim());
        var lastLink = links.find(function (a) { return a.textContent.trim().toLowerCase() === 'last'; });
        var total = Math.max.apply(null, numbered.map(function (a) { return Number(a.textContent.trim()); }));
        if (lastLink && lastLink.href) {
            var lastParam = new URL(lastLink.href, document.baseURI).searchParams.get('page');
            if (lastParam !== null && /^\d+$/.test(lastParam)) total = Math.max(total, Number(lastParam) + 1);
        }
        if (!(total >= 1) || !(current >= 1)) return;

        var sampleURL = new URL(numbered[0].href, document.baseURI);
        var group = document.createElement('span');
        group.className = 'ss-unified-numbers';
        group.setAttribute('aria-label', 'Page numbers');
        var prev = links.find(function (a) { return a.textContent.trim().toLowerCase() === 'previous'; });
        var next = links.find(function (a) { return a.textContent.trim().toLowerCase() === 'next'; });
        if (prev) prev.setAttribute('data-ss-nav', 'previous');
        if (next) next.setAttribute('data-ss-nav', 'next');

        var pages = pageWindow(current, total);
        var previousNumber = 0;
        pages.forEach(function (number) {
            if (number > previousNumber + 1) {
                var gap = document.createElement('span');
                gap.className = 'ss-unified-ellipsis';
                gap.setAttribute('aria-hidden', 'true');
                gap.textContent = '…';
                group.appendChild(gap);
            }
            var link = document.createElement('a');
            var url = new URL(sampleURL.href);
            url.searchParams.set('page', String(number - 1));
            link.href = url.href;
            link.className = 'page-button' + (number === current ? ' active' : '');
            link.textContent = String(number);
            link.setAttribute('aria-label', 'Page ' + number);
            if (number === current) link.setAttribute('aria-current', 'page');
            group.appendChild(link);
            previousNumber = number;
        });

        numbered.forEach(function (a) { a.remove(); });
        links.forEach(function (a) {
            var label = a.textContent.trim().toLowerCase();
            if (label === 'first' || label === 'last') a.classList.add('ss-unified-hide');
        });
        if (next) controls.insertBefore(group, next);
        else controls.appendChild(group);
        controls.dataset.ssPaginationUnified = 'true';
    }

    function run() {
        document.querySelectorAll('.inventory-pagination .pagination-controls, .employees-pagination .pagination-controls').forEach(enhance);
    }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', run);
    else run();
})();
