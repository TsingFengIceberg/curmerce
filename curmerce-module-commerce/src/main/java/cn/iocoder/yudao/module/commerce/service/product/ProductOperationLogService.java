package cn.iocoder.yudao.module.commerce.service.product;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductOperationLogRespVO;

public interface ProductOperationLogService {

    int OPERATOR_PERSONAL = 1;
    int OPERATOR_MERCHANT = 2;
    int OPERATOR_ADMIN = 3;

    void record(Long productId, Long operatorUserId, int operatorType, String action,
                Integer fromAuditStatus, Integer toAuditStatus,
                Integer fromSaleStatus, Integer toSaleStatus, String remark);

    PageResult<ProductOperationLogRespVO> getPage(Long productId, PageParam pageParam);
}
