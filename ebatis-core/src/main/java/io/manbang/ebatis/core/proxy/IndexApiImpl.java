package io.manbang.ebatis.core.proxy;

import io.manbang.ebatis.core.cluster.ClusterRouter;
import io.manbang.ebatis.core.domain.Context;
import io.manbang.ebatis.core.domain.ContextHolder;
import io.manbang.ebatis.core.domain.HttpConfig;
import io.manbang.ebatis.core.exception.EbatisException;
import io.manbang.ebatis.core.interceptor.DefaultPostResponseInfo;
import io.manbang.ebatis.core.interceptor.Interceptor;
import io.manbang.ebatis.core.interceptor.InterceptorFactory;
import io.manbang.ebatis.core.mapper.IndexApi;
import io.manbang.ebatis.core.meta.MethodMeta;
import io.manbang.ebatis.core.meta.ParameterMeta;
import io.manbang.ebatis.core.meta.RequestType;
import io.manbang.ebatis.core.meta.ResultType;
import io.manbang.ebatis.core.response.ResponseExtractor;
import org.apache.commons.lang3.concurrent.ConcurrentException;
import org.apache.commons.lang3.concurrent.LazyInitializer;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Requests;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * @author weilong.hu
 * @since 2021/07/05 15:29
 */
class IndexApiImpl implements IndexApi {
    private static final Interceptor INTERCEPTOR = InterceptorFactory.interceptors();

    public static IndexApi index(LazyInitializer<ClusterRouter> clusterRouter) {
        return new IndexApiImpl(clusterRouter);
    }

    private final LazyInitializer<ClusterRouter> clusterRouter;

    IndexApiImpl(LazyInitializer<ClusterRouter> clusterRouter) {
        this.clusterRouter = clusterRouter;
    }

    @Override
    public RefreshResponse refresh(String... indices) {
        return refreshAsync(indices).join();
    }

    @Override
    public CompletableFuture<RefreshResponse> refreshAsync(String... indices) {
        CompletableFuture<RefreshResponse> future = new CompletableFuture<>();
        Context context = ContextHolder.getContext();
        final RefreshRequest refreshRequest = Requests.refreshRequest(indices);
        try {
            clusterRouter.get().route(getMethodMeta(indices)).highLevelClient().indices().refreshAsync(refreshRequest, RequestOptions.DEFAULT, ActionListener.wrap(response -> {
                ContextHolder.setContext(context);
                try {
                    future.complete(response);
                    INTERCEPTOR.postResponse(new DefaultPostResponseInfo<>(refreshRequest, response));
                } finally {
                    ContextHolder.remove();
                }
            }, exception -> {
                future.completeExceptionally(exception);
                INTERCEPTOR.handleException(exception);
                ContextHolder.remove();
            }));
        } catch (ConcurrentException e) {
            throw new EbatisException(e);
        }
        return future;
    }

    private MethodMeta getMethodMeta(String[] indices) {
        return new MethodMeta() {
            @Override
            public Class<?> getReturnType() {
                return null;
            }

            @Override
            public Optional<Class<?>> unwrappedReturnType() {
                return Optional.empty();
            }

            @Override
            public String[] getIndices() {
                return indices;
            }

            @Override
            public String[] getTypes() {
                return new String[0];
            }

            @Override
            public RequestType getRequestType() {
                return null;
            }

            @Override
            public ResultType getResultType() {
                return null;
            }

            @Override
            public <A extends Annotation> A getRequestAnnotation() {
                return null;
            }

            @Override
            public HttpConfig getHttpConfig() {
                return null;
            }

            @Override
            public List<ParameterMeta> getParameterMetas() {
                return Collections.emptyList();
            }

            @Override
            public ParameterMeta getConditionParameter() {
                return null;
            }

            @Override
            public Optional<ParameterMeta> findConditionParameter() {
                return Optional.empty();
            }

            @Override
            public Optional<ParameterMeta> getPageableParameter() {
                return Optional.empty();
            }

            @Override
            public ParameterMeta getResponseExtractorParameter() {
                return null;
            }

            @Override
            public String[] getIncludeFields() {
                return new String[0];
            }

            @Override
            public ResponseExtractor<?> getResponseExtractor(Object[] args) {
                return null;
            }

            @Override
            public Method getElement() {
                return null;
            }
        };
    }
}
