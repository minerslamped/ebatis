package io.manbang.ebatis.sample;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.manbang.ebatis.sample.entity.RecentOrderModel;
import io.manbang.ebatis.sample.mapper.RecentOrderIndexMapper;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.rest.RestStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author weilong.hu
 * @since 2020/6/28 18:49
 */
@Slf4j
public class EsIndexTest extends ESAbstractTest {
    private RecentOrderIndexMapper recentOrderIndexMapper;

    @Before
    public void startup() {
        recentOrderIndexMapper = createEsMapper(RecentOrderIndexMapper.class);
    }


    @Test
    public void indexRecentOrderBoolean() {
        Boolean bool = recentOrderIndexMapper.indexRecentOrderBoolean(new RecentOrderModel());
        log.info("index result:{}", bool);
    }


    @Test
    public void indexRecentOrderBool() {
        boolean bool = recentOrderIndexMapper.indexRecentOrderBool(new RecentOrderModel());
        log.info("index result:{}", bool);
    }


    @Test
    public void indexRecentOrderString() {
        String id = recentOrderIndexMapper.indexRecentOrderString(new RecentOrderModel());
        log.info("index id:{}", id);
    }


    @Test
    public void indexRecentOrderVoid() {
        recentOrderIndexMapper.indexRecentOrderVoid(new RecentOrderModel());
        log.info("index success ");
    }


    @Test
    public void indexRecentOrderIndexResponse() throws JsonProcessingException {
        IndexResponse indexResponse = recentOrderIndexMapper.indexRecentOrderIndexResponse(new RecentOrderModel());
        String response = getJsonResult(indexResponse);
        log.info("indexResponse success ：{}", response);
    }


    @Test
    public void indexRecentOrderRestStatus() {
        RestStatus restStatus = recentOrderIndexMapper.indexRecentOrderRestStatus(new RecentOrderModel());
        log.info("index success restStatus：{}", restStatus);
    }


    @Test
    public void indexRecentOrderCompletableFuture() throws InterruptedException, ExecutionException {
        AtomicReference<Throwable> ex = new AtomicReference<>();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        CompletableFuture<RestStatus> restStatusCompletableFuture = recentOrderIndexMapper.indexRecentOrderCompletableFuture(new RecentOrderModel());
        restStatusCompletableFuture.whenCompleteAsync((r, e) -> {
            log.info("index result:{}", r);
            ex.set(e);
            countDownLatch.countDown();
        });
        countDownLatch.await();
        log.info("index success restStatus：{}", restStatusCompletableFuture.get());
        Assert.assertNull(ex.get());
    }


    @Test
    public void indexRecentOrderFutureVoid() throws InterruptedException {
        AtomicReference<Throwable> ex = new AtomicReference<>();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        CompletableFuture<Void> voidCompletableFuture = recentOrderIndexMapper.indexRecentOrderFutureVoid(new RecentOrderModel());
        voidCompletableFuture.whenCompleteAsync((v, e) -> {
            log.info("index over,result:{}", v, e);
            ex.set(e);
            countDownLatch.countDown();
        });
        countDownLatch.await();
        log.info("index success");
        Assert.assertNull(ex.get());
    }

    @Test
    public void refreshIndex() {
        final RefreshResponse refreshAll1 = recentOrderIndexMapper.refresh("_all");
        final RefreshResponse refreshAll2 = recentOrderIndexMapper.refresh(null);
        Assert.assertNotNull(refreshAll1);
        Assert.assertNotNull(refreshAll2);
        log.info("refresh result,status:{},totalShards:{},successfulShards:{},failedShards:{}", refreshAll1.getStatus(), refreshAll1.getTotalShards(), refreshAll1.getSuccessfulShards(), refreshAll1.getFailedShards());
    }

    @Test
    public void createIndex() {
        Map settings = new HashMap<>();
        settings.put("number_of_shards", 3);
        settings.put("number_of_replicas", 2);
        Map type = new HashMap<>();
        Map fields = new HashMap<>();
        fields.put("field1", Collections.singletonMap("type", "text"));
        type.put("properties", fields);
        final CreateIndexResponse response = recentOrderIndexMapper.create("2021_11_29_test", settings, type);
        log.info("create index result:{}", response.isAcknowledged());
    }

    @Test
    public void deleteIndex() {
        final AcknowledgedResponse response = recentOrderIndexMapper.delete("2021_11_29_test");
        log.info("delete index result:{}", response.isAcknowledged());
    }
}
