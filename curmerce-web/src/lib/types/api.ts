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

export interface ProductFavorite {
  id: number;
  productId: number;
  favoriteTime?: ApiDateValue;
  product?: PublicProductSummary | null;
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
  merchantName?: string | null;
  storeId?: number | null;
  storeName?: string | null;
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

export interface AdminMemberSummary {
  id: number;
  mobile: string;
  nickname: string;
  avatar?: string;
  email?: string;
  status: number;
  createTime?: string;
}

export interface AreaNode {
  id: number;
  name: string;
  children?: AreaNode[];
}

export interface AdminPermissionInfo {
  user: { id: number; username: string; nickname: string };
  roles: string[];
  permissions: string[];
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

export interface ReleaseItem {
  id: number;
  productId: number;
  skuId: number;
  productName?: string | null;
  productImageUrl?: string | null;
  skuLabel?: string | null;
  originalPrice?: number | null;
  campaignPrice: number;
  stock: number;
  soldCount: number;
}

export interface ReleaseCampaign {
  id: number;
  name: string;
  status: number;
  startTime: ApiDateValue;
  endTime: ApiDateValue;
  perUserLimit: number;
  items: ReleaseItem[];
}

export interface ReleaseCreateInput {
  name: string;
  startTime: string;
  endTime: string;
  perUserLimit: number;
  items: Array<{ productId: number; skuId: number; campaignPrice: number; stock: number }>;
}

export interface ReleasePurchaseResult {
  purchaseId: number;
  campaignId: number;
  itemId: number;
  quantity: number;
  unitPrice: number;
  orderId?: number;
  orderNo?: string | null;
  orderStatus?: number;
}

export interface AuctionSession {
  id: number;
  name: string;
  productId: number;
  skuId: number;
  productName?: string | null;
  productImageUrl?: string | null;
  skuLabel?: string | null;
  originalPrice?: number | null;
  status: number;
  startingPrice: number;
  minIncrement: number;
  startTime: ApiDateValue;
  endTime: ApiDateValue;
  currentAmount?: number | null;
  currentBidderUserId?: number | null;
  bidCount?: number | null;
  winnerUserId?: number | null;
  winningBidId?: number | null;
  settlementFailedTime?: ApiDateValue | null;
  settlementFailureReason?: string | null;
}

export interface AuctionBid {
  id: number;
  amount: number;
  bidderLabel: string;
  mine: boolean;
  leading: boolean;
  createTime?: ApiDateValue;
}

export interface AuctionCreateInput {
  name: string;
  productId: number;
  skuId: number;
  startingPrice: number;
  minIncrement: number;
  startTime: string;
  endTime: string;
}

export interface MerchantOrder {
  id: number;
  orderNo: string;
  memberUserId: number;
  buyerMobile?: string | null;
  buyerNickname?: string | null;
  buyerEmail?: string | null;
  merchantId?: number | null;
  merchantName?: string | null;
  storeId?: number | null;
  storeName?: string | null;
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
  merchantName?: string | null;
  storeId: number;
  storeName?: string | null;
  categoryId: number;
  categoryName?: string | null;
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

export interface ProductOperationLog {
  id: number;
  productId: number;
  operatorUserId?: number | null;
  operatorType: number;
  action: string;
  fromAuditStatus?: number | null;
  toAuditStatus?: number | null;
  fromSaleStatus?: number | null;
  toSaleStatus?: number | null;
  remark?: string | null;
  createTime?: ApiDateValue;
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
  dateFrom?: string;
  dateTo?: string;
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
  postCount?: number;
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

export interface SearchPage<T> {
  results: T[];
  total: number;
}

export interface SearchProductDocument {
  id?: number | string;
  productId?: number | string;
  categoryId?: number | string | null;
  storeId?: number | string | null;
  storeName?: string | null;
  sellerType?: number | null;
  sellerUserId?: number | string | null;
  sellerName?: string | null;
  name?: string | null;
  subtitle?: string | null;
  description?: string | null;
  mainImageUrl?: string | null;
  minPrice?: number | null;
  minMarketPrice?: number | null;
  totalStock?: number | null;
  available?: boolean | null;
  visible?: boolean;
}

export interface SearchPostDocument {
  id?: number | string;
  postId?: number | string;
  authorUserId?: number | string | null;
  authorNickname?: string | null;
  authorAvatar?: string | null;
  title?: string | null;
  content?: string | null;
  mediaUrls?: string[] | null;
  status?: number | null;
  likeCount?: number | null;
  favoriteCount?: number | null;
  commentCount?: number | null;
  productIds?: (number | string)[] | null;
  topics?: CommunityTopic[] | null;
  createTime?: ApiDateValue;
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
  postTitle?: string | null;
  postContent?: string | null;
  postMediaUrls?: string[] | null;
  postAuthorUserId?: number | null;
  postAuthorNickname?: string | null;
  reporterUserId: number;
  reporterNickname?: string | null;
  reason: string;
  status: number;
  reviewerUserId?: number | null;
  reviewRemark?: string | null;
  reviewTime?: ApiDateValue;
  createTime?: ApiDateValue;
}
