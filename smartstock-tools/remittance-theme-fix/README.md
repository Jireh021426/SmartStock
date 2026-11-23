# SmartStock Remittance History Theme Repair

Run from the SmartStock project root: `bash smartstock-tools/remittance-theme-fix/install.sh`

This is a CSS-only, backup-first, idempotent patch to `src/main/resources/static/css/remittances.css`.
It fixes the pale manager review footer in dark mode, input/label contrast, and card-header text contrast.
The installer appends a scoped override to the user's existing stylesheet to preserve newer local customizations.
The `full-source-reference/remittances.css` file contains the complete updated CSS based on the uploaded SmartStock source snapshot; it is for reference, **not** copied over customized files.

Backups are in `~/smartstock-private-backups/`. Restart the local app after installation, then hard-refresh Chrome. Test both light and dark themes.
No migration required. No production deployment.
