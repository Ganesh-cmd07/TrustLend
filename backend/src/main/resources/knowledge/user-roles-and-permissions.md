# TrustLend User Roles, Access Control, and Governance

## Role-Based Access Control (RBAC)
TrustLend enforces strict role-based access control backed by JSON Web Tokens (JWT) and Spring Security filters.

## User Roles
1. **BORROWER:**
   - Default role upon initial registration.
   - Can view personal credit score, risk tier, and maximum eligible loan limits.
   - Can create or join Self-Help Groups (SHGs).
   - Can submit individual or group-backed micro-loan applications.
   - Can view repayment schedules and pay installments from simulated wallet funds.
   - Can request to become a LENDER.
2. **LENDER:**
   - Dedicated role for community investors and lenders.
   - Can browse all administrator-approved loan requests from borrowers and groups.
   - Can review expected interest returns, risk tiers, and borrower profiles.
   - Can fund loans directly from their wallet and earn simple interest upon repayment.
3. **ADMIN (Administrator):**
   - High-privilege role for platform governance.
   - Reviews and decides on all submitted loan applications (`APPROVE` or `REJECT` with mandatory reason).
   - Reviews and approves or rejects borrower requests to become lenders.
   - Manages platform users and reviews operational audit logs.
   - Triggers Knowledge Base re-indexing for the Retrieval-Augmented Generation (RAG) AI assistant.
