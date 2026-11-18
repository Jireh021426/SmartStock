document.addEventListener("DOMContentLoaded", () => {
    const list = document.getElementById("remList");
    if (!list) return;

    const cards = Array.from(list.querySelectorAll(".rem-card"));
    const search = document.getElementById("remSearch");
    const buttons = Array.from(
        document.querySelectorAll(".rem-filters [data-filter]")
    );
    const noMatch = document.getElementById("remNoMatch");

    const counts = {
        total: cards.length,
        pending: cards.filter(
            card => card.dataset.status === "pending"
        ).length,
        reviewed: cards.filter(
            card => card.dataset.status === "reviewed"
        ).length
    };

    document.querySelectorAll("[data-count]").forEach(element => {
        const key = element.dataset.count;
        if (Object.prototype.hasOwnProperty.call(counts, key)) {
            element.textContent = counts[key];
        }
    });

    let activeFilter = "all";

    function updateList() {
        const query = (search?.value || "").trim().toLowerCase();
        let visible = 0;

        cards.forEach(card => {
            const matchesFilter =
                activeFilter === "all" ||
                card.dataset.status === activeFilter;

            const header = card.querySelector(".rc-head");
            const searchableText =
                (header?.textContent || "").toLowerCase();

            const matchesSearch = searchableText.includes(query);
            const show = matchesFilter && matchesSearch;

            card.hidden = !show;
            if (show) visible++;
        });

        if (noMatch) {
            noMatch.hidden = cards.length === 0 || visible > 0;
        }
    }

    buttons.forEach(button => {
        button.addEventListener("click", () => {
            activeFilter = button.dataset.filter;

            buttons.forEach(item => {
                const active = item === button;
                item.classList.toggle("active", active);
                item.setAttribute("aria-pressed", String(active));
            });

            updateList();
        });
    });

    search?.addEventListener("input", updateList);

    updateList();
});
