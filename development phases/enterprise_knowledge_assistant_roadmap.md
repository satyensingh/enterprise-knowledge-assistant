%% Enterprise Knowledge Assistant - Development Roadmap
%% Status legend:
%% Done = green, Partially Done = amber, Pending = gray

flowchart TD
    P0["Phase 0<br/>Monorepo + Local Infra<br/><b>Done</b>"]
    P1["Phase 1<br/>Database Model + Migrations<br/><b>Partially Done</b>"]
    P2["Phase 2<br/>Backend Foundation<br/><b>Done</b>"]
    P3["Phase 3<br/>Direct File Upload Ingestion<br/><b>Partially Done</b>"]
    P4["Phase 4<br/>Chunking + Embeddings + pgvector<br/><b>Done</b>"]
    P5["Phase 5<br/>Retrieval + Chat Answer with Citations<br/><b>Done</b>"]
    P6["Phase 6<br/>UI Chat Screen<br/><b>Pending</b>"]
    P7["Phase 7<br/>Admin Console for Documents / Jobs<br/><b>Pending</b>"]
    P8["Phase 8<br/>Connector Framework<br/><b>Done</b>"]
    P9["Phase 9<br/>Real Connectors<br/>Confluence, Jira, Slack, Teams<br/><b>Partially Done: Jira + Confluence</b>"]
    P10["Phase 10<br/>Security, ACL, Evaluation, Observability<br/><b>Pending</b>"]
    P11["Phase 11<br/>Docker Compose Hardening + Demo Data<br/><b>Partially Done</b>"]

    P0 --> P1 --> P2 --> P3 --> P4 --> P5 --> P6 --> P7 --> P8 --> P9 --> P10 --> P11

    subgraph Foundation["Foundation"]
        P0
        P1
        P2
    end

    subgraph RAGCore["RAG Core"]
        P3
        P4
        P5
    end

    subgraph ProductUI["Product Experience"]
        P6
        P7
    end

    subgraph Integrations["Enterprise Integrations"]
        P8
        P9
    end

    subgraph ProductionReadiness["Production Readiness"]
        P10
        P11
    end

    classDef done fill:#dcfce7,stroke:#16a34a,stroke-width:2px,color:#14532d;
    classDef partial fill:#fef3c7,stroke:#d97706,stroke-width:2px,color:#78350f;
    classDef pending fill:#f1f5f9,stroke:#64748b,stroke-width:2px,color:#334155;
    classDef group fill:#ffffff,stroke:#cbd5e1,stroke-width:1px,color:#0f172a;

    class P0,P2,P4,P5,P8 done;
    class P1,P3,P9,P11 partial;
    class P6,P7,P10 pending;
    class Foundation,RAGCore,ProductUI,Integrations,ProductionReadiness group;
