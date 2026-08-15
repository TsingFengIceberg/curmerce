package cn.iocoder.yudao.module.member;

import cn.iocoder.yudao.module.member.controller.app.auth.MemberAuthController;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.MemberAuthRegisterReqVO;
import cn.iocoder.yudao.module.member.controller.app.user.MemberProfileController;
import org.junit.jupiter.api.Test;

import jakarta.annotation.security.PermitAll;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class MemberContractTest {
    @Test
    void registrationRequestDoesNotExposePasswordInToString() {
        MemberAuthRegisterReqVO request = new MemberAuthRegisterReqVO();
        request.setMobile("13800138000");
        request.setNickname("buyer");
        request.setPassword("secret-password");
        assertFalse(request.toString().contains("secret-password"));
    }

    @Test
    void onlyAuthEndpointsArePublic() throws Exception {
        assertNotNull(MemberAuthController.class.getMethod("register", MemberAuthRegisterReqVO.class)
                .getAnnotation(PermitAll.class));
        assertNotNull(MemberAuthController.class.getMethod("login", cn.iocoder.yudao.module.member.controller.app.auth.vo.MemberAuthLoginReqVO.class)
                .getAnnotation(PermitAll.class));
        for (Method method : MemberProfileController.class.getDeclaredMethods()) {
            assertNull(method.getAnnotation(PermitAll.class));
        }
    }
}
