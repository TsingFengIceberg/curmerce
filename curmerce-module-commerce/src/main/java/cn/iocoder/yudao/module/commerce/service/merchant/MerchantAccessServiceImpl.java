package cn.iocoder.yudao.module.commerce.service.merchant;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantOperatorDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.store.StoreDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.merchant.MerchantMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.merchant.MerchantOperatorMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.store.StoreMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.commerce.enums.merchant.MerchantAuditStatusEnum.APPROVED;
import static cn.iocoder.yudao.module.commerce.enums.merchant.MerchantOperatorTypeEnum.OWNER;

@Service
public class MerchantAccessServiceImpl implements MerchantAccessService {

    @Resource
    private MerchantOperatorMapper operatorMapper;
    @Resource
    private MerchantMapper merchantMapper;
    @Resource
    private StoreMapper storeMapper;

    @Override
    public MerchantAccessContext requireApprovedOwner() {
        Long userId = getLoginUserId();
        if (userId == null) {
            throw ServiceExceptionUtil.exception(STORE_ACCESS_DENIED);
        }
        List<MerchantOperatorDO> relations = new ArrayList<>(operatorMapper.selectListByUserId(userId));
        relations.removeIf(item -> !OWNER.getType().equals(item.getOperatorType()));
        if (relations.isEmpty()) {
            throw ServiceExceptionUtil.exception(MERCHANT_OPERATOR_NOT_EXISTS);
        }
        if (relations.size() > 1) {
            throw ServiceExceptionUtil.exception(MERCHANT_OPERATOR_AMBIGUOUS);
        }
        MerchantOperatorDO relation = relations.get(0);
        if (!CommonStatusEnum.ENABLE.getStatus().equals(relation.getStatus())) {
            throw ServiceExceptionUtil.exception(MERCHANT_OPERATOR_NOT_ACTIVE);
        }
        MerchantDO merchant = merchantMapper.selectById(relation.getMerchantId());
        if (merchant == null || !APPROVED.getStatus().equals(merchant.getStatus())) {
            throw ServiceExceptionUtil.exception(STORE_ACCESS_DENIED);
        }
        StoreDO store = storeMapper.selectByMerchantId(merchant.getId());
        if (store == null) {
            throw ServiceExceptionUtil.exception(STORE_NOT_EXISTS);
        }
        return new MerchantAccessContext(merchant, store);
    }
}
