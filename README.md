# TrustLend

TrustLend is a Spring Boot 4 / Java 17 API with a React/Vite frontend. The backend uses MySQL for application data.

## Local setup

1. Copy `.env.example` to `.env` and set the MySQL URL, username, password, and a JWT secret. The database must already exist; the app does not create a managed database for you. Keep `.env` private and do not commit it.
2. Generate a stable JWT key once, then save it in `.env` (or a secure environment-variable store). In PowerShell, this command creates 32 random bytes encoded as Base64:

   ```powershell
   $bytes = [byte[]]::new(32)
   $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
   $rng.GetBytes($bytes)
   [Convert]::ToBase64String($bytes)
   $rng.Dispose()
   ```

   Store the result as `TRUSTLEND_JWT_SECRET`; do not regenerate it at each start, since that invalidates existing tokens.
3. To create the first administrator, set `TRUSTLEND_ADMIN_EMAIL` and `TRUSTLEND_ADMIN_PASSWORD` in the secure runtime environment. The password must be 12–72 characters. Bootstrap is skipped when both are empty; a partial pair fails startup. It only creates a missing ADMIN user and never changes an existing account/password. Remove the bootstrap password after provisioning.
4. For direct Aiven development, run `./scripts/start-aiven-backend.ps1` from PowerShell. It prompts for the Aiven hostname, classic MySQL port, username, and hidden password; sets the datasource URL to the existing case-sensitive `Trustlend` database with `sslMode=REQUIRED`; preserves an existing process `TRUSTLEND_JWT_SECRET` or prompts for it securely; then starts Spring Boot. Spring Boot does not load the root `.env` automatically (Docker Compose does).
5. Run the frontend with `cd frontend; npm ci; npm run dev`. Set `VITE_API_BASE_URL` in `frontend/.env.development` to the backend URL if it differs from `http://localhost:4040`.
6. Run `docker compose up --build` from the root after creating `.env`. The frontend is served on port 8080 and the API on port 4040 by default. Compose connects to the MySQL URL in `.env`; it does not provision a database service. Set `VITE_API_BASE_URL` if the browser should reach the API at another address.

## Account and lending workflows

- Public signup offers Borrower and Lender applicant accounts. Lender applicants remain borrowers until an ADMIN approves the pending lender request; public signup cannot assign ADMIN.
- An authenticated user can update their own display name at `PUT /api/v1/auth/me`. The server takes account identity from the verified JWT principal.
- Borrowers below the documented 550 credit-score threshold cannot submit regular or group-backed loan applications. Eligible scores determine principal limits; lenders still decide whether to fund an application.
- New loan applications follow `SUBMITTED → APPROVED/REJECTED → FUNDED → ACTIVE → REPAID`. ADMIN decisions are recorded with reviewer and timestamp in an additive review-history table; rejection requires a reason. Group-backed applications must be leader co-signed before admin approval. Legacy `PENDING` loans remain eligible for marketplace funding during migration from the earlier workflow.
- SHG members can view recent contribution activity; only active members can contribute, and the contribution is deducted from the member's simulated wallet in the same transaction that updates group savings.
- Borrowers can view their latest 20 credit-score snapshots at `GET /api/v1/credit-score/history`. `GET /api/v1/credit-score/me` records a snapshot when score inputs or results change; unchanged reads are deduplicated. Wallet history is server-paginated at `GET /api/v1/wallet/transactions?page=0&size=20` (page is zero-based; size is limited to 100) and always scoped to the authenticated user's wallet.
- Wallets, deposits, withdrawals, group contributions, funding and repayments are simulated ledger entries for this project. They do not move real money or connect to a payment provider.

## Current limits

The RAG assistant uses OpenAI `text-embedding-3-small` for document and query vectors, stores chunks and vectors in the same persistent MySQL database, ranks by cosine similarity, and grounds responses with the configured chat model (default `gpt-4o-mini`). Set `TRUSTLEND_OPENAI_API_KEY`, then authenticate as ADMIN and call `POST /api/v1/admin/ai/reindex` to index the bundled Markdown policies. `GET /api/v1/ai/status` reports configuration and indexed chunk count. Without a key or indexed corpus, chat says it is unavailable. Personal loan status is fetched only from the authenticated user's own records and is not sent to the model or shared index. Model API usage requires network access and may incur provider charges.

Credit scores use an in-process Spring cache keyed by user ID. Wallet, loan-status, and group membership/contribution writes evict cached scores. Cache contents are ephemeral and local to each backend instance; there is no distributed cache. Score history is separate from the cache and uses the additive `credit_score_history` table managed through Hibernate update mode. The scheduled overdue-installment task remains enabled at 01:00 server time. RAG indexing runs on a bounded async executor; failures are logged and indexed chunk count can be checked through the status endpoint.

The committed `.env.example` intentionally leaves all passwords and signing/provider secrets blank. Generate a stable JWT secret and keep actual database, JWT, bootstrap-admin, and AI credentials only in a private `.env` or secure environment store. For direct Aiven startup, the PowerShell launcher prompts for the JWT secret without echoing it when it is not already present in the process environment.

See [PROJECT_AUDIT.md](PROJECT_AUDIT.md) for inspected scope and validation evidence.
See the [API reference](docs/API.md) for the actual request paths, roles, and payloads.

## Automated checks

Run the backend suite with `cd backend; .\mvnw.cmd test`. Run frontend component tests, production build, and lint with `cd frontend; npm test; npm run build; npm run lint`. UI component tests mock the API; they are not browser E2E tests against a running backend. Backend persistence tests use isolated H2 and do not validate Aiven connectivity.

Frontend tests use Vitest, jsdom, and React Testing Library. `npm test` includes formatter unit tests plus component tests for Wallet transaction pagination, admin user search/pagination, and borrower credit-score history; it does not run browser E2E against the live backend.
