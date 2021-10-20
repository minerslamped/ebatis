package io.manbang.ebatis.core.request;

import io.manbang.ebatis.core.annotation.Agg;
import io.manbang.ebatis.core.annotation.DeleteByQuery;
import io.manbang.ebatis.core.annotation.MultiSearch;
import io.manbang.ebatis.core.annotation.QueryType;
import io.manbang.ebatis.core.annotation.Search;
import io.manbang.ebatis.core.annotation.SearchScroll;
import io.manbang.ebatis.core.annotation.UpdateByQuery;
import io.manbang.ebatis.core.builder.QueryBuilderFactory;
import io.manbang.ebatis.core.common.AnnotationUtils;
import io.manbang.ebatis.core.domain.Collapse;
import io.manbang.ebatis.core.domain.ContextHolder;
import io.manbang.ebatis.core.domain.Pageable;
import io.manbang.ebatis.core.domain.ScriptField;
import io.manbang.ebatis.core.domain.Sort;
import io.manbang.ebatis.core.meta.MetaUtils;
import io.manbang.ebatis.core.meta.MethodMeta;
import io.manbang.ebatis.core.meta.ParameterMeta;
import io.manbang.ebatis.core.provider.CollapseProvider;
import io.manbang.ebatis.core.provider.HighlighterProvider;
import io.manbang.ebatis.core.provider.RoutingProvider;
import io.manbang.ebatis.core.provider.ScriptFieldProvider;
import io.manbang.ebatis.core.provider.SearchAfterProvider;
import io.manbang.ebatis.core.provider.SortProvider;
import io.manbang.ebatis.core.provider.SourceProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static io.manbang.ebatis.core.domain.HighlighterBuilderUtils.toHighlighterBuilder;

/**
 * @author 章多亮
 * @since 2019/12/17 15:32
 */
@Slf4j
class SearchRequestFactory extends AbstractRequestFactory<Search, SearchRequest> {
    static final SearchRequestFactory INSTANCE = new SearchRequestFactory();
    private static final Map<MethodMeta, QueryBuilderFactory> QUERY_BUILDER_FACTORIES = new ConcurrentHashMap<>();
    private static final List<Class<? extends Annotation>> SEARCH_ANNOTATION_CLASSES;

    static {
        // search request annotation list
        List<Class<? extends Annotation>> annotationClasses = new LinkedList<>();
        annotationClasses.add(Search.class);
        annotationClasses.add(MultiSearch.class);
        annotationClasses.add(SearchScroll.class);
        annotationClasses.add(UpdateByQuery.class);
        annotationClasses.add(DeleteByQuery.class);
        annotationClasses.add(Agg.class);

        SEARCH_ANNOTATION_CLASSES = Collections.unmodifiableList(annotationClasses);
    }

    private SearchRequestFactory() {
    }

    @Override
    protected void setAnnotationMeta(SearchRequest request, Search search) {
        request.preference(StringUtils.trimToNull(search.preference()))
                .searchType(search.searchType());
        request.source().timeout(TimeValue.parseTimeValue(search.timeout(), "search timeout"));
        if (search.countOnly()) {
            request.source().fetchSource(false).size(0);
        }
        if (search.trackTotalHits()) {
            request.source().trackTotalHits(true);
        }
    }

    @Override
    protected SearchRequest doCreate(MethodMeta meta, Object[] args) {
        // 目前支持一个入参作为条件查询，所以可以通过多参数变成一个实体类
        // 传过来的只有一个入参条件
        Optional<ParameterMeta> conditionMeta = meta.findConditionParameter();
        Object condition = conditionMeta.map(p -> p.getValue(args)).orElse(null);

        // 1. 如果是一个入参
        SearchRequest request = Requests.searchRequest(meta.getIndices());
        setTypesIfNecessary(meta, request::types);

        // 获取语句构建器，不能的查询语句是不一样的
        QueryBuilderFactory factory = getQueryBuilderFactory(meta);

        // 创建查询语句
        QueryBuilder queryBuilder = factory.create(conditionMeta.orElse(null), condition);

        SearchSourceBuilder searchSource = new SearchSourceBuilder();
        searchSource.query(queryBuilder);

        // 设置分页参数
        meta.getPageableParameter()
                .map(p -> p.getValue(args))
                .map(Pageable.class::cast)
                .ifPresent(p -> {
                    // 设个上下文是为了创建Page
                    ContextHolder.setPageable(p);
                    searchSource.from(p.getFrom()).size(p.getSize());
                });

        setProviderMeta(condition, searchSource, meta, request);

        request.source(searchSource);

        return request;
    }

    private QueryBuilderFactory getQueryBuilderFactory(MethodMeta meta) {
        return QUERY_BUILDER_FACTORIES.computeIfAbsent(meta, m -> SEARCH_ANNOTATION_CLASSES.stream()
                .map(meta::findAnnotation)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(this::fromAnnotation)
                .findFirst().orElseThrow(IllegalArgumentException::new));
    }

    private QueryBuilderFactory fromAnnotation(Annotation searchAnnotation) {
        Optional<QueryType> queryType = AnnotationUtils.findAttribute(searchAnnotation, "queryType");
        return queryType.orElse(QueryType.AUTO).getQueryBuilderFactory();
    }

    private void setProviderMeta(Object condition, SearchSourceBuilder searchSource, MethodMeta meta, SearchRequest request) {
        if (condition instanceof ScriptFieldProvider) {
            ScriptField[] fields = ((ScriptFieldProvider) condition).getScriptFields();
            for (ScriptField field : fields) {
                searchSource.scriptField(field.getName(), field.getScript().toEsScript());
            }
        }

        if (condition instanceof SortProvider) {
            Sort[] sorts = ((SortProvider) condition).getSorts();
            for (Sort sort : sorts) {
                searchSource.sort(sort.toSortBuilder());
            }
        }
        if (meta.unwrappedReturnType().map(MetaUtils::isBasic).orElse(false)) {
            searchSource.fetchSource(false);
        } else {
            if (condition instanceof SourceProvider) {
                SourceProvider sourceProvider = (SourceProvider) condition;
                searchSource.fetchSource(sourceProvider.getIncludeFields(), sourceProvider.getExcludeFields());
            } else {
                searchSource.fetchSource(meta.getIncludeFields(), ArrayUtils.EMPTY_STRING_ARRAY);
            }
        }
        if (condition instanceof CollapseProvider) {
            Collapse collapse = ((CollapseProvider) condition).getCollapse();
            searchSource.collapse(collapse.toCollapseBuilder());
        }
        if (condition instanceof RoutingProvider) {
            request.routing(((RoutingProvider) condition).routing());
        }

        if (condition instanceof SearchAfterProvider) {
            searchSource.searchAfter(((SearchAfterProvider) condition).sortValues());
        }

        if (condition instanceof HighlighterProvider) {
            searchSource.highlighter(toHighlighterBuilder(((HighlighterProvider) condition).highlighterBuilder()));
        }
    }
}
