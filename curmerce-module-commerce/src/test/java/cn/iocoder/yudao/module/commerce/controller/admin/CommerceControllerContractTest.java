package cn.iocoder.yudao.module.commerce.controller.admin;

import cn.iocoder.yudao.module.commerce.controller.admin.merchant.MerchantController;
import cn.iocoder.yudao.module.commerce.controller.admin.merchant.vo.MerchantApproveReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.store.StoreController;
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
