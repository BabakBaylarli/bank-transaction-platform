package com.bankplatform.search.index;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface TransactionDocumentRepository extends ElasticsearchRepository<TransactionDocument, String> {
}
