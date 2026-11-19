/* SmartStock — Remittance history filters, search, and counters */
document.addEventListener("DOMContentLoaded", function () {
    "use strict";

    const remList = document.getElementById("remList");
    if (!remList) return;

    const cards = Array.from(remList.querySelectorAll(".rem-card"));
    const filterButtons = Array.from(document.querySelectorAll(".rem-filters .chip-btn"));
    const searchInput = document.getElementById("remSearch");
    const noMatchMessage = document.getElementById("remNoMatch");

    const counts = {
        total: cards.length,
        pending: cards.filter(card => card.dataset.status === "pending").length,
        reviewed: cards.filter(card => card.dataset.status === "reviewed").length
    };

    for (const [type, count] of Object.entries(counts)) {
        document.querySelectorAll('[data-count="' + type + '"]').forEach(element => {
            element.textContent = String(count);
        });
    }

    let currentFilter = "all";
    let currentSearch = "";

    function applyFilters() {
        let visibleCount = 0;

        cards.forEach(card => {
            const matchesFilter = currentFilter === "all" || card.dataset.status === currentFilter;
            const matchesSearch = card.textContent.toLowerCase().includes(currentSearch);
            const visible = matchesFilter && matchesSearch;
            card.hidden = !visible;
            if (visible) visibleCount++;
        });

        if (noMatchMessage) {
            noMatchMessage.hidden = cards.length === 0 || visibleCount !== 0;
        }
    }

    filterButtons.forEach(button => {
        button.addEventListener("click", function () {
            currentFilter = button.dataset.filter || "all";
            filterButtons.forEach(item => {
                const selected = item === button;
                item.classList.toggle("active", selected);
                item.setAttribute("aria-pressed", String(selected));
            });
            applyFilters();
        });
    });

    if (searchInput) {
        searchInput.addEventListener("input", function () {
            currentSearch = searchInput.value.trim().toLowerCase();
            applyFilters();
        });
    }

    applyFilters();
});
