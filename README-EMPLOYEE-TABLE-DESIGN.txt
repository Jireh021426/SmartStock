SMARTSTOCK EMPLOYEE TABLE REDESIGN - FULL REPLACEMENT FILES

Includes 6 complete files (not partial snippets):
- src/main/resources/templates/employees.html
- src/main/resources/static/css/employees.css
- src/main/java/com/smartstock/controller/EmployeeController.java
- src/main/java/com/smartstock/service/EmployeeService.java
- src/main/java/com/smartstock/repository/EmployeeRepository.java
- src/test/java/com/smartstock/EmployeePaginationTest.java

IMPORTANT:
- Designed on the source structure from your most recently tested employee controller
  (default page size 10, allowed sizes 5/10/20/50, 7-page window).
- Search uses the database and covers ALL matching employees, not just the active page.
- Export CSV exports all matching employees; no password fields; ADMIN only.
- Search term persists across navigation/page size/activate/deactivate actions.
- CSV cells are quoted and have formula-injection protection.
- The existing form, role/location rules, accessibility, pagination and theme remain.
- No DB schema changes or migrations.
- Do not blindly deploy if your working branch contains newer changes in these files;
  compare with git diff first. Run targeted tests and then complete mvn test.
