/* SmartStock — Light / Dark Theme Manager */

(function () {
    "use strict";

    const STORAGE_KEY = "smartstock-theme";
    const root = document.documentElement;

    function savedTheme() {
        try {
            return localStorage.getItem(STORAGE_KEY) === "dark"
                ? "dark"
                : "light";
        } catch (error) {
            return "light";
        }
    }

    function applyTheme(theme) {
        const selected = theme === "dark" ? "dark" : "light";

        root.setAttribute("data-theme", selected);

        document.querySelectorAll("[data-theme-toggle]")
            .forEach(function (button) {
                button.setAttribute(
                    "aria-label",
                    selected === "dark"
                        ? "Switch to light mode"
                        : "Switch to dark mode"
                );

                button.setAttribute(
                    "title",
                    selected === "dark"
                        ? "Light mode"
                        : "Dark mode"
                );

                button.setAttribute(
                    "aria-pressed",
                    String(selected === "dark")
                );
            });
    }

    function toggleTheme() {
        const current = root.getAttribute("data-theme");
        const next = current === "dark" ? "light" : "dark";

        applyTheme(next);

        try {
            localStorage.setItem(STORAGE_KEY, next);
        } catch (error) {
            // Theme switching still works without storage.
        }
    }

    applyTheme(savedTheme());

    document.addEventListener("DOMContentLoaded", function () {
        applyTheme(savedTheme());

        document.querySelectorAll("[data-theme-toggle]")
            .forEach(function (button) {
                button.addEventListener("click", toggleTheme);
            });
    });
})();
