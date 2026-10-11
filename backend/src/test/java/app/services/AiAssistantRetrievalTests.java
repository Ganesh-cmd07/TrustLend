package app.services;

import org.junit.jupiter.api.Test;
import app.Repo.KnowledgeChunkRepo;
import app.models.KnowledgeChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentCaptor.forClass;

class AiAssistantRetrievalTests {
    @Test
    void cosineSimilarityRanksAlignedEmbeddingsAboveUnrelatedOnes() {
        var query = List.of(1.0, 0.0, 0.0);
        assertThat(AiAssistantService.cosine(query, List.of(0.9, 0.1, 0.0)))
                .isGreaterThan(AiAssistantService.cosine(query, List.of(0.0, 0.0, 1.0)));
        assertThat(AiAssistantService.cosine(query, List.of(1.0, 0.0))).isEqualTo(-1.0);
    }

    @Test
    void missingModelCredentialIsReportedWithoutCallingExternalServices() {
        var chunks = mock(KnowledgeChunkRepo.class);
        var assistant = new AiAssistantService("", "chat-model", "embedding-model", chunks,
                mock(KnowledgeIndexStore.class), mock(LoanService.class), new ObjectMapper(), mock(RestTemplate.class));

        var response = assistant.ask("What are repayment rules?", "borrower@example.test");

        assertThat(response.get("ragConfigured")).isEqualTo(false);
        assertThat(response.get("reply").toString()).contains("not configured");
        verifyNoInteractions(chunks);
    }

    @Test
    void embedsQuestionRetrievesRelevantStoredChunkAndGroundsModelRequest() throws Exception {
        var repo = mock(KnowledgeChunkRepo.class);
        var http = mock(RestTemplate.class);
        var mapper = new ObjectMapper();
        var index = new KnowledgeIndexStore(repo);
        var assistant = new AiAssistantService("test-key", "chat-model", "embedding-model", repo,
                index, mock(LoanService.class), mapper, http);
        var chunk = new KnowledgeChunk("loan-policy.md", 0, "Repayment installments are monthly.", "[1.0,0.0]");
        when(repo.findAllByOrderBySourceNameAscChunkIndexAsc()).thenReturn(List.of(chunk));
        doReturn(Map.of("data", List.of(Map.of("index", 0, "embedding", List.of(1.0, 0.0)))))
                .when(http).postForObject(eq("https://api.openai.com/v1/embeddings"), any(HttpEntity.class), eq(Map.class));
        doReturn(Map.of("choices", List.of(Map.of("message", Map.of("content", "Repayments are monthly.")))))
                .when(http).postForObject(eq("https://api.openai.com/v1/chat/completions"), any(HttpEntity.class), eq(Map.class));

        var response = assistant.ask("How often are installments?", "borrower@example.test");

        assertThat(response.get("reply").toString()).contains("Repayments are monthly", "loan-policy.md");
        assertThat(response.get("sources")).isEqualTo(List.of("loan-policy.md"));
        var requestCaptor = forClass(HttpEntity.class);
        verify(http).postForObject(eq("https://api.openai.com/v1/chat/completions"), requestCaptor.capture(), eq(Map.class));
        Map<?, ?> body = (Map<?, ?>) requestCaptor.getValue().getBody();
        List<?> messages = (List<?>) body.get("messages");
        assertThat(messages.get(0).toString()).contains("Repayment installments are monthly.");
    }

    @Test
    void personalizedStatusQuestionUsesAuthenticatedAccountAndSkipsSharedModel() {
        var repo = mock(KnowledgeChunkRepo.class);
        var loans = mock(LoanService.class);
        when(loans.getMyLoans("current-user@example.test")).thenReturn(List.of());
        var http = mock(RestTemplate.class);
        var assistant = new AiAssistantService("test-key", "chat-model", "embedding-model", repo,
                mock(KnowledgeIndexStore.class), loans, new ObjectMapper(), http);

        var response = assistant.ask("What is the status of my loan?", "current-user@example.test");

        assertThat(response.get("reply").toString()).contains("in your account");
        verify(loans).getMyLoans("current-user@example.test");
        verifyNoInteractions(http, repo);
    }

    @Test
    void emptyIndexReturnsNoGroundedAnswerAndDoesNotCallChatModel() {
        var repo = mock(KnowledgeChunkRepo.class);
        var http = mock(RestTemplate.class);
        var assistant = new AiAssistantService("test-key", "chat-model", "embedding-model", repo,
                mock(KnowledgeIndexStore.class), mock(LoanService.class), new ObjectMapper(), http);
        when(repo.findAllByOrderBySourceNameAscChunkIndexAsc()).thenReturn(List.of());
        doReturn(Map.of("data", List.of(Map.of("index", 0, "embedding", List.of(1.0, 0.0)))))
                .when(http).postForObject(eq("https://api.openai.com/v1/embeddings"), any(HttpEntity.class), eq(Map.class));

        var response = assistant.ask("What are the TrustLend loan policies?", "borrower@example.test");

        assertThat(response.get("reply").toString()).contains("couldn't find relevant information");
        assertThat(response.get("sources")).isEqualTo(List.of());
        verify(http).postForObject(eq("https://api.openai.com/v1/embeddings"), any(HttpEntity.class), eq(Map.class));
        org.mockito.Mockito.verifyNoMoreInteractions(http);
    }

    @Test
    void providerFailureReturnsGracefulUnavailableResponse() {
        var repo = mock(KnowledgeChunkRepo.class);
        var http = mock(RestTemplate.class);
        var assistant = new AiAssistantService("test-key", "chat-model", "embedding-model", repo,
                mock(KnowledgeIndexStore.class), mock(LoanService.class), new ObjectMapper(), http);
        org.mockito.Mockito.when(http.postForObject(eq("https://api.openai.com/v1/embeddings"), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("simulated provider outage"));

        var response = assistant.ask("What are the repayment rules?", "borrower@example.test");

        assertThat(response.get("reply").toString()).contains("temporarily unavailable");
        assertThat(response.get("sources")).isEqualTo(List.of());
    }

    @Test
    void geminiProviderEmbedsAndGeneratesGroundedAnswerSuccessfully() {
        var repo = mock(KnowledgeChunkRepo.class);
        var http = mock(RestTemplate.class);
        var assistant = new AiAssistantService("AIzaTestKey", "gemini-2.5-flash", "gemini-embedding-001", repo,
                mock(KnowledgeIndexStore.class), mock(LoanService.class), new ObjectMapper(), http);
        var chunk = new KnowledgeChunk("group-lending.md", 0, "SHG group loans require leader approval.", "[1.0,0.0]");
        when(repo.findAllByOrderBySourceNameAscChunkIndexAsc()).thenReturn(List.of(chunk));

        String embedUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key=AIzaTestKey";
        String chatUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=AIzaTestKey";

        doReturn(Map.of("embedding", Map.of("values", List.of(1.0, 0.0))))
                .when(http).postForObject(eq(embedUrl), any(HttpEntity.class), eq(Map.class));
        doReturn(Map.of("candidates", List.of(Map.of("content", Map.of("parts", List.of(Map.of("text", "SHG loans require leader approval.")))))))
                .when(http).postForObject(eq(chatUrl), any(HttpEntity.class), eq(Map.class));

        var response = assistant.ask("Who approves SHG loans?", "borrower@example.test");

        assertThat(response.get("reply").toString()).contains("SHG loans require leader approval", "group-lending.md");
        assertThat(response.get("sources")).isEqualTo(List.of("group-lending.md"));
        verify(http).postForObject(eq(embedUrl), any(HttpEntity.class), eq(Map.class));
        verify(http).postForObject(eq(chatUrl), any(HttpEntity.class), eq(Map.class));
    }
}
