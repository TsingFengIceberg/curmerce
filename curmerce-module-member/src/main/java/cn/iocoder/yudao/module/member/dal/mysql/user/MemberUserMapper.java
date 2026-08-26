package cn.iocoder.yudao.module.member.dal.mysql.user;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.user.vo.MemberUserPageReqVO;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface MemberUserMapper extends BaseMapperX<MemberUserDO> {
    default PageResult<MemberUserDO> selectPage(MemberUserPageReqVO reqVO) {
        LambdaQueryWrapper<MemberUserDO> wrapper = new LambdaQueryWrapper<MemberUserDO>()
                .eq(reqVO.getStatus() != null, MemberUserDO::getStatus, reqVO.getStatus());
        if (StrUtil.isNotBlank(reqVO.getKeyword())) {
            String keyword = StrUtil.trim(reqVO.getKeyword());
            wrapper.and(query -> query.like(MemberUserDO::getNickname, keyword)
                    .or().like(MemberUserDO::getMobile, keyword)
                    .or().like(MemberUserDO::getEmail, keyword));
        }
        wrapper.orderByDesc(MemberUserDO::getId);
        return selectPage(reqVO, wrapper);
    }
    default MemberUserDO selectByMobile(String mobile) {
        return selectOne(new LambdaQueryWrapper<MemberUserDO>().eq(MemberUserDO::getMobile, mobile));
    }
    default MemberUserDO selectActiveById(Long id) {
        return selectOne(new LambdaQueryWrapper<MemberUserDO>().eq(MemberUserDO::getId, id)
                .eq(MemberUserDO::getStatus, 0));
    }
    default MemberUserDO selectActiveByIdForUpdate(Long id) {
        return selectOneForUpdate(new LambdaQueryWrapper<MemberUserDO>().eq(MemberUserDO::getId, id)
                .eq(MemberUserDO::getStatus, 0));
    }
    default int updateLogin(Long id, String loginIp, LocalDateTime loginDate) {
        return update(new MemberUserDO().setLoginIp(loginIp).setLoginDate(loginDate),
                new LambdaUpdateWrapper<MemberUserDO>().eq(MemberUserDO::getId, id));
    }
    default int updateProfile(Long id, String nickname, String avatar, String email, Integer sex) {
        return update(new MemberUserDO().setNickname(nickname).setAvatar(avatar).setEmail(email).setSex(sex),
                new LambdaUpdateWrapper<MemberUserDO>().eq(MemberUserDO::getId, id)
                        .eq(MemberUserDO::getStatus, 0));
    }
}
