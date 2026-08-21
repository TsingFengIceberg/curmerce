export interface CommonResult<T> {
  code: number;
  msg?: string;
  data: T;
}

export interface ApiPage<T> {
  list: T[];
  total: number;
}

export interface PublicCategoryNode {
  id: number;
  parentId: number | null;
  name: string;
  imageUrl?: string | null;
  children: PublicCategoryNode[];
}

export interface ProductSpecificationValue {
  name: string;
  value: string;
}

export interface PublicProductSummary {
  id: number;
  categoryId: number;
  storeId: number | null;
  storeName: string;
  sellerType?: number;
  sellerUserId?: number | null;
  sellerName?: string | null;
  name: string;
  subtitle?: string | null;
  mainImageUrl?: string | null;
  minPrice: number;
  minMarketPrice?: number | null;
  totalStock: number;
  available: boolean;
}

export interface PublicProductSku {
  id: number;
  specificationValues: ProductSpecificationValue[];
  imageUrl?: string | null;
  price: number;
  marketPrice?: number | null;
  stock: number;
  available: boolean;
}

export interface PublicProductDetail extends PublicProductSummary {
  imageUrls: string[];
  description?: string | null;
  skus: PublicProductSku[];
}

export interface PersonalListing {
  id: number;
  categoryId: number;
  name: string;
  condition: string;
  mainImageUrl?: string | null;
  imageUrls?: string[] | null;
  description?: string | null;
  price?: number | null;
  auditStatus: number;
  saleStatus: number;
  stock: number;
  rejectReason?: string | null;
  createTime?: ApiDateValue;
  updateTime?: ApiDateValue;
}

export interface PersonalListingInput {
  categoryId: number;
  name: string;
  condition: string;
  mainImageUrl: string;
  imageUrls: string[];
  description: string;
  price: number;
}

export interface PersonalSellerOrder {
  id: number;
  orderNo: string;
  buyerUserId: number;
  buyerMobile?: string | null;
  buyerNickname?: string | null;
  buyerEmail?: string | null;
  sellerUserId?: number | null;
  status: number;
  itemCount: number;
  totalAmount: number;
  payableAmount: number;
  receiverName?: string | null;
  receiverMobile?: string | null;
  receiverAreaName?: string | null;
  receiverDetailAddress?: string | null;
  shippingTime?: ApiDateValue;
  logisticsCompany?: string | null;
  trackingNo?: string | null;
  createTime?: ApiDateValue;
  items: OrderItem[];
}

export interface CartItem {
  id: number;
  quantity: number;
  selected: boolean;
  product?: PublicProductSummary | null;
  sku?: PublicProductSku | null;
  invalidReason?: string | null;
}

export interface CartList {
  validList: CartItem[];
  invalidList: CartItem[];
}

export interface OrderCreateResult {
  orderId: number;
  orderNo: string;
  status: number;
  payableAmount: number;
}

export type ApiDateValue = string | number | null;

export interface OrderSummary {
  id: number;
  orderNo: string;
  merchantId?: number | null;
  storeId?: number | null;
  sellerType?: number | null;
  sellerUserId?: number | null;
  status: number;
  refundStatus?: number | null;
  itemCount: number;
  totalAmount: number;
  payableAmount: number;
  createTime?: ApiDateValue;
  completionTime?: ApiDateValue;
}

export interface OrderItem {
  id: number;
  productId: number;
  skuId: number;
  productName: string;
  productImageUrl?: string | null;
  skuCode?: string | null;
  specificationValues?: ProductSpecificationValue[] | null;
  skuImageUrl?: string | null;
  price: number;
  quantity: number;
  totalAmount: number;
}

export interface RefundSummary {
  id: number;
  refundNo: string;
  orderId?: number;
  orderNo?: string;
  amount: number;
  status: number;
  reason?: string | null;
  requestedTime?: ApiDateValue;
  reviewerUserId?: number | null;
  reviewedTime?: ApiDateValue;
  reviewRemark?: string | null;
  callbackId?: string | null;
  callbackSuccess?: boolean | null;
  processedTime?: ApiDateValue;
}

export interface RefundDetail extends RefundSummary {
  orderId: number;
  orderNo: string;
}

export interface OrderDetail extends OrderSummary {
  paymentNo?: string | null;
  paymentStatus?: number | null;
  paymentAmount?: number | null;
  paidTime?: ApiDateValue;
  receiverName?: string | null;
  receiverMobile?: string | null;
  receiverAreaId?: number | null;
  receiverAreaName?: string | null;
  receiverDetailAddress?: string | null;
  shippingTime?: ApiDateValue;
  logisticsCompany?: string | null;
  trackingNo?: string | null;
  items: OrderItem[];
  refund?: RefundSummary | null;
}

export interface PaymentCreateResult {
  paymentId: number;
  paymentNo: string;
  orderId: number;
  orderNo: string;
  amount: number;
  status: number;
}

export interface PaymentCallbackResult {
  paymentId: number;
  paymentNo: string;
  orderId: number;
  paymentStatus: number;
  orderStatus: number;
  paidAmount: number;
  callbackId: string;
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

export interface MerchantOrder {
  id: number;
  orderNo: string;
  memberUserId: number;
  buyerMobile?: string | null;
  buyerNickname?: string | null;
  buyerEmail?: string | null;
  merchantId?: number | null;
  storeId?: number | null;
  sellerType?: number | null;
  sellerUserId?: number | null;
  status: number;
  itemCount: number;
  totalAmount: number;
  payableAmount: number;
  receiverName?: string | null;
  receiverMobile?: string | null;
  receiverAreaId?: number | null;
  receiverAreaName?: string | null;
  receiverDetailAddress?: string | null;
  shippingTime?: ApiDateValue;
  logisticsCompany?: string | null;
  trackingNo?: string | null;
  completionTime?: ApiDateValue;
  createTime?: ApiDateValue;
  items: OrderItem[];
}

export interface AdminRefundPageQuery {
  pageNo: number;
  pageSize: number;
  status?: number;
  orderNo?: string;
  memberUserId?: number;
}

export interface ProductCategoryNode {
  id: number;
  parentId?: number | null;
  code: string;
  name: string;
  imageUrl?: string | null;
  sort: number;
  status: number;
  children: ProductCategoryNode[];
}

export interface StoreSummary {
  id: number;
  merchantId: number;
  name: string;
  code: string;
  description?: string | null;
  contactName?: string | null;
  contactMobile?: string | null;
  status: number;
  createTime?: ApiDateValue;
  updateTime?: ApiDateValue;
}

export interface MerchantSummary {
  id: number;
  name: string;
  code: string;
  contactName: string;
  contactMobile: string;
  defaultStoreName: string;
  defaultStoreCode: string;
  status: number;
  ownerUserId?: number | null;
  reviewerUserId?: number | null;
  reviewTime?: ApiDateValue;
  rejectReason?: string | null;
  createTime?: ApiDateValue;
}

export interface ProductSpecificationValueAdmin {
  name: string;
  value: string;
}

export interface ProductSkuAdmin {
  id?: number;
  productId?: number;
  code: string;
  specificationValues?: ProductSpecificationValueAdmin[] | null;
  imageUrl?: string | null;
  price: number;
  marketPrice?: number | null;
  stock: number;
  status: number;
  sort: number;
}

export interface ProductAdmin {
  id: number;
  merchantId: number;
  storeId: number;
  categoryId: number;
  code: string;
  name: string;
  subtitle?: string | null;
  mainImageUrl?: string | null;
  imageUrls?: string[] | null;
  description?: string | null;
  auditStatus: number;
  saleStatus: number;
  reviewerUserId?: number | null;
  reviewTime?: ApiDateValue;
  rejectReason?: string | null;
  sort: number;
  createTime?: ApiDateValue;
  updateTime?: ApiDateValue;
  skus: ProductSkuAdmin[];
}

export interface ProductPageQuery {
  pageNo: number;
  pageSize: number;
  storeId?: number;
  merchantId?: number;
  categoryId?: number;
  code?: string;
  name?: string;
  auditStatus?: number;
  saleStatus?: number;
}

export interface ProductSkuInput {
  id?: number;
  code: string;
  specificationValues: ProductSpecificationValueAdmin[];
  imageUrl: string;
  price: number;
  marketPrice: number;
  stock: number;
  status: number;
  sort: number;
}

export interface ProductSaveInput {
  storeId: number;
  categoryId: number;
  name: string;
  subtitle: string;
  mainImageUrl: string;
  imageUrls: string[];
  description: string;
  sort: number;
  skus: ProductSkuInput[];
}

export interface CommunityTopic {
  id: number;
  name: string;
  slug: string;
}

export interface CommunityProductLink extends PublicProductSummary {}

export interface CommunityPost {
  id: number;
  authorUserId: number;
  authorNickname?: string | null;
  authorAvatar?: string | null;
  title: string;
  content: string;
  mediaUrls?: string[] | null;
  status: number;
  likeCount: number;
  favoriteCount: number;
  commentCount: number;
  liked?: boolean;
  favorited?: boolean;
  followingAuthor?: boolean;
  topics: CommunityTopic[];
  products: CommunityProductLink[];
  createTime?: ApiDateValue;
  updateTime?: ApiDateValue;
}

export interface CommunityComment {
  id: number;
  postId: number;
  parentId?: number | null;
  authorUserId: number;
  authorNickname?: string | null;
  content: string;
  status: number;
  createTime?: ApiDateValue;
}

export interface CommunityReport {
  id: number;
  postId: number;
  reporterUserId: number;
  reason: string;
  status: number;
  reviewerUserId?: number | null;
  reviewRemark?: string | null;
  reviewTime?: ApiDateValue;
  createTime?: ApiDateValue;
}
