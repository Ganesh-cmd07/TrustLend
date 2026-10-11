# TrustLend API reference

Base path: `http://localhost:4040`. Protected routes require `Authorization: Bearer <JWT>`. The server derives the acting user from the validated token; IDs in a request never replace ownership checks.

Errors use `{ "timestamp", "status", "error", "message", "details" }`. DTO validation failures return HTTP 400 with field messages. Unauthenticated requests return 401; authenticated users without the required role return 403.

## Authentication and profile

| Method and path | Access | Request | Response |
|---|---|---|---|
| `POST /api/v1/signup`, `POST /api/v1/auth/signup` | Public | `{name,email,password,role?}`; role is `BORROWER` or `LENDER`, password 8–72 chars | 201 `{message}`; lender applicants remain pending |
| `POST /api/v1/login`, `POST /api/v1/auth/login` | Public | `{email,password}` | 200 `{message,token}` |
| `GET /api/v1/auth/me` | Authenticated | None | Safe account fields: id, name, email, role, lenderRequestStatus, createdAt |
| `PUT /api/v1/auth/me` | Authenticated | `{name}` (1–100 chars) | Updated safe account fields; identity comes from JWT |
| `POST /api/v1/users/lender-request` | BORROWER | None | 201 lender request |

## Credit scoring

| Method and path | Access | Response |
|---|---|---|
| `GET /api/v1/credit-score/me` | BORROWER | Current score, risk tier, eligibility, maximum amount and scoring factors; records a snapshot when values change |
| `GET /api/v1/credit-score/history` | BORROWER | Latest 20 of the caller's score snapshots, newest first |

## Loans and wallet

| Method and path | Access | Request / response |
|---|---|---|
| `POST /api/v1/loans/apply` | BORROWER | `{amount,interestRate,termMonths}`; returns submitted loan and repayment fields |
| `GET /api/v1/loans/my-loans` | Authenticated | Caller-owned borrower/lender loans |
| `GET /api/v1/loans/marketplace` | LENDER | Approved (and legacy pending) applications available for funding |
| `POST /api/v1/loans/{id}/fund` | LENDER | Funds an eligible application from simulated wallet |
| `POST /api/v1/loans/{id}/repay` | BORROWER | Pays the next installment on caller-owned loan |
| `GET /api/v1/wallet/me` | Authenticated | Caller wallet and newest 20 transaction summaries |
| `GET /api/v1/wallet/transactions?page=0&size=20` | Authenticated | `{content,page,size,totalElements,totalPages}` for the caller's own wallet; page is zero-based and size is 1–100 |
| `POST /api/v1/wallet/deposit`, `POST /api/v1/wallet/withdraw` | Authenticated | `{amount}`; returns updated wallet summary; these are simulated ledger operations |

Loan amount must be positive with at most two decimal places; rate is 0–100 with at most two decimals; term is 1–120 months. Credit eligibility and amount limits are enforced by the backend.

## Self-help groups

| Method and path | Access | Request / behavior |
|---|---|---|
| `GET /api/v1/groups` | Authenticated | Active groups; nonmembers receive public group summaries |
| `GET /api/v1/groups/my` | BORROWER | Caller’s active groups |
| `GET /api/v1/groups/{id}` | Authenticated active member | Group detail, member/contribution data, leader co-sign queue where authorized |
| `POST /api/v1/groups/create` | BORROWER | `{name,description?}`; name max 100 and description max 1000 chars |
| `POST /api/v1/groups/{id}/join` | BORROWER | Creates a pending membership request |
| `POST /api/v1/groups/{id}/members/{membershipId}/approve` or `/reject` | BORROWER group leader | Approves or rejects the pending member |
| `POST /api/v1/groups/{id}/contribute` | BORROWER active member | `{amount}`; debits caller wallet and updates group savings atomically |
| `POST /api/v1/groups/{id}/loans/apply` | BORROWER active nonleader | `{amount,interestRate,termMonths}`; requires leader co-sign and admin review before funding |
| `POST /api/v1/groups/{id}/loans/{loanId}/co-sign` | BORROWER group leader | Co-signs another active member’s submitted group loan; `/cosign` is a legacy alias |

The legacy `POST /api/v1/groups/{id}/members/{membershipId}/review?approve=true|false` route remains supported.

## Administration

All paths below require `ADMIN`.

| Method and path | Behavior |
|---|---|
| `GET /api/v1/admin/users` | User directory summaries |
| `GET /api/v1/admin/lender-requests` | Pending lender access requests |
| `POST /api/v1/admin/lender-requests/{requestId}/approve`, `/reject` | Review lender access; approval grants lender role |
| `GET /api/v1/admin/loan-applications` | Submitted applications awaiting review |
| `POST /api/v1/admin/loan-applications/{loanId}/review` | `{decision:"APPROVE"|"REJECT",reason?}`; reject requires reason (max 1000 chars) |
| `GET /api/v1/admin/loan-review-history` | Latest 20 loan decisions with reviewer and reason |
| `POST /api/v1/admin/ai/reindex` | Starts asynchronous trusted policy ingestion; returns 202 |

## AI assistant

| Method and path | Access | Request / response |
|---|---|---|
| `GET /api/v1/ai/status` | Authenticated | Provider configured flag, indexed chunk count and model names |
| `POST /api/v1/ai/chat` | Authenticated | `{prompt}` (max 2000 chars); returns `{reply,sources,ragConfigured}` |

Personal loan-status questions use the authenticated user's own loan records and do not send those records to the model. Policy answers cite source names selected from retrieved stored passages. Live OpenAI calls require `TRUSTLEND_OPENAI_API_KEY` in the backend process.
