package cn.iocoder.yudao.module.commerce.service.product;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductOperationLogRespVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductOperationLogDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductOperationLogMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductOperationLogServiceImpl implements ProductOperationLogService {

    @Resource
    private ProductOperationLogMapper operationLogMapper;

    @Override
    public void record(Long productId, Long operatorUserId, int operatorType, String action,
                       Integer fromAuditStatus, Integer toAuditStatus,
                       Integer fromSaleStatus, Integer toSaleStatus, String remark) {
        operationLogMapper.insert(new ProductOperationLogDO().setProductId(productId)
                .setOperatorUserId(operatorUserId).setOperatorType(operatorType).setAction(action)
                .setFromAuditStatus(fromAuditStatus).setToAuditStatus(toAuditStatus)
                .setFromSaleStatus(fromSaleStatus).setToSaleStatus(toSaleStatus).setRemark(remark));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ProductOperationLogRespVO> getPage(Long productId, PageParam pageParam) {
        PageResult<ProductOperationLogDO> page = operationLogMapper.selectPageByProductId(productId, pageParam);
        return new PageResult<>(page.getList().stream().map(log -> new ProductOperationLogRespVO()
                .setId(log.getId()).setProductId(log.getProductId()).setOperatorUserId(log.getOperatorUserId())
                .setOperatorType(log.getOperatorType())
                .setAction(log.getAction()).setFromAuditStatus(log.getFromAuditStatus()).setToAuditStatus(log.getToAuditStatus())
                .setFromSaleStatus(log.getFromSaleStatus()).setToSaleStatus(log.getToSaleStatus())
                .setRemark(log.getRemark()).setCreateTime(log.getCreateTime())).toList(), page.getTotal());
    }
}
