package cn.iocoder.yudao.module.commerce.service.merchant;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.commerce.controller.admin.merchant.vo.MerchantApproveReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.merchant.MerchantMapper;
import cn.iocoder.yudao.module.commerce.enums.merchant.MerchantAuditStatusEnum;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import jakarta.annotation.Resource;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@Import(MerchantServiceImpl.class)
class MerchantApprovalTransactionTest extends BaseDbUnitTest {

    @Resource
    private MerchantServiceImpl merchantService;
    @Resource
    private MerchantMapper merchantMapper;
    @Resource
    private DataSource dataSource;
    @MockitoBean
    private AdminUserApi adminUserApi;

    private JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(dataSource);
    }

    @Test
    void systemProvisionFailureRollsBackSystemAndCommerceWritesTogether() {
        MerchantDO merchant = new MerchantDO().setName("Merchant A").setCode("merchant_a")
                .setContactName("Alice").setContactMobile("13800138000")
                .setDefaultStoreName("Store A").setDefaultStoreCode("store_a")
                .setStatus(MerchantAuditStatusEnum.PENDING.getStatus());
        merchantMapper.insert(merchant);

        doAnswer(invocation -> {
            jdbcTemplate().update("INSERT INTO system_users (username, password, nickname, status, tenant_id) "
                    + "VALUES (?, ?, ?, ?, ?)", "owner01", "encoded", "Owner", 0, 0);
            jdbcTemplate().update("INSERT INTO system_user_role (user_id, role_id, tenant_id) VALUES (?, ?, ?)",
                    1L, 7L, 0L);
            throw new IllegalStateException("simulated System provisioning failure");
        }).when(adminUserApi).provisionUser(any());

        assertThrows(IllegalStateException.class, () -> merchantService.approveMerchant(
                new MerchantApproveReqVO().setId(merchant.getId()).setUsername("owner01")
                        .setNickname("Owner").setPassword("secret-123")));

        assertEquals(MerchantAuditStatusEnum.PENDING.getStatus(),
                merchantMapper.selectById(merchant.getId()).getStatus());
        assertEquals(0, jdbcTemplate().queryForObject("SELECT COUNT(*) FROM system_users", Integer.class));
        assertEquals(0, jdbcTemplate().queryForObject("SELECT COUNT(*) FROM system_user_role", Integer.class));
        assertEquals(0, jdbcTemplate().queryForObject("SELECT COUNT(*) FROM commerce_store", Integer.class));
        assertEquals(0, jdbcTemplate().queryForObject("SELECT COUNT(*) FROM commerce_merchant_operator", Integer.class));
    }
}
