package io.manbang.ebatis.core.session;

import io.manbang.ebatis.core.cluster.Cluster;
import io.manbang.ebatis.core.config.Env;
import io.manbang.ebatis.core.domain.Context;
import io.manbang.ebatis.core.domain.ContextHolder;
import io.manbang.ebatis.core.domain.Page;
import io.manbang.ebatis.core.domain.Pageable;
import io.manbang.ebatis.core.exception.InvalidResponseException;
import io.manbang.ebatis.core.interceptor.DefaultPostResponseInfo;
import io.manbang.ebatis.core.interceptor.DefaultPreResponseInfo;
import io.manbang.ebatis.core.interceptor.Interceptor;
import io.manbang.ebatis.core.interceptor.InterceptorFactory;
import io.manbang.ebatis.core.request.CatRequest;
import io.manbang.ebatis.core.response.ResponseExtractor;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 集群亲和会话
 *
 * @author 章多亮
 * @since 2020/5/23 17:14
 */
class CachedClusterSession implements ClusterSession {
    private static final Map<Cluster, ClusterSession> CLUSTER_SESSIONS = new ConcurrentHashMap<>();
    private final Cluster cluster;
    private final Interceptor interceptor;

    private CachedClusterSession(Cluster cluster) {
        this.cluster = cluster;
        this.interceptor = InterceptorFactory.interceptors();
    }

    static synchronized ClusterSession createOrGet(Cluster cluster) {
        return CLUSTER_SESSIONS.computeIfAbsent(cluster, CachedClusterSession::new);
    }

    @Override
    public <T> CompletableFuture<T> getAsync(GetRequest request, ResponseExtractor<T> extractor) {
        return performRequestAsync(cluster::getAsync, request, extractor);
    }

    @Override
    public <T> CompletableFuture<T> deleteAsync(DeleteRequest request, ResponseExtractor<T> extractor) {
        return performRequestAsync(cluster::deleteAsync, request, extractor);
    }

    @Override
    public <T> CompletableFuture<T> deleteByQueryAsync(DeleteByQueryRequest request, ResponseExtractor<T> extractor) {
        return performRequestAsync(cluster::deleteByQueryAsync, request, extractor);
    }

    @Override
    public <T> CompletableFuture<T> updateAsync(UpdateRequest request, ResponseExtractor<T> extractor) {
        return performRequestAsync(cluster::updateAsync, request, extractor);
    }

    @Override
    public <T> CompletableFuture<T> updateByQueryAsync(UpdateByQueryRequest request, ResponseExtractor<T> extractor) {
        return performRequestAsync(cluster::updateByQueryAsync, request, extractor);
    }

    @Override
    public <T> CompletableFuture<T> indexAsync(IndexRequest request, ResponseExtractor<T> extractor) {
        return performRequestAsync(cluster::indexAsync, request, extractor);
    }

    @Override
    public <T> CompletableFuture<T> multiSearchAsync(MultiSearchRequest request, ResponseExtractor<T> extractor) {
        return performRequestAsync(cluster::multiSearchAsync, request, extractor);
    }

    @Override
    public <T> CompletableFuture<T> multiSearchAsync(MultiSearchRequest request, ResponseExtractor<T> extractor, Pageable[] pageable) {
        return performRequestAsync(cluster::multiSearchAsync, request, extractor);
    }

    @Override
    public <T> CompletableFuture<T> searchAsync(SearchRequest request, ResponseExtractor<T> extractor) {
        return performRequestAsync(cluster::searchAsync, request, extractor);
    }

    @Override
    public <T> CompletableFuture<Page<T>> searchAsync(SearchRequest request, ResponseExtractor<Page<T>> extractor, Pageable pageable) {
        return performRequestAsync(cluster::searchAsync, request, extractor);
    }

    @Override
    public <T> CompletableFuture<T> bulkAsync(BulkRequest request, ResponseExtractor<T> extractor) {
        return performRequestAsync(cluster::bulkAsync, request, extractor);
    }

    @Override
    public <T> T getMapper(Class<T> mapperInterface) {
        return null;
    }

    @Override
    public Cluster getCluster() {
        return cluster;
    }

    @Override
    public <T> CompletableFuture<T> catAsync(CatRequest request, ResponseExtractor<T> extractor) {
        return performRequestAsync(cluster::catAsync, request, extractor);
    }

    @Override
    public <T> CompletableFuture<T> scrollAsync(SearchScrollRequest request, ResponseExtractor<T> extractor) {
        return performRequestAsync(cluster::scrollAsync, request, extractor);
    }

    @Override
    public <T> CompletableFuture<T> clearScrollAsync(ClearScrollRequest request, ResponseExtractor<T> extractor) {
        return performRequestAsync(cluster::clearScrollAsync, request, extractor);
    }

    @Override
    public <T> CompletableFuture<T> mgetAsync(MultiGetRequest request, ResponseExtractor<T> extractor) {
        return performRequestAsync(cluster::mgetAsync, request, extractor);
    }

    private <R extends ActionResponse, T extends ActionRequest, E> ActionListener<R> wrap(CompletableFuture<E> future,
                                                                                          ResponseExtractor<E> extractor, T request) {
        Context context = ContextHolder.getContext();

        return ActionListener.wrap(response -> {
            ContextHolder.setContext(context);

            try {
                boolean validated = extractor.validate(response);
                if (validated) {
                    future.complete(extractor.extractData(response));
                } else {
                    if (extractor.fallbackEnabled()) {
                        future.complete(extractor.fallback(null));
                    } else {
                        future.completeExceptionally(new InvalidResponseException(response.toString()));
                    }
                }
                interceptor.postResponse(new DefaultPostResponseInfo<>(request, response));
            } finally {
                ContextHolder.remove();
            }
        }, exception -> {
            ContextHolder.setContext(context);
            try {
                future.completeExceptionally(exception);
                interceptor.handleException(exception);
            } finally {
                ContextHolder.remove();
            }
        });
    }

    private <T extends ActionRequest, R extends ActionResponse, E> CompletableFuture<E> performRequestAsync(RequestExecutor<T, R> executor, T request, ResponseExtractor<E> extractor) {
        CompletableFuture<E> future = new CompletableFuture<>();
        interceptor.preResponse(new DefaultPreResponseInfo<>(request, extractor));
        if (Env.isOfflineEnabled()) {
            future.complete(extractor.empty());
            return future;
        }

        executor.execute(request, wrap(future, extractor, request));
        return future;
    }


    @FunctionalInterface
    private interface RequestExecutor<A extends ActionRequest, R extends ActionResponse> {
        /**
         * 执行ES请求
         *
         * @param request  ES请求
         * @param listener 响应监听器
         */
        void execute(A request, ActionListener<R> listener);
    }
}
