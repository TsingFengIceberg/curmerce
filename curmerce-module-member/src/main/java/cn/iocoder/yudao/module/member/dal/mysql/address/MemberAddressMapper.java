package cn.iocoder.yudao.module.member.dal.mysql.address;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.address.MemberAddressDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MemberAddressMapper extends BaseMapperX<MemberAddressDO> {
    default List<MemberAddressDO> selectListByUserIdForUpdate(Long userId) {
        return selectList(new LambdaQueryWrapper<MemberAddressDO>().eq(MemberAddressDO::getUserId, userId)
                .orderByAsc(MemberAddressDO::getId).last("FOR UPDATE"));
    }
    default List<MemberAddressDO> selectListByUserId(Long userId) {
        return selectList(new LambdaQueryWrapper<MemberAddressDO>().eq(MemberAddressDO::getUserId, userId)
                .orderByDesc(MemberAddressDO::getDefaultStatus).orderByAsc(MemberAddressDO::getId));
    }
    default MemberAddressDO selectByIdAndUserId(Long id, Long userId) {
        return selectOne(new LambdaQueryWrapper<MemberAddressDO>().eq(MemberAddressDO::getId, id)
                .eq(MemberAddressDO::getUserId, userId));
    }
    default MemberAddressDO selectByIdAndUserIdForUpdate(Long id, Long userId) {
        return selectOneForUpdate(new LambdaQueryWrapper<MemberAddressDO>().eq(MemberAddressDO::getId, id)
                .eq(MemberAddressDO::getUserId, userId));
    }
    default int clearDefault(Long userId) {
        return update(new MemberAddressDO().setDefaultStatus(false).setDefaultMarker(null),
                new LambdaUpdateWrapper<MemberAddressDO>().eq(MemberAddressDO::getUserId, userId)
                        .eq(MemberAddressDO::getDefaultStatus, true));
    }
    default int clearDefaultExcept(Long userId, Long addressId) {
        return update(new MemberAddressDO().setDefaultStatus(false).setDefaultMarker(null),
                new LambdaUpdateWrapper<MemberAddressDO>().eq(MemberAddressDO::getUserId, userId)
                        .eq(MemberAddressDO::getDefaultStatus, true)
                        .ne(MemberAddressDO::getId, addressId));
    }
    default int markDefault(Long id, Long userId) {
        return update(new MemberAddressDO().setDefaultStatus(true).setDefaultMarker(1),
                new LambdaUpdateWrapper<MemberAddressDO>().eq(MemberAddressDO::getId, id)
                        .eq(MemberAddressDO::getUserId, userId));
    }
    default int updateOwned(MemberAddressDO update, Long userId) {
        return update(update, new LambdaUpdateWrapper<MemberAddressDO>().eq(MemberAddressDO::getId, update.getId())
                .eq(MemberAddressDO::getUserId, userId));
    }
    @Update("UPDATE member_address SET deleted = 1, default_status = 0, default_marker = NULL "
            + "WHERE id = #{id} AND user_id = #{userId} AND deleted = 0")
    int deleteOwned(@Param("id") Long id, @Param("userId") Long userId);
}
