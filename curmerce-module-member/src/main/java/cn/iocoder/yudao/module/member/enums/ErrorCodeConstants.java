package cn.iocoder.yudao.module.member.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface ErrorCodeConstants {
    ErrorCode MOBILE_ALREADY_REGISTERED = new ErrorCode(1_023_004_000, "手机号已注册");
    ErrorCode BAD_CREDENTIALS = new ErrorCode(1_023_004_001, "手机号或密码错误");
    ErrorCode USER_DISABLED = new ErrorCode(1_023_004_002, "会员账号已停用");
    ErrorCode USER_NOT_EXISTS = new ErrorCode(1_023_004_003, "会员账号不存在或已停用");
    ErrorCode ADDRESS_NOT_FOUND = new ErrorCode(1_023_004_004, "收货地址不存在");
    ErrorCode ADDRESS_DEFAULT_REQUIRED = new ErrorCode(1_023_004_005, "至少需要保留一个默认收货地址");
    ErrorCode ADDRESS_LIMIT = new ErrorCode(1_023_004_006, "收货地址数量已达上限");
    ErrorCode ADDRESS_AREA_INVALID = new ErrorCode(1_023_004_007, "收货地区不存在");
    ErrorCode ADDRESS_STATE_CONFLICT = new ErrorCode(1_023_004_008, "收货地址状态已被其他请求改变");
}
