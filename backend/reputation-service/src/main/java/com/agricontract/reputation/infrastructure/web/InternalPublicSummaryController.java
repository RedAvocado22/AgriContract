package com.agricontract.reputation.infrastructure.web;

import com.agricontract.reputation.application.dto.BatchSummaryQuery;
import com.agricontract.reputation.application.usecase.GetPublicReputationSummaries;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalPublicSummaryController {
    private final GetPublicReputationSummaries summaries;

    public InternalPublicSummaryController(GetPublicReputationSummaries summaries) {
        this.summaries = summaries;
    }

    @PostMapping("/internal/v1/reputation/public-summaries")
    public ReputationBatchSummaryResponse post(@RequestBody ReputationBatchSummaryRequest request) {
        return ReputationBatchSummaryResponse.from(summaries.execute(new BatchSummaryQuery(request.userIds())));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ErrorResponse> invalidRequest(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(request, 400, "Bad Request", "VALIDATION_ERROR"));
    }
}
