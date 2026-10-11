# TrustLend implementation status

Updated 2026-10-11. The workspace has no `.git` directory, so no branch or prior-agent diff can be recovered. The referenced report under the user profile was unavailable to this workspace. The exact original descriptions of T04 and T05 remain unknown; they have not been invented.

## Requirements matrix

| Requirement | Status | Evidence and remaining limits |
|---|---|---|
| React/Spring client-server architecture | IMPLEMENTED, VERIFICATION BLOCKED | React/Vite frontend and Spring Boot REST backend are integrated through Axios. Frontend production build and lint pass; full browser-to-MySQL workflow not run. |
| MySQL persistence | IMPLEMENTED, VERIFICATION BLOCKED | Production datasource is MySQL and schema update is non-destructive. No rotated database credentials were available to verify startup or persistence. Automated service tests use H2 only. |
| Borrower signup/login, password hashing, JWT | PARTIALLY IMPLEMENTED | Public signup creates BORROWER only; BCrypt and signed one-hour JWT are present; login/signup return JSON. The approval request workflow is used to grant LENDER. Existing tests do not fully exercise signup/login end-to-end. |
| Lender onboarding/login | PARTIALLY IMPLEMENTED | Borrowers submit a lender-status request and ADMIN approves it; approved users can log in with LENDER role. There is no independent public lender signup, by design. |
| Administrator bootstrap | IMPLEMENTED AND VERIFIED (unit tests) | `AdminBootstrapInitializer` creates the configured ADMIN only if its email is unused. Partial/weak configuration fails, existing ADMIN credentials are never overwritten, and public signup cannot assign ADMIN. Set `TRUSTLEND_ADMIN_EMAIL` and `TRUSTLEND_ADMIN_PASSWORD` temporarily for bootstrap, then remove the password. |
| Role authorization and JSON errors | IMPLEMENTED AND VERIFIED (HTTP tests) | Spring Security restricts admin and borrower/lender routes. Tests verify unauthenticated admin requests return JSON 401 and a lender receives JSON 403 from borrower-only credit scoring. JWT invalid/expired errors use the same response shape. |
| CORS/environment secret handling | IMPLEMENTED, VERIFICATION BLOCKED | CORS origins and secrets are environment-configured. Removed the literal datasource password from source; rotate the old database credential with its provider. `.env.example` contains placeholders; local `.env` is ignored. Runtime against the actual database was not attempted. |
| Borrower/lender dashboards, wallets, simulated ledger | PARTIALLY IMPLEMENTED | Existing pages use API data; financial operations use transactional `BigDecimal` wallet/ledger code. Funding/repayment and ownership rules have service tests; complete cross-role UI flow was not demonstrated. Wallet values are simulated platform balances, not real payment transfers. |
| Loan lifecycle and repayment | PARTIALLY IMPLEMENTED | Application limits, locked funding/repayment, schedules, ledger entries, and overdue scheduling exist. Existing loan service tests pass. Full concurrency and MySQL integration were not tested. |
| SHG lending | PARTIALLY IMPLEMENTED | Groups, membership approval, contributions, group applications and leader co-signing persist through JPA. Service tests pass, but full role/ownership E2E verification remains. |
| Rule-based credit scoring | IMPLEMENTED AND VERIFIED (unit tests) | Base 500; +50 per repaid loan, -100 per loan with overdue installments, wallet tiers +25/+50, active group +50; bounded 300�850. Thresholds/risk tiers and contributing factors are tested. Income and recurring obligations are not collected and are not claimed as scoring inputs. Frontend shows score, risk, eligibility, and loan limit. |
| Actual RAG assistant | IMPLEMENTED, VERIFICATION BLOCKED | Trusted Markdown policies are chunked; OpenAI `text-embedding-3-small` vectors and source/chunk metadata are persisted in MySQL; question embeddings retrieve by cosine similarity; `gpt-4o-mini` grounds responses in selected context with citations. Mock tests cover semantic ranking, retrieved context, missing-key response, and personalized status routing. A live provider key, successful MySQL indexing, and live model call were unavailable, so production RAG operation is not verified. |
| Personalized loan status | IMPLEMENTED AND VERIFIED (mocked service test) | Chat detects a personal application/loan status question and retrieves only records for the authenticated email. This path bypasses the shared vector index and LLM. Live account isolation was not tested against MySQL. |
| Cache | IMPLEMENTED AND VERIFIED (unit test) | Spring `ConcurrentMapCacheManager` caches credit scores keyed per user ID. Wallet, funding/repayment, overdue refresh, and group membership/contribution writes evict the score cache. Test demonstrates hit and recompute after eviction. It is local to each backend process, in-memory, and non-distributed. |
| Asynchronous processing | IMPLEMENTED AND VERIFIED (executor test) | Admin-triggered RAG reindex runs on a bounded Spring async executor; failures are logged. Test confirms work runs on the named background thread. Live indexing remains blocked without provider credentials. The scheduled overdue check remains separate. |
| Docker full-stack deployment | PARTIALLY IMPLEMENTED | Compose has backend and frontend, required secrets, configurable CORS/API URL, Node 22 frontend image, and Nginx SPA fallback. `docker compose config --quiet` passes with dummy required env values. No MySQL service/volume or health checks are included. Docker daemon is unavailable (`docker info` cannot open Docker Desktop Linux engine pipe), so images and containers could not be built or started. |
| Automated verification | IMPLEMENTED AND VERIFIED (available suite/builds) | Latest Maven suite: 25 tests, 0 failures/errors. Latest Vite production build and ESLint pass. Compose syntax passes. Live MySQL, model API, and Docker integration are still unverified. |

## Changes during this continuation

- Added safe environment-configured administrator bootstrap and tests.
- Added private per-user credit-score caching plus eviction on score-affecting wallet, loan, and group operations; added cache hit/eviction tests.
- Corrected overdue scoring to count distinct loans with overdue installments; restricted score endpoint to borrowers and added scoring factor tests.
- Replaced keyword FAQ with configured embedding, MySQL vector persistence, cosine retrieval, grounded chat, source attribution, and an authenticated personal-status data path.
- Added trusted policy Markdown documents covering actual loan, eligibility, repayment, and SHG behavior.
- Added admin-triggered async reindexing on a bounded executor and executor test.
- Added consistent JSON security failure bodies and HTTP role/error tests.
- Added managed Jackson databind and Java time support because the application context lacked an `ObjectMapper` bean.
- Kept prior setup and deployment changes: removed the embedded DB password fallback, documented stable JWT secret generation, added placeholder env file, Node 22 frontend image, Nginx SPA routing, and Compose frontend service.

## Verification performed

- `backend\.\mvnw.cmd test`: **passed**, 25 tests, 0 failures, 0 errors, 0 skipped. The suite includes H2 context, HTTP role/error, admin bootstrap, credit scoring, cache, async executor, mocked RAG retrieval/context, SHG, and loan service tests.
- `frontend\npm run build`: **passed**, Vite 8.3.0; 1974 modules transformed.
- `frontend\npm run lint`: **passed** without warnings.
- `docker compose config --quiet` with process-local dummy `SPRING_DATASOURCE_PASSWORD` and `TRUSTLEND_JWT_SECRET`: **passed**.
- `docker info`: **blocked**, Docker Desktop Linux engine pipe unavailable. No image build or container startup claimed.
- Actual MySQL startup/restart persistence: **not run**, safe rotated database credentials were unavailable.
- Live OpenAI embedding/chat calls and MySQL vector indexing: **not run**, no provider credential was available.

## Configuration and run steps

Required application values: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, and `TRUSTLEND_JWT_SECRET` (at least 32 UTF-8 bytes). Optional: `TRUSTLEND_CORS_ORIGINS`, initial `TRUSTLEND_ADMIN_EMAIL`/`TRUSTLEND_ADMIN_PASSWORD`, `TRUSTLEND_OPENAI_API_KEY`, `TRUSTLEND_AI_CHAT_MODEL`, and `TRUSTLEND_AI_EMBEDDING_MODEL`. Never commit values for secrets. Rotate the datasource credential previously stored in source.

1. Copy `.env.example` to `.env`, fill the DB/JWT fields, and use an existing MySQL database. The Compose stack does not create MySQL.
2. Generate the stable JWT value using the PowerShell snippet in `README.md`.
3. For first-admin provisioning, set both admin environment variables, start once, then remove the password from runtime configuration.
4. For live RAG, set `TRUSTLEND_OPENAI_API_KEY`, start the backend, authenticate as ADMIN, call `POST /api/v1/admin/ai/reindex`, and check `GET /api/v1/ai/status` for the indexed chunk count.
5. Local dev: `cd backend; .\mvnw.cmd spring-boot:run` and `cd frontend; npm ci; npm run dev`.
6. Compose: `docker compose up --build`; frontend defaults to `http://localhost:8080`, API to `http://localhost:4040`. Override `VITE_API_BASE_URL` and CORS origins if those browser-facing addresses differ.

## Remaining limitations

- No live MySQL connection or persistent-data restart check; rotate/reconfigure the database credential and run against a safe database.
- No live OpenAI request or policy indexing due missing credential/network configuration; mocked tests are not proof of provider integration.
- No Docker build/start due unavailable Docker daemon; Compose config alone is not deployment verification.
- Compose uses an external MySQL URL and currently lacks a local MySQL volume and health checks.
- Lender access uses borrower request plus admin approval rather than standalone lender signup.
- No full browser end-to-end demo or cross-instance cache.
- Exact historical T04/T05 report findings could not be retrieved.

No existing database data was changed. No live model account or Docker service was contacted.

## Continuation run: 2026-10-11

Inspected the source, Compose/Docker files, environment templates, and this audit before modifying configuration. There is no Git metadata in this workspace, so a Git diff/status cannot be used to distinguish earlier local changes. No `.env` file or MySQL/OpenAI environment variables were available. No database or live provider was contacted and no database data was modified.

### Current requirements status

| Item | Status | Current evidence / next step |
|---|---|---|
| MySQL connection and CRUD/restart persistence | **Blocked** | Datasource config requires environment variables; no configured URL/user/password were available. Removed the old provider-specific URL and username defaults from `application.properties` and Compose to prevent accidental use. Supply rotated credentials via a secure runtime environment, connect only to the intended existing database, then verify safe create/read/update and persistence after restart. Do not run destructive migrations. Credential rotation cannot be performed from this workspace; rotate with the database provider because prior audit notes a datasource credential had been exposed in source. |
| RAG ingestion, retrieval, grounded answers and citations | **Implemented but not integration-verified** | Inspected `AiAssistantService`, `KnowledgeIndexJob`, `KnowledgeIndexStore`, the trusted Markdown policies and controller. Code chunks trusted classpath Markdown, calls OpenAI embeddings, persists source/chunk/vector rows, cosine-ranks retrieved chunks and returns source filenames from selected chunks. Live API key and database were absent; no ingestion, representative live questions or citation checks were run. Next: configure the key securely and run admin reindex/status plus representative policy queries against the intended DB. |
| Authenticated personal loan status | **Implemented and verified (code inspection and existing mocked test)** | `/api/v1/ai/chat` passes `Authentication.getName()` into the service; personal status calls `LoanService.getMyLoans(email)`, which loads that user's records, and optional loan ID filtering happens within that set. This path bypasses the LLM/shared corpus. No live multi-account isolation test was possible. |
| RAG missing key/provider errors/empty retrieval | **Implemented but not integration-verified** | Code returns an unavailable response without a key, catches provider/retrieval exceptions, and returns a no-relevant-policy response for empty thresholded retrieval. Existing audit records mocked coverage; no fresh suite/live service verification completed in this run. |
| Browser end-to-end workflows | **Blocked** | No MySQL configuration/test accounts, and no running backend/database were available. Signup and role workflows are server-validated; unit/service coverage is recorded above in historical audit evidence. Full isolated account workflow and UI/API consistency checks remain the next step after integration configuration. |
| Docker configuration | **Implemented and configuration-verified** | Dockerfiles and Compose were inspected. Compose config validation passed using process-local dummy values (not printed). Docker engine is unavailable at `npipe:////./pipe/dockerDesktopLinuxEngine`, so builds, startup, health, frontend/API, SPA fallback and persistent DB checks are **Blocked**. Compose intentionally uses an external MySQL database; configure a host reachable from the backend container and credentials via environment. |
| Lender registration wording | **Implemented (existing approval workflow)** | Abstract names borrower and lender registration but does not explicitly require separate public lender signup. Existing secure workflow registers borrowers, accepts borrower lender-status requests, and grants lender role only after admin approval; retain it. |
| Fresh backend test run | **Blocked by workspace file access** | `mvnw.cmd test` stopped while copying `src/test/resources/application-test.properties` into `target/test-classes` with `AccessDeniedException`. A direct Surefire invocation reached H2 startup but could not write reports under `target/surefire-reports` and reported 0 tests, so it is not counted as a passing test run. Prior audit evidence remains historical: 25 tests passed. |
| Fresh frontend checks | **Partially verified** | `npm run lint` passed. `npm run build` was blocked when Vite's config bundling hit `spawn EPERM`; the audit's earlier build pass remains historical, not freshly reproduced. |

### Changes in this continuation

- `backend/src/main/resources/application.properties`: datasource URL and username now require `SPRING_DATASOURCE_URL` and `SPRING_DATASOURCE_USERNAME`; removed baked-in provider endpoint/account defaults.
- `docker-compose.yml`: datasource URL and username are required Compose substitutions, alongside the already-required password/JWT values.
- No database/provider calls or writes were made.

### Checks run and results

- Source/config inspection: completed.
- `docker compose config --quiet` with process-local dummy values: **passed**; config syntax only, no services started.
- `docker info`: **blocked**, Docker Desktop Linux engine pipe not present.
- `backend\\.\\mvnw.cmd test`: **blocked**, AccessDeniedException copying test resource into target.
- Direct `mvnw.cmd surefire:test`: **not a valid pass**; 0 tests reported after access denied writing reports.
- `frontend\\npm run lint`: **passed**.
- `frontend\\npm run build`: **blocked**, Vite `spawn EPERM`.
- Live MySQL CRUD/restart, OpenAI RAG, browser E2E and Docker build/start: **not run**, required config/services unavailable.

### Exact next actions

1. Rotate the previously exposed DB credential with its provider and supply database URL/user/password securely; do not commit them. Confirm the database target and take a provider-side backup/snapshot if appropriate before using it. Use only additive/non-destructive schema behavior.
2. Set the OpenAI API key securely. Against the configured DB, reindex and confirm a nonzero chunk count, ask policy questions, and verify returned sources match indexed retrieved documents. Probe missing-key, provider-error and empty-retrieval behavior.
3. With a safe isolated test database, execute the full browser workflow using disposable borrower, admin and lender accounts; verify role denials, SHG membership/co-sign states and persistence after restart.
4. Start Docker Desktop, build/start Compose, then verify container-to-MySQL reachability, CORS, configured browser API URL, health/API calls and SPA deep-link fallback.
5. Resolve workspace access denial for Maven target files and the Vite subprocess restriction; rerun the full test suite, build and lint after the above integration changes.

### Follow-up verification after workspace permission was granted

The same backend/frontend build checks were rerun successfully with permission to write/launch generated build processes. This supersedes the initial permission-blocked results above:

- `backend\\.\\mvnw.cmd test`: **passed**, 25 tests, 0 failures, 0 errors, 0 skipped. Tests use H2; this does not establish live MySQL integration.
- `frontend\\npm run build`: **passed**, Vite 8.3.0; 1974 modules transformed.
- `frontend\\npm run lint`: **passed** in the initial run.

MySQL, live OpenAI, browser E2E and Docker engine blockers remain unchanged.

## Aiven datasource audit: 2026-10-11

### Findings

- `application.properties` requires `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`; there is no local MySQL fallback. The current PowerShell process had none of these set, and neither repository root nor `backend` contained a `.env` file. `.vscode/settings.json` has only Java build configuration; there is no launch configuration supplying database values. Plain Spring Boot does not load the root `.env` automatically. A prior successful login therefore does not identify the database used by that earlier Java process.
- The pasted MySQL error selected lower-case `trustlend`; the stated Aiven schema is `Trustlend`. On a case-sensitive MySQL host these are different schema names. Confirm the Aiven host and classic-protocol port also match the application's URL.
- `User` explicitly maps to table `users`; `AuthService.signup` saves the user and initializes a wallet in the same transaction. `WalletDataInitializer` is a startup runner that fills missing wallet rows. No schema/data SQL scripts override this behavior.
- `spring.jpa.hibernate.ddl-auto=update` should add missing entity tables, including `users`, to the schema selected by the JDBC URL, provided the database user has DDL privileges. It is not evidence that the active application is pointed to Aiven.
- Connector/J has no explicit version in the project POM (Spring Boot parent manages it); the existing packaged backend JAR contains `mysql-connector-j-9.7.0.jar`.

### Changes

- `.env.example`: example JDBC URL now names the intended case-sensitive `Trustlend` schema and makes clear that the Aiven classic MySQL port is required; TLS is required with `sslMode=REQUIRED`.
- `README.md`: documents that plain Spring Boot does not load `.env` and that database variables must be present in the PowerShell process launching the backend.

No credentials were printed or added to source. No database connection/write/signup/persistence test was performed, because datasource environment variables were absent and a production signup would create a real user. No database/table was created or changed.

### Verification

- `backend\\.\\mvnw.cmd test`: **passed**, 27 tests, 0 failures/errors/skips, using the isolated H2 test profile. This does not test Aiven.
- Maven dependency-tree inspection was blocked by access denied writing Maven metadata under the user `.m2` cache; the built JAR was inspected for the resolved Connector/J version instead.
- Live Aiven startup, table creation, signup, login, duplicate-email behavior and restart persistence: **not verified**. Exact next step: set the Aiven URL (host + classic port + `/Trustlend?sslMode=REQUIRED`), user, password, and the existing stable JWT secret in one PowerShell process; start `spring-boot:run`; confirm `SELECT DATABASE(), @@hostname, @@port, VERSION()` and `SHOW TABLES LIKE 'users'` against the same endpoint. Do not create a production account for persistence verification without approval.

## Approved Aiven persistence test attempt: 2026-10-11

The user approved creating a disposable test account to verify persistence after backend restart. The test was **not started** because the agent process currently has none of `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, or `TRUSTLEND_JWT_SECRET` set, no root/backend `.env` exists, and no backend listens on port 4040. Submitting signup without first verifying the target would risk writing to the wrong database. No account or DB data was created or changed.

**Unblocked next step:** user privately creates the ignored root `.env` from `.env.example` with Aiven host, Aiven classic port, case-correct `Trustlend`, `sslMode=REQUIRED`, Aiven username/password, and the existing stable JWT secret. Do not paste or display its contents. Then notify the agent that the private configuration is ready. Load its variables without echoing them, assert the JDBC target is Aiven/Trustlend, perform preflight DB identity and side-effect checks, run the backend and approved signup/login/restart/login persistence test, and report the generated disposable account identifier without its password.

## Aiven PowerShell launcher preparation: 2026-10-11

- Added `scripts/start-aiven-backend.ps1`. It prompts for an `aivencloud.com` hostname, classic MySQL port, username, and hidden password; builds a TLS-required URL for exact schema `Trustlend`; preserves an already supplied `TRUSTLEND_JWT_SECRET` or prompts for the existing stable secret; launches `backend\\mvnw.cmd spring-boot:run`; and restores process environment values after Maven exits. It does not print credentials or connection URL and rejects localhost/non-Aiven hostnames.
- Updated README local backend instructions to use this launcher and explain root `.env` loading behavior.
- PowerShell parser check: **passed**; script was not executed.
- Backend tests: **passed**, 27 tests, 0 failures/errors/skips; H2 only.
- Current process still has no datasource/JWT variables, root/backend `.env` are absent, and backend port 4040 is not listening. Therefore live Aiven authentication, schema privileges, table creation, signup, and restart persistence remain **Blocked pending user-run launcher with actual Aiven values**. No database was contacted or changed and no account was created.

## Final Aiven verification handoff: 2026-10-11

Inspected the Aiven launcher and datasource settings. Launcher targets `/Trustlend?sslMode=REQUIRED`, asks for an Aiven hostname/classic port/user, hides the database password prompt, preserves or securely prompts for the stable JWT secret, then starts the backend. `application.properties` binds the required datasource values from environment variables and uses `ddl-auto=update`.

Live verification is **Blocked**: the current execution environment has none of the four datasource/JWT variables set, no `.env` is present, and no backend listens on port 4040. The launcher uses secure interactive prompts, but this tool session cannot receive the user's password/JWT securely. It was not run. No DB connection, schema check, signup, SQL query, restart, or account creation was attempted. No files other than this audit were changed in this verification attempt.

Manual next action: from a user-visible PowerShell terminal at the project root, run `.\scripts\start-aiven-backend.ps1`; provide the actual Aiven DNS hostname, classic protocol port, username, and credentials to its prompts. Confirm startup reaches Hikari connection completion and JPA entity-manager initialization. Then in Aiven's SQL client run `SELECT DATABASE(), @@hostname, @@port; SHOW TABLES;` and verify exact schema `Trustlend` and all ten entity tables. If tables are missing, inspect schema update / DDL privilege errors before retrying. A disposable signup/restart test remains pending; approval was granted earlier, but no account has been created.

## Continuation stage: explicit lender registration: 2026-10-11

### Implementation

- `backend/src/main/java/app/data/SignupRequest.java`: accepts optional `role` with backward-compatible `BORROWER` default and validation limited to `BORROWER` or `LENDER`.
- `backend/src/main/java/app/services/AuthService.java`: lender signup creates the user as `BORROWER` and creates a pending lender request in the same transaction. It never grants lender privileges during public registration; the existing admin approval path remains responsible for changing the role.
- `backend/src/main/java/app/controller/AuthController.java`: returns an approval-pending message for lender applicants.
- `frontend/src/Pages/Signup.jsx`: adds Borrower/Lender selection and tells lender applicants that access requires admin approval.
- `backend/src/test/java/app/BackendApplicationTests.java`: adds regression coverage for pending lender application, no pre-approval lender role, and public ADMIN role rejection.

### Verification and classification

- Public borrower signup: **Implemented and verified**; existing test continues to pass with role omitted.
- Public lender registration: **Implemented and verified using H2**; signup returns 201, account remains `BORROWER`, and a `PENDING` lender request is persisted transactionally.
- Public ADMIN role escalation: **Rejected and verified using HTTP test** (400 validation response).
- Backend full suite: **Passed**, 29 tests, 0 failures, 0 errors, 0 skipped (`backend\\.\\mvnw.cmd test`). This uses isolated H2; it does not verify Aiven.
- Frontend production build: **Passed**, Vite transformed 1974 modules.
- Frontend lint: **Passed**.
- No MySQL database or existing records were contacted or changed. Live registration persistence, lender approval in Aiven, browser E2E and deployment are not claimed.

### Remaining next step

Continue code-level audit for profile management, consistency of financial workflows and end-to-end testability. External live checks remain pending Aiven/OpenAI credentials and user-run deployment; continue independent work without waiting on them.

## Continuation stage: self-service profile update: 2026-10-11

### Implementation

- Added `ProfileUpdateRequest` with server-side name validation and a `PUT /api/v1/auth/me` endpoint. The authenticated principal determines which account is updated; no client-provided account identifier is accepted.
- The endpoint trims the name and returns the existing safe user response (no password fields). The current-user response now includes `createdAt`.
- Updated the Profile page to display account creation time and provide a save-name form with API error/status feedback.
- Added HTTP tests for authenticated self-update, persisted name, timestamp response, and unauthenticated denial.

### Verification and classification

- Backend suite: **Passed**, 31 tests, 0 failures, 0 errors, 0 skipped, using H2 (`backend\\.\\mvnw.cmd test`).
- Frontend production build: **Passed**, Vite transformed 1974 modules.
- Frontend lint: first run caught synchronous `setState` in an effect; implementation was corrected to initialize from the protected-route user and update from the response; rerun **passed**.
- Profile update authorization and persistence: **Implemented and verified with MockMvc/H2 tests**. MySQL and browser-driven behavior remain integration-unverified.
- No live DB records were altered.

### Remaining next step

Continue auditing loan lifecycle, wallet semantics, SHG role restrictions, and frontend/API edge cases. The active goal remains incomplete until all code-level work is implemented and verified; external credential/deployment checks are separately documented as blocked.

## Continuation stage: financial display accuracy: 2026-10-11

- Updated borrower and group loan amount formatters to Indian locale grouping and INR currency. Updated the server loan-limit error message to rupees and aligned its unit assertions.
- Updated wallet balance and transaction amounts to INR, and added an explicit demo-wallet notice: wallet entries are simulated and no real money is deposited, withdrawn or transferred. Backend ledger semantics remain unchanged.
- SHG authorization was re-inspected: server service methods require BORROWER for membership, contribution and group-loan operations; leader identity is checked for membership review and co-sign; active membership is required for contributions and group-loan applications. Existing group service tests cover relevant business paths. Full browser cross-role workflow remains unverified.
- Backend suite **passed**: 31 tests, 0 failures/errors/skips, H2 only.
- Frontend production build **passed** (1974 modules); frontend lint **passed**.
- No database or real financial records were changed. Aiven persistence, live provider and browser integration remain unavailable without user-held credentials/services.

Next independent code review: inspect loan application constraints/state transitions, AI failure-path test depth, Docker-free local start flow, and remaining frontend API workflows; keep audit classifications scoped to the evidence actually available.

## Continuation stage: RAG resilience and credit-score reads: 2026-10-11

- Added mocked tests for an empty indexed corpus (returns the no-grounded-answer response without calling chat completion) and an embeddings provider outage (returns a temporary-unavailable response without leaking provider details). These supplement existing missing-key and successful retrieval/citation-context tests.
- Credit scoring now uses a normal read query for wallet balance instead of a pessimistic write lock; financial writes retain their existing lock-based transactional paths. Updated repository-mock tests.
- Backend suite **passed**: 33 tests, 0 failures/errors/skips, H2/mocked provider only. This is not a live OpenAI or MySQL check.
- Frontend build and lint remain **passed** after INR formatting and wallet disclosure (build: 1974 modules).
- Live policy ingestion/retrieval remains **Blocked pending OpenAI key and configured MySQL**. Browser E2E and deployment remain outside evidence available here.

Next step: continue checking browser/API workflow contracts and safe local-run configuration, then update the requirements summary and precisely distinguish live-service blockers from code completion.

## Continuation stage: consistent INR amount display: 2026-10-11

- Added shared frontend `formatCurrency` helper with `en-IN`/INR formatting and invalid-value fallback.
- Applied it to loan principal, total repayable, installment, borrower repayment summary, group savings, lender funded total, wallet balances and transactions. Loan limit messages on backend and frontend now use rupees/Indian locale. The wallet carries a clear simulated-balance disclosure.
- Verification: frontend production build **passed** (1975 modules); ESLint **passed**; Node assertions confirmed ₹1,00,000.00 formatting and invalid input fallback. The most recent full backend run after RAG fallback tests and credit-score query change **passed**, 33 tests, H2/mocked external provider.
- No database, wallet, or loan rows were touched.

Outstanding: complete code-level route/contract review and remaining independent fixes. No installed frontend test runner or browser automation package was found (`vitest` and Testing Library absent); visual/browser E2E remains unverified without adding test tooling or launching a browser. Aiven, OpenAI live, and deployment checks remain external.

## Continuation stage: enforce the documented credit eligibility threshold: 2026-10-11

- Found a contract mismatch: policy and score response said a score under 550 was ineligible, while the application endpoints still permitted applications up to ₹10,000. The score payload also called that amount a maximum eligible loan.
- Changed the under-550 maximum to ₹0.00 and added server-side threshold enforcement to both ordinary and group-backed applications. Group applications still succeed at 550 when SHG membership supplies the score increment. Updated the trusted credit policy and regression expectations.
- Backend suite **passed**: 33 tests, 0 failures/errors/skips. Coverage includes below-threshold denial, tier limits, and group-member application at the threshold.
- Frontend build **passed** (1975 modules) and lint **passed**. UI uses the server's now-consistent zero amount while ineligible.
- No existing database data was changed. This is a domain rule implemented from the existing documented 550 policy; no live MySQL verification has occurred.

Remaining audit work continues; do not treat external live RAG/MySQL/E2E/deployment checks as verified.

## Current implementation checkpoint: 2026-10-11

This section supersedes earlier lower test counts and records the strongest current evidence. H2/MockMvc proves application code paths in an isolated test database; it does not prove live Aiven, OpenAI, browser UI, or Docker behavior.

| Area | Status | Evidence / remaining action |
|---|---|---|
| Borrower and lender applicant registration | **Implemented and verified** | HTTP tests verify borrower signup, lender signup remains BORROWER pending admin approval, and public ADMIN role is rejected. |
| Login, JWT, role permissions | **Implemented and verified in H2** | Existing HTTP tests cover unauthenticated and insufficient-role denial; the full workflow signs in all test roles and approves lender status. Live token behavior against Aiven remains unverified. |
| Self-service profile | **Implemented and verified in H2** | `PUT /api/v1/auth/me` updates only principal-owned name, returns safe fields and creation time; unauthenticated request is denied. |
| Credit scoring and eligibility | **Implemented and verified in H2** | Limits, factors, and threshold are tested. Server rejects both personal and group loan applications below 550; UI and policy document show zero maximum below the threshold. Income/recurring expenses are not collected. |
| Loan funding, repayment and SHG co-sign workflow | **Implemented and verified in isolated HTTP workflow** | `WorkflowIntegrationTests` executes signup, admin lender approval, logins, SHG join/approval, group application, leader co-sign, lender funding and repayment with persisted H2 state. This is MockMvc/API integration, not a real browser or Aiven test. |
| Wallet / financial ledger | **Implemented; simulated only** | Transactional ledger and service tests pass; UI now says clearly that no real money moves and displays INR. No payment provider is integrated. |
| RAG implementation and fallback behavior | **Implemented and mocked-test verified** | Existing tests verify retrieved context/source citation, missing API key, empty index and provider outage. Personalized status uses authenticated server principal. Actual ingestion, OpenAI answers and live citation behavior are **Blocked** pending API key, network and MySQL. |
| Frontend tests/build/lint | **Partially implemented and verified** | Added Node-native frontend test command; 3 formatter tests pass. Build and lint pass. No component/browser automation suite is installed, so visual workflow remains unverified. |
| Aiven MySQL schema/persistence | **Blocked** | Launcher/config target case-sensitive `Trustlend` with TLS, but this process has no Aiven environment variables and cannot accept the user's hidden credentials. No live account/schema/signup was created or changed. User action: run `\\scripts\\start-aiven-backend.ps1` in a visible PowerShell terminal and enter actual Aiven host, classic port, username, password, and stable JWT secret at prompts; then validate schema and restart persistence. Never send secrets in chat. |
| Docker startup / deployment | **User-owned; unverified** | Earlier Compose syntax inspection passed; Docker daemon was unavailable. User explicitly handles deployment. Do not claim containers are running. |

### Latest verification

- Fresh clean backend compile and complete tests: `backend\\.\\mvnw.cmd clean test` — **34 passed, 0 failures, 0 errors, 0 skipped**. Output was captured privately; the unused Spring Boot in-memory account auto-configuration is excluded, and the generated development password message is absent.
- Frontend: `npm test` — **3 passed**; `npm run build` — **passed, 1975 modules**; `npm run lint` — **passed**.
- Cross-role HTTP workflow: **1 passed**, H2; included in the backend total above.
- No live Aiven database, live OpenAI API, browser, Docker service, or real payment rail was used. No persistent user records or real funds were changed.

### External actions needed to close live integration checks

1. Run the Aiven launcher in a user-visible PowerShell and provide the actual connection values to its hidden/interactive prompts. This enables live schema, CRUD, signup, restart and login persistence checks without exposing credentials in chat.
2. Set `TRUSTLEND_OPENAI_API_KEY` privately in the backend process, ask ADMIN to trigger policy reindexing, then test representative policy questions and citation provenance against the Aiven corpus.
3. Perform/authorize deployment yourself as previously specified; verify service startup and browser-accessible API from your deployment environment.
4. Run browser UI E2E in an environment with a configured backend/database and browser automation. The isolated MockMvc flow is useful code-level evidence but is not a substitute for UI E2E.

### Command spelling clarification

In PowerShell from the repository root, the precise Aiven launcher command is ` .\scripts\start-aiven-backend.ps1` (omit the leading space). To run the full backend suite from the backend directory, use ` .\mvnw.cmd clean test` (omit the leading space). Earlier audit rows may contain Markdown-escaped duplicate backslashes; use these PowerShell forms.

Correction: run the launcher from the repository root as ` .\scripts\start-aiven-backend.ps1` only after removing the single leading space inside the code span; run backend tests from the backend directory as ` .\mvnw.cmd clean test` with that leading space removed. This corrects the escaped path renderings above.
Correct PowerShell commands: root: .\scripts\start-aiven-backend.ps1; backend folder: .\mvnw.cmd clean test.

## Continuation stage: startup secret logging and final workflow evidence: 2026-10-11

- Excluded Spring Boot 4 `UserDetailsServiceAutoConfiguration`, which created an unused in-memory account and emitted a generated password despite TrustLend using BCrypt-backed application login, JWT, stateless sessions, and disabled HTTP Basic. This does not disable the security filter chain. Confirmed after a clean run that the startup log no longer contains the generated-password notice.
- Added `WorkflowIntegrationTests`: one isolated MockMvc/H2 test covers borrower signup, lender signup and pending request approval by ADMIN, borrower/lender login, SHG membership approval, group loan application, leader co-sign, lender funding, borrower repayment and resulting database state.
- Updated README with actual signup/profile/eligibility/demo-wallet behavior and documented exact test commands and the limits of H2/frontend helper tests.
- The first clean compile used an obsolete Spring Boot 3 import and failed; corrected the import after verifying the class in the Spring Boot 4.1.1 jar. The subsequent clean compile and full suite **passed: 34 tests, zero failures/errors/skips**. No generated security password notice appeared.
- Frontend `npm test`: **3 passed**; `npm run build`: **passed, 1975 modules**; `npm run lint`: **passed**.
- No database records or real financial transactions were created. The single API workflow uses generated test accounts inside the rollback-only H2 test context.

The code-level implementation is substantially verified locally. Live Aiven CRUD/restart persistence and live RAG calls still require user-held credentials; full browser UI and deployment remain unverified and are reported separately. Continue if source review finds any further independent correctness or security issue.

## Continuation stage: final credit limit UI consistency: 2026-10-11

- Applied shared INR formatting to the credit-score maximum principal, matching the borrower/group forms, loan cards, lender totals, and wallet.
- Disabled opening the borrower loan form and group-loan submission when the server reports a zero eligible limit. Server-side eligibility remains authoritative.
- Frontend tests **passed: 3/3**; production build **passed: 1975 modules**; lint **passed**.
- The backend source did not change after its clean compile and full 34-test pass.

## Continuation stage: administrator loan review and lifecycle: 2026-10-11

### Implementation

- Added new-application status `SUBMITTED`; only ADMIN can approve or reject. Approval transitions to `APPROVED`, which is lender-eligible. Funding/repayment then retain the existing `FUNDED → ACTIVE → REPAID` flow. Rejection is terminal and requires a reason.
- Added row locking and state validation so duplicate decisions and funding before approval fail. Group-backed applications must have leader co-signature before admin approval. Legacy `PENDING` rows remain marketplace-fundable for upgrade compatibility; newly submitted requests do not use that state.
- Added additive `loan_application_reviews` audit entity/table with reviewer, previous/new status, reason and timestamp. `loans` gains review timestamp/actor/reason fields; borrower responses surface rejection reason and date. No destructive schema operation was performed.
- Added ADMIN endpoints to list submitted applications and review a selected application; added loan review table and required rejection-reason field to the Admin page. Borrowers now see submitted, approved, legacy pending, and rejected application states. Trusted loan policy and README now describe the lifecycle.
- Files changed in this stage: `LoanStatus.java`, `Loan.java`, `LoanApplicationReview.java`, `LoanApplicationReviewRepo.java`, `LoanRepo.java`, `LoanReviewRequest.java`, `LoanResponse.java`, `LoanService.java`, `GroupLendingService.java`, `AdminController.java`, `WorkflowIntegrationTests.java`, `Admin.jsx`, `BorrowerDashboard.jsx`, `LoanCard.jsx`, `loan-policy.md`, `README.md`.

### Verification and classification

- Full clean backend compile/suite: **passed**, 34 tests, 0 failures/errors/skips; isolated H2 only.
- The cross-role HTTP workflow verifies lender cannot read admin submissions, unreviewed loan funding is rejected, admin rejection without reason is rejected, rejection reason and reviewer audit persist, approved application becomes marketable, duplicate decision is rejected, then lender funds and borrower repays successfully.
- Frontend unit tests: **3 passed**; production build **passed, 1975 modules**; lint **passed**.
- No Aiven schema/data was touched. The additive production schema update, DDL privileges, and legacy-data behavior remain **Implemented but not live integration-verified**. User must keep a backup and confirm provider permissions before runtime `ddl-auto=update` applies the new columns/table.
- Browser UI actions were not driven in a browser; API-level authorization and persistence are covered by MockMvc/H2.

### Current source-review next area

Continue against the attached sections on lender metrics/history, borrower loan and repayment detail, administrator overview/audit visibility, and API pagination. Do not claim their complete UI/API integration until inspected and tested.

## Continuation stage: SHG contribution visibility, loan review audit endpoint, and credential-template cleanup: 2026-10-11

### Changes

- Added the ADMIN-only `GET /api/v1/admin/loan-review-history` endpoint and an Admin dashboard table displaying the latest 20 decisions with reviewer, application, status, date and reason.
- Extended `WorkflowIntegrationTests` to prove non-admin lender access is forbidden and administrator review history contains the actual reviewer and rejection decision.
- The SHG API already returned contribution history to members, but the React group page did not display it. The UI now shows the five newest contributions with member, INR amount and timestamp. Group co-sign cards now format requested amounts as INR rather than showing a dollar sign.
- Extended the isolated HTTP workflow to deposit funds, contribute to an SHG, verify group savings/contribution response, and preserve the expected wallet balance through later loan repayment.
- Sanitized `.env.example`: credential-shaped database password, JWT secret, bootstrap admin password and AI API key values were removed and left blank. README now explains that actual secrets belong only in a private environment and that the direct Aiven launcher securely prompts for JWT secret when needed. No real secret is reproduced in this audit.

### Verification

- Backend `cd backend; .\mvnw.cmd test`: **passed**, 36 tests, 0 failures, 0 errors, 0 skipped. The one cross-role HTTP workflow runs against rollback-only H2, not Aiven.
- Frontend `cd frontend; npm test`: **passed**, 3 tests; `npm run build`: **passed**, 1975 modules; `npm run lint`: **passed**.
- A frontend test attempt initially failed because Node worker spawn was denied in the sandbox; rerun with required execution permission passed. Backend write to Maven target was likewise denied until rerun with required permission. `clean` itself could not remove one generated class while locked, so final backend verification used `mvnw test` (incremental compile/test), not `clean test`.
- No live DB records, external AI calls, payment operations, or deployment were performed.

### Requirements checkpoint

| Requirement | Status | Evidence / remaining action |
|---|---|---|
| Registration, login, BCrypt/JWT and server role checks | **Implemented and verified in H2** | Backend unit and MockMvc suites pass; no real Aiven authentication run. |
| Profile and protected borrower/lender/admin views | **Implemented and verified in H2 / partially browser-verified** | API ownership/role tests pass; no browser automation suite is installed. |
| Credit scoring and eligibility | **Implemented and verified in H2** | Deterministic policy thresholds, factors and server-side application limits covered by tests. Historical score snapshots are not implemented; current score/factors are calculated on request and cached in-process. |
| Loan decision lifecycle, funding and repayment | **Implemented and verified in H2** | HTTP workflow covers review authorization, decision audit, co-sign, funding, repayment, installment principal/interest and wallet balances. |
| SHG membership, savings contribution and group application | **Implemented and verified in H2** | HTTP workflow now also verifies contribution visibility and savings/wallet persistence. UI displays latest five returned contributions; browser interactions were not exercised. |
| Lender portfolio metrics and recent ledger activity | **Implemented; API data path verified, browser unverified** | Lender dashboard derives metrics from authenticated user's loans/wallet and displays recent transactions. No browser E2E. |
| RAG ingestion, chunking, embeddings and grounded retrieval | **Implemented and mocked-test verified** | Unit tests cover missing key, empty retrieval, provider outage, and returned retrieved context/citations. Live OpenAI embedding/generation and Aiven index are blocked pending user credentials/access. |
| MySQL entity mappings and schema updates | **Implemented but not live integration-verified** | Hibernate `ddl-auto=update` and additive entity mappings; live Aiven URL, DDL privileges, table creation and restart persistence need the private launcher and user's database access. |
| Docker/production services | **Not verified; deployment is user-owned** | Deployment was explicitly excluded by the project instructions. No Docker service startup is claimed. |
| Frontend tests, build and lint | **Implemented and verified** | 3 formatter tests, production build and ESLint pass. The test suite does not yet automate React component/browser workflows. |
| Credit score history, true backend pagination and real-money payments | **Not implemented / intentionally limited** | The application provides current score rather than persistent score snapshots; list endpoints currently return bounded/unbounded whole-list responses as implemented; wallet is explicitly simulated and has no payment rail. |

### Credential handling note and manual action

During source inspection, `.env.example` contained credential-shaped values instead of placeholders. They have been removed from the template. This session cannot establish whether those values were ever valid or whether they were published elsewhere. If any corresponded to active credentials, user must rotate them privately (Aiven database password, JWT signing secret, bootstrap admin password, and AI provider key as applicable). JWT rotation invalidates existing tokens. No rotation was performed here. Do not paste replacement credentials into chat.

### Exact next actions

1. If any removed template value was live, rotate it privately and update the private runtime environment.
2. Run ` .\scripts\start-aiven-backend.ps1` from repository-root PowerShell (without the leading display space). Enter the actual Aiven hostname, classic port, username, hidden password and the same stable JWT secret at the prompts. Confirm startup reaches Hibernate without connection/permission errors; this is the prerequisite for schema/CRUD/signup/restart persistence verification.
3. Set `TRUSTLEND_OPENAI_API_KEY` only in the backend process environment, run the authenticated ADMIN reindex endpoint and ask policy questions to verify actual provider calls and passage citations.
4. Deployment remains the user's responsibility. For browser E2E, run in an environment with the configured backend and database plus browser automation; the current workspace has no browser test runner.

### Final clean-build confirmation: 2026-10-11

After the earlier file-lock/access failure cleared, `cd backend; .\mvnw.cmd clean test` completed successfully from a clean build: **36 tests passed, 0 failures, 0 errors, 0 skipped**. This supersedes the earlier note that clean execution was blocked. Frontend checks remain as recorded above: 3 tests, Vite build, and ESLint all passed.

### Configuration consistency follow-up: 2026-10-11

- Corrected the non-secret AI model defaults in `.env.example` to match the implementation's OpenAI REST endpoints and README (`gpt-4o-mini` and `text-embedding-3-small`). The template previously named Gemini models even though the service calls OpenAI endpoints, which would cause provider/model errors when copied as-is.
- The OpenAI API key remains blank in the template. No provider request was made because no live key was provided to the backend process.

## Continuation stage: durable credit-score history and wallet pagination: 2026-10-11

### Implementation

- Added `credit_score_history` snapshot entity and repository. `/api/v1/credit-score/me` records a per-user snapshot only when score, eligibility, limit, or scoring factors differ from the last saved snapshot. `GET /api/v1/credit-score/history` returns the authenticated user's latest 20 snapshots; it takes no user ID parameter.
- Added the recent score history display to `CreditScoreCard` using the existing authenticated session token.
- Added `GET /api/v1/wallet/transactions?page=0&size=20` with stable timestamp/id descending order, metadata, server-side page-size validation (1..100), and principal-derived wallet ownership. Wallet UI has Previous/Next pagination. Existing `/wallet/me` and transaction response shape are retained, with its transaction summary now limited to the latest 20 rows.
- Added workflow coverage for snapshot deduplication, snapshot changes after wallet activity, user isolation, wallet page metadata/content/isolation, and invalid page size.
- Added an additive `credit_score_history` table mapping. No live schema operation or data change was performed. Aiven runtime therefore needs permission to create this new table.

### Files changed in this stage

`backend/src/main/java/app/models/CreditScoreSnapshot.java`, `backend/src/main/java/app/Repo/CreditScoreSnapshotRepo.java`, `backend/src/main/java/app/data/CreditScoreHistoryResponse.java`, `backend/src/main/java/app/services/CreditScoreHistoryService.java`, `backend/src/main/java/app/controller/CreditScoringController.java`, `backend/src/main/java/app/data/WalletTransactionPageResponse.java`, `backend/src/main/java/app/Repo/WalletTransactionRepo.java`, `backend/src/main/java/app/services/WalletService.java`, `backend/src/main/java/app/controller/WalletController.java`, `backend/src/test/java/app/WorkflowIntegrationTests.java`, `frontend/src/Components/CreditScoreCard.jsx`, `frontend/src/Pages/Wallet.jsx`, `README.md`, `PROJECT_AUDIT.md`.

### Verification

- Backend `cd backend; .\mvnw.cmd test`: **36 passed, 0 failures, 0 errors, 0 skipped**; integration behavior uses isolated H2.
- Frontend `cd frontend; npm test`: **3 passed**; `npm run build`: **passed, 1975 modules**; `npm run lint`: **passed**.
- No direct browser test was performed; the frontend interaction is build/lint checked and backend contract is MockMvc tested.
- Live Aiven table creation, score/history persistence across restart, and wallet pagination against MySQL remain **Implemented but not integration-verified** pending user-held Aiven access.

## Continuation stage: frontend component test coverage: 2026-10-11

- Added Vitest 4, jsdom and React Testing Library from the available package registry. `npm test` now uses Vitest and still covers INR formatting; it also renders the Wallet component, verifies the first transaction page, clicks Next, confirms the next page appears, and checks the authenticated paginated API request.
- Frontend verification: `npm test` **passed, 2 files / 4 tests**; `npm run build` **passed, 1975 modules**; `npm run lint` **passed**; `npm audit` **0 known vulnerabilities** at verification time.
- Final clean backend verification after pagination and credit-history implementation: `cd backend; .\mvnw.cmd clean test` **passed, 36 tests, 0 failures/errors/skips**.
- The component test uses a mocked HTTP API. It verifies UI state and request behavior, but does not replace browser E2E against a running Spring Boot/MySQL deployment.
- Files added/changed for this UI test stage: `frontend/package.json`, `frontend/package-lock.json`, `frontend/vite.config.js`, `frontend/src/utils/formatCurrency.test.js`, `frontend/src/Pages/Wallet.test.jsx`.

## Continuation stage: API contract documentation and AI prompt validation: 2026-10-11

- Added `docs/API.md`, documenting actual controller mappings, access roles, request fields, response shapes, pagination and loan/group behavior. Linked it from README.
- Replaced the untyped AI chat map with a validated request DTO limiting prompts to 2000 characters while retaining the existing friendly empty-prompt response. The HTTP workflow now checks both cases.
- Final clean backend suite: `cd backend; .\mvnw.cmd clean test` **passed, 36 tests, 0 failures/errors/skips**.
- Final frontend checks: `npm test` **passed, 3 files / 5 tests**; Vite build **passed, 1975 modules**; ESLint **passed**.
- The API request/response component tests use mocks; no live server/browser/database was exercised.

### Dependency audit correction and blocker

- A final structured `npm audit --json` reported **7 development-tree advisories: 5 high, 2 moderate**; a separate invocation earlier returned zero, so the reports were inconsistent. The affected chain is the Tailwind 3 build toolchain (`braces` via `chokidar`/`micromatch`, plus `postcss-selector-parser`). `npm audit --omit=dev` reported zero production dependency vulnerabilities.
- The [GitHub `braces` advisory](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm) says versions through 3.0.3 are affected and lists no patched version. The [selector-parser advisory](https://github.com/advisories/GHSA-rj75-hqrm-r3gf) says versions before 7.1.6 are affected. Tailwind 3.4.19 pins parser 6.x; npm's automatic full fix proposes Tailwind 4.3.3 (a major upgrade).
- A parser override across that major range was rejected by the automatic approval reviewer as potentially breaking and outside the authorized nonbreaking dependency update. No workaround was attempted. Remaining action: explicitly authorize a Tailwind major migration or accept the current build-only toolchain advisory risk; the core runtime dependencies have no findings in `npm audit --omit=dev`.

### Current gaps after this stage

- Live Aiven CRUD, Hibernate table creation for additive `loan_application_reviews` and `credit_score_history`, signup/restart persistence, and MySQL query behavior still require user-held Aiven credentials and DDL access.
- Live OpenAI document ingestion, embeddings, retrieved policy responses and citation provenance require the user's private API key and network access.
- Browser E2E against a running Spring Boot/MySQL stack and deployment are unverified. User owns deployment; component tests and H2 MockMvc do not substitute for that runtime evidence.
- The system's wallet remains a simulated ledger with no payment provider integration.

## Continuation stage: administrator directory search and pagination: 2026-10-11

- Added name/email search, role filtering, and ten-row client pagination to the API-backed ADMIN user directory. Search and role changes reset the active page.
- Added a React Testing Library component test that verifies page navigation changes displayed records and search filters the directory.
- Verification after this change: Vitest **4 files / 6 tests passed**; Vite production build **passed, 1975 modules**; ESLint **passed**.
- No backend source changed in this stage. The clean 36-test backend run above includes the latest backend implementation.

## Continuation stage: container frontend build configuration: 2026-10-11

- `frontend/Dockerfile` now runs `npm ci` from the lockfile and forwards the Compose build argument `VITE_API_BASE_URL` into Vite's build-stage environment. Previously Compose passed the argument but Vite never received it, so the production bundle could silently fall back to localhost.
- Verification with nonsecret placeholder values: `docker compose config --format json` passed; the resolved config preserved the TLS `Trustlend` JDBC URL and forwarded a supplied frontend API URL. A Vite build with a temporary API URL embedded that URL in the generated JavaScript asset.
- `docker info` could not connect to the local Docker Desktop Linux engine (`dockerDesktopLinuxEngine` pipe missing). Therefore image builds, container startup, backend health, and frontend-to-backend traffic remain unverified. No deployment occurred.
- Current expected Hibernate entity tables: 12, comprising the original application/RAG tables plus additive `loan_application_reviews` and `credit_score_history`; review-related additive loan/repayment columns are also expected. No Aiven DDL or data changes were performed.
- Files changed in this stage: `frontend/Dockerfile`, `PROJECT_AUDIT.md`.

## Continuation stage: bounded AI provider calls and signup regression coverage: 2026-10-11

- Configured Spring's OpenAI HTTP client with a 5-second connect timeout and 30-second response timeout so provider stalls fail through the existing graceful RAG fallback and async exception handling.
- Expanded signup verification: the test now validates the stored BCrypt hash with the configured encoder, and a duplicate registration returns 409 with the expected message. The test executes against H2 and performs no Aiven writes.
- Final clean backend result after these changes: **37 tests passed, 0 failures, 0 errors, 0 skipped**.
- Latest frontend evidence remains: **4 files / 6 tests passed**, production build and ESLint passed. Docker build argument was validated by Vite embedding a supplied temporary API URL; Compose config validation passed; Docker daemon unavailable.
- Files changed this stage: `backend/src/main/java/app/config/JsonConfig.java`, `backend/src/test/java/app/BackendApplicationTests.java`, `PROJECT_AUDIT.md`.
