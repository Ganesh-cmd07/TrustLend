package app.services;

import app.Repo.LenderRequestRepo;
import app.Repo.UserRepo;
import app.data.LenderRequestResponse;
import app.data.UserSummaryResponse;
import app.data.PagedUsersResponse;
import app.exception.ApiException;
import app.models.LenderRequest;
import app.models.LenderRequestStatus;
import app.models.User;
import app.models.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.util.List;

@Service
public class LenderRequestService {

    private final UserRepo userRepo;
    private final LenderRequestRepo lenderRequestRepo;

    public LenderRequestService(UserRepo userRepo, LenderRequestRepo lenderRequestRepo) {
        this.userRepo = userRepo;
        this.lenderRequestRepo = lenderRequestRepo;
    }

    @Transactional
    public LenderRequestResponse requestLenderStatus(String email) {
        User user = userRepo.findByEmailForUpdate(email)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user was not found"
                ));
        if (user.getRole() != UserRole.BORROWER) {
            throw new ApiException(HttpStatus.CONFLICT, "Only borrowers may request lender status");
        }

        lenderRequestRepo.findFirstByUser_IdOrderByCreatedAtDescIdDesc(user.getId())
                .filter(request -> request.getStatus() == LenderRequestStatus.PENDING)
                .ifPresent(request -> {
                    throw new ApiException(HttpStatus.CONFLICT, "A lender request is already pending");
                });

        return LenderRequestResponse.from(lenderRequestRepo.save(new LenderRequest(user)));
    }

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> getUsers() {
        return userRepo.findAllByOrderByIdAsc()
                .stream()
                .map(UserSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedUsersResponse searchUsers(int page, int size, UserRole role, String query) {
        if (page < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Page must be zero or greater");
        if (size < 1 || size > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "Page size must be between 1 and 100");
        if (query != null && query.length() > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "Search must be at most 100 characters");
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        return PagedUsersResponse.from(userRepo.searchDirectory(role, normalizedQuery,
                PageRequest.of(page, size)));
    }

    @Transactional(readOnly = true)
    public List<LenderRequestResponse> getPendingRequests() {
        return lenderRequestRepo.findAllByStatusOrderByCreatedAtAscIdAsc(LenderRequestStatus.PENDING)
                .stream()
                .map(LenderRequestResponse::from)
                .toList();
    }

    @Transactional
    public LenderRequestResponse approveRequest(Long requestId) {
        LenderRequest request = findPendingRequest(requestId);
        User user = request.getUser();
        user.setRole(UserRole.LENDER);
        request.setStatus(LenderRequestStatus.APPROVED);
        userRepo.save(user);
        return LenderRequestResponse.from(request);
    }

    @Transactional
    public LenderRequestResponse rejectRequest(Long requestId) {
        LenderRequest request = findPendingRequest(requestId);
        request.setStatus(LenderRequestStatus.REJECTED);
        return LenderRequestResponse.from(request);
    }

    private LenderRequest findPendingRequest(Long requestId) {
        LenderRequest request = lenderRequestRepo.findById(requestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lender request was not found"));
        if (request.getStatus() != LenderRequestStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "Lender request has already been reviewed");
        }
        return request;
    }

}
