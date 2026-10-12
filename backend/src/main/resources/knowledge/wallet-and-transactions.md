# TrustLend Wallet and Audit Transaction Ledger

## Simulated Digital Wallet
TrustLend incorporates a closed-loop simulated digital wallet system for financial inclusion. Balances simulate real-world microfinance capital without requiring external payment gateway dependencies.

## Key Financial Transactions
1. **Wallet Deposit (Top-Up):**
   - Users can add funds to their wallet balance on the Wallet page.
   - Each deposit generates a transaction ledger entry with type DEPOSIT and records the new balance.
2. **Loan Disbursement:**
   - When a lender approves and funds an approved loan application, funds are automatically transferred from the lender's wallet to the borrower's wallet.
   - Generates matching DISBURSEMENT ledger records on both user accounts.
3. **Monthly Installment Repayment:**
   - Installments are computed using simple interest: `Interest = (Principal * Rate * Months) / 1200`.
   - Total repayable amount is divided evenly across monthly installments.
   - When a borrower pays an installment, their wallet is debited and the lender's wallet is credited with principal plus interest returns.
4. **SHG Group Savings Contributions:**
   - Self-Help Group members can contribute simulated funds towards the group collective pool, tracked on the platform ledger.
