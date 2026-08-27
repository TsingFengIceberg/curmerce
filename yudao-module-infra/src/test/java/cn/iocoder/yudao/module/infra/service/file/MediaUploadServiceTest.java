package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.MediaUploadTicketDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.dal.mysql.file.MediaUploadTicketMapper;
import cn.iocoder.yudao.module.infra.framework.file.config.CurmerceMediaProperties;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileObjectMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_DIRECT_UPLOAD_MISSING;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_UPLOAD_TICKET_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaUploadServiceTest {

    private final MediaUploadService service = new MediaUploadService();
    @Mock private FileConfigService fileConfigService;
    @Mock private FileMapper fileMapper;
    @Mock private MediaUploadTicketMapper ticketMapper;
    @Mock private FileClient fileClient;
    @Mock private MediaImageInspector imageInspector;
    @Mock private MediaContentScanner contentScanner;
    @Mock private MediaQuotaService quotaService;
    @Mock private MediaMetrics metrics;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "fileConfigService", fileConfigService);
        ReflectionTestUtils.setField(service, "fileMapper", fileMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "imageInspector", imageInspector);
        ReflectionTestUtils.setField(service, "contentScanner", contentScanner);
        ReflectionTestUtils.setField(service, "quotaService", quotaService);
        ReflectionTestUtils.setField(service, "metrics", metrics);
        ReflectionTestUtils.setField(service, "transactionManager", transactionManager);
        ReflectionTestUtils.setField(service, "properties", new CurmerceMediaProperties());
        LoginUser loginUser = new LoginUser().setId(7L).setUserType(1);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsTicketOwnedByAnotherUser() {
        MediaUploadTicketDO ticket = ticket(0).setOwnerUserId(8L);
        when(ticketMapper.selectByTicketKey("ticket")).thenReturn(ticket);

        assertServiceException(() -> service.finalizeTicket("ticket"), FILE_UPLOAD_TICKET_FORBIDDEN);
        verifyNoInteractions(fileConfigService);
    }

    @Test
    void mapsMissingObjectAndReleasesClaimForRetry() throws Exception {
        MediaUploadTicketDO ticket = ticket(0);
        when(ticketMapper.selectByTicketKey("ticket")).thenReturn(ticket);
        when(ticketMapper.claim(1L)).thenReturn(1);
        when(fileConfigService.getFileClient(2L)).thenReturn(fileClient);
        when(fileClient.getMetadata("uploads/test.png")).thenReturn(null);
        when(ticketMapper.selectById(1L)).thenReturn(ticket);

        assertServiceException(() -> service.finalizeTicket("ticket"), FILE_DIRECT_UPLOAD_MISSING);
        verify(ticketMapper).releaseClaim(1L);
    }

    @Test
    void finalizedTicketIsIdempotent() {
        MediaUploadTicketDO ticket = ticket(10).setFinalizedFileId(9L);
        when(ticketMapper.selectByTicketKey("ticket")).thenReturn(ticket);
        when(fileMapper.selectById(9L)).thenReturn(
                new FileDO().setAssetKey("44a8bdcf-570c-4b43-a9da-a69a87d126e5"));

        assertEquals("/app-api/infra/file/assets/44a8bdcf-570c-4b43-a9da-a69a87d126e5",
                service.finalizeTicket("ticket"));
        verifyNoInteractions(fileConfigService);
    }

    @Test
    void invalidDirectImageIsRejectedAndRemoved() throws Exception {
        MediaUploadTicketDO ticket = ticket(0);
        when(transactionManager.getTransaction(any(DefaultTransactionDefinition.class))).thenReturn(transactionStatus);
        when(ticketMapper.selectByTicketKey("ticket")).thenReturn(ticket);
        when(ticketMapper.claim(1L)).thenReturn(1);
        when(fileConfigService.getFileClient(2L)).thenReturn(fileClient);
        when(fileClient.getMetadata("uploads/test.png"))
                .thenReturn(new FileObjectMetadata(3L, "image/png", "etag"));
        when(fileClient.getContent("uploads/test.png")).thenReturn(new byte[]{1, 2, 3});
        when(imageInspector.inspect(any(), eq("test.png")))
                .thenThrow(new IllegalArgumentException("invalid image"));

        assertThrows(IllegalArgumentException.class, () -> service.finalizeTicket("ticket"));

        verify(fileClient).delete("uploads/test.png");
        verify(ticketMapper).updateById(argThat((MediaUploadTicketDO update) ->
                Integer.valueOf(30).equals(update.getStatus())));
        verify(quotaService).commitStorage(any(MediaQuotaReservation.class));
        verify(ticketMapper, never()).releaseClaim(1L);
    }

    private static MediaUploadTicketDO ticket(int status) {
        return new MediaUploadTicketDO().setId(1L).setTicketKey("ticket").setAssetKey("asset")
                .setConfigId(2L).setPath("uploads/test.png").setOriginalName("test.png")
                .setExpectedType("image/png").setExpectedSize(3L).setVisibility(0)
                .setOwnerUserId(7L).setOwnerUserType(1).setQuotaDate(LocalDate.now())
                .setStatus(status).setExpiresAt(LocalDateTime.now().plusMinutes(5));
    }
}
