package cn.iocoder.yudao.module.member.service.address;

import cn.iocoder.yudao.module.member.controller.app.address.vo.*;
import java.util.List;

public interface MemberAddressService {
    Long createAddress(Long userId, MemberAddressCreateReqVO reqVO);
    void updateAddress(Long userId, MemberAddressUpdateReqVO reqVO);
    void deleteAddress(Long userId, Long id);
    MemberAddressRespVO getAddress(Long userId, Long id);
    MemberAddressRespVO getDefaultAddress(Long userId);
    List<MemberAddressRespVO> getAddressList(Long userId);
}
