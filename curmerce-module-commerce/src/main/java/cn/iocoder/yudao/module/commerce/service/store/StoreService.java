package cn.iocoder.yudao.module.commerce.service.store;

import cn.iocoder.yudao.module.commerce.controller.admin.store.vo.StoreUpdateOwnReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.store.StoreDO;

public interface StoreService {
    StoreDO getOwnStore();
    void updateOwnStore(StoreUpdateOwnReqVO reqVO);
}
