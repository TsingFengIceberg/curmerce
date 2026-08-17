package cn.iocoder.yudao.module.member.api.address;

import cn.iocoder.yudao.module.member.api.address.dto.MemberAddressRespDTO;

public interface MemberAddressApi {

    /**
     * Gets an address owned by the buyer and locks it for the surrounding local transaction.
     * Returns {@code null} when the address does not exist or belongs to another buyer.
     */
    MemberAddressRespDTO getOwnedAddressForUpdate(Long userId, Long addressId);
}
