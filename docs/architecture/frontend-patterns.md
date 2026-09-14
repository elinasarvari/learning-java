# Frontend Patterns

**Repo**: `frontend/` | **Stack**: JavaScript / React 19 / Vite 8 | **Status**: Starter template; most standards prescriptive

---

## Technical Context

| Item | Value | Status |
|------|-------|--------|
| **Language** | JavaScript (ES modules, `"type": "module"`) | ✅ — TypeScript is a pending decision |
| **Framework** | React 19.2 + Vite 8.2 (`@vitejs/plugin-react`, Oxc transform) | ✅ |
| **UI Component Library** | None adopted | ⚠️ Pending decision |
| **Server State** | TanStack Query | ❌ Prescribed; app uses raw `fetch` (GAP-FE-001) |
| **HTTP Client** | `fetch` (native) | ✅ — no Axios, no shared client yet |
| **Forms** | None adopted | — |
| **Routing** | None adopted (single-page `App.jsx`) | — |
| **Authentication** | None | ❌ GAP-ARC-006 |
| **Icons** | Static SVG sprite at `public/icons.svg` | ✅ |
| **Lint** | oxlint 1.75 with the `react` and `oxc` plugins | ✅ |
| **Unit Testing** | Vitest + React Testing Library | ❌ Prescribed, not installed |
| **E2E Testing** | Playwright | ❌ Not installed |
| **API Types** | None (plain JS, no generated models) | ❌ Blocked on the TypeScript decision |
| **Node** | Any current LTS | — |

> **Read this before applying any standard below.** The frontend is the unmodified Vite React starter plus one `fetch` call. Standards marked *prescribed* describe the target shape for the first real feature — they are not descriptions of existing code. The two open questions that gate most of them (TypeScript? component library?) are tracked in [TECHNICAL_DECISIONS_PENDING.md](TECHNICAL_DECISIONS_PENDING.md), Decisions 3 and 4.

---

## Project Structure

Current tree, with prescribed additions marked.

```text
frontend/
├── index.html
├── vite.config.js
├── package.json
├── .oxlintrc.json
├── public/
│   ├── favicon.svg
│   └── icons.svg                    # SVG sprite; referenced via <use href="/icons.svg#id">
└── src/
    ├── main.jsx                     # createRoot + StrictMode
    ├── App.jsx                      # single page — currently the whole app
    ├── App.css
    ├── index.css
    ├── assets/                      # imported images (hero.png, react.svg, vite.svg)
    ├── api/                         # (prescribed)
    │   ├── client.js                #   shared fetch wrapper — STD-FE-003
    │   ├── services/                #   raw HTTP per feature
    │   │   └── <feature>.service.js
    │   ├── hooks/                   #   TanStack Query wrappers — STD-FE-004
    │   │   └── use<Feature>Queries.js
    │   └── types/                   #   generated API models (blocked on the TS decision)
    ├── components/                  # (prescribed) shared, feature-agnostic components
    ├── hooks/                       # (prescribed) custom non-API hooks
    ├── lib/                         # (prescribed)
    │   ├── endpoints.js             #   API path constants
    │   └── queryClient.js           #   TanStack Query client configuration
    ├── pages/                       # (prescribed) one thin route shell per route
    │   └── <feature>/<feature>.jsx
    ├── sections/                    # (prescribed) substantive feature UI
    │   └── <feature>/
    ├── routes/                      # (prescribed) route definitions
    └── test/                        # (prescribed) shared test setup + renderWithProviders
```

---

## STD-FE-001 — Component Library Policy

**Normative Rule**: This project has **not** adopted a component library. Until one is chosen (Decision 4), UI is built from semantic HTML plus the CSS in `src/App.css` / `src/index.css`. Once a library is adopted, it becomes the single source for every interactive control, and raw `<button>`, `<input>`, `<select>`, and `<table>` become PROHIBITED for interactive UI.

**Applies today**:
- Use semantic elements — `<button>` not `<div onClick>`, `<nav>`, `<section>`, `<ul>` for lists
- Every interactive element MUST be keyboard reachable and have an accessible name
- Decorative images and SVGs MUST carry `alt=""` and `aria-hidden="true"`; meaningful ones MUST have a real `alt`

`App.jsx` already follows the SVG convention:

```jsx
<svg className="icon" role="presentation" aria-hidden="true">
  <use href="/icons.svg#documentation-icon"></use>
</svg>
```

**Anti-Patterns**:
- ❌ Introducing a component library ad hoc in one feature without recording the decision
- ❌ Mixing two component libraries
- ❌ `<div onClick>` — not focusable, not announced, no keyboard activation
- ❌ Adding `target="_blank"` without `rel="noopener noreferrer"` (the starter links in `App.jsx` omit it — fix on next touch)

**Traceability**: `frontend/src/App.jsx`, `frontend/package.json` (no UI library dependency)

**Confidence**: High (accurately describes the current state)

---

## STD-FE-002 — Styling

**Normative Rule**: Styling MUST live in CSS files, not in inline `style={{}}` attributes. Shared design values (colors, spacing, font stacks) MUST be declared once as CSS custom properties in `src/index.css` and referenced by name — never repeated as literals across components.

**Pattern**:

```css
/* src/index.css */
:root {
  --color-surface: #ffffff;
  --color-text: #213547;
  --color-accent: #646cff;
  --space-3: 1rem;
  --radius-md: 8px;
}
```

```css
/* src/App.css */
.hero {
  background: var(--color-surface);
  padding: var(--space-3);
  border-radius: var(--radius-md);
}
```

```jsx
<div className="hero">…</div>
```

**Anti-Patterns**:
- ❌ `style={{ marginBottom: '24px' }}` — invisible to the design system, unthemeable, uncacheable
- ❌ A hex color literal repeated in more than one CSS rule
- ❌ Introducing a CSS-in-JS library (styled-components, emotion) alongside plain CSS

**Traceability**: `frontend/src/index.css`, `frontend/src/App.css`

**Confidence**: Medium (files exist; the custom-property convention is prescribed)

---

## STD-FE-003 — API Client Layer

**Normative Rule**: All HTTP calls MUST go through one shared client module at `src/api/client.js`. The backend base URL MUST come from `import.meta.env.VITE_API_BASE_URL` — a hard-coded `http://localhost:8080` is PROHIBITED. Feature service functions MUST be grouped in `src/api/services/<feature>.service.js`. Services MUST NOT be called directly from components — only from query hooks.

**Shared client** (prescribed):

```js
// src/api/client.js
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

/**
 * Unwraps the backend's ApiResponse envelope and throws a typed error on failure.
 * See docs/architecture/api-standards.md STD-API-004 for the envelope shape.
 */
export async function request(path, options = {}) {
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    ...options,
  })

  if (response.status === 204) return null

  const body = await response.json().catch(() => null)

  if (!response.ok) {
    const error = new Error(body?.errors?.[0]?.message ?? 'Request failed')
    error.status = response.status
    error.errors = body?.errors ?? []
    throw error
  }

  return body?.data ?? null
}
```

Two things this buys: components never see the `{ data, errors }` envelope, and `error.status` is always available for the 404-vs-500 branch in STD-FE-006.

**Service pattern** (prescribed):

```js
// src/api/services/asset.service.js
import { request } from '../client'

export const assetService = {
  getById: (id) => request(`/assets/${id}`),
  search: (params) => request(`/assets?${new URLSearchParams(params)}`),
  create: (body) => request('/assets', { method: 'POST', body: JSON.stringify(body) }),
}
```

**Environment configuration**:

```
# frontend/.env.development
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

Only variables prefixed `VITE_` are exposed to client code by Vite. That prefix is a safety boundary — never put a secret behind it; anything in `import.meta.env` ships to the browser.

> ⚠️ **GAP-FE-001**: `src/App.jsx` currently calls `fetch('http://localhost:8080/api/hello')` inline, with the origin hard-coded. That breaks this standard on two counts. It is starter scaffolding and MUST be migrated when the first real feature lands.

**Anti-Patterns**:
- ❌ `fetch` called directly from a component
- ❌ A hard-coded origin anywhere outside `.env.*`
- ❌ Duplicating envelope-unwrapping logic per service
- ❌ Swallowing a non-2xx response — always throw so the query hook surfaces `isError`

**Traceability**: `frontend/src/App.jsx` (the anti-pattern), `frontend/vite.config.js`

**Confidence**: Low (prescriptive — `src/api/` does not exist yet)

---

## STD-FE-004 — TanStack Query Hooks

**Normative Rule**: All server state MUST be managed via TanStack Query. Data fetching and mutations MUST be wrapped in custom hooks in `src/api/hooks/use<Feature>Queries.js`. Components MUST consume only the custom hook, never the service directly. `useState` + `useEffect` MUST NOT be used to manage server state.

**Setup** (prescribed):

```js
// src/lib/queryClient.js
import { QueryClient } from '@tanstack/react-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000, retry: 1, refetchOnWindowFocus: false },
  },
})
```

```jsx
// src/main.jsx
createRoot(document.getElementById('root')).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
)
```

**Hook pattern**:

```js
// src/api/hooks/useassetQueries.js
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { assetService } from '../services/asset.service'

export const assetKeys = {
  all: ['assets'],
  detail: (id) => ['assets', 'detail', id],
  search: (params) => ['assets', 'search', params],
}

export function useasset(id) {
  return useQuery({
    queryKey: assetKeys.detail(id),
    queryFn: () => assetService.getById(id),
    enabled: Boolean(id),
  })
}

export function useCreateasset() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: assetService.create,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: assetKeys.all }),
  })
}
```

Exporting a `assetKeys` object rather than inlining array literals is what makes `invalidateQueries` reliable — a typo in an inline key silently invalidates nothing.

**Consumption in a component**:

```jsx
const { data: asset, isLoading, isError, error } = useasset(id)
```

Because `client.js` already unwraps the envelope, `data` is the payload — no `data.data`.

**Anti-Patterns**:
- ❌ `useState` + `useEffect` + `fetch` for server data — this is exactly what `App.jsx` does today (GAP-FE-001)
- ❌ Manual `loading` / `error` state for an API call
- ❌ Inline `queryKey: ['assets', id]` literals instead of the exported key factory
- ❌ Calling a service function inside a component body

**Traceability**: prescribed; `@tanstack/react-query` is not in `package.json`

**Confidence**: Low (prescriptive)

---

## STD-FE-005 — Page and Component Organization

**Normative Rule**: Page components MUST live in `src/pages/<feature>/<feature>.jsx` and act as thin route shells — page metadata, layout wrapper, and route-parameter extraction only. Substantive UI (grids, dialogs, forms, feature-scoped hooks) MUST live in `src/sections/<feature>/`. Feature-agnostic shared components MUST live in `src/components/`.

**Pages vs Sections**:
- **`src/pages/<feature>/`** — one file per route. Contains only: document title, layout wrapper, `useParams`/`useNavigate`, and imports from `src/sections/<feature>/`. No substantive UI logic.
- **`src/sections/<feature>/`** — the real implementation. Sub-folders per component (`<feature>-grid/`, `<feature>-dialog/`), a `hooks/` folder for feature-scoped hooks, and co-located tests.
- **`src/components/`** — only components used by more than one feature. A component used once belongs in that feature's `sections/` folder.

**Pattern**:

```jsx
// src/pages/asset-profile/asset-profile.jsx
import { useParams } from 'react-router-dom'
import { useasset } from '../../api/hooks/useassetQueries'
import { assetProfileBanner } from '../../sections/asset-profile/asset-profile-banner'

export default function assetProfile() {
  const { id } = useParams()
  const { data: asset, isLoading, isError, error } = useasset(Number(id))

  if (isLoading) return <p role="status">Loading…</p>
  if (isError) return <ErrorMessage error={error} resource="asset" />

  return <assetProfileBanner asset={asset} />
}
```

**File naming**: kebab-case files, PascalCase component names, one exported component per file. `asset-profile.jsx` exports `assetProfile`. This matches the existing convention (`App.jsx` → `App`) while keeping multi-word filenames readable.

**Confidence**: Low (prescriptive — `pages/` and `sections/` do not exist)

---

## STD-FE-006 — Error Handling in Components

**Normative Rule**: API errors MUST be rendered as a user-facing message, never as a raw error object or a stack trace. HTTP 404 MUST show a resource-specific not-found message; 5xx MUST show a generic fallback. Error containers MUST carry `role="alert"` so screen readers announce them.

**Pattern**:

```jsx
// src/components/error-message.jsx
export function ErrorMessage({ error, resource = 'data' }) {
  const message = error?.status === 404
    ? `The ${resource} you are looking for does not exist.`
    : `Failed to load ${resource}. Please try again later.`

  return <div role="alert" className="error-banner">{message}</div>
}
```

`error.status` is populated by the shared client in STD-FE-003 — which is the reason that client throws a decorated `Error` rather than returning a result object.

**Anti-Patterns**:
- ❌ `{JSON.stringify(error)}` or `{error.message}` straight from a 500 — server messages leak internals
- ❌ A silent `.catch(() => {})` that leaves the UI in a permanent loading state
- ❌ An error banner with no `role="alert"`

> The current `App.jsx` does `.catch(() => setMessage('Could not reach backend'))`. The message is user-safe, but the pattern (manual state, no role, no status branch) is the one this standard replaces.

**Confidence**: Low (prescriptive)

---

## STD-FE-007 — Linting

**Normative Rule**: `npm run lint` MUST pass before any commit. The oxlint configuration at `frontend/.oxlintrc.json` is the single source of lint truth. `react/rules-of-hooks` is an **error** and MUST NOT be downgraded or suppressed with an inline disable comment.

```json
{
  "$schema": "./node_modules/oxlint/configuration_schema.json",
  "plugins": ["react", "oxc"],
  "rules": {
    "react/rules-of-hooks": "error",
    "react/only-export-components": ["warn", { "allowConstantExport": true }]
  }
}
```

**Rationale for `rules-of-hooks` as an error**: a conditional or nested hook call produces state corruption that manifests as an unrelated bug several renders later. It is not a style rule.

**Traceability**: `frontend/.oxlintrc.json`, `frontend/package.json` (`"lint": "oxlint"`)

**Confidence**: High

---

## STD-FE-008 — API Model Types (Blocked)

**Normative Rule** (blocked): Once the project adopts TypeScript (Decision 3) and springdoc (GAP-API-001), request/response types MUST be generated from the OpenAPI spec into `src/api/types/` via an `npm run models:generate` script. Hand-written duplicates of a backend DTO are PROHIBITED.

```json
"scripts": {
  "models:generate": "openapi-typescript http://localhost:8080/v3/api-docs -o src/api/types/api.d.ts"
}
```

**Until then**: document the expected shape of an API payload with a JSDoc `@typedef` next to the service function that returns it. Editors will use it for completion, and it converts cleanly to a real type later.

```js
/**
 * @typedef {object} assetResponse
 * @property {number} id
 * @property {string} name
 */
```

**Blocked on**: [TECHNICAL_DECISIONS_PENDING.md](TECHNICAL_DECISIONS_PENDING.md) Decision 3, and GAP-API-001.

**Confidence**: Low (blocked)

---

## STD-FE-009 — Test IDs

**Normative Rule**: All interactive elements MUST carry a `data-testid` attribute. Test assertions MUST prefer `screen.getByRole()` for semantic queries, fall back to `screen.getByTestId()` for interactive elements without a distinguishing accessible name, and use `screen.getByText()` only as a last resort.

```jsx
<button data-testid="go-back-to-summary-button" onClick={handleGoBack}>
  Go back to summary
</button>
```

`getByRole` is listed first deliberately: a query that finds an element the way a screen reader does is simultaneously a test and an accessibility assertion. `data-testid` exists for the cases where that is genuinely ambiguous, not as the default.

> See [testing-guide.md STD-TST-006](testing-guide.md) for the full frontend testing pattern.

**Confidence**: Low (prescriptive — no tests exist)

---

## AI Implementation Hints

- **Read the Status column first.** Most of this file describes the target, not the code. `src/api/`, `src/pages/`, and `src/sections/` do not exist yet.
- **Base URL**: always `import.meta.env.VITE_API_BASE_URL`. The hard-coded `http://localhost:8080` in `App.jsx` is the bug, not the pattern.
- **State management**: TanStack Query for server data, `useState` for local UI state only.
- **Hook pattern**: Service → Hook → Component. Never Service → Component.
- **Envelope**: the shared client unwraps `{ data, errors }`. Components see the payload directly — no `data.data`.
- **Language**: the project is plain JavaScript. Do not add `.ts`/`.tsx` files or a `tsconfig.json` until Decision 3 is made.
- **Component library**: none. Do not add MUI, Chakra, or shadcn without recording Decision 4.
- **Pitfall**: `style={{}}` for design-token values — use a CSS class and a custom property.
- **Pitfall**: `useEffect` + `fetch` is the anti-pattern this document exists to replace.
- **Lint**: `npm run lint` must pass; never suppress `react/rules-of-hooks`.
