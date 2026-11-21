/* SmartStock — persisted light/dark mode, Gentelella layout edition. */
(function () {
    'use strict';
    var root = document.documentElement;
    var STORAGE_KEY = 'smartstock-theme';
    var EDITION_KEY = 'smartstock-theme-gentelella-v1';

    function read(key) {
        try { return window.localStorage.getItem(key); } catch (e) { return null; }
    }
    function save(key, value) {
        try { window.localStorage.setItem(key, value); } catch (e) { /* optional */ }
    }
    function initialTheme() {
        // The first install switches to the requested dark dashboard. Subsequent
        // visits respect the user's choice and never silently reset the theme.
        if (read(EDITION_KEY) !== 'ready') {
            save(EDITION_KEY, 'ready');
            save(STORAGE_KEY, 'dark');
            return 'dark';
        }
        return read(STORAGE_KEY) === 'light' ? 'light' : 'dark';
    }
    function apply(theme) {
        var value = theme === 'light' ? 'light' : 'dark';
        root.setAttribute('data-theme', value);
        var dark = value === 'dark';
        document.querySelectorAll('[data-theme-toggle]').forEach(function (button) {
            button.setAttribute('aria-label', dark ? 'Switch to light mode' : 'Switch to dark mode');
            button.setAttribute('title', dark ? 'Light mode' : 'Dark mode');
            button.setAttribute('aria-pressed', String(dark));
        });
    }
    function toggle() {
        var next = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
        save(STORAGE_KEY, next);
        apply(next);
    }
    // Set before layout paints to avoid a flash of the wrong theme.
    var selected = initialTheme();
    apply(selected);
    document.addEventListener('DOMContentLoaded', function () {
        apply(root.getAttribute('data-theme'));
        document.querySelectorAll('[data-theme-toggle]').forEach(function (button) {
            button.addEventListener('click', toggle);
        });
    });
})();
