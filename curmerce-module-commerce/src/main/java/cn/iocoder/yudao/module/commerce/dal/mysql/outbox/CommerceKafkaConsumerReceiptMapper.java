package cn.iocoder.yudao.module.commerce.dal.mysql.outbox;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.outbox.CommerceKafkaConsumerReceiptDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommerceKafkaConsumerReceiptMapper extends BaseMapperX<CommerceKafkaConsumerReceiptDO> {

    default boolean exists(String consumerGroup, Long eventId) {
        return selectCount(new LambdaQueryWrapper<CommerceKafkaConsumerReceiptDO>()
                .eq(CommerceKafkaConsumerReceiptDO::getConsumerGroup, consumerGroup)
                .eq(CommerceKafkaConsumerReceiptDO::getEventId, eventId)) > 0;
    }
}
