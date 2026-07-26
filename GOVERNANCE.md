# Governance

`cloud-itonami-isic-6910` is an OSS open-business blueprint. Governance covers
both code and the operator model.

## Maintainers

Maintainers may merge changes that preserve these invariants:

- Registrar-LLM cannot directly file or pay a government registry.
- RegistrarGovernor remains independent of the advisor.
- hard governor violations (fabricated spec-basis, sanctions hit,
  incomplete documents) cannot be overridden by human approval.
- `:filing/submit` is never a member of any phase's `:auto` set.
- every commit, hold and approval path is auditable.
- real customer identification documents and screening results stay
  outside Git.
- no jurisdiction is added to `formation.facts` without a real, citable
  official source.

## Decision Records

Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, actuation invariant, public business model, operator
certification or license should add or update an ADR.
