package cn.iocoder.yudao.module.member.service.user;

import cn.iocoder.yudao.module.member.controller.app.auth.vo.MemberAuthRegisterReqVO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.user.vo.MemberUserPageReqVO;
import cn.iocoder.yudao.module.member.controller.app.user.vo.MemberProfileUpdateReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;

public interface MemberUserService {
    MemberUserDO register(MemberAuthRegisterReqVO reqVO, String registerIp);
    MemberUserDO getUser(Long id);
    MemberUserDO getActiveUser(Long id);
    MemberUserDO requireActiveUser(Long id);
    MemberUserDO requireActiveUserForUpdate(Long id);
    MemberUserDO getUserByMobile(String mobile);
    PageResult<MemberUserDO> getUserPage(MemberUserPageReqVO reqVO);
    boolean isPasswordMatch(String rawPassword, String encodedPassword);
    void updateLogin(Long id, String loginIp);
    void updateProfile(Long id, MemberProfileUpdateReqVO reqVO);
}
