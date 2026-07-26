# Security Policy

This project handles company-incorporation workflows, including officer
identification and KYC/sanctions-screening results. Treat vulnerabilities
as potentially high impact even when the demo data is synthetic.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real customer, officer or identification-document exposure
- authorization bypass
- RegistrarGovernor bypass
- a path that lets `:filing/submit` auto-commit at any phase
- audit-ledger tampering
- tenant isolation failures

## Reporting

Use GitHub private vulnerability reporting when available for the
repository. If that is unavailable, contact the repository maintainers
through the cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on customer data, governor enforcement, actuation invariant or
  audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real customer/officer data outside this repository.
- Run governor and phase tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
- Never wire `:filing/submit` or a payment effect to run without a human
  approval step, regardless of confidence or phase.
