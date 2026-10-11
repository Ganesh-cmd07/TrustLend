package app.Repo;

import app.models.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeChunkRepo extends JpaRepository<KnowledgeChunk, Long> {
    List<KnowledgeChunk> findAllByOrderBySourceNameAscChunkIndexAsc();
    void deleteAllBySourceName(String sourceName);
}
