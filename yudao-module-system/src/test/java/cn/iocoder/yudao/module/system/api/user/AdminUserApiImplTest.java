package cn.iocoder.yudao.module.system.api.user;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserProvisionReqDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.permission.RoleService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.ROLE_IS_DISABLE;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.ROLE_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserApiImplTest {
    @Mock private AdminUserService userService;
    @Mock private RoleService roleService;
    @Mock private PermissionService permissionService;
    @InjectMocks private AdminUserApiImpl api;

    @Test
    void provisionUser_assignsOnlyRequestedRole() {
        RoleDO role = new RoleDO().setId(7L).setName("Merchant Owner").setCode("merchant_owner")
                .setStatus(CommonStatusEnum.ENABLE.getStatus());
        when(roleService.getRoleByCode("merchant_owner")).thenReturn(role);
        when(userService.provisionUser(any())).thenReturn(42L);

        Long userId = api.provisionUser(new AdminUserProvisionReqDTO().setUsername("owner01")
                .setNickname("Owner").setPassword("secret-123").setRoleCode("merchant_owner"));

        assertEquals(42L, userId);
        verify(permissionService).assignUserRole(42L, java.util.Set.of(7L));
    }

    @Test
    void provisionUser_rejectsMissingOrDisabledRole() {
        AdminUserProvisionReqDTO request = new AdminUserProvisionReqDTO().setRoleCode("merchant_owner");
        when(roleService.getRoleByCode("merchant_owner")).thenReturn(null);
        ServiceException missing = assertThrows(ServiceException.class, () -> api.provisionUser(request));
        assertEquals(ROLE_NOT_EXISTS.getCode(), missing.getCode());

        when(roleService.getRoleByCode("merchant_owner"))
                .thenReturn(new RoleDO().setId(7L).setName("Merchant Owner").setStatus(CommonStatusEnum.DISABLE.getStatus()));
        ServiceException disabled = assertThrows(ServiceException.class, () -> api.provisionUser(request));
        assertEquals(ROLE_IS_DISABLE.getCode(), disabled.getCode());
        verifyNoInteractions(userService, permissionService);
    }

    @Test
    void provisionUser_rejectsNullOrBlankRequestBeforeRoleLookup() {
        ServiceException nullRequest = assertThrows(ServiceException.class, () -> api.provisionUser(null));
        assertEquals(400, nullRequest.getCode());
        ServiceException blankRole = assertThrows(ServiceException.class,
                () -> api.provisionUser(new AdminUserProvisionReqDTO()));
        assertEquals(400, blankRole.getCode());
        verifyNoInteractions(roleService, userService, permissionService);
    }
}
