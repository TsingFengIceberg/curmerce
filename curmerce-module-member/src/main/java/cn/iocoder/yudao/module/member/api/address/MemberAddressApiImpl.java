package cn.iocoder.yudao.module.member.api.address;

import cn.iocoder.yudao.framework.ip.core.utils.AreaUtils;
import cn.iocoder.yudao.module.member.api.address.dto.MemberAddressRespDTO;
import cn.iocoder.yudao.module.member.dal.dataobject.address.MemberAddressDO;
import cn.iocoder.yudao.module.member.service.address.MemberAddressService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class MemberAddressApiImpl implements MemberAddressApi {

    @Resource
    private MemberAddressService addressService;

    @Override
    public MemberAddressRespDTO getOwnedAddressForUpdate(Long userId, Long addressId) {
        MemberAddressDO address = addressService.getAddressForUpdate(userId, addressId);
        if (address == null) {
            return null;
        }
        return new MemberAddressRespDTO().setId(address.getId()).setUserId(address.getUserId())
                .setName(address.getName()).setMobile(address.getMobile()).setAreaId(address.getAreaId())
                .setAreaName(AreaUtils.format(address.getAreaId())).setDetailAddress(address.getDetailAddress());
    }
}
