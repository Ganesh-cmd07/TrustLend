package app.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "knowledge_chunks", uniqueConstraints =
        @UniqueConstraint(name = "uk_knowledge_source_chunk", columnNames = {"source_name", "chunk_index"}))
public class KnowledgeChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_name", nullable = false, length = 255)
    private String sourceName;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Lob
    @Column(name = "embedding_json", nullable = false, columnDefinition = "LONGTEXT")
    private String embeddingJson;

    protected KnowledgeChunk() { }

    public KnowledgeChunk(String sourceName, int chunkIndex, String content, String embeddingJson) {
        this.sourceName = sourceName;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.embeddingJson = embeddingJson;
    }

    public Long getId() { return id; }
    public String getSourceName() { return sourceName; }
    public int getChunkIndex() { return chunkIndex; }
    public String getContent() { return content; }
    public String getEmbeddingJson() { return embeddingJson; }
}
