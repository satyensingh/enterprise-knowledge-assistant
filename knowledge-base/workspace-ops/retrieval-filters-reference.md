# Retrieval Filters Reference

The ask endpoint supports deterministic filtering:

- sourceTypes: LOCAL_FILE_SYSTEM, JIRA, CONFLUENCE
- sourcePaths: include only source path prefixes
- workspaceIds: match workspace metadata or source-path-derived workspace
- updatedAfter / updatedBefore: inclusive timestamp filtering
- metadataFilters: exact key-value matching on document/chunk metadata
- connectorFilters: connector/source-path specific constraints

Common example:
Use workspaceIds=["workspace-ops"] with sourceTypes=["LOCAL_FILE_SYSTEM"] for operations docs only.
