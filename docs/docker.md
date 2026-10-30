# CAP in Docker Desktop (local only)

Docker runs the packaged Java backend and original frontend in one Linux container. PostgreSQL 18.6 continues running as a Windows service. The container connects to the existing `cap` database through `host.docker.internal:5432`; no database is copied, replaced or created. Existing accounts, changed passwords, workouts and Stripe test records stay in that database.

An **image** is the built copy of CAP. A **container** is a running instance of that image. Removing the app container does not remove this database: it is outside Docker.

## Daily use

Open Docker Desktop and wait for its engine to be running. PostgreSQL must also be running. Stop any native CAP backend with Ctrl+C first: native CAP and Docker CAP share port 8081.

From the project root:

```powershell
.\scripts\start-docker.cmd
```

Open **http://127.0.0.1:8081/app/index.html** and use your existing login, including any new password you chose. The launcher returns to the prompt after CAP is healthy; leave Docker Desktop running. Closing the terminal does not stop CAP. This does not put CAP online or expose it to other devices.

Payments remain **Stripe TEST only**. Keep this running in another terminal when testing checkout:

```powershell
.\scripts\stripe-listen.cmd
```

The launcher reuses saved Windows-encrypted database/API/webhook credentials and existing local catalogue/reviewer settings. It privately prompts if a credential is missing. Use `-UpdateWebhook` if the listener secret changes, or `-RefreshCredentials` to replace saved credentials. It never substitutes a temporary database. `-WithoutPayments` is an explicit accounts-only option; it disables payment/paid-booking enforcement, like the native accounts-only launcher.

```powershell
.\scripts\docker-status.cmd
.\scripts\docker-status.cmd -Logs
.\scripts\stop-docker.cmd
.\scripts\restart-docker.cmd
```

Stop removes only this Compose project's app container, including its runtime secret copy. It keeps the image and native database. Restart creates a new container, reloads configuration and requires signing in again. A stopped container can also be removed through Docker Desktop; never delete your native PostgreSQL data directory.

## After editing code

Docker runs a packaged copy, so HTML, CSS, JavaScript and Java changes all require rebuilding the image:

```powershell
.\scripts\restart-docker.cmd -Build
```

The first build downloads Java/Maven dependencies. Subsequent builds reuse the Maven cache. Java 25 is included in the image; host Java selection does not affect it. No DevTools or H2 is packaged.

For the faster save/refresh development workflow, stop Docker CAP and keep using `.\scripts\start-stripe-test.cmd`. That native launcher still supports live frontend files and VS Code Java compilation/DevTools restart. Both workflows use the same database. Do not run both on port 8081.

## How files and credentials are handled

- Root `Dockerfile`: builds with the Maven wrapper on Java 25 JDK, then copies only the executable JAR and HTTP health probe into a Java 25 JRE image.
- `.dockerignore`: build-input allowlist. `login.txt`, `.local`, Git/history, database files, build output, notes and unrelated files are not sent into the build context.
- `compose.docker.yaml`: app only, localhost port binding, non-root UID 10001, Linux capabilities dropped, 512 MB memory limit and bounded logs. It does not start PostgreSQL or use the older `compose.accounts.yaml` database experiment.
- `start-docker.cmd`: reads the encrypted Windows cache into memory, creates a JSON/YAML runtime configuration and supplies it through an environment-backed Compose secret. Spring reads `/run/secrets/cap-runtime.yaml`. Secret values are not container environment variables, build arguments, image layers, source files or a plaintext config file in the checkout. No secrets are printed. People with Docker administrator access can read runtime secrets; do not export/commit a running configured container.
- Compose's environment-backed secret injection requires a writable container filesystem. The app still runs as a non-root user with dropped capabilities. Stopping through the helper removes the container and its runtime secret copy.
- PostgreSQL migrations are validated/applied normally without resetting data. New passwords remain hashed in PostgreSQL; the temporary `login.txt` file never enters Docker.

This is a local packaging workflow, not production hosting. Public HTTPS/domain configuration, production database/backups, staff password handover and Stripe live-mode work remain separate. The current billing code deliberately rejects live keys and non-localhost billing URLs.

## If startup fails

- **Port in use:** stop native CAP with Ctrl+C, or use `stop-docker.cmd`. The launcher never kills another process.
- **Docker unavailable:** open Docker Desktop. The helper also finds a fresh install when the current terminal has an old PATH.
- **Database connection refused/authentication failure:** confirm the Windows PostgreSQL service is running and the saved `cap` role password is correct. Inspect `docker-status.cmd -Logs`. Do not create another database to hide the error.
- **No `pg_hba.conf` entry:** Docker's host connection needs permission for database `cap` and role `cap`. Ask for a narrowly scoped rule after identifying the client address; do not enable unrestricted database access. No PostgreSQL listener, firewall or authentication changes were needed on the verified PC.
- **Payments disabled/missing catalogue:** configure the existing sandbox with the test catalogue guide, or choose `-WithoutPayments` explicitly. Stripe forwarding runs on Windows and still targets localhost:8081.

An alternate `-HttpPort` is available for isolated checks, but the default Stripe forwarding helper targets 8081. Use the default for ordinary payment testing.

References: [Docker host networking](https://docs.docker.com/desktop/features/networking/networking-how-tos/), [Compose secrets](https://docs.docker.com/compose/how-tos/use-secrets/), [multi-stage builds](https://docs.docker.com/build/building/multi-stage/).
