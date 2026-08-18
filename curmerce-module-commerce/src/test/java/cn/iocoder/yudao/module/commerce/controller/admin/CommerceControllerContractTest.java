package cn.iocoder.yudao.module.commerce.controller.admin;

import cn.iocoder.yudao.module.commerce.controller.admin.merchant.MerchantController;
import cn.iocoder.yudao.module.commerce.controller.admin.merchant.vo.MerchantApproveReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.ProductCategoryController;
import cn.iocoder.yudao.module.commerce.controller.admin.product.ProductController;
import cn.iocoder.yudao.module.commerce.controller.admin.product.ProductReviewController;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductCreateOwnReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductSkuSaveReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductUpdateOwnReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.store.StoreController;
import cn.iocoder.yudao.module.commerce.controller.admin.order.OrderController;
import cn.iocoder.yudao.module.commerce.controller.admin.order.vo.MerchantOrderPageReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.order.vo.MerchantOrderShipReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.refund.RefundController;
import cn.iocoder.yudao.module.commerce.controller.admin.refund.vo.RefundAuditReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.refund.vo.RefundCallbackReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.refund.vo.RefundPageReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CommerceControllerContractTest {

    @Test
    void platformEndpointsDeclareLeastPrivilegePermissions() throws Exception {
        assertPermission(MerchantController.class, "create", "commerce:merchant:create");
        assertPermission(MerchantController.class, "page", "commerce:merchant:query");
        assertPermission(MerchantController.class, "get", "commerce:merchant:query");
        assertPermission(MerchantController.class, "approve", "commerce:merchant:audit");
        assertPermission(MerchantController.class, "reject", "commerce:merchant:audit");
        assertPermission(StoreController.class, "getOwn", "commerce:store:self-query");
        assertPermission(StoreController.class, "updateOwn", "commerce:store:self-update");
        assertPermission(ProductCategoryController.class, "create", "commerce:product-category:create");
        assertPermission(ProductCategoryController.class, "update", "commerce:product-category:update");
        assertPermission(ProductCategoryController.class, "updateStatus", "commerce:product-category:update");
        assertPermission(ProductCategoryController.class, "tree", "commerce:product-category:query");
        assertPermission(ProductController.class, "createOwn", "commerce:product:self-create");
        assertPermission(ProductController.class, "updateOwn", "commerce:product:self-update");
        assertPermission(ProductController.class, "getOwn", "commerce:product:self-query");
        assertPermission(ProductController.class, "pageOwn", "commerce:product:self-query");
        assertPermission(ProductController.class, "submitOwn", "commerce:product:self-submit");
        assertPermission(ProductController.class, "listOwn", "commerce:product:self-publish");
        assertPermission(ProductController.class, "delistOwn", "commerce:product:self-publish");
        assertPermission(ProductReviewController.class, "page", "commerce:product:query");
        assertPermission(ProductReviewController.class, "get", "commerce:product:query");
        assertPermission(ProductReviewController.class, "approve", "commerce:product:audit");
        assertPermission(ProductReviewController.class, "reject", "commerce:product:audit");
        assertPermission(OrderController.class, "pageOwnPendingShipment", "commerce:order:self-query");
        assertPermission(OrderController.class, "shipOwn", "commerce:order:self-ship");
        assertPermission(RefundController.class, "page", "commerce:refund:query");
        assertPermission(RefundController.class, "get", "commerce:refund:query");
        assertPermission(RefundController.class, "approve", "commerce:refund:audit");
        assertPermission(RefundController.class, "reject", "commerce:refund:audit");
        assertPermission(RefundController.class, "simulateCallback", "commerce:refund:callback");
        assertPermission(RefundController.class, "pageOwn", "commerce:refund:self-query");
        assertPermission(RefundController.class, "getOwn", "commerce:refund:self-query");
        assertPermission(RefundController.class, "approveOwn", "commerce:refund:self-audit");
        assertPermission(RefundController.class, "rejectOwn", "commerce:refund:self-audit");
    }

    @Test
    void selfServiceDoesNotAcceptClientOwnershipIdentifiers() {
        Method getOwn = Arrays.stream(StoreController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("getOwn")).findFirst().orElseThrow();
        Method updateOwn = Arrays.stream(StoreController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("updateOwn")).findFirst().orElseThrow();
        assertEquals(0, getOwn.getParameterCount());
        assertEquals(1, updateOwn.getParameterCount());
        assertTrue(updateOwn.getParameterAnnotations()[0].length > 0);
        assertTrue(Arrays.stream(updateOwn.getParameterAnnotations()[0])
                .anyMatch(annotation -> annotation.annotationType().equals(RequestBody.class)));
    }

    @Test
    void productSelfServiceRequestsDoNotAcceptServerOwnedFields() throws Exception {
        assertNoField(ProductCreateOwnReqVO.class, "merchantId", "reviewerUserId", "auditStatus", "saleStatus");
        assertNoField(ProductUpdateOwnReqVO.class, "merchantId", "reviewerUserId", "auditStatus", "saleStatus", "code");
        assertNoField(ProductSkuSaveReqVO.class, "merchantId", "productId");
    }

    @Test
    void merchantOrderSelfServiceRequestsDoNotAcceptClientOwnershipFields() {
        assertNoField(MerchantOrderPageReqVO.class, "merchantId", "storeId");
        assertNoField(MerchantOrderShipReqVO.class, "merchantId", "storeId");
    }

    @Test
    void refundRequestsDoNotAcceptServerOwnedFields() {
        assertNoField(RefundPageReqVO.class, "merchantId", "storeId", "reviewerUserId", "callbackSuccess");
        assertNoField(RefundAuditReqVO.class, "reviewerUserId", "merchantId", "storeId");
        assertNoField(RefundCallbackReqVO.class, "merchantId", "storeId", "reviewerUserId",
                "processedTime", "callbackSuccess");
    }

    private static void assertNoField(Class<?> type, String... names) {
        for (String name : names) {
            Class<?> current = type;
            while (current != null) {
                try {
                    current.getDeclaredField(name);
                    fail(type.getSimpleName() + " unexpectedly declares " + name);
                } catch (NoSuchFieldException ignored) {
                    // Continue through the inheritance chain.
                }
                current = current.getSuperclass();
            }
        }
    }

    @Test
    void approvalRequestToStringCannotExposePassword() {
        MerchantApproveReqVO request = new MerchantApproveReqVO().setId(1L).setUsername("owner01")
                .setNickname("Owner").setPassword("sentinel-secret");
        String rendered = request.toString();
        assertFalse(rendered.contains("sentinel-secret"));
        assertFalse(rendered.contains("password"));
    }

    private static void assertPermission(Class<?> controller, String methodName, String expected) {
        Method method = Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation);
        assertEquals("@ss.hasPermission('" + expected + "')", annotation.value());
    }
}
