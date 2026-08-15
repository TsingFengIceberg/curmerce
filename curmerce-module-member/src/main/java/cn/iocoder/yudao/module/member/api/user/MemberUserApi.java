package cn.iocoder.yudao.module.member.api.user;

import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;

public interface MemberUserApi {
    MemberUserRespDTO getUser(Long id);
    void validateActiveUser(Long id);
}
