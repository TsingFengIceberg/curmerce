package cn.iocoder.yudao.module.member.service.user;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.MemberAuthRegisterReqVO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.user.vo.MemberUserPageReqVO;
import cn.iocoder.yudao.module.member.controller.app.user.vo.MemberProfileUpdateReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;

@Service
public class MemberUserServiceImpl implements MemberUserService {
    @Resource private MemberUserMapper userMapper;
    @Resource private PasswordEncoder passwordEncoder;
    @Resource private FileApi fileApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberUserDO register(MemberAuthRegisterReqVO reqVO, String registerIp) {
        String mobile = StrUtil.trim(reqVO.getMobile());
        if (userMapper.selectByMobile(mobile) != null) throw exception(MOBILE_ALREADY_REGISTERED);
        MemberUserDO user = new MemberUserDO().setMobile(mobile)
                .setPassword(passwordEncoder.encode(reqVO.getPassword()))
                .setNickname(StrUtil.trim(reqVO.getNickname())).setStatus(CommonStatusEnum.ENABLE.getStatus())
                .setRegisterIp(registerIp);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw exception(MOBILE_ALREADY_REGISTERED);
        }
        return user;
    }

    @Override public MemberUserDO getUser(Long id) { return id == null ? null : userMapper.selectById(id); }
    @Override public MemberUserDO getActiveUser(Long id) { return id == null ? null : userMapper.selectActiveById(id); }
    @Override public MemberUserDO requireActiveUser(Long id) {
        MemberUserDO user = getActiveUser(id);
        if (user == null) throw exception(USER_NOT_EXISTS);
        return user;
    }
    @Override public MemberUserDO requireActiveUserForUpdate(Long id) {
        MemberUserDO user = userMapper.selectActiveByIdForUpdate(id);
        if (user == null) throw exception(USER_NOT_EXISTS);
        return user;
    }
    @Override public MemberUserDO getUserByMobile(String mobile) { return userMapper.selectByMobile(StrUtil.trim(mobile)); }
    @Override public PageResult<MemberUserDO> getUserPage(MemberUserPageReqVO reqVO) { return userMapper.selectPage(reqVO); }
    @Override public boolean isPasswordMatch(String rawPassword, String encodedPassword) {
        return encodedPassword != null && passwordEncoder.matches(rawPassword, encodedPassword);
    }
    @Override public void updateLogin(Long id, String loginIp) { userMapper.updateLogin(id, loginIp, LocalDateTime.now()); }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long id, MemberProfileUpdateReqVO reqVO) {
        requireActiveUser(id);
        String avatar = StrUtil.trim(reqVO.getAvatar());
        if (userMapper.updateProfile(id, StrUtil.trim(reqVO.getNickname()), avatar,
                StrUtil.trim(reqVO.getEmail()), reqVO.getSex()) != 1) throw exception(USER_NOT_EXISTS);
        fileApi.replaceFileReferences("member_user", id.toString(), "avatar",
                StrUtil.isBlank(avatar) ? List.of() : List.of(avatar));
    }
}
