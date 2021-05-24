package io.manbang.ebatis.sample.condition;

import io.manbang.ebatis.core.annotation.Field;
import io.manbang.ebatis.core.annotation.Must;
import io.manbang.ebatis.core.annotation.Nested;
import io.manbang.ebatis.core.annotation.QueryType;
import io.manbang.ebatis.core.annotation.Should;
import io.manbang.ebatis.core.domain.Highlighter;
import io.manbang.ebatis.core.domain.HighlighterBuilder;
import io.manbang.ebatis.core.domain.Range;
import io.manbang.ebatis.core.domain.ScoreFunction;
import io.manbang.ebatis.core.domain.ScoreFunctionMode;
import io.manbang.ebatis.core.domain.Script;
import io.manbang.ebatis.core.provider.HighlighterProvider;
import io.manbang.ebatis.core.provider.ScoreFunctionProvider;
import io.manbang.ebatis.sample.condition.base.Cargo;
import io.manbang.ebatis.sample.condition.base.Load;
import io.manbang.ebatis.sample.condition.base.Protocol;
import io.manbang.ebatis.sample.condition.base.SecurityTran;
import io.manbang.ebatis.sample.condition.base.Truck;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction;

import java.util.List;

/**
 * @author weilong.hu
 * @since 2020/6/15 17:21
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class RecentOrderCondition extends SampleRecentOrderCondition implements ScoreFunctionProvider, HighlighterProvider {

    /**
     * 基本类型
     */
    @Must
    private Integer cargoType;
    /**
     * 基本类型集合
     */
    @Must
    private List<Integer> orderSource;

    /**
     * 基本类型集合
     */
    @Must
    @Field("orderType")
    private Integer[] type;

    /**
     * 嵌套条件
     */
    @Must
    private Protocol protocol;

    /**
     * 非基本类型集合
     */
    @Must
    private List<SecurityTran> securityTranList;

    /**
     * 非基本类型集合
     */
    @Must
    private SecurityTran[] securityTrans;

    /**
     * 单范围查询
     */
    @Must
    private Range<Integer> channel;

    /**
     * 脚本查询
     */
    @Must
    private Script script;

    /**
     * 多范围组合查询
     */
    @Should
    private List<Range<Integer>> channels;
    /**
     * 多脚本组合查询
     */
    @Should(minimumShouldMatch = "2")
    private Script[] scripts;
    /**
     * 动态化计算实例类型
     */
    @Must
    private Object shipperInfo;

    @Must
    private Object[] shipperInfos;

    @Must(queryType = QueryType.EXISTS)
    private boolean startAreaCode;

    @Must(queryType = QueryType.WILDCARD)
    private String unloadAddress;

    @Must(queryType = QueryType.NESTED, nest = @Nested(path = "attrs"))
    private Integer cargoAttr;

    @Must(queryType = QueryType.BOOSTING)
    private Load load;

    @Must(queryType = QueryType.DIS_MAX)
    private Truck truck;

    @Must(queryType = QueryType.MULTI_MATCH)
    private Cargo cargo;

    @Override
    public ScoreFunction getFunction() {
        return ScoreFunction.fieldValueFactor("startCityId", 10, 10, FieldValueFactorFunction.Modifier.LN);
    }

    @Override
    public ScoreFunctionMode getFunctionMode() {
        return null;
    }


    @Override
    public HighlighterBuilder highlighterBuilder() {
        return Highlighter.highlighter().addFields(Highlighter.field("loadAddress"));
    }
}
