package cn.iocoder.yudao.module.commerce.service.store;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.commerce.controller.admin.store.vo.StoreUpdateOwnReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.store.StoreDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.store.StoreMapper;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessContext;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantAccessService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.*;

@Service
public class StoreServiceImpl implements StoreService {
    @Resource private MerchantAccessService merchantAccessService;
    @Resource private StoreMapper storeMapper;

    @Override public StoreDO getOwnStore() {
        return merchantAccessService.requireApprovedOwner().store();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOwnStore(StoreUpdateOwnReqVO reqVO) {
        MerchantAccessContext context = merchantAccessService.requireApprovedOwner();
        StoreDO current = context.store();
        int updated = storeMapper.updateOwned(current.getId(), context.merchant().getId(),
                BeanUtils.toBean(reqVO, StoreDO.class));
        if (updated != 1) throw exception(STORE_ACCESS_DENIED);
    }
}
