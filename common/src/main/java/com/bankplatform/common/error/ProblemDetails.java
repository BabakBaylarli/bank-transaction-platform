package com.bankplatform.common.error;

import com.bankplatform.common.logging.CorrelationId;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;

/** Factory for the platform's RFC 7807 responses: stable {@code type} URIs plus correlation data. */
public final class ProblemDetails {

    public static final String TYPE_BASE = "https://bank-platform.dev/problems/";

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatusCode status, String errorCode, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + errorCode));
        problem.setTitle(title);
        problem.setProperty("errorCode", errorCode);
        return enrich(problem);
    }

    /** Adds the correlation id and a timestamp to any problem detail. */
    public static ProblemDetail enrich(ProblemDetail problem) {
        if (problem.getProperties() == null || !problem.getProperties().containsKey("timestamp")) {
            problem.setProperty("timestamp", Instant.now());
        }
        String correlationId = CorrelationId.current();
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        return problem;
    }
}
