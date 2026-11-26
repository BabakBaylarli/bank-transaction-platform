package com.bankplatform.search.search;

import com.bankplatform.common.security.AuthenticatedUser;
import com.bankplatform.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Tag(name = "Search")
@RestController
@RequestMapping("/api/v1/search/transactions")
@RequiredArgsConstructor
public class SearchController {

    private final TransactionSearchService searchService;
    private final Clock clock;

    @Operation(summary = "Search transactions",
            description = "Customers see only transactions touching their own accounts; ADMIN sees everything.")
    @GetMapping
    public PageResponse<TransactionSearchHit> search(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @Valid @ParameterObject SearchCriteria criteria) {
        return searchService.search(user, criteria);
    }

    @Operation(summary = "Totals per day, status and currency (ADMIN)",
            description = "Defaults to the last 30 days.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/stats")
    public List<DailyStats> stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant end = to != null ? to : clock.instant();
        Instant start = from != null ? from : end.minus(Duration.ofDays(30));
        return searchService.dailyStats(start, end);
    }
}
