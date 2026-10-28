# Local CAP development

Open the **Cap_App.v01 project folder** in VS Code. Install the recommended
Extension Pack for Java if prompted (it is already installed on the current PC).
Wait for Java to finish importing `backend/pom.xml` in Standard mode. The older
console project under `src/` is separate; the backend Maven project writes its
compiled classes to `backend/target/classes`.

## Start

From the project root in the VS Code terminal:

```powershell
.\scripts\start-accounts.cmd
```

If the terminal is inside `backend`, use `..\scripts\start-accounts.cmd` instead.
Enter the **PostgreSQL cap-role password**, not your CAP login password. Wait
for `Started BackendApplication`. Open and bookmark:

**http://127.0.0.1:8081/app/index.html**

Alternatively use **Terminal -> Run Task -> CAP: Start development**. It opens
the same launcher and private password prompt. No password goes into VS Code
settings, tasks or Git. Stop with **Ctrl+C**, then **Y** if Windows asks to
terminate the batch job. Start only one backend on port 8081.

## Edit and refresh

| Change | What to do |
| --- | --- |
| HTML, CSS, JavaScript, images in `frontend/` (including login/invitations) | Save, then refresh the browser. No backend restart. |
| Java in `backend/src/main/java/` | Save and wait for VS Code to compile and DevTools to restart, then refresh. Log in again if prompted. |
| `pom.xml`, dependencies, launcher scripts, environment variables, JVM options | Stop and start the launcher manually. |
| Backend `.properties`, database migrations, other backend resources | Restart manually to reliably copy resources and apply configuration/migrations. Do not edit an already-applied migration; add a new one. |

DevTools watches **compiled classes**, not Java source. VS Code has Java
auto-build enabled and Maven import/configuration updates enabled. The launcher
uses Maven `-Pdev` to include DevTools and Spring profiles `accounts,local-frontend`.
Saving source updates `target/classes`; DevTools then restarts the application
inside the same Java process. Compilation errors must be fixed before the update
can work. This is an application restart, not uninterrupted hot swapping.

Login sessions are intentionally in memory and may end after a Java restart;
accounts, workouts and food logs stay in PostgreSQL. Frontend refreshes alone
keep your session. No Live Server or browser extension is needed for real accounts.
Live Server remains a separate demo.

If Java does not restart: check VS Code **Problems**, wait for Java import/build,
and confirm **Java Projects** contains `backend`. Use **Java: Import Java Projects
into Workspace** if needed. Ensure Java is in Standard mode, not Lightweight.
As a diagnostic fallback, run these from the project root:

```powershell
cd backend
.\mvnw.cmd -Pdev compile
```

That explicitly compiles classes for DevTools. Normal editing should not need it.

## Production separation and checks

Ordinary Maven builds do not activate `dev`, so they have no DevTools dependency.
The Spring Boot plugin also excludes DevTools from packaged jars by default.
Without `local-frontend`, the backend uses bundled frontend files and its ordinary
resource caching. Development explicitly disables resource caching for `/app/`
and `/accounts/`, and disables DevTools' verbose default error settings and
LiveReload server. Authentication, ownership checks and PostgreSQL remain active.

Verified on this PC with fake accounts in a separate PostgreSQL test instance:
changing a Java health-response string caused VS Code compilation and an automatic
DevTools restart without a Maven compile command. The API returned the edited
value. Restoring it automatically restarted again. A temporary HTML title edit
and its restoration appeared after browser refresh; the frontend edit did not
restart the backend. Main app and login CSS/JS returned `Cache-Control: no-store`.
All temporary verification text was restored.

References: [Spring Boot DevTools](https://docs.spring.io/spring-boot/reference/using/devtools.html)
and [VS Code Java configuration](https://code.visualstudio.com/docs/java/java-debugging).
