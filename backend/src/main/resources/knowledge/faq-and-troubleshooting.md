# TrustLend FAQ and Platform Troubleshooting

## Frequently Asked Questions

### How do I apply for a loan on TrustLend?
To apply for a micro-loan:
1. Check that your credit score is at least 550. If your score is below 550, your eligible loan limit is ₹0.00.
2. Navigate to the "Borrow" page from the main navigation bar.
3. Choose whether to submit an Individual application or a Group-backed application.
4. Enter the requested principal amount within your maximum credit limit (up to ₹25,000 for 550+, ₹50,000 for 650+, or ₹1,00,000 for 750+).
5. Specify your proposed annual interest rate (between 0% and 100%) and loan term (from 1 to 120 months).
6. Submit the application. Group applications require Self-Help Group (SHG) leader co-signature first. Then an Administrator reviews and approves or rejects the application before lenders can fund it.

### How do I become a lender?
Borrowers wishing to fund peer loans can request a role upgrade:
1. Click the "Become a lender" button on the navigation bar.
2. Provide your motivation and financial intent, then submit the request.
3. An Administrator reviews pending lender requests in the Admin Operations portal.
4. Once approved, your account role changes to LENDER, unlocking the Lender Dashboard where you can review and fund loan applications.

### How do I deposit money into my TrustLend wallet?
1. Open the "Wallet" page from the navigation menu.
2. Enter the deposit amount into the Top Up field.
3. Click "Deposit".
4. The simulated wallet balance updates instantly and writes an immutable audit ledger entry.

### Why was my loan application rejected?
Administrators review all submitted loan applications to protect platform integrity. An administrator may reject a request if the applicant does not meet underwriting guidelines or provides incomplete information. The administrator must provide an official rejection reason, which is permanently stored in the audit logs along with reviewer identity and timestamp.

### What happens if I miss a loan repayment installment?
TrustLend runs an automated daily background check on all active installments. If an unpaid installment passes its due date:
1. The installment status is marked as OVERDUE.
2. The borrower's credit score drops by 100 points per overdue loan.
3. Overdue installments prevent new loan approvals until the outstanding installment is paid.
To repay an overdue installment, ensure your simulated wallet has sufficient balance and click "Pay installment" on the Borrower Dashboard.

### How does a Self-Help Group leader co-sign a loan?
When an active member of an SHG applies for a group-backed loan:
1. The loan application is created with status SUBMITTED.
2. The SHG group leader sees a pending co-signature alert in their Groups tab.
3. The leader reviews the member's request and clicks "Co-sign".
4. Only after leader co-signature can the Administrator approve the application for lender funding. Note: Leaders cannot co-sign their own personal loans.
