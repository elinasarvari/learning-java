# Repository & Module Layout

This project is a **single repository containing two independently buildable modules**. Unlike a multi-repo product-definition setup, there is nothing to clone alongside it — `backend/` and `frontend/` are both checked out with the repo, and this `docs/` folder sits beside them.

```
learning-java/
├── README.md            Run instructions for both modules
├── docs/                ← you are here
│   ├── architecture/    Implementation standards (this folder)
│   ├── speckit/         Spec-driven development process
│   └── reference/       Quick-reference matrices
├── backend/             Maven module — Spring Boot 3 API + Camel routes
└── frontend/            npm workspace — React 19 + Vite
```

---

## backend

| | |
|--|--|
| **Path** | `backend/` |
| **Build tool** | Maven (wrapper committed — always use `./mvnw`, never a system `mvn`) |
| **Artifact** | `com.example:app:0.0.1-SNAPSHOT` |
| **Java** | 21 |
| **Purpose** | The authoritative source for API contracts, Camel routes, and (once entities exist) the database schema |

### Run

```bash
cd backend
./mvnw spring-boot:run
```

- API: <http://localhost:8080>
- H2 console (dev): <http://localhost:8080/h2-console> — JDBC URL `jdbc:h2:mem:devdb`, user `sa`, empty password

### Build and test

```bash
cd backend
./mvnw test                      # run all tests
./mvnw clean package             # build the executable jar into target/
java -jar target/app-0.0.1-SNAPSHOT.jar
```

**Always use the wrapper.** `.mvn/wrapper/maven-wrapper.properties` pins Maven 3.9.16. A developer's system `mvn` may be a different version and resolve plugins differently, producing a build that succeeds locally and fails in CI.

**`spring-boot-devtools` is on the classpath** (runtime, optional). It restarts the application when a class file changes, so `./mvnw spring-boot:run` plus a recompile in your IDE gives hot reload. It is excluded from the packaged jar automatically.

### Version coupling

`pom.xml` pins two versions that must move together:

```xml
<parent>spring-boot-starter-parent 3.3.2</parent>
<camel.version>4.8.5</camel.version>
```

Camel 4.8 is the LTS line targeting Spring Boot 3.3.x. Bumping one without the other fails at startup. Check the Camel Spring Boot compatibility matrix before changing either.

---

## frontend

| | |
|--|--|
| **Path** | `frontend/` |
| **Build tool** | npm + Vite 8 |
| **Language** | JavaScript (ES modules) — TypeScript is a pending decision |
| **Purpose** | The authoritative source for React components, routing, and API-consumption patterns |

### Run

```bash
cd frontend
npm install
npm run dev
```

App: <http://localhost:5173>

### Build and lint

```bash
cd frontend
npm run build       # production build into dist/
npm run preview     # serve the production build locally
npm run lint        # oxlint — must pass before commit (STD-FE-007)
```

There is no `npm test` script yet — see [testing-guide.md STD-TST-005](testing-guide.md).

---

## Running both together

The two modules talk over HTTP and must both be running for the app to work end to end.

```bash
# terminal 1
cd backend && ./mvnw spring-boot:run

# terminal 2
cd frontend && npm run dev
```

**The cross-origin contract** is the coupling point between them:

| Setting | Where | Value |
|---------|-------|-------|
| Backend port | `backend/src/main/resources/application.properties` | `server.port=8080` |
| Allowed origin | `backend/src/main/resources/application.properties` | `cors.allowed-origins=http://localhost:5173` |
| Frontend dev port | Vite default | `5173` |
| Backend base URL used by the frontend | `frontend/src/App.jsx` (hard-coded — see GAP-FE-001) | `http://localhost:8080` |

Change any one of these and the other must change with it. The correct fix is to move the frontend's base URL into `VITE_API_BASE_URL` — see [frontend-patterns.md STD-FE-003](frontend-patterns.md).

**Alternative: a Vite dev proxy.** Proxying `/api` and `/camel` through the dev server removes the cross-origin call entirely, which means CORS stops being a dev-time concern:

```js
// vite.config.js
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api':   'http://localhost:8080',
      '/camel': 'http://localhost:8080',
    },
  },
})
```

With this in place `VITE_API_BASE_URL` defaults to `/api/v1` and the browser only ever talks to `localhost:5173`. Not currently configured.

---

## What is deliberately absent

| Missing | Why it matters |
|---------|----------------|
| Root-level build orchestration | There is no Makefile, Gradle root, or npm workspace tying the two modules together. Each is built independently. |
| CI configuration | No `.github/workflows/`, no pipeline. Nothing runs `./mvnw test` or `npm run lint` automatically. |
| Containerisation | No `Dockerfile`, no `compose.yaml`. There is no deployment target defined. |
| `.gitignore` at the repo root | `frontend/.gitignore` exists; `backend/target/` relies on a global ignore. Worth adding. |

None of these are blockers for a learning project. They are listed so a reader does not go looking for them.

---

## Keeping the docs honest

These architecture docs cite specific files and line-level behaviour. When you change one of the following, re-read the doc that cites it:

| If you change… | Re-check |
|---|---|
| `backend/pom.xml` dependencies | [README.md](README.md) Stack Summary, [backend-patterns.md](backend-patterns.md) Technical Context |
| `backend/src/main/resources/application.properties` | [backend-patterns.md STD-BE-008](backend-patterns.md), [database-patterns.md STD-DB-006](database-patterns.md), [integration-guide.md STD-INT-005](integration-guide.md) |
| Anything under `backend/src/main/java/com/example/app/camel/` | [integration-guide.md](integration-guide.md), [testing-guide.md STD-TST-013/014](testing-guide.md) |
| `frontend/package.json` | [frontend-patterns.md](frontend-patterns.md) Technical Context |
| Adding Spring Security, Actuator, springdoc, Flyway, or MapStruct | Clear the corresponding `GAP-` marker in the doc that owns it |
