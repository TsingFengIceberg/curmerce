package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.infra.dal.mysql.file.MediaUploadQuotaMapper;
import cn.iocoder.yudao.module.infra.framework.file.config.CurmerceMediaProperties;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUser;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_UPLOAD_LOGIN_REQUIRED;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_UPLOAD_QUOTA_EXCEEDED;

@Service
public class MediaQuotaService {

    @Resource
    private MediaUploadQuotaMapper quotaMapper;

    @Resource
    private CurmerceMediaProperties properties;

    @Transactional(rollbackFor = Exception.class)
    public MediaQuotaReservation reserve(long bytes) {
        LoginUser user = getLoginUser();
        if (user == null) {
            throw exception(FILE_UPLOAD_LOGIN_REQUIRED);
        }
        LocalDate quotaDate = LocalDate.now();
        quotaMapper.ensureQuotaRow(user.getId(), user.getUserType(), quotaDate);
        CurmerceMediaProperties.Quota limit = Integer.valueOf(2).equals(user.getUserType())
                ? properties.getAdminQuota() : properties.getMemberQuota();
        int updated = quotaMapper.reserve(user.getId(), user.getUserType(), quotaDate, bytes,
                limit.getDailyUploadCount(), limit.getDailyUploadBytes().toBytes(),
                limit.getTotalStoredBytes().toBytes());
        if (updated != 1) {
            throw exception(FILE_UPLOAD_QUOTA_EXCEEDED);
        }
        return new MediaQuotaReservation(user.getId(), user.getUserType(), quotaDate, bytes);
    }

    @Transactional(rollbackFor = Exception.class)
    public void commitStorage(MediaQuotaReservation reservation) {
        quotaMapper.commitStorage(reservation.userId(), reservation.userType(),
                reservation.quotaDate(), reservation.bytes());
    }

    @Transactional(rollbackFor = Exception.class)
    public void release(MediaQuotaReservation reservation) {
        quotaMapper.release(reservation.userId(), reservation.userType(),
                reservation.quotaDate(), reservation.bytes());
    }
}
