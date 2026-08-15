package cn.iocoder.yudao.module.member.api.user;

import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.USER_NOT_EXISTS;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
public class MemberUserApiImpl implements MemberUserApi {
    @Resource
    private MemberUserService userService;

    @Override
    public MemberUserRespDTO getUser(Long id) {
        MemberUserDO user = userService.getUser(id);
        if (user == null) return null;
        return new MemberUserRespDTO().setId(user.getId()).setMobile(user.getMobile())
                .setNickname(user.getNickname()).setEmail(user.getEmail()).setStatus(user.getStatus());
    }

    @Override
    public void validateActiveUser(Long id) {
        if (userService.getActiveUser(id) == null) throw exception(USER_NOT_EXISTS);
    }
}
