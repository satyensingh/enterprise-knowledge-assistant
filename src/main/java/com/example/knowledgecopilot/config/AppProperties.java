package com.example.knowledgecopilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private final Ingestion ingestion = new Ingestion();
    private final Chunking chunking = new Chunking();
    private final Retrieval retrieval = new Retrieval();
    private final Evaluation evaluation = new Evaluation();
    private final Reindex reindex = new Reindex();
    private final Embedding embedding = new Embedding();
    private final Llm llm = new Llm();
    private final Observability observability = new Observability();
    private final VectorStore vectorStore = new VectorStore();

    public Ingestion getIngestion() { return ingestion; }
    public Chunking getChunking() { return chunking; }
    public Retrieval getRetrieval() { return retrieval; }
    public Evaluation getEvaluation() { return evaluation; }
    public Reindex getReindex() { return reindex; }
    public Embedding getEmbedding() { return embedding; }
    public Llm getLlm() { return llm; }
    public Observability getObservability() { return observability; }
    public VectorStore getVectorStore() { return vectorStore; }

    public static class Ingestion {
        private String rootFolder;
        private List<String> supportedExtensions = new ArrayList<>();
        private Jira jira = new Jira();
        private Confluence confluence = new Confluence();
        public String getRootFolder() { return rootFolder; }
        public void setRootFolder(String rootFolder) { this.rootFolder = rootFolder; }
        public List<String> getSupportedExtensions() { return supportedExtensions; }
        public void setSupportedExtensions(List<String> supportedExtensions) { this.supportedExtensions = supportedExtensions; }
        public Jira getJira() { return jira; }
        public void setJira(Jira jira) { this.jira = jira; }
        public Confluence getConfluence() { return confluence; }
        public void setConfluence(Confluence confluence) { this.confluence = confluence; }

        public static class Jira {
            private boolean enabled = false;
            private String baseUrl;
            private String email;
            private String apiToken;
            private String jql = "ORDER BY updated DESC";

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public String getBaseUrl() { return baseUrl; }
            public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
            public String getEmail() { return email; }
            public void setEmail(String email) { this.email = email; }
            public String getApiToken() { return apiToken; }
            public void setApiToken(String apiToken) { this.apiToken = apiToken; }
            public String getJql() { return jql; }
            public void setJql(String jql) { this.jql = jql; }
        }

        public static class Confluence {
            private boolean enabled = false;
            private String baseUrl;
            private String email;
            private String apiToken;
            private String cql = "type=page order by lastmodified desc";

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public String getBaseUrl() { return baseUrl; }
            public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
            public String getEmail() { return email; }
            public void setEmail(String email) { this.email = email; }
            public String getApiToken() { return apiToken; }
            public void setApiToken(String apiToken) { this.apiToken = apiToken; }
            public String getCql() { return cql; }
            public void setCql(String cql) { this.cql = cql; }
        }
    }

    public static class Chunking {
        private int chunkSize = 1200;
        private int overlap = 150;
        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
        public int getOverlap() { return overlap; }
        public void setOverlap(int overlap) { this.overlap = overlap; }
    }

    public static class Retrieval {
        private int topK = 5;
        private int candidatePoolSize = 20;
        private double vectorWeight = 0.8d;
        private double lexicalWeight = 0.2d;
        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
        public int getCandidatePoolSize() { return candidatePoolSize; }
        public void setCandidatePoolSize(int candidatePoolSize) { this.candidatePoolSize = candidatePoolSize; }
        public double getVectorWeight() { return vectorWeight; }
        public void setVectorWeight(double vectorWeight) { this.vectorWeight = vectorWeight; }
        public double getLexicalWeight() { return lexicalWeight; }
        public void setLexicalWeight(double lexicalWeight) { this.lexicalWeight = lexicalWeight; }
    }

    public static class Reindex {
        private long initialDelayMs = 60_000;
        private long fixedDelayMs = 900_000;

        public long getInitialDelayMs() { return initialDelayMs; }
        public void setInitialDelayMs(long initialDelayMs) { this.initialDelayMs = initialDelayMs; }
        public long getFixedDelayMs() { return fixedDelayMs; }
        public void setFixedDelayMs(long fixedDelayMs) { this.fixedDelayMs = fixedDelayMs; }
    }

    public static class Evaluation {
        private int questionClusterCandidatePoolSize = 20;
        private double questionClusterSimilarityThreshold = 0.86d;

        public int getQuestionClusterCandidatePoolSize() { return questionClusterCandidatePoolSize; }
        public void setQuestionClusterCandidatePoolSize(int questionClusterCandidatePoolSize) {
            this.questionClusterCandidatePoolSize = questionClusterCandidatePoolSize;
        }
        public double getQuestionClusterSimilarityThreshold() { return questionClusterSimilarityThreshold; }
        public void setQuestionClusterSimilarityThreshold(double questionClusterSimilarityThreshold) {
            this.questionClusterSimilarityThreshold = questionClusterSimilarityThreshold;
        }
    }

    public static class Embedding {
        private String apiKey;
        private String model = "text-embedding-3-small";
        private String baseUrl = "https://api.openai.com/v1";
        private int dimensions = 1536;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public int getDimensions() { return dimensions; }
        public void setDimensions(int dimensions) { this.dimensions = dimensions; }
    }

    public static class Llm {
        private String apiKey;
        private String model;
        private String baseUrl = "https://api.openai.com/v1";
        private int maxOutputTokens = 800;
        private String promptVersion = "v1";
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public int getMaxOutputTokens() { return maxOutputTokens; }
        public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
        public String getPromptVersion() { return promptVersion; }
        public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    }

    public static class Observability {
        private String requestIdHeader = "X-Request-Id";
        private String serviceName = "knowledge-copilot";

        public String getRequestIdHeader() { return requestIdHeader; }
        public void setRequestIdHeader(String requestIdHeader) { this.requestIdHeader = requestIdHeader; }
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    }

    public static class VectorStore {
        private boolean enabled = true;
        private final Chroma chroma = new Chroma();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Chroma getChroma() { return chroma; }

        public static class Chroma {
            private String baseUrl = "http://localhost:8000";
            private String collectionName = "knowledge_chunks";
            private String apiToken;
            private String apiVersion = "v1";
            private String tenant = "default_tenant";
            private String database = "default_database";

            public String getBaseUrl() { return baseUrl; }
            public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
            public String getCollectionName() { return collectionName; }
            public void setCollectionName(String collectionName) { this.collectionName = collectionName; }
            public String getApiToken() { return apiToken; }
            public void setApiToken(String apiToken) { this.apiToken = apiToken; }
            public String getApiVersion() { return apiVersion; }
            public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }
            public String getTenant() { return tenant; }
            public void setTenant(String tenant) { this.tenant = tenant; }
            public String getDatabase() { return database; }
            public void setDatabase(String database) { this.database = database; }
        }
    }
}
