# TrustLend credit assessment

TrustLend uses a transparent rule-based score, not machine learning. A borrower starts at 500 points. Each repaid loan adds 50 points and each loan with at least one overdue installment subtracts 100 points. A wallet balance of at least 500 adds 25 points; at least 1000 adds 50 points. Active membership in a self-help group adds 50 points. The result is bounded between 300 and 850.

Borrowers below 550 points are not eligible to submit loan applications and have a maximum eligible principal of zero. From 550 points the maximum principal is 25000, from 650 it is 50000, and from 750 it is 100000. The current model does not collect income or recurring obligations, so they are not included. Eligibility is a platform rule and is not a promise of funding; a lender still decides whether to fund an application.
