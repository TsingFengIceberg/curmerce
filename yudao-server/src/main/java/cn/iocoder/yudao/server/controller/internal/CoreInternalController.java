package cn.iocoder.yudao.server.controller.internal;

import cn.iocoder.yudao.curmerce.cloud.api.CoreMediaReferencesReqDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CoreMemberUserRespDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CorePermissionCheckReqDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CoreProductSummaryRespDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CoreTokenCheckReqDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CoreAuctionItemCheckReqDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CoreAuctionItemCheckRespDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CoreAuctionOrderReqDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CoreAuctionOrderRespDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CoreMerchantOwnerRespDTO;
import cn.iocoder.yudao.curmerce.cloud.api.CoreOrderStatusRespDTO;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.commerce.controller.app.catalog.vo.PublicProductSummaryRespVO;
import cn.iocoder.yudao.module.commerce.service.catalog.PublicCatalogService;
import cn.iocoder.yudao.module.commerce.service.auction.AuctionCoreIntegrationService;
import cn.iocoder.yudao.module.commerce.service.order.OrderService;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/internal-api/curmerce/core")
@PermitAll
public class CoreInternalController {

    private static final String INTERNAL_TOKEN_HEADER = "X-Curmerce-Internal-Token";

    @Resource private InternalRequestGuard requestGuard;
    @Resource private OAuth2TokenCommonApi tokenApi;
    @Resource private PermissionCommonApi permissionApi;
    @Resource private MemberUserApi memberUserApi;
    @Resource private PublicCatalogService catalogService;
    @Resource private FileApi fileApi;
    @Resource private AuctionCoreIntegrationService auctionIntegrationService;
    @Resource private OrderService orderService;

    @PostMapping("/auth/check")
    public CommonResult<OAuth2AccessTokenCheckRespDTO> checkToken(
            @RequestHeader(INTERNAL_TOKEN_HEADER) String internalToken,
            @Valid @RequestBody CoreTokenCheckReqDTO request) {
        requestGuard.check(internalToken);
        return success(tokenApi.checkAccessToken(request.getToken()));
    }

    @PostMapping("/permission/check")
    public CommonResult<Boolean> checkPermission(
            @RequestHeader(INTERNAL_TOKEN_HEADER) String internalToken,
            @Valid @RequestBody CorePermissionCheckReqDTO request) {
        requestGuard.check(internalToken);
        String[] values = request.getValues().toArray(String[]::new);
        boolean allowed = CorePermissionCheckReqDTO.TYPE_ROLE.equals(request.getType())
                ? permissionApi.hasAnyRoles(request.getUserId(), values)
                : permissionApi.hasAnyPermissions(request.getUserId(), values);
        return success(allowed);
    }

    @GetMapping("/permission/dept/{userId}")
    public CommonResult<DeptDataPermissionRespDTO> getDeptDataPermission(
            @RequestHeader(INTERNAL_TOKEN_HEADER) String internalToken,
            @PathVariable Long userId) {
        requestGuard.check(internalToken);
        return success(permissionApi.getDeptDataPermission(userId));
    }

    @GetMapping("/member/{id}")
    public CommonResult<CoreMemberUserRespDTO> getMember(
            @RequestHeader(INTERNAL_TOKEN_HEADER) String internalToken,
            @PathVariable Long id) {
        requestGuard.check(internalToken);
        return success(toMember(memberUserApi.getUser(id)));
    }

    @PostMapping("/member/{id}/validate")
    public CommonResult<Boolean> validateMember(
            @RequestHeader(INTERNAL_TOKEN_HEADER) String internalToken,
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean forUpdate) {
        requestGuard.check(internalToken);
        if (forUpdate) {
            memberUserApi.validateActiveUserForUpdate(id);
        } else {
            memberUserApi.validateActiveUser(id);
        }
        return success(true);
    }

    @GetMapping("/product/{id}")
    public CommonResult<CoreProductSummaryRespDTO> getVisibleProduct(
            @RequestHeader(INTERNAL_TOKEN_HEADER) String internalToken,
            @PathVariable Long id) {
        requestGuard.check(internalToken);
        return success(toProduct(catalogService.getVisibleSummary(id, null)));
    }

    @PostMapping("/media/references")
    public CommonResult<Boolean> replaceMediaReferences(
            @RequestHeader(INTERNAL_TOKEN_HEADER) String internalToken,
            @Valid @RequestBody CoreMediaReferencesReqDTO request) {
        requestGuard.check(internalToken);
        fileApi.replaceFileReferences(request.getBusinessType(), request.getBusinessId(),
                request.getFieldName(), request.getUrls());
        return success(true);
    }

    @PostMapping("/auction/item/check")
    public CommonResult<CoreAuctionItemCheckRespDTO> checkAuctionItem(
            @RequestHeader(INTERNAL_TOKEN_HEADER) String internalToken,
            @Valid @RequestBody CoreAuctionItemCheckReqDTO request) {
        requestGuard.check(internalToken);
        return success(auctionIntegrationService.validateOwnedItem(request));
    }

    @PostMapping("/auction/settlement-order")
    public CommonResult<CoreAuctionOrderRespDTO> createAuctionSettlementOrder(
            @RequestHeader(INTERNAL_TOKEN_HEADER) String internalToken,
            @Valid @RequestBody CoreAuctionOrderReqDTO request) {
        requestGuard.check(internalToken);
        return success(auctionIntegrationService.createSettlementOrder(request));
    }

    @GetMapping("/auction/owner/{userId}")
    public CommonResult<CoreMerchantOwnerRespDTO> getAuctionMerchantOwner(
            @RequestHeader(INTERNAL_TOKEN_HEADER) String internalToken,
            @PathVariable Long userId) {
        requestGuard.check(internalToken);
        return success(auctionIntegrationService.findMerchantOwner(userId));
    }

    @GetMapping("/order/{userId}/{orderId}/status")
    public CommonResult<CoreOrderStatusRespDTO> getOwnOrderStatus(
            @RequestHeader(INTERNAL_TOKEN_HEADER) String internalToken,
            @PathVariable Long userId, @PathVariable Long orderId) {
        requestGuard.check(internalToken);
        var order = orderService.getOrder(userId, orderId);
        CoreOrderStatusRespDTO response = new CoreOrderStatusRespDTO().setOrderId(order.getId())
                .setOrderNo(order.getOrderNo()).setStatus(order.getStatus()).setRefundStatus(order.getRefundStatus())
                .setPayableAmount(order.getPayableAmount()).setCreateTime(order.getCreateTime())
                .setShippingTime(order.getShippingTime()).setCompletionTime(order.getCompletionTime())
                .setLogisticsCompany(order.getLogisticsCompany()).setTrackingNo(order.getTrackingNo());
        return success(response);
    }

    private static CoreMemberUserRespDTO toMember(MemberUserRespDTO source) {
        if (source == null) {
            return null;
        }
        return new CoreMemberUserRespDTO().setId(source.getId()).setMobile(source.getMobile())
                .setNickname(source.getNickname()).setAvatar(source.getAvatar())
                .setEmail(source.getEmail()).setStatus(source.getStatus());
    }

    private static CoreProductSummaryRespDTO toProduct(PublicProductSummaryRespVO source) {
        if (source == null) {
            return null;
        }
        return new CoreProductSummaryRespDTO().setId(source.getId()).setCategoryId(source.getCategoryId())
                .setStoreId(source.getStoreId()).setStoreName(source.getStoreName())
                .setSellerType(source.getSellerType()).setSellerUserId(source.getSellerUserId())
                .setSellerName(source.getSellerName()).setName(source.getName()).setCondition(source.getCondition())
                .setSubtitle(source.getSubtitle()).setMainImageUrl(source.getMainImageUrl())
                .setMinPrice(source.getMinPrice()).setMinMarketPrice(source.getMinMarketPrice())
                .setTotalStock(source.getTotalStock()).setAvailable(source.getAvailable());
    }
}
