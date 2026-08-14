package cn.iocoder.yudao.module.commerce.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface ErrorCodeConstants {

    ErrorCode MERCHANT_NOT_EXISTS = new ErrorCode(1_023_001_000, "商家不存在");
    ErrorCode MERCHANT_CODE_DUPLICATE = new ErrorCode(1_023_001_001, "商家编码已存在");
    ErrorCode MERCHANT_DEFAULT_STORE_CODE_DUPLICATE = new ErrorCode(1_023_001_002, "默认店铺编码已存在");
    ErrorCode MERCHANT_NOT_PENDING = new ErrorCode(1_023_001_003, "商家不处于待审核状态");
    ErrorCode MERCHANT_REVIEW_CONFLICT = new ErrorCode(1_023_001_004, "商家审核状态已被其他请求改变");
    ErrorCode MERCHANT_OWNER_PASSWORD_INVALID = new ErrorCode(1_023_001_005, "商家主账号密码长度必须为 8-64 位");
    ErrorCode STORE_CODE_DUPLICATE = new ErrorCode(1_023_001_006, "店铺编码已存在");
    ErrorCode STORE_NOT_EXISTS = new ErrorCode(1_023_001_007, "店铺不存在");
    ErrorCode MERCHANT_OPERATOR_NOT_EXISTS = new ErrorCode(1_023_001_008, "商家操作员关系不存在");
    ErrorCode MERCHANT_OPERATOR_NOT_ACTIVE = new ErrorCode(1_023_001_009, "商家操作员关系已停用");
    ErrorCode MERCHANT_OPERATOR_AMBIGUOUS = new ErrorCode(1_023_001_010, "当前账号关联多个商家，暂不支持自助操作");
    ErrorCode STORE_ACCESS_DENIED = new ErrorCode(1_023_001_011, "无权访问该店铺");
}
