package com.example.knowledgecopilot.retrieval.api;

import com.example.knowledgecopilot.dto.AskResponse;

public interface RetrievalModule {
    AskResponse ask(String question);
    AskExecutionTrace askWithTrace(String question);
    default AskExecutionTrace askWithTrace(RetrievalQuery query) {
        return askWithTrace(query == null ? null : query.getQuestion());
    }

    default AskExecutionTrace askWithTrace(String question, String conversationSummary) {
        return askWithTrace(question);
    }

    default AskExecutionTrace askWithTrace(RetrievalQuery query, String conversationSummary) {
        return askWithTrace(query);
    }
}
