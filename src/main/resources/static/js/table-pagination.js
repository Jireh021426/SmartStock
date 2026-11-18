/*
 * SmartStock table pagination (no dependencies).
 *
 * Paginates any <table class="remittance-table"> or
 * <table data-paginate> on the page. Optional attribute:
 *   data-page-size="10"   (default 10)
 *
 * Rows that are a single wide cell (e.g. "No records yet.")
 * are treated as empty-state rows and are never hidden.
 */
(function () {
    "use strict";

    var SIZES = [10, 25, 50, 100];

    function makeButton(label, onClick, disabled, active, title) {
        var b = document.createElement("button");
        b.type = "button";
        b.className = "pager-btn" + (active ? " active" : "");
        b.textContent = label;
        if (title) {
            b.title = title;
            b.setAttribute("aria-label", title);
        }
        b.disabled = !!disabled;
        if (active) {
            b.setAttribute("aria-current", "page");
        }
        b.addEventListener("click", onClick);
        return b;
    }

    function pageList(current, total) {
        // 1 … 4 5 [6] 7 8 … 20
        var pages = [];
        var start = Math.max(1, current - 2);
        var end = Math.min(total, current + 2);

        if (start > 1) {
            pages.push(1);
            if (start > 2) pages.push("…");
        }
        for (var p = start; p <= end; p++) pages.push(p);
        if (end < total) {
            if (end < total - 1) pages.push("…");
            pages.push(total);
        }
        return pages;
    }

    function init(table) {
        var tbody = table.tBodies[0];
        if (!tbody) return;

        var rows = Array.prototype.filter.call(tbody.rows, function (r) {
            return !(r.cells.length === 1 && r.cells[0].colSpan > 1);
        });
        if (!rows.length) return;

        var size = parseInt(table.getAttribute("data-page-size"), 10) || 10;
        var page = 1;

        var bar = document.createElement("nav");
        bar.className = "pager no-print";
        bar.setAttribute("aria-label", "Table pagination");

        var host = table.closest(".remittance-table-scroll") || table;
        host.parentNode.insertBefore(bar, host.nextSibling);

        function render() {
            var pages = Math.max(1, Math.ceil(rows.length / size));
            if (page > pages) page = pages;

            var from = (page - 1) * size;
            var to = Math.min(rows.length, from + size);

            rows.forEach(function (row, i) {
                row.classList.toggle("page-hidden", i < from || i >= to);
            });

            while (bar.firstChild) bar.removeChild(bar.firstChild);

            var info = document.createElement("span");
            info.className = "pager-info";
            info.textContent =
                "Showing " + (from + 1) + "–" + to + " of " + rows.length;
            bar.appendChild(info);

            var sizeWrap = document.createElement("label");
            sizeWrap.className = "pager-size";
            sizeWrap.appendChild(document.createTextNode("Rows per page "));

            var select = document.createElement("select");
            SIZES.forEach(function (n) {
                var o = document.createElement("option");
                o.value = String(n);
                o.textContent = String(n);
                if (n === size) o.selected = true;
                select.appendChild(o);
            });
            select.addEventListener("change", function () {
                size = parseInt(select.value, 10) || 10;
                page = 1;
                render();
            });
            sizeWrap.appendChild(select);
            bar.appendChild(sizeWrap);

            var nav = document.createElement("div");
            nav.className = "pager-nav";

            nav.appendChild(makeButton("‹", function () {
                page--; render();
            }, page === 1, false, "Previous page"));

            pageList(page, pages).forEach(function (p) {
                if (p === "…") {
                    var gap = document.createElement("span");
                    gap.className = "pager-gap";
                    gap.textContent = "…";
                    nav.appendChild(gap);
                } else {
                    nav.appendChild(makeButton(String(p), function () {
                        page = p; render();
                    }, false, p === page, "Page " + p));
                }
            });

            nav.appendChild(makeButton("›", function () {
                page++; render();
            }, page === pages, false, "Next page"));

            bar.appendChild(nav);
        }

        render();
    }

    document.addEventListener("DOMContentLoaded", function () {
        var tables = document.querySelectorAll(
            "table.remittance-table, table[data-paginate]");
        Array.prototype.forEach.call(tables, init);
    });
})();