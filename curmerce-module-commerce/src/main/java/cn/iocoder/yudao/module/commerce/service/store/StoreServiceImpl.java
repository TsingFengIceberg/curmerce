package cn.iocoder.yudao.module.commerce.service.store;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.commerce.controller.admin.store.vo.StoreUpdateOwnReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantOperatorDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.store.StoreDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.merchant.MerchantMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.merchant.MerchantOperatorMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.store.StoreMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.commerce.enums.merchant.MerchantOperatorTypeEnum.OWNER;
import static cn.iocoder.yudao.module.commerce.enums.merchant.MerchantAuditStatusEnum.APPROVED;

@Service
public class StoreServiceImpl implements StoreService {
    @Resource private MerchantOperatorMapper operatorMapper;
    @Resource private MerchantMapper merchantMapper;
    @Resource private StoreMapper storeMapper;

    private MerchantDO resolveMerchant() {
        Long userId = getLoginUserId();
        if (userId == null) throw exception(STORE_ACCESS_DENIED);
        List<MerchantOperatorDO> relations = new java.util.ArrayList<>(operatorMapper.selectListByUserId(userId));
        relations.removeIf(item -> !OWNER.getType().equals(item.getOperatorType()));
        if (relations.isEmpty()) throw exception(MERCHANT_OPERATOR_NOT_EXISTS);
        if (relations.size() > 1) throw exception(MERCHANT_OPERATOR_AMBIGUOUS);
        MerchantOperatorDO relation = relations.get(0);
        if (!CommonStatusEnum.ENABLE.getStatus().equals(relation.getStatus())) throw exception(MERCHANT_OPERATOR_NOT_ACTIVE);
        MerchantDO merchant = merchantMapper.selectById(relation.getMerchantId());
        if (merchant == null || !APPROVED.getStatus().equals(merchant.getStatus())) throw exception(STORE_ACCESS_DENIED);
        return merchant;
    }

    @Override public StoreDO getOwnStore() {
        MerchantDO merchant = resolveMerchant();
        StoreDO store = storeMapper.selectByMerchantId(merchant.getId());
        if (store == null) throw exception(STORE_NOT_EXISTS);
        return store;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOwnStore(StoreUpdateOwnReqVO reqVO) {
        MerchantDO merchant = resolveMerchant();
        StoreDO current = storeMapper.selectByMerchantId(merchant.getId());
        if (current == null) throw exception(STORE_NOT_EXISTS);
        int updated = storeMapper.updateOwned(current.getId(), merchant.getId(), BeanUtils.toBean(reqVO, StoreDO.class));
        if (updated != 1) throw exception(STORE_ACCESS_DENIED);
    }
}
