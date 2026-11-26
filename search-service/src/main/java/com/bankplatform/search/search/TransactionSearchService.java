package com.bankplatform.search.search;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.bankplatform.common.event.TransactionStatus;
import com.bankplatform.common.security.AuthenticatedUser;
import com.bankplatform.common.web.PageResponse;
import com.bankplatform.search.index.TransactionDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionSearchService {

    private static final String CREATED_AT = "createdAt";

    private final ElasticsearchOperations operations;
    private final SearchHitMapper mapper;

    public PageResponse<TransactionSearchHit> search(AuthenticatedUser user, SearchCriteria criteria) {
        var pageable = PageRequest.of(criteria.pageOrDefault(), criteria.sizeOrDefault(),
                Sort.by(Sort.Direction.DESC, CREATED_AT));
        NativeQuery query = NativeQuery.builder()
                .withQuery(Query.of(q -> q.bool(buildFilter(user, criteria))))
                .withPageable(pageable)
                .withTrackTotalHits(true)
                .build();

        SearchHits<TransactionDocument> hits = operations.search(query, TransactionDocument.class);
        List<TransactionSearchHit> content = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(mapper::toHit)
                .toList();
        long total = hits.getTotalHits();
        int totalPages = (int) Math.ceil((double) total / pageable.getPageSize());
        return new PageResponse<>(content, pageable.getPageNumber(), pageable.getPageSize(), total, totalPages);
    }

    /** Totals per day / status / currency - computed by Elasticsearch, not in the JVM. */
    public List<DailyStats> dailyStats(Instant from, Instant to) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(Query.of(q -> q.range(r -> r.date(d -> d.field(CREATED_AT)
                        .gte(from.toString()).lt(to.toString())))))
                .withAggregation("per_day", Aggregation.of(day -> day
                        .dateHistogram(h -> h.field(CREATED_AT).calendarInterval(CalendarInterval.Day)
                                .format("yyyy-MM-dd").minDocCount(1))
                        .aggregations("per_status", Aggregation.of(st -> st
                                .terms(t -> t.field("status"))
                                .aggregations("per_currency", Aggregation.of(cur -> cur
                                        .terms(t -> t.field("currency"))
                                        .aggregations("total", Aggregation.of(sum -> sum.sum(s -> s.field("amount"))))))))))
                .withMaxResults(0)
                .build();

        SearchHits<TransactionDocument> hits = operations.search(query, TransactionDocument.class);
        var aggregations = (ElasticsearchAggregations) hits.getAggregations();
        List<DailyStats> result = new ArrayList<>();
        if (aggregations == null) {
            return result;
        }
        List<DateHistogramBucket> days = aggregations.get("per_day").aggregation().getAggregate()
                .dateHistogram().buckets().array();
        for (DateHistogramBucket day : days) {
            LocalDate date = LocalDate.parse(day.keyAsString());
            for (StringTermsBucket status : day.aggregations().get("per_status").sterms().buckets().array()) {
                for (StringTermsBucket currency : status.aggregations().get("per_currency").sterms().buckets().array()) {
                    double sum = currency.aggregations().get("total").sum().value();
                    result.add(new DailyStats(date, TransactionStatus.valueOf(status.key().stringValue()),
                            currency.key().stringValue(), currency.docCount(),
                            BigDecimal.valueOf(sum).setScale(2, RoundingMode.HALF_UP)));
                }
            }
        }
        return result;
    }

    private static BoolQuery buildFilter(AuthenticatedUser user, SearchCriteria c) {
        BoolQuery.Builder bool = new BoolQuery.Builder();
        if (!user.isAdmin()) {
            // customers only ever see transactions that touch one of their own accounts
            bool.filter(f -> f.term(t -> t.field("participants").value(user.id().toString())));
        }
        if (c.account() != null) {
            bool.filter(f -> f.bool(b -> b
                    .should(s -> s.term(t -> t.field("sourceAccount").value(c.account())))
                    .should(s -> s.term(t -> t.field("targetAccount").value(c.account())))
                    .minimumShouldMatch("1")));
        }
        if (c.status() != null) {
            bool.filter(f -> f.term(t -> t.field("status").value(FieldValue.of(c.status().name()))));
        }
        if (c.currency() != null) {
            bool.filter(f -> f.term(t -> t.field("currency").value(FieldValue.of(c.currency().name()))));
        }
        if (c.minAmount() != null || c.maxAmount() != null) {
            bool.filter(f -> f.range(r -> r.number(n -> {
                n.field("amount");
                if (c.minAmount() != null) {
                    n.gte(c.minAmount().doubleValue());
                }
                if (c.maxAmount() != null) {
                    n.lte(c.maxAmount().doubleValue());
                }
                return n;
            })));
        }
        if (c.from() != null || c.to() != null) {
            bool.filter(f -> f.range(r -> r.date(d -> {
                d.field(CREATED_AT);
                if (c.from() != null) {
                    d.gte(c.from().toString());
                }
                if (c.to() != null) {
                    d.lt(c.to().toString());
                }
                return d;
            })));
        }
        if (c.q() != null && !c.q().isBlank()) {
            bool.must(m -> m.simpleQueryString(s -> s.query(c.q())
                    .fields("description", "rejectionDetail", "sourceAccount", "targetAccount")
                    .lenient(true)));
        }
        return bool.build();
    }
}
