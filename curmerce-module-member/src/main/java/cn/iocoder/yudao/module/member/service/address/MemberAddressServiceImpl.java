package cn.iocoder.yudao.module.member.service.address;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.ip.core.utils.AreaUtils;
import cn.iocoder.yudao.module.member.controller.app.address.vo.*;
import cn.iocoder.yudao.module.member.dal.dataobject.address.MemberAddressDO;
import cn.iocoder.yudao.module.member.dal.mysql.address.MemberAddressMapper;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;

@Service
public class MemberAddressServiceImpl implements MemberAddressService {
    private static final int MAX_ADDRESSES = 20;
    @Resource private MemberAddressMapper addressMapper;
    @Resource private MemberUserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAddress(Long userId, MemberAddressCreateReqVO reqVO) {
        userService.requireActiveUserForUpdate(userId);
        List<MemberAddressDO> addresses = addressMapper.selectListByUserIdForUpdate(userId);
        if (addresses.size() >= MAX_ADDRESSES) throw exception(ADDRESS_LIMIT);
        validateArea(reqVO.getAreaId());
        boolean makeDefault = Boolean.TRUE.equals(reqVO.getDefaultStatus()) || addresses.isEmpty();
        if (makeDefault) addressMapper.clearDefault(userId);
        MemberAddressDO address = toDO(userId, reqVO).setDefaultStatus(makeDefault)
                .setDefaultMarker(makeDefault ? 1 : null);
        addressMapper.insert(address);
        return address.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAddress(Long userId, MemberAddressUpdateReqVO reqVO) {
        userService.requireActiveUserForUpdate(userId);
        List<MemberAddressDO> addresses = addressMapper.selectListByUserIdForUpdate(userId);
        MemberAddressDO current = addresses.stream().filter(item -> Objects.equals(item.getId(), reqVO.getId())).findFirst()
                .orElseThrow(() -> exception(ADDRESS_NOT_FOUND));
        validateArea(reqVO.getAreaId());
        boolean makeDefault = Boolean.TRUE.equals(reqVO.getDefaultStatus());
        if (!makeDefault && Boolean.TRUE.equals(current.getDefaultStatus())) {
            throw exception(ADDRESS_DEFAULT_REQUIRED);
        }
        if (makeDefault) addressMapper.clearDefaultExcept(userId, current.getId());
        MemberAddressDO update = toDO(userId, reqVO).setId(current.getId()).setDefaultStatus(makeDefault)
                .setDefaultMarker(makeDefault ? 1 : null);
        if (addressMapper.updateOwned(update, userId) != 1) throw exception(ADDRESS_STATE_CONFLICT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAddress(Long userId, Long id) {
        userService.requireActiveUserForUpdate(userId);
        List<MemberAddressDO> addresses = addressMapper.selectListByUserIdForUpdate(userId);
        MemberAddressDO current = addresses.stream().filter(item -> Objects.equals(item.getId(), id)).findFirst()
                .orElseThrow(() -> exception(ADDRESS_NOT_FOUND));
        boolean wasDefault = Boolean.TRUE.equals(current.getDefaultStatus());
        if (addressMapper.deleteOwned(id, userId) != 1) throw exception(ADDRESS_STATE_CONFLICT);
        if (wasDefault && addresses.size() > 1) {
            MemberAddressDO promote = addresses.stream().filter(item -> !Objects.equals(item.getId(), id)).findFirst().orElse(null);
            if (promote != null) {
                addressMapper.clearDefault(userId);
                addressMapper.markDefault(promote.getId(), userId);
            }
        }
    }

    @Override public MemberAddressRespVO getAddress(Long userId, Long id) {
        userService.requireActiveUser(userId);
        return toResp(addressMapper.selectByIdAndUserId(id, userId));
    }
    @Override public MemberAddressRespVO getDefaultAddress(Long userId) {
        userService.requireActiveUser(userId);
        return addressMapper.selectListByUserId(userId).stream().filter(item -> Boolean.TRUE.equals(item.getDefaultStatus()))
                .findFirst().map(this::toResp).orElse(null);
    }
    @Override public List<MemberAddressRespVO> getAddressList(Long userId) {
        userService.requireActiveUser(userId);
        return addressMapper.selectListByUserId(userId).stream().map(this::toResp).toList();
    }
    private MemberAddressDO toDO(Long userId, MemberAddressBaseVO req) {
        return new MemberAddressDO().setUserId(userId).setName(StrUtil.trim(req.getName()))
                .setMobile(StrUtil.trim(req.getMobile())).setAreaId(req.getAreaId())
                .setDetailAddress(StrUtil.trim(req.getDetailAddress()));
    }
    private MemberAddressRespVO toResp(MemberAddressDO address) {
        if (address == null) throw exception(ADDRESS_NOT_FOUND);
        MemberAddressRespVO response = new MemberAddressRespVO();
        response.setId(address.getId());
        response.setName(address.getName());
        response.setMobile(address.getMobile());
        response.setAreaId(address.getAreaId());
        response.setAreaName(AreaUtils.format(address.getAreaId()));
        response.setDetailAddress(address.getDetailAddress());
        response.setDefaultStatus(address.getDefaultStatus());
        return response;
    }
    private void validateArea(Integer areaId) {
        if (AreaUtils.getArea(areaId) == null) throw exception(ADDRESS_AREA_INVALID);
    }
}
