package cn.iocoder.yudao.module.commerce.service.merchant;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.commerce.controller.admin.merchant.vo.*;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantOperatorDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.store.StoreDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.merchant.MerchantMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.merchant.MerchantOperatorMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.store.StoreMapper;
import cn.iocoder.yudao.module.commerce.enums.merchant.MerchantAuditStatusEnum;
import cn.iocoder.yudao.module.commerce.enums.merchant.MerchantOperatorTypeEnum;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserProvisionReqDTO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.commerce.enums.merchant.MerchantAuditStatusEnum.*;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.USER_USERNAME_EXISTS;

@Service
public class MerchantServiceImpl implements MerchantService {

    private static final String OWNER_ROLE_CODE = "merchant_owner";

    @Resource private MerchantMapper merchantMapper;
    @Resource private StoreMapper storeMapper;
    @Resource private MerchantOperatorMapper operatorMapper;
    @Resource private AdminUserApi adminUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMerchant(MerchantCreateReqVO reqVO) {
        String code = reqVO.getCode().trim();
        String storeCode = reqVO.getDefaultStoreCode().trim();
        if (merchantMapper.selectByCode(code) != null) throw exception(MERCHANT_CODE_DUPLICATE);
        if (merchantMapper.selectByDefaultStoreCode(storeCode) != null || storeMapper.selectByCode(storeCode) != null) {
            throw exception(MERCHANT_DEFAULT_STORE_CODE_DUPLICATE);
        }
        MerchantDO merchant = BeanUtils.toBean(reqVO, MerchantDO.class)
                .setName(reqVO.getName().trim()).setCode(code).setDefaultStoreCode(storeCode)
                .setStatus(PENDING.getStatus());
        merchantMapper.insert(merchant);
        return merchant.getId();
    }

    @Override public MerchantDO getMerchant(Long id) { return merchantMapper.selectById(id); }
    @Override public PageResult<MerchantDO> getMerchantPage(MerchantPageReqVO reqVO) { return merchantMapper.selectPage(reqVO); }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveMerchant(MerchantApproveReqVO reqVO) {
        if (reqVO.getPassword() == null || reqVO.getPassword().length() < 8 || reqVO.getPassword().length() > 64) {
            throw exception(MERCHANT_OWNER_PASSWORD_INVALID);
        }
        Long reviewerId = getLoginUserId();
        MerchantDO merchant = merchantMapper.selectPendingForUpdate(reqVO.getId());
        if (merchant == null) throw exception(MERCHANT_NOT_EXISTS);
        if (!merchant.isPending()) throw exception(MERCHANT_NOT_PENDING);
        if (storeMapper.selectByCode(merchant.getDefaultStoreCode()) != null) throw exception(STORE_CODE_DUPLICATE);
        try {
            Long userId = adminUserApi.provisionUser(new AdminUserProvisionReqDTO()
                    .setUsername(reqVO.getUsername()).setNickname(reqVO.getNickname())
                    .setPassword(reqVO.getPassword()).setRoleCode(OWNER_ROLE_CODE));
            storeMapper.insert(new StoreDO().setMerchantId(merchant.getId()).setName(merchant.getDefaultStoreName())
                    .setCode(merchant.getDefaultStoreCode()).setContactName(merchant.getContactName())
                    .setContactMobile(merchant.getContactMobile()).setStatus(CommonStatusEnum.ENABLE.getStatus()));
            operatorMapper.insert(new MerchantOperatorDO().setMerchantId(merchant.getId()).setUserId(userId)
                    .setOperatorType(MerchantOperatorTypeEnum.OWNER.getType()).setStatus(CommonStatusEnum.ENABLE.getStatus()));
            int updated = merchantMapper.updateReview(merchant.getId(), PENDING.getStatus(), APPROVED.getStatus(),
                    reviewerId, LocalDateTime.now(), userId, null);
            if (updated != 1) throw exception(MERCHANT_REVIEW_CONFLICT);
        } catch (RuntimeException ex) {
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectMerchant(MerchantRejectReqVO reqVO) {
        MerchantDO merchant = merchantMapper.selectPendingForUpdate(reqVO.getId());
        if (merchant == null) throw exception(MERCHANT_NOT_EXISTS);
        if (!merchant.isPending()) throw exception(MERCHANT_NOT_PENDING);
        int updated = merchantMapper.updateReview(merchant.getId(), PENDING.getStatus(), REJECTED.getStatus(),
                getLoginUserId(), LocalDateTime.now(), null, StrUtil.trim(reqVO.getReason()));
        if (updated != 1) throw exception(MERCHANT_REVIEW_CONFLICT);
    }
}
