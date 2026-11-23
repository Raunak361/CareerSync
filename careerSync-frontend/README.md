# CareerSync frontend

React + TypeScript + Vite frontend for the Spring Boot Career Monitor backend.

## Features included
- Keycloak login and registration redirect
- Protected application routes
- List companies followed by the current user
- Add a company by selecting from the available company catalogue
- Remove a followed company
- List opportunities and filter them client-side by experience
- User profile from `GET /users/me`
- Responsive layout for desktop and mobile

## 1. Install and run

```bash
npm install
```

Copy `.env.example` to `.env` and fill in the Keycloak values (restart the dev server after editing `.env`). Then:

```bash
npm run dev
```

Open http://localhost:5173.

## 2. Backend API contracts used

The source backend ZIP provided for this frontend has Spring context path `/api` and these controller routes:

| Method | Path | Purpose |
|---|---|---|
| GET | `/companies` | Current user's followed companies |
| POST | `/companies` | Subscribe to company IDs; body `{ "companyIds": [1, 2] }` |
| DELETE | `/companies/{id}` | Remove a company from current user's list |
| GET | `/opportunities` | Opportunities belonging to followed companies |
| GET | `/users/me` | Current user's profile; also creates the user on first authenticated request |

The Axios base URL defaults to `http://localhost:8080/api`. Change `VITE_API_BASE_URL` if your backend runs elsewhere.

## 3. One backend endpoint is missing for the company dropdown

The provided backend code does **not** expose an endpoint that returns the global company catalogue. `GET /companies` returns only companies already followed by the current user, while `POST /companies` requires company IDs. The frontend therefore expects this additional endpoint:

`GET /api/companies/available`

It should return a JSON array in the existing `CompanyResponseDto` shape, for example:

```json
[
  { "id": 1, "name": "Google", "url": "https://careers.google.com", "lastCheckedTimestamp": null },
  { "id": 2, "name": "Microsoft", "url": "https://careers.microsoft.com", "lastCheckedTimestamp": null }
]
```

The endpoint should be authenticated and return the global company catalogue (`companyRepo.findAll()`). The frontend's expected path can be changed with `VITE_AVAILABLE_COMPANIES_PATH`.

Until this endpoint is added, the Companies page can still list and delete followed companies, but its add-company dropdown cannot be populated. It displays a clear message instead of silently pretending the feature works.

## 4. Keycloak setup

Create a Keycloak **public client** for the browser application, enable Standard Flow, and configure:

- Valid redirect URI: `http://localhost:5173/*`
- Web origin: `http://localhost:5173`
- Client authentication: OFF (public client)
- Standard flow: ON
- PKCE: S256

Enable user registration in the realm if users should register themselves. The frontend uses Keycloak's hosted login and registration screens; it does not store passwords.

Set these `.env` values to match your Keycloak installation:

```env
VITE_KEYCLOAK_URL=http://localhost:8180
VITE_KEYCLOAK_REALM=career_monitor_realm
VITE_KEYCLOAK_CLIENT_ID=careersync-frontend
```

The realm and client ID above are examples/defaults, not confirmed values from the backend ZIP. Use the actual values in your Keycloak admin console.

## 5. CORS

The supplied backend `WebConfig` currently allows `*` origins for development. For deployment, replace that with your actual Vercel domain. Ensure Spring Security permits CORS preflight requests and that the allowed methods/headers include Authorization and OPTIONS.

## 6. Build check

```bash
npm run build
```

This frontend uses the endpoints found in the supplied backend source. It does not invent login/register REST endpoints: authentication is handled by Keycloak and the backend validates the resulting JWT.
