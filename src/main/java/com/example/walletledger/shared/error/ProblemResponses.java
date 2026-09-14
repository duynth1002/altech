package com.example.walletledger.shared.error;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

final class ProblemResponses {

    private static final String TYPE_BASE = "https://example.com/problems/";

    private ProblemResponses() {
    }

    static ResponseEntity<ProblemDetail> of(
            HttpStatus status,
            ErrorCode code,
            String title,
            String detail,
            String instance,
            Clock clock) {
        return of(status, code, title, detail, instance, clock, null);
    }

    static ResponseEntity<ProblemDetail> of(
            HttpStatus status,
            ErrorCode code,
            String title,
            String detail,
            String instance,
            Clock clock,
            List<FieldErrorDetail> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(TYPE_BASE + slug(code)));
        if (instance != null) {
            problem.setInstance(URI.create(instance));
        }
        problem.setProperty("code", code.name());
        problem.setProperty("timestamp", Instant.now(clock));
        problem.setProperty("traceId", MDC.get("traceId"));
        if (errors != null && !errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static String slug(ErrorCode code) {
        return code.name().toLowerCase().replace('_', '-');
    }
}
