package cn.iocoder.yudao.module.commerce.dal.mysql.cart;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.cart.CartItemDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import java.util.Collection;
import java.util.List;

@Mapper
public interface CartItemMapper extends BaseMapperX<CartItemDO> {
    default CartItemDO selectByUserAndSku(Long userId, Long skuId) {
        return selectOne(new LambdaQueryWrapper<CartItemDO>().eq(CartItemDO::getMemberUserId, userId).eq(CartItemDO::getSkuId, skuId));
    }
    default CartItemDO selectByUserAndSkuForUpdate(Long userId, Long skuId) {
        return selectOneForUpdate(new LambdaQueryWrapper<CartItemDO>().eq(CartItemDO::getMemberUserId, userId).eq(CartItemDO::getSkuId, skuId));
    }
    default List<CartItemDO> selectListByUserId(Long userId) {
        return selectList(new LambdaQueryWrapper<CartItemDO>().eq(CartItemDO::getMemberUserId, userId).orderByDesc(CartItemDO::getId));
    }
    default int updateQuantity(Long id, Long userId, int quantity) {
        return update(new CartItemDO().setQuantity(quantity), new LambdaUpdateWrapper<CartItemDO>().eq(CartItemDO::getId, id).eq(CartItemDO::getMemberUserId, userId));
    }
    default int updateSelected(Collection<Long> ids, Long userId, boolean selected) {
        if (ids == null || ids.isEmpty()) return 0;
        return update(new CartItemDO().setSelected(selected), new LambdaUpdateWrapper<CartItemDO>()
                .eq(CartItemDO::getMemberUserId, userId).in(CartItemDO::getId, ids));
    }
    default int deleteOwned(Long userId, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        return delete(new LambdaQueryWrapper<CartItemDO>().eq(CartItemDO::getMemberUserId, userId).in(CartItemDO::getId, ids));
    }
    default Long sumQuantity(Long userId) {
        QueryWrapper<CartItemDO> wrapper = new QueryWrapper<CartItemDO>().select("COALESCE(SUM(quantity),0)").eq("member_user_id", userId);
        Long sum = selectObjs(wrapper)
                .stream().findFirst().map(value -> ((Number) value).longValue()).orElse(0L);
        return sum;
    }
}
