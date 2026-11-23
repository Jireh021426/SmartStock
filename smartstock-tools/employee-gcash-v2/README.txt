SmartStock Employee Dropdowns & GCash QR V2

- Fixes unreadable Role and Assigned Location native option menus in dark mode (Chrome on Windows).
- Fixes employee activation/deactivation action icon contrast in dark mode.
- Adds user-provided GCash QR original pixels at /images/gcash-merchant-qr.png.
- Opens QR when GCash is chosen on New Sale, Customer Utang Repayment and Independent-Stall Collection forms.
- UI-only. QR does not automatically verify or process a payment. Verify recipient and payment receipt outside the popup.
- Existing Java/business logic/database/Railway are not changed.

Installation: run python smartstock-tools/employee-gcash-v2/install.py from the repository root.
Use --dry-run first if desired. Existing differing QR image is never overwritten.
Backups: ~/smartstock-private-backups/employee-gcash-v2-before-*.zip

Note: Once deployed, this QR image can be viewed by anyone who can reach /images/gcash-merchant-qr.png.
