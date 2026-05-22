# Authorization-Aware RAG Policy

## Mandatory Rule
Never send unauthorized chunks to the LLM context.

## Correct Pipeline
Retrieve candidates -> apply ACL filter -> rerank authorized chunks -> build prompt context.

## Expected Behavior
- ROLE_ADMIN can access all content.
- ROLE_USER receives only chunks allowed by access metadata.
- Unauthorized traces may appear in diagnostics with authorized=false and excerpt redacted.
