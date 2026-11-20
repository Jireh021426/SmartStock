SmartStock - Edit Independent Stall Details (2026-11-20)

This archive contains complete source file replacements, not partial patches.
It is intended for commit 22084a2 (automatic independent-stall delivery references).

New feature:
- "Edit" button beside every independent stall on /independent-stalls
- Change stall name and contact / unique account identifier
- Server-side ADMIN/MANAGER permission via existing SecurityConfig route restrictions
- Cannot edit RETAIL customers through independent-stall routes
- Duplicate contact/ID checks plus existing database unique constraint
- Keeps customer ID, type, outstanding balance, deliveries and collections untouched
- Five extra automated tests

NO DATABASE MIGRATION REQUIRED because both edited fields already exist.

INSTALL LOCALLY (from project root):
  tar -xzf "$HOME/Downloads/smartstock-independent-stall-edit-update.tar.gz" -C .

RUN FOCUSED TESTS:
  JAVA_HOME='C:\Program Files\Java\jdk-21.0.12.1' PATH="/c/Program Files/Java/jdk-21.0.12.1/bin:$PATH" mvn -Dtest=IndependentStallFlowTest test

Run ALL regression tests before deploying to Railway.
Do NOT copy anything from this archive into Railway manually; commit and push only after tests.
