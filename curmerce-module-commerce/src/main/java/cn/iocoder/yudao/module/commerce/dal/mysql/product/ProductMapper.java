package cn.iocoder.yudao.module.commerce.dal.mysql.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductPageOwnReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductReviewPageReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.personal.vo.PersonalListingPageReqVO;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapperX<ProductDO> {

    /**
     * Public catalog pagination must apply every visibility predicate before the
     * pagination interceptor calculates records and totals.
     */
    IPage<ProductDO> selectPublicPage(IPage<ProductDO> page,
                                      @Param("categoryIds") Collection<Long> categoryIds,
                                      @Param("keyword") String keyword);

    default PageResult<ProductDO> selectPublicPage(PageParam pageParam,
                                                   Collection<Long> categoryIds,
                                                   String keyword) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return PageResult.empty();
        }
        if (PageParam.PAGE_SIZE_NONE.equals(pageParam.getPageSize())) {
            List<ProductDO> list = selectPublicPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, -1),
                    categoryIds, keyword).getRecords();
            return new PageResult<>(list, (long) list.size());
        }
        IPage<ProductDO> page = MyBatisUtils.buildPage(pageParam);
        selectPublicPage(page, categoryIds, keyword);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    default List<ProductDO> selectPublicCandidates(String keyword) {
        LambdaQueryWrapper<ProductDO> wrapper = new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getAuditStatus, cn.iocoder.yudao.module.commerce.enums.product.ProductAuditStatusEnum.APPROVED.getStatus())
                .eq(ProductDO::getSaleStatus, cn.iocoder.yudao.module.commerce.enums.product.ProductSaleStatusEnum.ON_SALE.getStatus())
                .orderByAsc(ProductDO::getSort).orderByDesc(ProductDO::getId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(ProductDO::getName, keyword).or().like(ProductDO::getSubtitle, keyword));
        }
        return selectList(wrapper);
    }

    default ProductDO selectByIdAndMerchantId(Long id, Long merchantId) {
        return selectOne(new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getId, id)
                .eq(ProductDO::getMerchantId, merchantId));
    }

    default ProductDO selectByMerchantIdAndCode(Long merchantId, String code) {
        return selectOne(new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getMerchantId, merchantId)
                .eq(ProductDO::getCode, code));
    }

    default ProductDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(new LambdaQueryWrapper<ProductDO>().eq(ProductDO::getId, id));
    }

    default ProductDO selectByIdAndMerchantIdForUpdate(Long id, Long merchantId) {
        return selectOneForUpdate(new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getId, id).eq(ProductDO::getMerchantId, merchantId));
    }

    default ProductDO selectPersonalById(Long id, Long sellerUserId) {
        return selectOne(new LambdaQueryWrapper<ProductDO>().eq(ProductDO::getId, id)
                .eq(ProductDO::getSellerType, 2).eq(ProductDO::getSellerUserId, sellerUserId));
    }

    default ProductDO selectPersonalByIdForUpdate(Long id, Long sellerUserId) {
        return selectOneForUpdate(new LambdaQueryWrapper<ProductDO>().eq(ProductDO::getId, id)
                .eq(ProductDO::getSellerType, 2).eq(ProductDO::getSellerUserId, sellerUserId));
    }

    default PageResult<ProductDO> selectPagePersonal(PersonalListingPageReqVO req, Long sellerUserId) {
        return selectPage(req, new LambdaQueryWrapperX<ProductDO>()
                .eq(ProductDO::getSellerType, 2).eq(ProductDO::getSellerUserId, sellerUserId)
                .eqIfPresent(ProductDO::getAuditStatus, req.getAuditStatus())
                .eqIfPresent(ProductDO::getSaleStatus, req.getSaleStatus())
                .orderByDesc(ProductDO::getId));
    }

    default int updatePersonalFields(ProductDO update, Long sellerUserId) {
        return update(update, new LambdaUpdateWrapper<ProductDO>().eq(ProductDO::getId, update.getId())
                .eq(ProductDO::getSellerType, 2).eq(ProductDO::getSellerUserId, sellerUserId)
                .eq(ProductDO::getSaleStatus, 0));
    }

    default int updatePersonalSaleExpected(Long id, Long sellerUserId, Integer expectedStatus, Integer targetStatus) {
        return update(new ProductDO().setSaleStatus(targetStatus),
                new LambdaUpdateWrapper<ProductDO>().eq(ProductDO::getId, id)
                        .eq(ProductDO::getSellerType, 2).eq(ProductDO::getSellerUserId, sellerUserId)
                        .eq(ProductDO::getAuditStatus, 2).eq(ProductDO::getSaleStatus, expectedStatus));
    }

    default PageResult<ProductDO> selectPageOwn(ProductPageOwnReqVO reqVO, Long merchantId) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProductDO>()
                .eq(ProductDO::getMerchantId, merchantId)
                .eqIfPresent(ProductDO::getStoreId, reqVO.getStoreId())
                .eqIfPresent(ProductDO::getCategoryId, reqVO.getCategoryId())
                .eqIfPresent(ProductDO::getCode, reqVO.getCode())
                .likeIfPresent(ProductDO::getName, reqVO.getName())
                .eqIfPresent(ProductDO::getAuditStatus, reqVO.getAuditStatus())
                .eqIfPresent(ProductDO::getSaleStatus, reqVO.getSaleStatus())
                .betweenIfPresent(ProductDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(ProductDO::getId));
    }

    default PageResult<ProductDO> selectReviewPage(ProductReviewPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProductDO>()
                .eqIfPresent(ProductDO::getMerchantId, reqVO.getMerchantId())
                .eqIfPresent(ProductDO::getStoreId, reqVO.getStoreId())
                .eqIfPresent(ProductDO::getCategoryId, reqVO.getCategoryId())
                .eqIfPresent(ProductDO::getCode, reqVO.getCode())
                .likeIfPresent(ProductDO::getName, reqVO.getName())
                .eqIfPresent(ProductDO::getAuditStatus, reqVO.getAuditStatus())
                .eqIfPresent(ProductDO::getSaleStatus, reqVO.getSaleStatus())
                .betweenIfPresent(ProductDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(ProductDO::getId));
    }

    default int updateAuditExpected(Long id, Integer expectedStatus, Integer targetStatus,
                                    Long reviewerId, LocalDateTime reviewTime, String rejectReason) {
        LambdaUpdateWrapper<ProductDO> wrapper = new LambdaUpdateWrapper<ProductDO>()
                .eq(ProductDO::getId, id)
                .eq(ProductDO::getAuditStatus, expectedStatus)
                .eq(ProductDO::getSaleStatus,
                        cn.iocoder.yudao.module.commerce.enums.product.ProductSaleStatusEnum.OFF_SHELF.getStatus())
                .set(ProductDO::getAuditStatus, targetStatus)
                .set(ProductDO::getReviewerUserId, reviewerId)
                .set(ProductDO::getReviewTime, reviewTime)
                .set(ProductDO::getRejectReason, rejectReason);
        return update(null, wrapper);
    }

    default int updateOwnFields(Long id, Long merchantId, ProductDO update) {
        LambdaUpdateWrapper<ProductDO> wrapper = new LambdaUpdateWrapper<ProductDO>()
                .eq(ProductDO::getId, id)
                .eq(ProductDO::getMerchantId, merchantId);
        return update(update, wrapper);
    }

    default int updateSaleExpected(Long id, Long merchantId, Integer expectedStatus, Integer targetStatus) {
        return update(new ProductDO().setSaleStatus(targetStatus),
                new LambdaUpdateWrapper<ProductDO>().eq(ProductDO::getId, id)
                        .eq(ProductDO::getMerchantId, merchantId)
                        .eq(ProductDO::getAuditStatus,
                                cn.iocoder.yudao.module.commerce.enums.product.ProductAuditStatusEnum.APPROVED.getStatus())
                        .eq(ProductDO::getSaleStatus, expectedStatus));
    }

    default long countOnSaleByCategoryIds(Collection<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return 0L;
        }
        return selectCount(new LambdaQueryWrapper<ProductDO>()
                .in(ProductDO::getCategoryId, categoryIds)
                .eq(ProductDO::getSaleStatus, cn.iocoder.yudao.module.commerce.enums.product.ProductSaleStatusEnum.ON_SALE.getStatus()));
    }
}
