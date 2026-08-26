import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page, type TestInfo } from "@playwright/test";

const emptyPage = { list: [], total: 0 };
const merchantProduct = {
  id: 21,
  merchantId: 3,
  merchantName: "山屿商家",
  storeId: 4,
  storeName: "山屿商店",
  categoryId: 5,
  categoryName: "生活器物",
  code: "summer_cup",
  name: "夏日玻璃杯",
  subtitle: "手工吹制",
  mainImageUrl: null,
  imageUrls: [],
  description: "清透耐热玻璃杯",
  auditStatus: 0,
  saleStatus: 0,
  sort: 0,
  createTime: "2026-08-26T08:00:00",
  updateTime: "2026-08-26T09:00:00",
  skus: [{ id: 31, productId: 21, code: "cup_clear", specificationValues: [{ name: "颜色", value: "透明" }], price: 12900, stock: 8, status: 0, sort: 0 }],
};

async function mockApi(page: Page, roles: string[] = []) {
  await page.route("http://127.0.0.1:48080/**", async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    let data: unknown = emptyPage;

    if (pathname.endsWith("/system/auth/get-permission-info")) {
      data = { user: { id: 1, nickname: "验收账号" }, roles, permissions: [] };
    } else if (pathname.endsWith("/commerce/catalog/category-tree")) {
      data = [];
    } else if (pathname.endsWith("/member/user/get")) {
      data = { id: 7, nickname: "验收用户", mobile: "13800000000" };
    } else if (pathname.endsWith("/member/address/list")) {
      data = [];
    }

    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({ code: 0, data, msg: "" }),
    });
  });
}

async function expectNoHorizontalOverflow(page: Page) {
  const dimensions = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
  }));
  expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth + 1);
}

async function capture(page: Page, testInfo: TestInfo, name: string) {
  await page.screenshot({ path: testInfo.outputPath(name), fullPage: true, animations: "disabled" });
}

test("desktop discovery has a focused public navigation and no serious accessibility violations", async ({ page }, testInfo) => {
  await mockApi(page);
  await page.goto("/");

  await expect(page.getByRole("heading", { level: 1, name: /从真实兴趣出发/ })).toBeVisible();
  await expect(page.getByRole("heading", { level: 2, name: "推荐商品" })).toBeVisible();
  await expect(page.getByRole("heading", { level: 2, name: "个人闲置" })).toBeVisible();
  await expect(page.getByRole("heading", { level: 2, name: "限时与稀缺" })).toBeVisible();
  const primaryNavigation = page.getByRole("navigation", { name: "主导航" });
  await expect(primaryNavigation.getByRole("link")).toHaveCount(4);
  await expect(primaryNavigation.getByRole("link", { name: "发现" })).toBeVisible();
  await expect(primaryNavigation.getByRole("link", { name: "商城" })).toBeVisible();
  await expect(page.getByText(/Token|租户请求头|当前前端进度/)).toHaveCount(0);
  await expectNoHorizontalOverflow(page);

  const accessibility = await new AxeBuilder({ page }).analyze();
  expect(accessibility.violations.filter((item) => ["serious", "critical"].includes(item.impact ?? ""))).toEqual([]);
  await capture(page, testInfo, "discovery-desktop.png");
});

test("mobile navigation stays collapsed until requested and fits the viewport", async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mockApi(page);
  await page.goto("/");

  const menuButton = page.getByRole("button", { name: "打开导航" });
  await expect(menuButton).toBeVisible();
  await expect(page.getByRole("navigation", { name: "主导航" })).not.toBeVisible();
  await menuButton.click();
  await expect(page.getByRole("button", { name: "关闭导航" })).toBeVisible();
  await expect(page.getByRole("navigation", { name: "主导航" })).toBeVisible();
  await expect(page.getByRole("link", { name: /工作台/ })).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await capture(page, testInfo, "discovery-mobile-navigation.png");
});

test("admin console exposes a stable sidebar, active state, breadcrumb and dashboard", async ({ page }, testInfo) => {
  await page.addInitScript(() => localStorage.setItem("curmerce.admin-access-token", "admin-test-token"));
  await mockApi(page, ["super_admin"]);
  await page.goto("/admin");

  const navigation = page.getByRole("navigation", { name: "平台管理导航" });
  await expect(navigation).toBeVisible();
  await expect(navigation.getByRole("link", { name: "工作台" })).toHaveClass(/workspace-nav__item--active/);
  await expect(page.getByText("平台管理").first()).toBeVisible();
  await expect(page.getByRole("heading", { level: 1, name: "平台工作台" })).toBeVisible();
  await expect(page.getByText("待审核商家")).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await capture(page, testInfo, "admin-dashboard-desktop.png");
});

test("merchant credentials cannot mount platform administration pages", async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem("curmerce.admin-access-token", "merchant-test-token"));
  await mockApi(page, ["merchant_owner"]);
  let merchantAdminPageRequests = 0;
  page.on("request", (request) => {
    if (new URL(request.url()).pathname.endsWith("/commerce/merchant/page")) merchantAdminPageRequests += 1;
  });

  await page.goto("/admin");

  await expect(page).toHaveURL(/\/merchant$/);
  await expect(page.getByRole("heading", { level: 1, name: "经营概览" })).toBeVisible();
  await expect(page.getByRole("heading", { level: 2, name: "近期订单" })).toBeVisible();
  expect(merchantAdminPageRequests).toBe(0);
});

test("personal seller workspace remains usable on a phone-sized viewport", async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.addInitScript(() => localStorage.setItem("curmerce.access-token", "buyer-test-token"));
  await mockApi(page);
  await page.goto("/personal");

  const navigation = page.getByRole("navigation", { name: "个人卖家中心导航" });
  await expect(navigation).toBeVisible();
  await expect(navigation.getByRole("link", { name: "卖家概览" })).toHaveClass(/workspace-nav__item--active/);
  await expect(page.getByRole("heading", { level: 1, name: "卖家概览" })).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await capture(page, testInfo, "personal-dashboard-mobile.png");
});

test("buyer account pages share a stable workspace navigation", async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.addInitScript(() => localStorage.setItem("curmerce.access-token", "buyer-test-token"));
  await mockApi(page);
  await page.goto("/account");

  const navigation = page.getByRole("navigation", { name: "我的 Curmerce导航" });
  await expect(navigation).toBeVisible();
  await expect(navigation.getByRole("link", { name: "我的首页" })).toHaveClass(/workspace-nav__item--active/);
  await expect(page.getByRole("heading", { level: 1, name: "我的" })).toBeVisible();
  await navigation.getByRole("link", { name: "买入订单" }).click();
  await expect(page).toHaveURL(/\/orders$/);
  await expect(page.getByText("我的 Curmerce").first()).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await capture(page, testInfo, "buyer-orders-mobile.png");
});

test("buyer logout preserves an independent administration session", async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem("curmerce.access-token", "buyer-test-token");
    localStorage.setItem("curmerce.admin-access-token", "admin-test-token");
  });
  await mockApi(page, ["super_admin"]);
  await page.goto("/account");

  await page.getByRole("button", { name: "退出用户账号" }).click();
  await expect(page).toHaveURL(/\/login$/);
  const sessions = await page.evaluate(() => ({
    buyer: localStorage.getItem("curmerce.access-token"),
    admin: localStorage.getItem("curmerce.admin-access-token"),
  }));
  expect(sessions).toEqual({ buyer: null, admin: "admin-test-token" });
});

test("buyer can review and remove a persisted product favorite", async ({ page }, testInfo) => {
  await page.addInitScript(() => localStorage.setItem("curmerce.access-token", "buyer-test-token"));
  await mockApi(page);
  let favorite = true;
  await page.route("http://127.0.0.1:48080/app-api/commerce/product-favorite/**", async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    if (pathname.endsWith("/set")) favorite = false;
    const data = pathname.endsWith("/page")
      ? favorite ? { list: [{ id: 1, productId: 21, product: { id: 21, categoryId: 3, storeId: 4, storeName: "山屿商店", name: "收藏测试商品", mainImageUrl: null, minPrice: 12900, totalStock: 5, available: true } }], total: 1 } : emptyPage
      : pathname.endsWith("/status") ? favorite : true;
    await route.fulfill({ contentType: "application/json", body: JSON.stringify({ code: 0, data, msg: "" }) });
  });

  await page.goto("/account/product-favorites");

  const navigation = page.getByRole("navigation", { name: "我的 Curmerce导航" });
  await expect(navigation.getByRole("link", { name: "商品收藏" })).toHaveClass(/workspace-nav__item--active/);
  await expect(page.getByRole("link", { name: "收藏测试商品" })).toBeVisible();
  await page.getByRole("button", { name: "取消收藏商品 收藏测试商品" }).click();
  await expect(page.getByText("还没有收藏商品")).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await capture(page, testInfo, "buyer-product-favorites.png");
});

test("merchant product history and lifecycle confirmation remain accessible when open", async ({ page }, testInfo) => {
  await page.addInitScript(() => localStorage.setItem("curmerce.admin-access-token", "merchant-test-token"));
  await mockApi(page, ["merchant_owner"]);
  await page.route("http://127.0.0.1:48080/admin-api/commerce/product/**", async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    const data = pathname.endsWith("/page-own")
      ? { list: [merchantProduct], total: 1 }
      : pathname.endsWith("/operation-log-own")
        ? { list: [
          { id: 2, productId: 21, operatorUserId: 8, operatorType: 2, action: "SUBMIT_REVIEW", fromAuditStatus: 0, toAuditStatus: 1, fromSaleStatus: 0, toSaleStatus: 0, remark: "提交平台审核", createTime: "2026-08-26T09:00:00" },
          { id: 1, productId: 21, operatorUserId: 8, operatorType: 2, action: "CREATE", toAuditStatus: 0, toSaleStatus: 0, remark: "创建商品草稿", createTime: "2026-08-26T08:00:00" },
        ], total: 2 }
        : true;
    await route.fulfill({ contentType: "application/json", body: JSON.stringify({ code: 0, data, msg: "" }) });
  });

  await page.goto("/merchant/products");
  await page.getByRole("button", { name: "查看 夏日玻璃杯 操作记录" }).click();
  const historyDrawer = page.getByRole("dialog", { name: "商品操作记录" });
  await expect(historyDrawer).toBeVisible();
  await expect(historyDrawer.getByText("提交审核")).toBeVisible();
  await expect(historyDrawer.getByText("平台管理员")).toHaveCount(0);
  let accessibility = await new AxeBuilder({ page }).include("dialog[open]").analyze();
  expect(accessibility.violations.filter((item) => ["serious", "critical"].includes(item.impact ?? ""))).toEqual([]);
  await historyDrawer.getByRole("button", { name: "关闭" }).click();

  await page.getByRole("button", { name: "提交 夏日玻璃杯 审核" }).click();
  const confirm = page.getByRole("dialog", { name: "提交商品审核" });
  await expect(confirm).toBeVisible();
  accessibility = await new AxeBuilder({ page }).include("dialog[open]").analyze();
  expect(accessibility.violations.filter((item) => ["serious", "critical"].includes(item.impact ?? ""))).toEqual([]);
  await expectNoHorizontalOverflow(page);
  await capture(page, testInfo, "merchant-product-confirm-dialog.png");
});

test("merchant product editor protects local changes during internal navigation", async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem("curmerce.admin-access-token", "merchant-test-token"));
  await mockApi(page, ["merchant_owner"]);
  await page.route("http://127.0.0.1:48080/admin-api/commerce/store/get-own", async (route) => {
    await route.fulfill({ contentType: "application/json", body: JSON.stringify({ code: 0, data: { id: 4, code: "mountain_store", name: "山屿商店", contactName: "店主", contactMobile: "13800000000" }, msg: "" }) });
  });

  await page.goto("/merchant/products/new");
  await page.getByLabel("商品名称").fill("尚未完成的商品");
  await page.getByRole("navigation", { name: "商家工作台导航" }).getByRole("link", { name: "订单履约" }).click();

  const dialog = page.getByRole("dialog", { name: "离开商品编辑器？" });
  await expect(dialog).toBeVisible();
  await dialog.getByRole("button", { name: "离开页面" }).click();
  await expect(page).toHaveURL(/\/merchant\/orders$/);
});

test("community cards support direct reactions with immediate feedback", async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem("curmerce.access-token", "buyer-test-token"));
  await mockApi(page);
  const reactions: Array<Record<string, unknown>> = [];
  const post = {
    id: 81,
    authorUserId: 7,
    authorNickname: "林间",
    title: "露营杯使用记录",
    content: "实际使用一周后的感受。",
    mediaUrls: [],
    status: 1,
    likeCount: 3,
    favoriteCount: 2,
    commentCount: 4,
    liked: false,
    favorited: false,
    followingAuthor: false,
    topics: [],
    products: [],
    createTime: "2026-08-26T08:00:00",
  };
  await page.route("http://127.0.0.1:48080/app-api/community/**", async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    let data: unknown = true;
    if (pathname.endsWith("/post/page")) data = { list: [post], total: 1 };
    else if (pathname.endsWith("/post/popular-topics")) data = [];
    else if (pathname.endsWith("/post/reaction")) reactions.push(route.request().postDataJSON());
    await route.fulfill({ contentType: "application/json", body: JSON.stringify({ code: 0, data, msg: "" }) });
  });

  await page.goto("/community");
  await page.getByRole("button", { name: "点赞" }).click();
  await expect(page.getByRole("button", { name: "取消点赞" })).toHaveAttribute("aria-pressed", "true");
  await page.getByRole("button", { name: "收藏" }).click();
  await expect(page.getByRole("button", { name: "取消收藏" })).toHaveAttribute("aria-pressed", "true");
  expect(reactions).toEqual([{ postId: 81, type: 1, active: true }, { postId: 81, type: 2, active: true }]);
});

test("merchant release editor supports create edit copy and paged product search", async ({ page }, testInfo) => {
  await page.addInitScript(() => localStorage.setItem("curmerce.admin-access-token", "merchant-test-token"));
  await mockApi(page, ["merchant_owner"]);
  const approvedProduct = { ...merchantProduct, auditStatus: 2, saleStatus: 1 };
  const campaign = {
    id: 41,
    name: "夏日限时活动",
    status: 0,
    startTime: "2026-09-01T10:00:00",
    endTime: "2026-09-02T10:00:00",
    perUserLimit: 2,
    items: [{ id: 51, productId: 21, skuId: 31, productName: approvedProduct.name, skuLabel: "透明", originalPrice: 12900, campaignPrice: 9900, stock: 6, soldCount: 0 }],
  };
  await page.route("http://127.0.0.1:48080/admin-api/commerce/release/**", async (route) => {
    const url = new URL(route.request().url());
    const status = url.searchParams.get("status");
    const data = url.pathname.endsWith("/page")
      ? status === "0" ? { list: [], total: 1 } : status === "20" ? emptyPage : { list: [campaign], total: 1 }
      : true;
    await route.fulfill({ contentType: "application/json", body: JSON.stringify({ code: 0, data, msg: "" }) });
  });
  await page.route("http://127.0.0.1:48080/admin-api/commerce/product/**", async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    const data = pathname.endsWith("/page-own") ? { list: [approvedProduct], total: 1 } : approvedProduct;
    await route.fulfill({ contentType: "application/json", body: JSON.stringify({ code: 0, data, msg: "" }) });
  });

  await page.goto("/merchant/releases");
  await page.getByRole("button", { name: "编辑 夏日限时活动" }).click();
  let editor = page.getByRole("dialog", { name: "编辑活动草稿" });
  await expect(editor.getByLabel("活动名称")).toHaveValue("夏日限时活动");
  await expect(editor.getByText("已选择").first()).toBeVisible();
  let accessibility = await new AxeBuilder({ page }).include("dialog[open]").analyze();
  expect(accessibility.violations.filter((item) => ["serious", "critical"].includes(item.impact ?? ""))).toEqual([]);
  await editor.getByRole("button", { name: "关闭" }).click();

  await page.getByRole("button", { name: "复制 夏日限时活动" }).click();
  editor = page.getByRole("dialog", { name: "复制限时发售" });
  await expect(editor.getByLabel("活动名称")).toHaveValue("夏日限时活动 副本");
  await editor.getByRole("button", { name: "关闭" }).click();

  await page.getByRole("button", { name: "创建活动", exact: true }).click();
  editor = page.getByRole("dialog", { name: "创建限时发售" });
  await editor.getByRole("search", { name: "搜索可选商品" }).getByLabel("商品名称").fill("玻璃杯");
  await editor.getByRole("button", { name: "搜索", exact: true }).click();
  await editor.getByRole("button", { name: /夏日玻璃杯/ }).click();
  await expect(editor.getByText("已选择").first()).toBeVisible();
  await editor.getByLabel("活动名称").fill("新建限时活动");
  await expectNoHorizontalOverflow(page);
  await capture(page, testInfo, "merchant-release-product-picker.png");
  await editor.getByRole("button", { name: "保存草稿" }).click();
  await expect(page.getByText("限时发售草稿已创建")).toBeVisible();
});

test("merchant auction editor supports create edit copy and product search", async ({ page }, testInfo) => {
  await page.addInitScript(() => localStorage.setItem("curmerce.admin-access-token", "merchant-test-token"));
  await mockApi(page, ["merchant_owner"]);
  const approvedProduct = { ...merchantProduct, auditStatus: 2, saleStatus: 1 };
  const session = {
    id: 61,
    name: "玻璃杯专场",
    productId: 21,
    skuId: 31,
    productName: approvedProduct.name,
    productImageUrl: null,
    skuLabel: "透明",
    originalPrice: 12900,
    status: 0,
    startingPrice: 6900,
    minIncrement: 500,
    startTime: "2026-09-01T10:00:00",
    endTime: "2026-09-02T10:00:00",
    currentAmount: null,
    bidCount: 0,
  };
  await page.route("http://127.0.0.1:48080/admin-api/commerce/auction/**", async (route) => {
    const url = new URL(route.request().url());
    const status = url.searchParams.get("status");
    const data = url.pathname.endsWith("/page")
      ? status === "0" ? { list: [], total: 1 } : status === "20" ? emptyPage : { list: [session], total: 1 }
      : true;
    await route.fulfill({ contentType: "application/json", body: JSON.stringify({ code: 0, data, msg: "" }) });
  });
  await page.route("http://127.0.0.1:48080/admin-api/commerce/product/**", async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    const data = pathname.endsWith("/page-own") ? { list: [approvedProduct], total: 1 } : approvedProduct;
    await route.fulfill({ contentType: "application/json", body: JSON.stringify({ code: 0, data, msg: "" }) });
  });

  await page.goto("/merchant/auctions");
  await page.getByRole("button", { name: "编辑 玻璃杯专场" }).click();
  let editor = page.getByRole("dialog", { name: "编辑拍卖草稿" });
  await expect(editor.getByLabel("拍卖名称")).toHaveValue("玻璃杯专场");
  await expect(editor.getByText("已选择").first()).toBeVisible();
  await editor.getByRole("button", { name: "关闭" }).click();

  await page.getByRole("button", { name: "复制 玻璃杯专场" }).click();
  editor = page.getByRole("dialog", { name: "复制拍卖场次" });
  await expect(editor.getByLabel("拍卖名称")).toHaveValue("玻璃杯专场 副本");
  await editor.getByRole("button", { name: "关闭" }).click();

  await page.getByRole("button", { name: "创建拍卖", exact: true }).click();
  editor = page.getByRole("dialog", { name: "创建拍卖" });
  await editor.getByRole("search", { name: "搜索可选商品" }).getByLabel("商品名称").fill("玻璃杯");
  await editor.getByRole("button", { name: "搜索", exact: true }).click();
  await editor.getByRole("button", { name: /夏日玻璃杯/ }).click();
  await editor.getByLabel("拍卖名称").fill("新建拍卖专场");
  const accessibility = await new AxeBuilder({ page }).include("dialog[open]").analyze();
  expect(accessibility.violations.filter((item) => ["serious", "critical"].includes(item.impact ?? ""))).toEqual([]);
  await capture(page, testInfo, "merchant-auction-product-picker.png");
  await editor.getByRole("button", { name: "保存草稿" }).click();
  await expect(page.getByText("拍卖草稿已创建")).toBeVisible();
});

test("admin can drag sibling categories and open an accessible category editor", async ({ page }, testInfo) => {
  await page.addInitScript(() => localStorage.setItem("curmerce.admin-access-token", "admin-test-token"));
  await mockApi(page, ["super_admin"]);
  const tree = [
    { id: 71, parentId: null, code: "camera", name: "摄影器材", imageUrl: null, sort: 0, status: 0, children: [] },
    { id: 72, parentId: null, code: "outdoor", name: "户外装备", imageUrl: null, sort: 10, status: 0, children: [] },
  ];
  const updates: Array<Record<string, unknown>> = [];
  await page.route("http://127.0.0.1:48080/admin-api/commerce/product-category/**", async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    let data: unknown = tree;
    if (pathname.endsWith("/update")) {
      updates.push(route.request().postDataJSON());
      data = true;
    }
    await route.fulfill({ contentType: "application/json", body: JSON.stringify({ code: 0, data, msg: "" }) });
  });

  await page.goto("/admin/categories");
  const source = page.locator(".category-tree-row").filter({ hasText: "户外装备" });
  const target = page.locator(".category-tree-row").filter({ hasText: "摄影器材" });
  await source.dragTo(target);
  await expect(page.getByText("同级分类顺序已更新")).toBeVisible();
  expect(updates).toHaveLength(2);
  expect(updates.find((item) => item.id === 72)?.sort).toBe(0);
  expect(updates.find((item) => item.id === 71)?.sort).toBe(10);

  await page.getByRole("button", { name: "创建分类", exact: true }).click();
  const editor = page.getByRole("dialog", { name: "创建顶级分类" });
  await expect(editor).toBeVisible();
  const accessibility = await new AxeBuilder({ page }).include("dialog[open]").analyze();
  expect(accessibility.violations.filter((item) => ["serious", "critical"].includes(item.impact ?? ""))).toEqual([]);
  await capture(page, testInfo, "admin-category-editor.png");
});

test("buyer can crop and upload an avatar", async ({ page }, testInfo) => {
  await page.addInitScript(() => localStorage.setItem("curmerce.access-token", "buyer-test-token"));
  await mockApi(page);
  await page.route("http://127.0.0.1:48080/app-api/member/profile/**", async (route) => {
    const data = new URL(route.request().url()).pathname.endsWith("/get")
      ? { id: 7, mobile: "13800000000", nickname: "验收用户", avatar: null, email: "", sex: 0 }
      : true;
    await route.fulfill({ contentType: "application/json", body: JSON.stringify({ code: 0, data, msg: "" }) });
  });
  await page.route("http://127.0.0.1:48080/app-api/infra/file/upload", async (route) => {
    await route.fulfill({ contentType: "application/json", body: JSON.stringify({ code: 0, data: "/uploads/avatar-test.jpg", msg: "" }) });
  });

  await page.goto("/profile");
  const png = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAFElEQVR42mP8z8AARAwMjIwgAQAQOwMBv9M6WQAAAABJRU5ErkJggg==", "base64");
  await page.locator('input[type="file"][accept*="image/jpeg"]').setInputFiles({ name: "avatar.png", mimeType: "image/png", buffer: png });
  const cropDialog = page.getByRole("dialog", { name: "裁剪头像" });
  await expect(cropDialog).toBeVisible();
  await expect.poll(() => cropDialog.getByAltText("头像裁剪预览").evaluate((image: HTMLImageElement) => image.naturalWidth)).toBeGreaterThan(0);
  await cropDialog.getByLabel("头像缩放").fill("1.5");
  const accessibility = await new AxeBuilder({ page }).include("dialog[open]").analyze();
  expect(accessibility.violations.filter((item) => ["serious", "critical"].includes(item.impact ?? ""))).toEqual([]);
  await cropDialog.getByRole("button", { name: "应用并上传" }).click();
  await expect(cropDialog).not.toBeVisible();
  await expect(page.getByAltText("验收用户的头像")).toHaveAttribute("src", /avatar-test\.jpg/);
  await capture(page, testInfo, "buyer-avatar-uploaded.png");
});
