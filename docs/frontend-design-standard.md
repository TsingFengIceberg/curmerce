# Curmerce Frontend Design Standard

## Purpose

Curmerce uses one product language with three role-aware surface modes. The
local references under the ignored `design-references/` directory are design
analysis inputs, not production components or official brand assets.

## Surface Modes

### Consumer and Community

- Xiaohongshu-inspired, content-first and image-led.
- Use Curmerce red (`#FF2442`) for one clear focal action, active reactions,
  and content emphasis.
- Prefer compact feed cards, image grids, pill controls, low-noise surfaces,
  and mobile bottom sheets for secondary actions.

### Merchant Workspace

- Apple-inspired operational restraint, not an Apple marketing layout.
- Use white and light-gray surfaces with blue (`#0066CC`) for ordinary actions.
- Prioritize ownership context, filters, tables, SKU details, status labels,
  shipment actions, and safe batch workflows.

### Administrator Workspace

- Neutral graphite workspace chrome with white and gray content surfaces.
- Use black/graphite for navigation and structure; keep action color restrained.
- Use semantic colors for success, warning, information, and destructive
  operations. Permission importance is communicated by scope, audit context,
  and confirmation, not by making the whole page black.

## Shared Rules

- Use system Chinese fonts and zero letter spacing.
- Use the 4/8/12/16/24/32 spacing rhythm.
- Use 8/12/16px surface radii and pill shapes only for clear actions, tags,
  filters, and compact controls.
- Default to flat surfaces and very light hover elevation; avoid heavy or
  colored shadows.
- Provide visible focus, disabled, loading, empty, and error states.
- Use Lucide icons for icon-only actions with accessible labels or tooltips.
- Keep interactive targets approximately 44px or larger on touch layouts.
- Do not copy Apple or Xiaohongshu logos, wordmarks, proprietary fonts, or
  unlicensed imagery.

## Implementation Contract

The source of truth for Curmerce implementation is the token layer in
`curmerce-web/src/app/curmerce-design.css`. Pages should consume shared
components and role-scoped variables instead of embedding third-party hex
values. Preserve existing API behavior and access boundaries while changing
presentation.

## Verification

Every visual migration should check desktop and mobile viewports, keyboard
focus, route-level permissions, async states, text overflow, and hydration.
Run `npm run typecheck`, `npm run build`, relevant component tests, and the
applicable Playwright checks before submitting the change.

## 中文说明

Curmerce 使用一套统一产品语言，并根据角色切换三种界面模式。被忽略的
`design-references/` 目录只是设计参考，不是生产组件，也不代表官方品牌资产。

- 用户与社区端：小红书式内容优先、图片优先，使用 Curmerce 红色作为主要行动强调。
- 商家工作台：Apple-inspired 的克制和清晰，使用白灰表面与蓝色业务操作色，突出商品、SKU、订单和发货。
- 管理员工作台：石墨色工作区边界和白灰内容区，使用语义色表达成功、警告、信息和危险操作。

三种模式共享字体、间距、圆角、按钮状态、焦点态、加载态、空态和错误态。管理员的高权限通过权限范围、审计信息和二次确认表达，不依赖整页黑色或单一颜色。
