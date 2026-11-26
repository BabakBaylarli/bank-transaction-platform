package com.bankplatform.search.search;

import com.bankplatform.search.index.TransactionDocument;
import org.mapstruct.Mapper;

@Mapper
public interface SearchHitMapper {

    TransactionSearchHit toHit(TransactionDocument document);
}
