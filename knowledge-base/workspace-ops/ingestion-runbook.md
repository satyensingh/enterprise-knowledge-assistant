# Ingestion Runbook

## Purpose
This runbook describes the deterministic ingestion flow in Knowledge Copilot.

## Flow
1. Connector full sync lists source items.
2. Text extraction runs for supported files.
3. Token-aware chunking splits content with overlap.
4. Embeddings are generated for each chunk.
5. Chunks are indexed in ChromaDB and metadata persisted in PostgreSQL.
6. Reindex pruning removes stale documents no longer present in the connector source.

## Operational Notes
- Admin reindex endpoint: POST /api/admin/reindex
- Scheduled reindex trigger runs every 15 minutes after initial delay.
- Failed documents are counted in ingestion metrics and logged with stack traces.
