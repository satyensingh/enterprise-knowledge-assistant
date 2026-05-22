# Confidential Incident Review 2026 Q1

Classification: Confidential
Audience: Admin only

Summary:
- Incident ID: KC-INC-2026-017
- Root cause: stale JWK endpoint override during host-mode app execution.
- Blast radius: authorization failures on protected routes.
- Corrective action: enforce localhost JWK endpoint in host profile and add startup validation.
- Follow-up: add explicit environment compatibility checks to deployment checklist.
