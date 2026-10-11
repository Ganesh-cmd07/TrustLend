package app.services;

import app.Repo.KnowledgeChunkRepo;
import app.models.KnowledgeChunk;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KnowledgeIndexStore {
    private final KnowledgeChunkRepo repository;

    public KnowledgeIndexStore(KnowledgeChunkRepo repository) {
        this.repository = repository;
    }

    @Transactional
    public void replaceAll(List<KnowledgeChunk> chunks) {
        repository.deleteAllInBatch();
        repository.saveAll(chunks);
    }
}
