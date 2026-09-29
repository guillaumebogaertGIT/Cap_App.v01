# Technologies and Concepts in Use

Reviewed: September 29, 2026. These are learning topics present in the project, not claims of user understanding or mastery.

- **Java/OOP:** classes, constructors, collections and methods in `src/`; records, streams, UUIDs and date/time APIs in the backend.
- **Persistence/errors:** text parsing and checked I/O exceptions; Jackson JSON, temporary files, atomic replacement and in-memory state. Console loading stops on malformed input; console saving is not atomic.
- **Spring Boot:** startup, REST controllers, GET/POST, request bodies, constructor dependency injection, services, configuration properties and exception advice.
- **HTTP:** browser fetch, JSON, status codes, async/await, timeouts and CORS. UI visibility/CORS are distinct from server authorization.
- **Browser UI:** semantic HTML, native form validation, templates, DOM rendering, events, hash routes, dialogs, focus and ARIA.
- **Responsive CSS:** Flexbox/Grid, custom properties, media queries, flexible sizing, theme overrides and system color preference. Older desktop-first and newer mobile-first rules coexist.
- **State/domain modeling:** browser versus backend storage, planned versus actual sets, session snapshots, exact-rep records, frozen targets and idempotent completion retries.
- **Tooling/testing:** Maven wrapper, JUnit, Spring integration tests, temporary test directories, Java HTTP client, Git ignore rules and VS Code Java configuration.

Real authentication, SQL/migrations, deployment and AI integration remain documented future topics, not implemented technologies. Teach unfamiliar concepts explicitly under `AGENTS.md` and the master plan; code presence is not evidence of understanding.
