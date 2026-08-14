package cn.iocoder.yudao.module.commerce.service.merchant;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.admin.merchant.vo.MerchantApproveReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.merchant.vo.MerchantCreateReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.merchant.vo.MerchantPageReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.merchant.vo.MerchantRejectReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantDO;

public interface MerchantService {
    Long createMerchant(MerchantCreateReqVO reqVO);
    MerchantDO getMerchant(Long id);
    PageResult<MerchantDO> getMerchantPage(MerchantPageReqVO reqVO);
    void approveMerchant(MerchantApproveReqVO reqVO);
    void rejectMerchant(MerchantRejectReqVO reqVO);
}
