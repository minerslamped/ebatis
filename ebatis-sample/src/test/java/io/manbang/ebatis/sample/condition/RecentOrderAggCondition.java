package io.manbang.ebatis.sample.condition;

import io.manbang.ebatis.core.annotation.Must;
import io.manbang.ebatis.core.annotation.Order;
import io.manbang.ebatis.core.domain.Aggregation;
import io.manbang.ebatis.core.domain.FiltersAggregation;
import io.manbang.ebatis.core.domain.TermsAggregation;
import io.manbang.ebatis.core.provider.AggProvider;
import lombok.Data;

/**
 * @author weilong.hu
 * @since 2020/7/6 22:18
 */
public class RecentOrderAggCondition extends SampleRecentOrderCondition implements AggProvider {
    @Override
    public Aggregation[] getAggregations() {
        TermsAggregation terms1 =
                Aggregation.terms("grpByEventType").fieldName("startCityCode").order(Order.COUNT_DESC, Order.KEY_ASC);
        TermsAggregation terms2 = Aggregation.terms("grpByUserId").fieldName("startProvinceCode").order(Order.COUNT_DESC);
        TermsAggregation terms3 = Aggregation.terms("grpByRoute").fieldName("startAreaCode").order(Order.COUNT_DESC);
        terms1.subAgg(terms2);
        terms2.subAgg(terms3);
        final Condition condition = new Condition();
        condition.setCargoType(1);
        final FiltersAggregation filter = Aggregation.filters("type").filter("cargoType", condition);
        return new Aggregation[]{terms1, terms2, filter};
    }

    @Data
    public static class Condition {
        @Must
        private Integer cargoType;
    }
}
