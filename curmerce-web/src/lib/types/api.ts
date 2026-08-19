export interface CommonResult<T> {
  code: number;
  msg?: string;
  data: T;
}

export interface ApiPage<T> {
  list: T[];
  total: number;
}

export interface ApiErrorShape {
  code?: number;
  msg?: string;
}

export interface MemberToken {
  userId: number;
  accessToken: string;
  refreshToken: string;
  expiresTime: string;
}

export interface MemberProfile {
  id: number;
  mobile: string;
  nickname: string;
  avatar?: string | null;
  email?: string | null;
  sex?: number | null;
}

export interface MemberAddress {
  id: number;
  name: string;
  mobile: string;
  areaId: number;
  areaName?: string | null;
  detailAddress: string;
  defaultStatus: boolean;
}

export interface MemberAddressInput {
  name: string;
  mobile: string;
  areaId: number;
  detailAddress: string;
  defaultStatus: boolean;
}

export interface MemberAddressUpdateInput extends MemberAddressInput {
  id: number;
}
