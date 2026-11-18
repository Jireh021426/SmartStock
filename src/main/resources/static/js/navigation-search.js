document.addEventListener("DOMContentLoaded", function () {
    const input = document.getElementById("smartstock-nav-search");
    const results = document.getElementById("smartstock-search-results");

    if (!input || !results) return;

    function getPages() {
        return Array.from(document.querySelectorAll("body > header nav a"))
            .filter(link => link.href && link.textContent.trim())
            .map(link => ({
                name: link.textContent.trim().replace(/\s+/g, " "),
                url: link.href
            }));
    }

    function hideResults() {
        results.hidden = true;
        results.replaceChildren();
    }

    function updateResults() {
        const query = input.value.trim().toLowerCase();
        results.replaceChildren();

        if (!query) {
            hideResults();
            return;
        }

        const matches = getPages()
            .filter(page => page.name.toLowerCase().includes(query))
            .slice(0, 8);

        if (matches.length === 0) {
            const message = document.createElement("div");
            message.className = "topbar-search-empty";
            message.textContent = "No matching pages";
            results.appendChild(message);
        } else {
            matches.forEach(page => {
                const link = document.createElement("a");
                link.href = page.url;
                link.textContent = page.name;
                results.appendChild(link);
            });
        }

        results.hidden = false;
    }

    input.addEventListener("input", updateResults);

    input.addEventListener("keydown", function (event) {
        if (event.key === "Escape") {
            hideResults();
            input.blur();
        }

        if (event.key === "Enter") {
            const first = results.querySelector("a");
            if (first && !results.hidden) {
                window.location.assign(first.href);
            }
        }
    });

    document.addEventListener("click", function (event) {
        if (!event.target.closest(".topbar-search")) {
            hideResults();
        }
    });
});
