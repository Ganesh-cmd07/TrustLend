package app.services;

import app.Repo.KnowledgeChunkRepo;
import app.models.KnowledgeChunk;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KnowledgeIndexStore {
    private final KnowledgeChunkRepo repository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public KnowledgeIndexStore(KnowledgeChunkRepo repository) {
        this(repository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public KnowledgeIndexStore(KnowledgeChunkRepo repository,
                               @org.springframework.beans.factory.annotation.Autowired(required = false) org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void ensureSchema() {
        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.execute("ALTER TABLE knowledge_chunks MODIFY COLUMN content LONGTEXT");
                jdbcTemplate.execute("ALTER TABLE knowledge_chunks MODIFY COLUMN embedding_json LONGTEXT");
            } catch (Exception ignored) {
            }
        }
    }

    @Transactional
    public void replaceAll(List<KnowledgeChunk> chunks) {
        ensureSchema();
        repository.deleteAllInBatch();
        repository.saveAll(chunks);
    }
}
