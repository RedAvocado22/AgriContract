package com.agricontract.reputation.infrastructure.web;
import com.agricontract.reputation.application.usecase.GetPublicReputationSummary;
import com.agricontract.reputation.domain.model.FactValues;
import com.agricontract.reputation.domain.model.ReputationSummary;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
public class PublicSummaryController {
    private final GetPublicReputationSummary summary;
    public PublicSummaryController(GetPublicReputationSummary summary) { this.summary = summary; }
    @GetMapping("/api/v1/reputation/{userId}/public-summary")
    public ReputationSummary get(@PathVariable String userId) {
        UUID target;
        try {
            target = FactValues.uuid(userId);
        } catch (IllegalArgumentException invalidId) {
            throw new InvalidUserIdException();
        }
        return summary.execute(target);
    }
    @ExceptionHandler(InvalidUserIdException.class)
    public ResponseEntity<ErrorResponse> invalidId(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(request, 400, "Bad Request", "VALIDATION_ERROR"));
    }
    // Only a path parsing failure is client validation. An IllegalArgumentException
    // raised inside the application is still an unexpected server failure.
    private static final class InvalidUserIdException extends RuntimeException {}
}
