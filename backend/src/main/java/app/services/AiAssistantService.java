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
import java.util.Set;
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
            if (isScoreImprovementQuestion(question) && userRepo != null && creditScoringService != null) {
                return answer(personalScoreImprovementRoadmap(authenticatedEmail), List.of("Your TrustLend account"), true);
            }
            if (isWhyScoreOrLimitQuestion(question) && userRepo != null && creditScoringService != null) {
                return answer(personalWhyScoreOrLimit(authenticatedEmail), List.of("Your TrustLend account"), true);
            }
            if (isCreditScoreCheckQuestion(question) && userRepo != null && creditScoringService != null) {
                return answer(personalCreditScoreStatus(authenticatedEmail), List.of("Your TrustLend account"), true);
            }
            if (isWalletQuestion(question) && userRepo != null) {
                return answer(personalWalletStatus(authenticatedEmail), List.of("Your TrustLend wallet"), true);
            }
            if (isPersonalLoanStatusQuestion(question)) {
                return answer(personalLoanStatus(question, authenticatedEmail), List.of("Your TrustLend account"), true);
            }
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
                double vecScore = cosine(queryVector, vector);
                double combinedScore = scoreChunkRelevance(question, candidate.getContent(), candidate.getSourceName(), vecScore);
                matches.add(new ScoredChunk(candidate, combinedScore));
            }
            List<ScoredChunk> selected = matches.stream()
                    .filter(match -> match.score() > 0.05)
                    .sorted((first, second) -> Double.compare(second.score(), first.score()))
                    .limit(2)
                    .toList();
            if (selected.isEmpty() && !matches.isEmpty()) {
                selected = matches.stream()
                        .sorted((first, second) -> Double.compare(second.score(), first.score()))
                        .limit(1)
                        .toList();
            }
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
        String q = question.toLowerCase().trim();

        // 1. Can I borrow without a group? / Individual loans
        if (q.contains("without a group") || q.contains("without shg") || q.contains("individual loan")
                || q.contains("need a group") || q.contains("alone") || q.contains("single borrower")) {
            return "Applying Individually vs. In a Group on TrustLend:\n"
                    + "• Yes, you can borrow individually without being part of a group.\n"
                    + "• Individual Loans: You apply directly from the 'Borrow' page. Once your credit score is 550+, your application is reviewed by an Administrator and made available to community lenders.\n"
                    + "• Group Loans: Backed by a Self-Help Group (SHG) and require the group leader's co-signature before Admin review, providing enhanced peer accountability.";
        }

        // 2. Can an SHG leader borrow?
        if (q.contains("leader borrow") || q.contains("leader apply") || q.contains("leader co-sign")
                || q.contains("leader's own loan") || q.contains("leader own loan")) {
            return "SHG Group Leader Borrowing Rules:\n"
                    + "• A group leader cannot co-sign their own personal group loan.\n"
                    + "• A group leader cannot apply for their own group's loan as an ordinary member.\n"
                    + "• However, a leader can apply for an Individual Micro-Loan (reviewed directly by Administrators), ensuring no self-approval conflicts.";
        }

        // 3. Due dates & first installment
        if (q.contains("first installment") || (q.contains("when is") && (q.contains("due") || q.contains("first payment")))) {
            return "TrustLend Installment Due Dates:\n"
                    + "• The first installment is due exactly 1 month after your loan is funded by a lender.\n"
                    + "• Subsequent installments are due monthly on the same day.\n"
                    + "• Installments are divided equally, with any rounding remainder added to the final installment.";
        }

        // 4. Early repayment / prepayment
        if (q.contains("early") || q.contains("prepay") || q.contains("in advance") || q.contains("pay ahead")) {
            return "Early Installment Repayment on TrustLend:\n"
                    + "• Yes! Borrowers can pay upcoming installments anytime ahead of their scheduled due dates.\n"
                    + "• Simply ensure your simulated digital wallet has sufficient balance and click 'Pay installment' on your Borrower Dashboard.\n"
                    + "• Early and timely repayments build positive credit history and protect your score from late penalties.";
        }

        // 5. Is it real money / real bank transfers?
        if (q.contains("real money") || q.contains("real cash") || q.contains("bank account")
                || q.contains("external bank") || q.contains("real transfer") || q.contains("real-world")) {
            return "TrustLend Wallet System & Currency:\n"
                    + "• TrustLend operates a closed-loop simulated digital wallet designed for microfinance inclusion and peer lending education.\n"
                    + "• All deposits, disbursements, and repayments are simulated ledger transactions within the platform.\n"
                    + "• Transactions do not connect to external real-world bank accounts or payment gateways.";
        }

        // 6. Interest rate range & calculation formula
        if (q.contains("how is interest") || q.contains("interest formula") || q.contains("simple interest")
                || q.contains("apr") || q.contains("interest rate range") || q.contains("rate range")) {
            return "TrustLend Interest Calculation & APR:\n"
                    + "• Simple Interest Formula: Interest = (Principal × Annual Rate × Term Months) / 1200.\n"
                    + "• Rate Range: Applicants can propose an annual interest rate from 0% to 100% per year.\n"
                    + "• Monthly Installments: Total Repayable = Principal + Total Interest. This total is split into equal monthly installments.";
        }

        // 7. Minimum credit score to apply
        if (q.contains("minimum score") || q.contains("minimum credit") || q.contains("score required")
                || q.contains("what score do i need") || q.contains("lowest score")) {
            return "Minimum Credit Score Requirement:\n"
                    + "• The minimum credit score to apply for a loan on TrustLend is 550 points.\n"
                    + "• Scores below 550 (300–549) have a ₹0 loan limit (HIGH risk).\n"
                    + "• Once your score reaches 550, you instantly unlock loan eligibility up to ₹25,000.";
        }

        // 8. Credit score tiers and maximum loan amounts
        if ((q.contains("tier") && q.contains("score")) || q.contains("credit tiers") || q.contains("maximum eligible loan")
                || q.contains("loan limit") || q.contains("maximum loan") || q.contains("tier limit")) {
            return "TrustLend Credit Score Tiers & Loan Limits:\n"
                    + "• Below 550 (300–549 points): HIGH Risk → ₹0 limit (Ineligible)\n"
                    + "• 550 to 649 points: HIGH Risk → Eligible up to ₹25,000\n"
                    + "• 650 to 749 points: MEDIUM Risk → Eligible up to ₹50,000\n"
                    + "• 750 to 850 points: LOW Risk → Eligible up to ₹1,00,000\n\n"
                    + "Points Rules: Base score is 500. Joining SHG (+50 pts), wallet balance ₹500+ (+25 pts) or ₹1,000+ (+50 pts), repaid loans (+50 pts each). Overdue loans deduct -100 pts each.";
        }

        // 9. Loan term durations
        if (q.contains("term") && (q.contains("month") || q.contains("duration") || q.contains("how long") || q.contains("range") || q.contains("length"))) {
            return "TrustLend Loan Terms & Durations:\n"
                    + "• Borrowers can select loan terms between 1 month and 120 months (up to 10 years).\n"
                    + "• Installments are billed monthly over the chosen term duration.\n"
                    + "• The term length directly determines the monthly installment amount and total simple interest.";
        }

        // 10. How to become a lender / lender returns
        if (q.contains("become a lender") || q.contains("be a lender") || q.contains("lender upgrade")
                || q.contains("lender returns") || q.contains("earn interest") || q.contains("fund loans")) {
            return "Becoming a Lender on TrustLend:\n"
                    + "1. Click 'Become a lender' on the top navigation bar.\n"
                    + "2. Submit your motivation and lending goals.\n"
                    + "3. An Administrator reviews and approves the request in the Admin portal.\n"
                    + "4. Once upgraded to LENDER, you can browse approved borrower and SHG loan requests and fund them from your wallet.\n"
                    + "5. You earn monthly simple interest directly in your wallet as borrowers repay their installments.";
        }

        // 11. Overdue loans, defaults, late fees
        if (q.contains("overdue") || q.contains("late payment") || q.contains("default")
                || q.contains("missed payment") || q.contains("late fee") || q.contains("penalty")) {
            return "What Happens if a Loan is Overdue:\n"
                    + "• Daily Audit: TrustLend automatically checks installment due dates every day.\n"
                    + "• Overdue Status: Unpaid installments past their due date are marked OVERDUE.\n"
                    + "• Credit Score Penalty: Your score immediately drops by 100 points for each overdue loan.\n"
                    + "• Application Freeze: You cannot apply for or receive new loans while having overdue installments.\n"
                    + "• Settlement: Deposit simulated funds into your wallet and click 'Pay installment' on your Borrower Dashboard to clear the overdue status.";
        }

        // 12. Self-Help Group (SHG) mechanics
        if (q.contains("shg") || q.contains("self-help") || q.contains("group lending")
                || q.contains("group members") || q.contains("join group") || q.contains("create group")) {
            return "How TrustLend Self-Help Groups (SHGs) Work:\n"
                    + "• Group Creation: Any borrower can create a new group and becomes the leader.\n"
                    + "• Joining a Group: Borrowers can request to join active groups; the leader reviews and approves requests.\n"
                    + "• Credit Boost: Active membership in an SHG immediately awards +50 points to your credit score!\n"
                    + "• Group Loans: Members can submit group-backed loans. The leader must co-sign before Admin review and lender funding.\n"
                    + "• Leader Rule: Leaders cannot co-sign their own personal loans.";
        }

        // 13. How to apply for a loan (procedure)
        if (q.contains("how to apply") || q.contains("how do i apply") || q.contains("application process")
                || q.contains("steps to apply") || q.contains("how to borrow") || q.contains("apply for a loan")) {
            return "How to Apply for a Loan on TrustLend:\n"
                    + "1. Check Score: Ensure your credit score is at least 550 (scores under 550 have a ₹0 limit).\n"
                    + "2. Go to 'Borrow': Click 'Borrow' on the top navigation bar.\n"
                    + "3. Choose Application Type: Select either an Individual application or an SHG Group-backed application.\n"
                    + "4. Enter Amount & Terms: Select a principal amount within your tier limit (up to ₹25,000 for 550+, ₹50,000 for 650+, ₹1,00,000 for 750+), interest rate (0%–100%), and term (1–120 months).\n"
                    + "5. Review Process: Submit the application. Group-backed loans require SHG leader co-signature first. Once approved by an Administrator, lenders can fund your loan.";
        }

        // 14. Loan rejection reasons
        if (q.contains("reject") || q.contains("decline") || q.contains("denied")) {
            return "Why a Loan Application May Be Rejected:\n"
                    + "• Administrators review submitted applications for credit eligibility, completeness, and platform guidelines.\n"
                    + "• When rejecting a loan, the Administrator is required to provide an official rejection reason.\n"
                    + "• The rejection reason and reviewer timestamp are permanently recorded in the audit logs.";
        }

        // 15. User roles and permissions
        if (q.contains("role") || q.contains("permission") || q.contains("admin") || q.contains("governance")) {
            return "TrustLend User Roles & Governance:\n"
                    + "• BORROWER: Default role. Can view credit score, join SHGs, apply for micro-loans (550+ score), and repay installments.\n"
                    + "• LENDER: Community investor role. Can browse approved loans from borrowers and SHGs, fund loans from wallet, and earn monthly interest.\n"
                    + "• ADMIN: Governance role. Reviews and approves/rejects loans (with mandatory reason), approves lender role upgrades, and monitors audit logs.";
        }

        // 16. Wallet deposit & ledger
        if (q.contains("wallet") || q.contains("deposit") || q.contains("top up") || q.contains("add money")) {
            return "TrustLend Simulated Digital Wallet:\n"
                    + "• Deposits: Top up simulated funds anytime on the 'Wallet' page by entering an amount and clicking 'Deposit'.\n"
                    + "• Credit Scoring Points: Maintaining a wallet balance of ₹500+ earns +25 points; maintaining ₹1,000+ earns +50 points toward your credit score.\n"
                    + "• Audit Ledger: Every deposit, loan disbursement, and installment repayment writes an immutable transaction ledger entry.";
        }

        // 17. Income, salary slips, documentation
        if (q.contains("income") || q.contains("salary") || q.contains("document")
                || q.contains("payslip") || q.contains("obligation") || q.contains("proof")) {
            return "TrustLend Underwriting & Documentation Policy:\n"
                    + "• TrustLend uses a transparent rule-based credit scoring engine and does NOT collect income proofs, salary slips, or recurring bank obligations.\n"
                    + "• Eligibility is determined by your platform behavior: base score (500), active SHG membership (+50 pts), simulated wallet balance (+25 to +50 pts), and timely repayment history (+50 pts per repaid loan).";
        }

        // 18. Dynamic Smart Extractor for any other question
        return dynamicSmartExtract(question, context);
    }

    static double scoreChunkRelevance(String query, String content, String source, double vectorScore) {
        if (content == null || content.isBlank()) return 0;
        String q = query.toLowerCase();
        String c = content.toLowerCase();
        double score = vectorScore > 0 ? vectorScore : 0;

        String[] keywords = q.replaceAll("[^a-z0-9\\s]", " ").split("\\s+");
        for (String word : keywords) {
            if (word.length() <= 2) continue;
            if (c.contains(word)) score += 0.3;
            if (source.toLowerCase().contains(word)) score += 0.6;
        }
        return score;
    }

    private String dynamicSmartExtract(String question, String context) {
        if (context == null || context.isBlank()) {
            return "I couldn't find specific information about that in the TrustLend policy documents.";
        }

        String cleanContext = context.replaceAll("Source:\\s*[^\\n]+", "")
                .replaceAll("(?m)^#+\\s*", "")
                .trim();

        String[] sentences = cleanContext.split("(?<=[.!?\\n])\\s+");
        String[] qWords = question.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+");
        Set<String> stopWords = Set.of("what", "when", "where", "which", "who", "whom", "whose", "why", "how",
                "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", "do", "does", "did",
                "can", "could", "should", "would", "will", "shall", "may", "might", "must", "the", "a", "an",
                "and", "or", "but", "if", "because", "as", "until", "while", "of", "at", "by", "for", "with",
                "about", "against", "between", "into", "through", "during", "before", "after", "above", "below",
                "to", "from", "up", "down", "in", "out", "on", "off", "over", "under", "again", "further", "then",
                "once", "here", "there", "all", "any", "both", "each", "few", "more", "most", "other", "some",
                "such", "no", "nor", "not", "only", "own", "same", "so", "than", "too", "very", "just", "now");

        List<String> meaningfulQWords = new ArrayList<>();
        for (String w : qWords) {
            if (w.length() >= 3 && !stopWords.contains(w)) {
                meaningfulQWords.add(w);
            }
        }

        if (meaningfulQWords.isEmpty() && sentences.length > 0) {
            return "Based on TrustLend platform policy:\n\n" + sentences[0].trim();
        }

        record ScoredSentence(String text, double score) {}
        List<ScoredSentence> scored = new ArrayList<>();

        for (String raw : sentences) {
            String s = raw.trim();
            if (s.length() < 15) continue;
            String lower = s.toLowerCase();
            double score = 0;
            int matchedWords = 0;
            for (String qw : meaningfulQWords) {
                if (lower.contains(qw)) {
                    score += 1.0;
                    matchedWords++;
                    if (lower.matches(".*\\b" + Pattern.quote(qw) + "\\b.*")) {
                        score += 0.5;
                    }
                }
            }
            if (matchedWords > 0) {
                double density = (double) matchedWords / meaningfulQWords.size();
                score += density * 2.0;
                scored.add(new ScoredSentence(s, score));
            }
        }

        scored.sort((a, b) -> Double.compare(b.score(), a.score()));

        if (scored.isEmpty()) {
            return "Based on TrustLend platform policy:\n\n" + (sentences.length > 0 ? sentences[0].trim() : cleanContext);
        }

        List<String> topSentences = new ArrayList<>();
        for (int i = 0; i < Math.min(3, scored.size()); i++) {
            String text = scored.get(i).text();
            if (!topSentences.contains(text)) {
                topSentences.add(text);
            }
        }

        StringBuilder sb = new StringBuilder("Based on TrustLend platform policy regarding your query:\n\n");
        for (String s : topSentences) {
            sb.append("• ").append(s).append("\n");
        }
        return sb.toString().trim();
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
        if (question == null) return false;
        String q = question.toLowerCase().trim();
        return isScoreImprovementQuestion(q)
                || isWhyScoreOrLimitQuestion(q)
                || isCreditScoreCheckQuestion(q)
                || isWalletQuestion(q)
                || isPersonalLoanStatusQuestion(q);
    }

    private boolean isScoreImprovementQuestion(String question) {
        if (question == null) return false;
        String q = question.toLowerCase();
        return q.contains("improve") || q.contains("raise") || q.contains("increase")
                || q.contains("reach 550") || q.contains("get to 550") || q.contains("boost")
                || q.contains("better score") || q.contains("higher score");
    }

    private boolean isWhyScoreOrLimitQuestion(String question) {
        if (question == null) return false;
        String q = question.toLowerCase();
        return q.contains("why") && (q.contains("500") || q.contains("zero") || q.contains("0")
                || q.contains("score") || q.contains("limit") || q.contains("ineligible") || q.contains("tier"));
    }

    private boolean isCreditScoreCheckQuestion(String question) {
        if (question == null) return false;
        String q = question.toLowerCase().trim();
        return (q.matches(".*\\b(my|mine)\\b.*") && (q.contains("score") || q.contains("credit") || q.contains("limit") || q.contains("eligibility") || q.contains("tier")))
                || q.contains("my credit score") || q.contains("my score") || q.contains("check my score");
    }

    private boolean isWalletQuestion(String question) {
        if (question == null) return false;
        String q = question.toLowerCase();
        if (q.contains("real") || q.contains("how does") || q.contains("what is") || q.contains("how do i deposit") || q.contains("how to deposit")) {
            return false;
        }
        return (q.contains("my") || q.contains("account")) && (q.contains("wallet") || q.contains("balance") || q.contains("funds"));
    }

    private boolean isPersonalLoanStatusQuestion(String question) {
        if (question == null) return false;
        String q = question.toLowerCase();
        if (q.contains("how") || q.contains("apply") || q.contains("procedure") || q.contains("steps") || q.contains("can i get") || q.contains("rules")) {
            return false;
        }
        return q.matches(".*\\b(my|mine)\\b.*\\b(loans?|applications?|status|history)\\b.*")
                || q.matches(".*\\b(status|history)\\b.*\\b(my|mine)\\b.*")
                || q.matches(".*\\bloan\\s*#?\\d+\\b.*");
    }

    private String personalWalletStatus(String email) {
        if (userRepo == null) return "Unable to access wallet service.";
        var userOpt = userRepo.findByEmail(email);
        if (userOpt.isEmpty()) return "Account not found for email " + email;
        var user = userOpt.get();
        var scoreData = creditScoringService != null ? creditScoringService.calculateCreditScore(user) : null;
        BigDecimal bal = BigDecimal.ZERO;
        if (scoreData != null && scoreData.get("factors") instanceof Map<?, ?> factors) {
            bal = factors.containsKey("walletBalance") ? (BigDecimal) factors.get("walletBalance") : BigDecimal.ZERO;
        }
        return "Your TrustLend Wallet Status:\n"
                + "• Current Simulated Balance: ₹" + bal + "\n"
                + "• Credit Scoring Contribution: " + (bal.compareTo(BigDecimal.valueOf(1000)) >= 0 ? "+50 points (top tier achieved)" : (bal.compareTo(BigDecimal.valueOf(500)) >= 0 ? "+25 points (maintain ₹1,000 for +50 pts)" : "+0 points (maintain ₹500 for +25 pts or ₹1,000 for +50 pts)")) + "\n\n"
                + "You can add simulated funds anytime by clicking 'Deposit' on the Wallet page.";
    }

    private String personalScoreImprovementRoadmap(String email) {
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
        BigDecimal maxLoan = (BigDecimal) scoreData.get("maxEligibleLoan");

        @SuppressWarnings("unchecked")
        Map<String, Object> factors = (Map<String, Object>) scoreData.get("factors");
        long repaidCount = factors.containsKey("repaidLoans") ? ((Number) factors.get("repaidLoans")).longValue() : 0;
        long overdueCount = factors.containsKey("overdueLoans") ? ((Number) factors.get("overdueLoans")).longValue() : 0;
        BigDecimal walletBal = factors.containsKey("walletBalance") ? (BigDecimal) factors.get("walletBalance") : BigDecimal.ZERO;
        boolean inGroup = Boolean.TRUE.equals(factors.get("activeGroupMember"));

        StringBuilder sb = new StringBuilder();
        sb.append("Action Plan to Improve Your Credit Score to 550+:\n\n");
        sb.append("Current Status: ").append(score).append(" / 850 (").append(riskTier).append(" risk)\n");
        sb.append("Minimum Required: 550 points to unlock micro-loan applications\n\n");

        sb.append("Tailored Actions for Your Profile:\n");
        if (inGroup) {
            sb.append("1. Self-Help Group (SHG): [Done] You are already an active SHG member (+50 points earned).\n");
        } else {
            sb.append("1. Self-Help Group (SHG): [Recommended] Join or create an SHG group on the 'Groups' page. Active membership instantly awards +50 points!\n");
        }

        if (walletBal.compareTo(BigDecimal.valueOf(1000)) >= 0) {
            sb.append("2. Wallet Balance: [Done] You hold ₹").append(walletBal).append(", earning the maximum +50 points.\n");
        } else if (walletBal.compareTo(BigDecimal.valueOf(500)) >= 0) {
            sb.append("2. Wallet Balance: Your wallet holds ₹").append(walletBal).append(" (+25 pts). Deposit to reach ₹1,000 to earn the top tier of +50 points (+25 additional pts).\n");
        } else {
            sb.append("2. Wallet Balance: [Quick Win] Go to the 'Wallet' tab and deposit at least ₹500 (+25 pts) or ₹1,000 (+50 pts).\n");
        }

        if (overdueCount > 0) {
            sb.append("3. Overdue Loans: [Urgent] You have ").append(overdueCount).append(" overdue loan(s) causing a -").append(overdueCount * 100).append(" pt penalty! Repaying overdue installments immediately restores 100 points per loan.\n");
        } else {
            sb.append("3. Overdue Loans: [Good] No overdue loans (no penalty applied).\n");
        }

        if (repaidCount > 0) {
            sb.append("4. Repayment History: You have fully repaid ").append(repaidCount).append(" loan(s) (+").append(repaidCount * 50).append(" pts).\n\n");
        } else {
            sb.append("4. Repayment History: Once approved, each fully repaid loan adds +50 points to your score.\n\n");
        }

        if (score < 550) {
            int needed = 550 - score;
            sb.append("Roadmap Projection: You only need ").append(needed).append(" more points. Completing SHG membership (+50 pts) or maintaining a ₹1,000 wallet balance (+50 pts) will push your score above 550 and immediately unlock loan limits up to ₹25,000!");
        } else {
            sb.append("Roadmap Projection: Your score is already ").append(score).append(", which qualifies you to apply for loans up to ₹").append(maxLoan).append("!");
        }
        return sb.toString();
    }

    private String personalWhyScoreOrLimit(String email) {
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
        BigDecimal maxLoan = (BigDecimal) scoreData.get("maxEligibleLoan");

        return "Why Your Score is " + score + " & Loan Limit is ₹" + maxLoan + ":\n\n"
                + "• Initial Starting Score: All new borrowers on TrustLend start with a base score of 500 points (HIGH risk tier) with an initial ₹0 loan limit to ensure responsible peer lending.\n"
                + "• 550 Eligibility Threshold: TrustLend rules require at least 550 credit score points before loan applications can be submitted.\n"
                + "• Fast Ways to Unlock Loans:\n"
                + "  1. Join an SHG group (+50 points) -> instantly reaches 550 points!\n"
                + "  2. Or deposit ₹1,000 in your wallet (+50 points) -> instantly reaches 550 points!\n"
                + "• Once your score reaches 550, your loan limit increases to ₹25,000!";
    }

    private String personalCreditScoreStatus(String email) {
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
        int walletPts = walletBal.compareTo(BigDecimal.valueOf(1000)) >= 0 ? 50 : (walletBal.compareTo(BigDecimal.valueOf(500)) >= 0 ? 25 : 0);

        StringBuilder sb = new StringBuilder();
        sb.append("Your TrustLend Credit Profile Overview:\n");
        sb.append("• Credit Score: ").append(score).append(" out of 850\n");
        sb.append("• Risk Tier: ").append(riskTier).append(" risk\n");
        sb.append("• Loan Limit: ₹").append(maxLoan).append("\n");
        sb.append("• Eligibility Status: ").append(eligible ? "Eligible to apply for micro-loans" : "Below 550 threshold (minimum required to apply)").append("\n\n");

        sb.append("Score Breakdown:\n");
        sb.append("• Base Score: 500 pts\n");
        sb.append("• Wallet Balance (₹").append(walletBal).append("): +").append(walletPts).append(" pts\n");
        sb.append("• Active SHG Membership: ").append(inGroup ? "+50 pts" : "+0 pts").append("\n");
        sb.append("• Repaid Loans (").append(repaidCount).append("): +").append(repaidCount * 50).append(" pts\n");
        sb.append("• Overdue Loans (").append(overdueCount).append("): -").append(overdueCount * 100).append(" pts");
        return sb.toString();
    }

    private String personalLoanStatus(String question, String email) {
        List<LoanResponse> loans = loanService.getMyLoans(email);
        Matcher idMatcher = Pattern.compile("\\bloan\\s*#?(\\d+)\\b", Pattern.CASE_INSENSITIVE).matcher(question);
        if (idMatcher.find()) {
            Long requestedId = Long.valueOf(idMatcher.group(1));
            loans = loans.stream().filter(loan -> loan.id().equals(requestedId)).toList();
        }
        if (loans.isEmpty()) return "I couldn't find any loans or applications in your account. When your credit score is 550+, you can apply from the 'Borrow' page.";
        StringBuilder sb = new StringBuilder("Your Personal Loan History:\n");
        for (var loan : loans) {
            sb.append("• Loan #").append(loan.id())
              .append(": ₹").append(loan.amount())
              .append(" (Status: ").append(loan.status())
              .append(", Interest: ").append(loan.interestRate()).append("%")
              .append(", Term: ").append(loan.termMonths()).append(" months)\n");
        }
        return sb.toString();
    }

    private Map<String, Object> answer(String reply, List<String> sources, boolean configured) {
        return Map.of("reply", reply, "sources", sources, "ragConfigured", configured);
    }

    private record ScoredChunk(KnowledgeChunk chunk, double score) { }
}
