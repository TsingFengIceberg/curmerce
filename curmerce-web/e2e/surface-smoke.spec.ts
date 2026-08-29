import { expect, test } from "@playwright/test";

test.describe("Curmerce surface smoke", () => {
  test("consumer surface exposes discovery and commerce entry points", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("heading", { name: "从真实兴趣出发，发现值得带回家的东西。" })).toBeVisible();
    await expect(page.getByRole("navigation", { name: "主导航" })).toBeVisible();
    await expect(page.getByRole("link", { name: /商城/ }).first()).toBeVisible();
  });

  test("merchant login is reachable without a management token", async ({ page }) => {
    await page.goto("/merchant/login");
    await expect(page.getByRole("heading", { name: "进入商家工作台" })).toBeVisible();
    await expect(page.getByLabel("后台用户名")).toBeVisible();
    await expect(page.getByText("正在验证商家身份…")).toHaveCount(0);
  });

  test("admin route explains the role entry point", async ({ page }) => {
    await page.goto("/admin");
    await expect(page).toHaveURL(/\/merchant\/login\?[^#]*role=admin/);
    await expect(page.getByRole("tab", { name: "管理员登录" })).toHaveAttribute("aria-selected", "true");
  });

  test("mobile consumer navigation stays available", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/community");
    const nav = page.getByRole("navigation", { name: "移动端快捷导航" });
    await expect(nav).toBeVisible();
    await expect(nav.getByRole("link", { name: "发现" })).toBeVisible();
    await expect(nav.getByRole("link", { name: "购物车" })).toBeVisible();
  });
});
