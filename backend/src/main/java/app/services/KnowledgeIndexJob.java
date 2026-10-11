package app.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeIndexJob {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexJob.class);
    private final AiAssistantService assistantService;

    public KnowledgeIndexJob(AiAssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @Async("trustLendTaskExecutor")
    public void reindexTrustedDocuments() {
        try {
            var result = assistantService.indexTrustedDocuments();
            log.info("RAG knowledge index replaced with {} chunks", result.get("indexedChunks"));
        } catch (RuntimeException exception) {
            log.error("RAG knowledge indexing task failed", exception);
            throw exception;
        }
    }
}
