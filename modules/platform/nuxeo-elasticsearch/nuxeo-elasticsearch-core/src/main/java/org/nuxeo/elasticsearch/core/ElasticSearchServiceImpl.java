/*
 * (C) Copyright 2014-2018 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Tiry
 *     bdelbosc
 */

package org.nuxeo.elasticsearch.core;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.Bucket;
import org.nuxeo.elasticsearch.aggregate.AggregateEsBase;
import org.nuxeo.elasticsearch.api.ElasticSearchService;
import org.nuxeo.elasticsearch.api.EsResult;
import org.nuxeo.elasticsearch.api.EsScrollResult;
import org.nuxeo.elasticsearch.fetcher.Fetcher;
import org.nuxeo.elasticsearch.query.NxQueryBuilder;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.metrics.MetricsService;
import org.opensearch.action.search.ClearScrollRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchScrollRequest;
import org.opensearch.action.search.SearchType;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.search.aggregations.Aggregation;
import org.opensearch.search.aggregations.bucket.filter.Filter;
import org.opensearch.search.builder.SearchSourceBuilder;

import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.SharedMetricRegistries;
import io.dropwizard.metrics5.Timer;
import io.dropwizard.metrics5.Timer.Context;

/**
 * @since 6.0
 */
public class ElasticSearchServiceImpl implements ElasticSearchService {

    private static final Logger log = LogManager.getLogger(ElasticSearchServiceImpl.class);

    private static final String LOG_MIN_DURATION_FETCH_KEY = "org.nuxeo.elasticsearch.core.log_min_duration_fetch_ms";

    private static final long LOG_MIN_DURATION_FETCH_NS = Long.parseLong(
            Framework.getProperty(LOG_MIN_DURATION_FETCH_KEY, "200")) * 1000000;

    // Metrics
    protected final MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());

    protected final Timer searchTimer;

    protected final Timer scrollTimer;

    protected final Timer fetchTimer;

    private final ElasticSearchAdminImpl esa;

    public ElasticSearchServiceImpl(ElasticSearchAdminImpl esa) {
        this.esa = esa;
        searchTimer = registry.timer(MetricName.build("nuxeo.elasticsearch.service.timer").tagged("service", "search"));
        scrollTimer = registry.timer(MetricName.build("nuxeo.elasticsearch.service.timer").tagged("service", "scroll"));
        fetchTimer = registry.timer(MetricName.build("nuxeo.elasticsearch.service.timer").tagged("service", "fetch"));
    }

    @Override
    public DocumentModelList query(NxQueryBuilder queryBuilder) {
        return queryAndAggregate(queryBuilder).getDocuments();
    }

    @SuppressWarnings("resource") // IterableQueryResult closed by EsResult.getRows().close()
    @Override
    public EsResult queryAndAggregate(NxQueryBuilder queryBuilder) {
        SearchResponse response = search(queryBuilder);
        List<Aggregate<Bucket>> aggs = getAggregates(queryBuilder, response);
        if (queryBuilder.returnsDocuments()) {
            DocumentModelListImpl docs = getDocumentModels(queryBuilder, response);
            return new EsResult(docs, aggs, response);
        } else if (queryBuilder.returnsRows()) {
            IterableQueryResult rows = getRows(queryBuilder, response);
            return new EsResult(rows, aggs, response);
        }
        return new EsResult(response);
    }

    @Override
    public EsScrollResult scroll(NxQueryBuilder queryBuilder, long keepAlive) {
        return scroll(queryBuilder, SearchType.DFS_QUERY_THEN_FETCH, keepAlive);
    }

    protected EsScrollResult scroll(NxQueryBuilder queryBuilder, SearchType searchType, long keepAlive) {
        SearchResponse response = searchScroll(queryBuilder, searchType, keepAlive);
        return getScrollResults(queryBuilder, response, response.getScrollId(), keepAlive);
    }

    @Override
    public EsScrollResult scroll(EsScrollResult scrollResult) {
        SearchResponse response = nextScroll(scrollResult.getScrollId(), scrollResult.getKeepAlive());
        return getScrollResults(scrollResult.getQueryBuilder(), response, response.getScrollId(),
                scrollResult.getKeepAlive());
    }

    @Override
    public void clearScroll(EsScrollResult scrollResult) {
        clearScroll(scrollResult.getScrollId());
    }

    protected void clearScroll(String scrollId) {
        log.debug("Clear scroll : curl -XDELETE 'http://localhost:9200/_search/scroll' -d '{\"scroll_id\" : [\"{}\"]}'",
                scrollId);
        ClearScrollRequest request = new ClearScrollRequest();
        request.addScrollId(scrollId);
        esa.getClient().clearScroll(request);
    }

    @SuppressWarnings("resource") // IterableQueryResult closed by EsResult.getRows().close()
    protected EsScrollResult getScrollResults(NxQueryBuilder queryBuilder, SearchResponse response, String scrollId,
            long keepAlive) {
        if (queryBuilder.returnsDocuments()) {
            DocumentModelListImpl docs = getDocumentModels(queryBuilder, response);
            return new EsScrollResult(docs, response, queryBuilder, scrollId, keepAlive);
        } else if (queryBuilder.returnsRows()) {
            IterableQueryResult rows = getRows(queryBuilder, response);
            return new EsScrollResult(rows, response, queryBuilder, scrollId, keepAlive);
        }
        return new EsScrollResult(response, queryBuilder, scrollId, keepAlive);
    }

    protected DocumentModelListImpl getDocumentModels(NxQueryBuilder queryBuilder, SearchResponse response) {
        DocumentModelListImpl ret;
        long totalSize = response.getHits().getTotalHits().value;
        if (!queryBuilder.returnsDocuments() || response.getHits().getHits().length == 0) {
            ret = new DocumentModelListImpl(0);
            ret.setTotalSize(totalSize);
            return ret;
        }
        try (Context stopWatch = fetchTimer.time()) {
            Fetcher fetcher = queryBuilder.getFetcher(response, esa.getRepositoryMap());
            ret = fetcher.fetchDocuments();
            logMinDurationFetch(stopWatch.stop(), totalSize);
        }
        ret.setTotalSize(totalSize);
        return ret;
    }

    private void logMinDurationFetch(long duration, long totalSize) {
        if (log.isDebugEnabled() && (duration > LOG_MIN_DURATION_FETCH_NS)) {
            String msg = String.format("Slow fetch duration_ms:\t%.2f\treturning:\t%d documents", duration / 1000000.0,
                    totalSize);
            if (log.isTraceEnabled()) {
                log.trace(msg, new Throwable("Slow fetch document stack trace"));
            } else {
                log.debug(msg);
            }
        }
    }

    protected List<Aggregate<Bucket>> getAggregates(NxQueryBuilder queryBuilder, SearchResponse response) {
        for (AggregateEsBase<Aggregation, Bucket> agg : queryBuilder.getAggregates()) {
            Filter filter = response.getAggregations().get(NxQueryBuilder.getAggregateFilterId(agg));
            if (filter == null) {
                continue;
            }
            Aggregation aggregation = filter.getAggregations().get(agg.getId());
            if (aggregation == null) {
                continue;
            }
            agg.parseAggregation(aggregation);
        }
        @SuppressWarnings("unchecked")
        List<Aggregate<Bucket>> ret = (List<Aggregate<Bucket>>) (List<?>) queryBuilder.getAggregates();
        return ret;
    }

    private IterableQueryResult getRows(NxQueryBuilder queryBuilder, SearchResponse response) {
        return new EsResultSetImpl(response, queryBuilder.getSelectFieldsAndTypes());
    }

    protected SearchResponse search(NxQueryBuilder query) {
        try (Context ignored = searchTimer.time()) {
            SearchType searchType = SearchType.DFS_QUERY_THEN_FETCH;
            SearchRequest request = buildEsSearchRequest(query, searchType);
            logSearchRequest(request, query, searchType);
            SearchResponse response = esa.getClient().search(request);
            log.debug("Response: {}", response);
            return response;
        }
    }

    protected SearchResponse searchScroll(NxQueryBuilder query, SearchType searchType, long keepAlive) {
        try (Context ignored = searchTimer.time()) {
            SearchRequest request = buildEsSearchScrollRequest(query, searchType, keepAlive);
            logSearchRequest(request, query, searchType);
            SearchResponse response = esa.getClient().search(request);
            log.debug("Response: {}", response);
            return response;
        }
    }

    protected SearchResponse nextScroll(String scrollId, long keepAlive) {
        try (Context ignored = scrollTimer.time()) {
            SearchScrollRequest request = buildEsScrollRequest(scrollId, keepAlive);
            logScrollRequest(scrollId, keepAlive);
            SearchResponse response = esa.getClient().searchScroll(request);
            log.debug("Response: {}", response);
            return response;
        }
    }

    protected SearchRequest buildEsSearchRequest(NxQueryBuilder query, SearchType searchType) {
        SearchRequest request = new SearchRequest(esa.getSearchIndexes(query.getSearchRepositories()));
        request.searchType(searchType);
        SearchSourceBuilder search = new SearchSourceBuilder();
        query.updateRequest(search);
        request.source(search);
        if (query.isFetchFromElasticsearch()) {
            // fetch the _source without the binaryfulltext field
            search.fetchSource(esa.getIncludeSourceFields(), esa.getExcludeSourceFields());
        }
        return request;
    }

    protected SearchRequest buildEsSearchScrollRequest(NxQueryBuilder query, SearchType searchType, long keepAlive) {
        SearchRequest request = buildEsSearchRequest(query, searchType);
        request.scroll(new TimeValue(keepAlive * 1000));
        return request;
    }

    protected SearchScrollRequest buildEsScrollRequest(String scrollId, long keepAlive) {
        return new SearchScrollRequest(scrollId).scroll(new TimeValue(keepAlive));
    }

    protected void logSearchRequest(SearchRequest request, NxQueryBuilder query, SearchType searchType) {
        if (log.isDebugEnabled()) {
            String scroll = request.scroll() != null ? "&scroll=" + request.scroll() : "";
            log.debug(String.format(
                    "Search query: curl -XGET 'http://localhost:9200/%s/_search?pretty&search_type=%s%s' -d '%s'",
                    getSearchIndexesAsString(query), searchType.toString().toLowerCase(), scroll,
                    request.source().toString()));
        }
    }

    protected void logScrollRequest(String scrollId, long keepAlive) {
        log.debug(
                "Scroll search: curl -XGET 'http://localhost:9200/_search/scroll?pretty' -d '{\"scroll\" : \"{}\", \"scroll_id\" : \"{}\"}'",
                keepAlive, scrollId);
    }

    protected String getSearchIndexesAsString(NxQueryBuilder query) {
        return StringUtils.join(esa.getSearchIndexes(query.getSearchRepositories()), ',');
    }

}
