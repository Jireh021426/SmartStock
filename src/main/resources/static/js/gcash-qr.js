/*
 * SmartStock merchant QR UI. Not a payment gateway or verification system.
 * An official merchant QR image must exist at /images/gcash-merchant-qr.png.
 */
(function () {
    "use strict";
    var modal = null;
    var lastFocus = null;
    var qrUrl = "/images/gcash-merchant-qr.png";
    var savedOverflow = null;
    function createModal() {
        if (modal) return modal;
        modal = document.createElement("div");
        modal.className = "ss-gcash-modal";
        modal.hidden = true;
        modal.innerHTML = [
            '<section class="ss-gcash-dialog" role="dialog" aria-modal="true" aria-labelledby="ss-gcash-title" aria-describedby="ss-gcash-description">',
            '<button class="ss-gcash-close" type="button" aria-label="Close GCash QR">&times;</button>',
            '<h2 id="ss-gcash-title">Pay with GCash</h2>',
            '<p class="ss-gcash-subtitle" id="ss-gcash-description">Scan the business\'s official GCash QR code using your GCash app.</p>',
            '<div class="ss-gcash-qr-frame"><img alt="Official merchant GCash payment QR code" hidden>',
            '<div class="ss-gcash-qr-not-configured" hidden><strong>GCash QR not configured</strong>',
            'The administrator must install the official merchant QR image before QR payments can be demonstrated.</div></div>',
            '<p class="ss-gcash-disclaimer">Displaying or scanning a QR code does not confirm payment. Verify the transaction independently before recording it in SmartStock.</p>',
            '<button class="ss-gcash-done" type="button">Close QR</button>',
            '</section>'
        ].join("");
        document.body.appendChild(modal);
        var image = modal.querySelector("img");
        var missing = modal.querySelector(".ss-gcash-qr-not-configured");
        image.addEventListener("load", function () { image.hidden = false; missing.hidden = true; });
        image.addEventListener("error", function () { image.hidden = true; missing.hidden = false; });
        image.src = qrUrl;
        modal.querySelector(".ss-gcash-close").addEventListener("click", closeModal);
        modal.querySelector(".ss-gcash-done").addEventListener("click", closeModal);
        modal.addEventListener("click", function (ev) { if (ev.target === modal) closeModal(); });
        document.addEventListener("keydown", function (ev) {
            if (!modal.hidden && ev.key === "Escape") { ev.preventDefault(); closeModal(); }
            if (!modal.hidden && ev.key === "Tab") {
                var buttons = Array.from(modal.querySelectorAll("button:not([disabled])"));
                if (buttons.length === 0) return;
                if (ev.shiftKey && document.activeElement === buttons[0]) { ev.preventDefault(); buttons[buttons.length - 1].focus(); }
                else if (!ev.shiftKey && document.activeElement === buttons[buttons.length - 1]) { ev.preventDefault(); buttons[0].focus(); }
            }
        });
        return modal;
    }
    function closeModal() {
        if (!modal) return;
        modal.hidden = true;
        if (savedOverflow === null) document.body.style.removeProperty("overflow");
        else document.body.style.overflow = savedOverflow;
        savedOverflow = null;
        if (lastFocus && lastFocus.isConnected) lastFocus.focus();
    }
    function openModal(from) {
        lastFocus = from || document.activeElement;
        createModal().hidden = false;
        savedOverflow = document.body.style.overflow || null;
        document.body.style.overflow = "hidden";
        modal.querySelector(".ss-gcash-close").focus();
    }
    function init() {
        var selects = Array.from(document.querySelectorAll("select")).filter(function (select) {
            return Array.from(select.options).some(function (option) { return option.value.toUpperCase() === "GCASH"; });
        });
        if (!selects.length) return;
        selects.forEach(function (select) {
            select.addEventListener("change", function () { if (select.value.toUpperCase() === "GCASH") openModal(select); });
        });
        /* A server validation error can redisplay a GCash selection. Don't unexpectedly cover its error message. */
    }
    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
    else init();
})();
