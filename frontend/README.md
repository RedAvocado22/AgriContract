# AgriContract frontend

React SPA for the Phase 1 marketplace, contract, escrow, cancellation, dispute, and arbitration flows.

## Stack

- React 19 and TypeScript 6
- Vite 8
- React Router 7
- TanStack Query for server state
- Zustand for authentication and small client-only state
- Axios with Keycloak token refresh
- React Hook Form and Zod
- Vitest, Testing Library, and Playwright

## Requirements

- Node.js `^20.19.0` or `>=22.12.0`
- npm
- For real API flows: the Phase 1 backend and imported Keycloak realm described in the repository [`README`](../README.md)

## Configuration

```bash
cp .env.example .env
npm ci
```

| Variable | Default | Purpose |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080` | API Gateway base URL |
| `VITE_KEYCLOAK_URL` | `http://localhost:8180` | Keycloak base URL |
| `VITE_KEYCLOAK_REALM` | `agricontract` | Realm name |
| `VITE_KEYCLOAK_CLIENT_ID` | `agricontract-frontend` | Public SPA client |
| `VITE_USE_MOCKS` | `false` | Use the local mock adapters instead of the real API |

Keycloak uses Authorization Code + PKCE (`S256`). Tokens stay in memory; the Axios interceptor refreshes shortly before expiry and attaches `Authorization: Bearer ...` to authenticated calls.

## Commands

```bash
npm run dev       # http://localhost:5173
npm test          # Vitest component tests
npm run lint      # Oxlint
npm run build     # TypeScript project build + Vite production bundle
npm run preview   # preview the production bundle
npm run test:e2e  # Playwright against a running real stack
```

Install the Playwright Chromium binary once before the first E2E run:

```bash
npx playwright install chromium
```

Override the Playwright target with `E2E_BASE_URL`; the default is `http://localhost:5173`.

## Routes

| Route | Access | Purpose |
|---|---|---|
| `/login` | Public | Start Keycloak login |
| `/register-profile` | Authenticated, missing profile | Create the Phase 1 user profile |
| `/listings` | Public | Browse and filter active listings |
| `/listings/:listingId` | Public | Listing detail; authenticated buyers can create offers |
| `/dashboard` | Authenticated | Marketplace and contract summary |
| `/listings/create` | SELLER | Create a product and listing |
| `/listings/mine` | SELLER | Seller listing list |
| `/contracts` | Authenticated | Buyer/seller contract list |
| `/contracts/:contractId` | Participant or ADMIN | Negotiate, sign, cancel, deliver, or dispute |
| `/escrow` | Authenticated | Escrow account and transaction history |
| `/admin/arbitrate/:contractId` | ADMIN | Allocate disputed escrow funds |

Marketplace price, category, delivery-window, search, and sort controls are applied in the frontend to the active-listing page returned by the backend.

## Tests

Component tests cover the contract action matrix and revision history. Playwright uses the real gateway, Keycloak, databases, RabbitMQ, and services to cover:

- public marketplace and Keycloak login;
- seller listing creation and buyer offer creation;
- counter-offer plus both signatures;
- seller deposit confirmation and contract activation;
- normal settlement, buyer/seller cancellation, buyer dispute, and admin arbitration.

The imported development users are `buyer1`, `seller1`, and `admin1`, all with password `pass123`.
