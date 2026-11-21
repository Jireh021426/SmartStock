SMARTSTOCK EMPLOYEES PAGINATION — LOCAL TEST FIRST

Adds true server-side paging to Employee Management. Existing create/deactivate/activate actions, role authorization and assigned-location handling are retained.

Files included:
- src/main/java/com/smartstock/controller/EmployeeController.java
- src/main/java/com/smartstock/service/EmployeeService.java
- src/main/java/com/smartstock/repository/EmployeeRepository.java
- src/main/resources/templates/employees.html
- src/main/resources/static/css/employees.css
- src/test/java/com/smartstock/EmployeePaginationTest.java

Default 10 employees/page; selectable 5, 10, 20, 50. Seven numbered links max. Stable sort by full name, then ID. Changes to employee active status preserve the list page. Doesn't modify database schema.

Do not include this README in source deployment or run local-only test data on Railway.

After extracting to the root of SmartStock (and backing up if needed):
JAVA_HOME='C:\Program Files\Java\jdk-21.0.12.1' PATH="/c/Program Files/Java/jdk-21.0.12.1/bin:$PATH" mvn -Dtest=EmployeePaginationTest test

Then full regression: mvn test

No production deployment performed by this package.
