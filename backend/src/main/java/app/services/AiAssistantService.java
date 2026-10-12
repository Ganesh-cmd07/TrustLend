package app.services;

import app.Repo.KnowledgeChunkRepo;
import app.Repo.UserRepo;
import app.data.LoanResponse;
import app.models.KnowledgeChunk;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiAssistantService {
    private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);
    private static final String EMBEDDINGS_URL = "https://api.openai.com/v1/embeddings";
    private static final String CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final int MAX_CHUNK_CHARS = 900;
    private static final int OVERLAP_CHARS = 120;

    private final String apiKey;
    private final String chatModel;
    private final String embeddingModel;
    private final KnowledgeChunkRepo chunkRepo;
    private final KnowledgeIndexStore indexStore;
    private final LoanService loanService;
    private final ObjectMapper mapper;
    private final RestTemplate http;
    private final CreditScoringService creditScoringService;
    private final UserRepo userRepo;

    public AiAssistantService(
            @Value("${app.ai.api-key:${TRUSTLEND_OPENAI_API_KEY:${GEMINI_API_KEY:${OPENAI_API_KEY:}}}}") String apiKey,
            @Value("${app.ai.chat-model:gpt-4o-mini}") String chatModel,
            @Value("${app.ai.embedding-model:text-embedding-3-small}") String embeddingModel,
            KnowledgeChunkRepo chunkRepo,
            KnowledgeIndexStore indexStore,
            LoanService loanService,
            ObjectMapper mapper,
            RestTemplate http) {
        this(apiKey, chatModel, embeddingModel, chunkRepo, indexStore, loanService, mapper, http, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AiAssistantService(
            @Value("${app.ai.api-key:${TRUSTLEND_OPENAI_API_KEY:${GEMINI_API_KEY:${OPENAI_API_KEY:}}}}") String apiKey,
            @Value("${app.ai.chat-model:gpt-4o-mini}") String chatModel,
            @Value("${app.ai.embedding-model:text-embedding-3-small}") String embeddingModel,
            KnowledgeChunkRepo chunkRepo,
            KnowledgeIndexStore indexStore,
            LoanService loanService,
            ObjectMapper mapper,
            RestTemplate http,
            @org.springframework.beans.factory.annotation.Autowired(required = false) CreditScoringService creditScoringService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) UserRepo userRepo) {
        this.apiKey = (apiKey == null || apiKey.trim().isEmpty() || apiKey.trim().startsWith("[")) ? "" : apiKey.trim();
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.chunkRepo = chunkRepo;
        this.indexStore = indexStore;
        this.loanService = loanService;
        this.mapper = mapper;
        this.http = http;
        this.creditScoringService = creditScoringService;
        this.userRepo = userRepo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void autoIndexOnStartup() {
        try {
            if (chunkRepo.count() == 0) {
                log.info("Knowledge chunk repository is empty on startup. Automatically indexing trusted policy documents...");
                indexTrustedDocuments();
                log.info("Knowledge documents automatically indexed with {} chunks.", chunkRepo.count());
            }
        } catch (Exception ex) {
            log.warn("Auto-indexing on startup skipped or completed with status: {}", ex.getMessage());
        }
    }

    public Map<String, Object> status() {
        return Map.of("configured", !apiKey.isBlank(), "indexedChunks", chunkRepo.count(),
                "embeddingModel", embeddingModel, "chatModel", chatModel);
    }

    public Map<String, Object> indexTrustedDocuments() {
        requireApiKey();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:knowledge/*.md");
            List<String> sources = new ArrayList<>();
            List<String> texts = new ArrayList<>();
            for (Resource resource : resources) {
                String source = resource.getFilename();
                String document = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                        .replaceAll("(?m)^#+\\s*", "").trim();
                List<String> chunks = chunk(document);
                for (String part : chunks) {
                    sources.add(source);
                    texts.add(part);
                }
            }
            if (texts.isEmpty()) {
                throw new IllegalStateException("No trusted knowledge documents were found.");
            }
            List<List<Double>> vectors;
            try {
                vectors = embed(texts);
            } catch (Exception ex) {
                log.warn("Remote embedding provider call failed ({}), generating local semantic vectors", ex.getMessage());
                vectors = embedLocal(texts);
            }
            List<KnowledgeChunk> chunks = new ArrayList<>(texts.size());
            Map<String, Integer> sourceIndexes = new HashMap<>();
            for (int i = 0; i < texts.size(); i++) {
                String source = sources.get(i);
                int count = sourceIndexes.getOrDefault(source, 0) + 1;
                sourceIndexes.put(source, count);
                int index = count - 1;
                chunks.add(new KnowledgeChunk(source, index, texts.get(i), mapper.writeValueAsString(vectors.get(i))));
            }
            indexStore.replaceAll(chunks);
            return Map.of("indexedChunks", chunks.size(), "sources", resources.length);
        } catch (Exception exception) {
            log.warn("Trusted knowledge indexing failed: {}", exception.getClass().getSimpleName());
            throw new IllegalStateException("Knowledge indexing failed. Check model availability and server logs.");
        }
    }

    public Map<String, Object> ask(String question, String authenticatedEmail) {
        if (question == null || question.isBlank()) {
            return answer("Please enter a question.", List.of(), !apiKey.isBlank());
        }
        if (isPersonalStatusQuestion(question)) {
            if (isCreditScoreQuestion(question) && userRepo != null && creditScoringService != null) {
                return answer(personalCreditScoreStatus(question, authenticatedEmail), List.of("Your TrustLend account"), true);
            }
            return answer(personalLoanStatus(question, authenticatedEmail), List.of("Your TrustLend account"), true);
        }
        if (apiKey.isBlank()) {
            return answer("The RAG assistant is not configured. Set OPENAI_API_KEY (or TRUSTLEND_OPENAI_API_KEY) and ask an administrator to index the trusted policy documents.", List.of(), false);
        }
        try {
            List<Double> queryVector;
            try {
                queryVector = embed(List.of(question)).get(0);
            } catch (Exception ex) {
                if (!isGemini() && ex instanceof org.springframework.web.client.RestClientException) {
                    throw (org.springframework.web.client.RestClientException) ex;
                }
                log.warn("Remote query embedding failed ({}), using local semantic query vector", ex.getMessage());
                queryVector = computeLocalVector(question);
            }

            List<KnowledgeChunk> candidates = chunkRepo.findAllByOrderBySourceNameAscChunkIndexAsc();
            List<ScoredChunk> matches = new ArrayList<>();
            for (KnowledgeChunk candidate : candidates) {
                List<Double> vector = mapper.readValue(candidate.getEmbeddingJson(), new TypeReference<>() { });
                matches.add(new ScoredChunk(candidate, cosine(queryVector, vector)));
            }
            List<ScoredChunk> selected = matches.stream()
                    .filter(match -> match.score() >= 0.20)
                    .sorted((first, second) -> Double.compare(second.score(), first.score()))
                    .limit(4)
                    .toList();
            if (selected.isEmpty()) {
                return answer("I couldn't find relevant information in the indexed TrustLend policy documents, so I can't answer that reliably.", List.of(), true);
            }
            String context = selected.stream()
                    .map(match -> "Source: " + match.chunk().getSourceName() + "\n" + match.chunk().getContent())
                    .reduce((left, right) -> left + "\n\n" + right).orElse("");
            String generated = generateAnswer(question, context);
            List<String> sources = selected.stream().map(item -> item.chunk().getSourceName()).distinct().toList();
            return answer(generated + "\n\nSources: " + String.join(", ", sources), sources, true);
        } catch (Exception exception) {
            log.warn("RAG request failed: {}", exception.getClass().getSimpleName());
            return answer("The RAG assistant is temporarily unavailable. Please try again later.", List.of(), true);
        }
    }

    private boolean isGemini() {
        return (chatModel != null && chatModel.toLowerCase().contains("gemini"))
                || (embeddingModel != null && embeddingModel.toLowerCase().contains("gemini"))
                || (apiKey != null && (apiKey.startsWith("AIza") || apiKey.startsWith("AQ.")));
    }

    private List<List<Double>> embed(List<String> inputs) {
        if (isGemini()) {
            return embedGemini(inputs);
        }
        return embedOpenAi(inputs);
    }

    private List<List<Double>> embedOpenAi(List<String> inputs) {
        Map<?, ?> response = post(EMBEDDINGS_URL, Map.of("model", embeddingModel, "input", inputs));
        Object dataValue = response.get("data");
        if (!(dataValue instanceof List<?> data)) throw new IllegalStateException("Embedding response has no data.");
        return data.stream()
                .sorted(Comparator.comparingInt(item -> ((Number) ((Map<?, ?>) item).get("index")).intValue()))
                .map(item -> ((List<?>) ((Map<?, ?>) item).get("embedding")).stream()
                        .map(value -> ((Number) value).doubleValue()).toList())
                .toList();
    }

    private List<List<Double>> embedGemini(List<String> inputs) {
        List<List<Double>> results = new ArrayList<>(inputs.size());
        String modelName = embeddingModel.startsWith("models/") ? embeddingModel.substring(7) : embeddingModel;
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":embedContent?key=" + apiKey;
        for (String text : inputs) {
            Map<String, Object> body = Map.of(
                    "content", Map.of("parts", List.of(Map.of("text", text)))
            );
            Map<?, ?> response = postJson(url, body);
            Object embeddingObj = response.get("embedding");
            if (!(embeddingObj instanceof Map<?, ?> embMap)) {
                throw new IllegalStateException("Gemini embedding response missing 'embedding' field.");
            }
            Object valuesObj = embMap.get("values");
            if (!(valuesObj instanceof List<?> values)) {
                throw new IllegalStateException("Gemini embedding response missing 'values' array.");
            }
            results.add(values.stream().map(v -> ((Number) v).doubleValue()).toList());
        }
        return results;
    }

    private List<List<Double>> embedLocal(List<String> inputs) {
        List<List<Double>> results = new ArrayList<>(inputs.size());
        for (String text : inputs) {
            results.add(computeLocalVector(text));
        }
        return results;
    }

    public static List<Double> computeLocalVector(String text) {
        final int DIM = 128;
        double[] vec = new double[DIM];
        if (text == null || text.isBlank()) {
            List<Double> empty = new ArrayList<>(DIM);
            for (int i = 0; i < DIM; i++) empty.add(0.0);
            return empty;
        }
        String[] words = text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+");
        for (String word : words) {
            if (word.isBlank() || word.length() < 2) continue;
            int hash1 = Math.abs(word.hashCode()) % DIM;
            int hash2 = Math.abs((word + "_seed2").hashCode()) % DIM;
            vec[hash1] += 1.0;
            vec[hash2] += 0.5;
        }
        double norm = 0;
        for (double v : vec) norm += v * v;
        norm = Math.sqrt(norm);
        List<Double> normalized = new ArrayList<>(DIM);
        for (double v : vec) {
            normalized.add(norm > 0 ? v / norm : 0.0);
        }
        return normalized;
    }

    private String generateAnswer(String question, String context) {
        if (isGemini()) {
            try {
                return generateAnswerGemini(question, context);
            } catch (Exception ex) {
                log.warn("Gemini generation call failed ({}), synthesizing answer directly from policy context", ex.getMessage());
                return synthesizePolicyAnswer(question, context);
            }
        }
        return generateAnswerOpenAi(question, context);
    }

    private String synthesizePolicyAnswer(String question, String context) {
        if (context == null || context.isBlank()) {
            return "I couldn't find relevant information in the indexed TrustLend policy documents.";
        }
        return "Based on TrustLend platform policy documents:\n\n" + context.trim();
    }

    private String generateAnswerOpenAi(String question, String context) {
        Map<?, ?> response = post(CHAT_URL, Map.of(
                "model", chatModel,
                "temperature", 0.1,
                "max_tokens", 350,
                "messages", List.of(
                        Map.of("role", "system", "content", "Answer only from the supplied retrieved TrustLend policy excerpts. If they do not support an answer, say so. Do not invent policies, eligibility, or account status. Keep financial guidance informational.\n\nRetrieved excerpts:\n" + context),
                        Map.of("role", "user", "content", question))));
        List<?> choices = (List<?>) response.get("choices");
        if (choices == null || choices.isEmpty()) throw new IllegalStateException("Chat response had no choices.");
        return String.valueOf(((Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message")).get("content")).trim();
    }

    private String generateAnswerGemini(String question, String context) {
        String modelName = chatModel.startsWith("models/") ? chatModel.substring(7) : chatModel;
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;
        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("role", "user", "parts", List.of(Map.of("text",
                                "Answer only from the supplied retrieved TrustLend policy excerpts. If they do not support an answer, say so. Do not invent policies, eligibility, or account status. Keep financial guidance informational.\n\nRetrieved excerpts:\n" + context + "\n\nQuestion: " + question)))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.1,
                        "maxOutputTokens", 450
                )
        );
        Map<?, ?> response = postJson(url, body);
        List<?> candidates = (List<?>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) throw new IllegalStateException("Gemini response had no candidates.");
        Map<?, ?> candidate = (Map<?, ?>) candidates.get(0);
        Map<?, ?> content = (Map<?, ?>) candidate.get("content");
        if (content == null) throw new IllegalStateException("Gemini candidate had no content.");
        List<?> parts = (List<?>) content.get("parts");
        if (parts == null || parts.isEmpty()) throw new IllegalStateException("Gemini content had no parts.");
        return String.valueOf(((Map<?, ?>) parts.get(0)).get("text")).trim();
    }

    private Map<?, ?> post(String url, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return http.postForObject(url, new HttpEntity<>(body, headers), Map.class);
    }

    private Map<?, ?> postJson(String url, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.postForObject(url, new HttpEntity<>(body, headers), Map.class);
    }

    private void requireApiKey() {
        if (apiKey.isBlank()) throw new IllegalStateException("Set an AI provider API key (GEMINI_API_KEY or TRUSTLEND_OPENAI_API_KEY) before indexing policy documents.");
    }

    private List<String> chunk(String document) {
        List<String> chunks = new ArrayList<>();
        String clean = document.replaceAll("\\s+", " ").trim();
        int start = 0;
        while (start < clean.length()) {
            int end = Math.min(start + MAX_CHUNK_CHARS, clean.length());
            if (end < clean.length()) {
                int boundary = clean.lastIndexOf(' ', end);
                if (boundary > start + MAX_CHUNK_CHARS / 2) end = boundary;
            }
            chunks.add(clean.substring(start, end).trim());
            if (end == clean.length()) break;
            start = Math.max(start + 1, end - OVERLAP_CHARS);
        }
        return chunks;
    }

    static double cosine(List<Double> left, List<Double> right) {
        if (left.size() != right.size() || left.isEmpty()) return -1;
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (int i = 0; i < left.size(); i++) {
            dot += left.get(i) * right.get(i);
            leftNorm += left.get(i) * left.get(i);
            rightNorm += right.get(i) * right.get(i);
        }
        return leftNorm == 0 || rightNorm == 0 ? -1 : dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private boolean isPersonalStatusQuestion(String question) {
        String normalized = question.toLowerCase();
        return normalized.matches(".*\\b(my|mine)\\b.*\\b(loans?|applications?|repayments?|scores?|credit|eligibility|limits?)\\b.*")
                || normalized.matches(".*\\b(loans?|applications?|repayments?|scores?|credit|eligibility|limits?)\\b.*\\b(my|mine)\\b.*")
                || normalized.matches(".*\\b(credit\\s*score|loan\\s*limit|my\\s*status|why\\s+500|why\\s+zero)\\b.*");
    }

    private boolean isCreditScoreQuestion(String question) {
        String normalized = question.toLowerCase();
        return normalized.contains("score") || normalized.contains("credit") || normalized.contains("eligibility")
                || normalized.contains("limit") || normalized.contains("500") || normalized.contains("tier");
    }

    private String personalCreditScoreStatus(String question, String email) {
        if (userRepo == null || creditScoringService == null) {
            return "Unable to access credit scoring service at this time.";
        }
        var userOpt = userRepo.findByEmail(email);
        if (userOpt.isEmpty()) {
            return "I couldn't find an account matching your email (" + email + ").";
        }
        var user = userOpt.get();
        var scoreData = creditScoringService.calculateCreditScore(user);
        int score = (int) scoreData.get("creditScore");
        String riskTier = String.valueOf(scoreData.get("riskTier"));
        boolean eligible = Boolean.TRUE.equals(scoreData.get("eligible"));
        BigDecimal maxLoan = (BigDecimal) scoreData.get("maxEligibleLoan");

        @SuppressWarnings("unchecked")
        Map<String, Object> factors = (Map<String, Object>) scoreData.get("factors");
        long repaidCount = factors.containsKey("repaidLoans") ? ((Number) factors.get("repaidLoans")).longValue() : 0;
        long overdueCount = factors.containsKey("overdueLoans") ? ((Number) factors.get("overdueLoans")).longValue() : 0;
        BigDecimal walletBal = factors.containsKey("walletBalance") ? (BigDecimal) factors.get("walletBalance") : BigDecimal.ZERO;
        boolean inGroup = Boolean.TRUE.equals(factors.get("activeGroupMember"));

        StringBuilder sb = new StringBuilder();
        sb.append("Your Credit Profile Overview:\n");
        sb.append("• Credit Score: ").append(score).append(" out of 850\n");
        sb.append("• Risk Tier: ").append(riskTier).append(" risk\n");
        sb.append("• Eligibility: ").append(eligible ? "Eligible for micro-loans" : "Below eligibility threshold (550 minimum required)").append("\n");
        sb.append("• Maximum Eligible Loan Limit: ₹").append(maxLoan).append("\n\n");

        sb.append("Contributing Factors:\n");
        sb.append("• Base Score: 500 points\n");
        sb.append("• Repaid Loans: ").append(repaidCount).append(" (+").append(repaidCount * 50).append(" pts)\n");
        sb.append("• Overdue Loans: ").append(overdueCount).append(" (-").append(overdueCount * 100).append(" pts)\n");
        sb.append("• Wallet Balance: ₹").append(walletBal).append(" (+").append(walletBal.compareTo(BigDecimal.valueOf(1000)) >= 0 ? 50 : (walletBal.compareTo(BigDecimal.valueOf(500)) >= 0 ? 25 : 0)).append(" pts)\n");
        sb.append("• Active SHG Membership: ").append(inGroup ? "Yes (+50 pts)" : "No (+0 pts)").append("\n\n");

        if (!eligible) {
            sb.append("Why is your loan limit ₹0.00?\n");
            sb.append("TrustLend requires a minimum credit score of 550 to qualify for loan applications.\n\n");
            sb.append("How to improve your score:\n");
            sb.append("1. Join an active Self-Help Group (SHG) to earn +50 points.\n");
            sb.append("2. Maintain a simulated wallet balance of at least ₹500 (+25 pts) or ₹1,000 (+50 pts).\n");
            sb.append("Once your score reaches 550, you unlock loan eligibility up to ₹25,000.");
        } else {
            sb.append("Your account is currently eligible to apply for micro-loans up to ₹").append(maxLoan).append(".");
        }
        return sb.toString();
    }

    private String personalLoanStatus(String question, String email) {
        List<LoanResponse> loans = loanService.getMyLoans(email);
        Matcher idMatcher = Pattern.compile("\\bloan\\s*#?(\\d+)\\b", Pattern.CASE_INSENSITIVE).matcher(question);
        if (idMatcher.find()) {
            Long requestedId = Long.valueOf(idMatcher.group(1));
            loans = loans.stream().filter(loan -> loan.id().equals(requestedId)).toList();
        }
        if (loans.isEmpty()) return "I couldn't find that loan or application in your account.";
        return "Your loan/application status: " + loans.stream()
                .map(loan -> "#" + loan.id() + " is " + loan.status() + " for " + loan.amount())
                .reduce((left, right) -> left + "; " + right).orElse("");
    }

    private Map<String, Object> answer(String reply, List<String> sources, boolean configured) {
        return Map.of("reply", reply, "sources", sources, "ragConfigured", configured);
    }

    private record ScoredChunk(KnowledgeChunk chunk, double score) { }
}
