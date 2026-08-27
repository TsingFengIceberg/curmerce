# 本地基础演示

## 服务端

当前本地演示使用第一次服务拆分后的混合拓扑，浏览器和前端只访问 Gateway：

```text
Gateway :48082
├── Core :48080
├── Community :48083
└── Agent :48084
        |
    Nacos :8848
```

先按 [`../deploy/cloud/README.md`](../deploy/cloud/README.md) 构建并启动 Nacos、Core、Community、Agent 和 Gateway。服务使用 `/home/wugang/.config/curmerce-services/credentials.env`，不要把文件内容复制到终端记录或仓库。初始化全新数据库时，将 `foundation-seed.sql` 中的 `__CURMERCE_FILE_BASE_URL__` 替换为 Gateway 回环地址 `http://127.0.0.1:48082`。如果端口冲突，必须同步修改服务注册、Gateway 路由、文件地址和前端 API 地址；不要终止来源不明的其他用户进程。

## 前端

```bash
cd curmerce-web
export NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:48082
npm run dev -- --hostname 127.0.0.1 --port 3003
```

打开 `http://127.0.0.1:3003`。前端与服务端都只绑定本机；服务器没有开放公网 HTTP 端口时，使用 SSH 隧道。SSH 命令保持单行：

```bash
ssh -p 2002 -L 3003:127.0.0.1:3003 -L 48082:127.0.0.1:48082 wugang@47.99.117.47
```

## 演示数据顺序

先使用 `super_admin` 平台管理员账号完成商家审核；审核时填写的店主账号和密码会生成独立的 `merchant_owner` 账号，再使用这个商家账号完成下列操作。两类账号共用后台登录入口，但不能互相替代：管理员不能直接调用商家自助接口，商家不能进入平台审核接口。

1. 创建店铺、平台分类和一个有库存的普通商品。
2. 提交商品审核，管理员通过后上架。
3. 使用普通用户创建收货地址，将商品加入购物车。
4. 创建普通订单并记录订单号；再创建一个限时发售活动和一个拍卖场次。
5. 使用第二个普通用户购买限时发售商品、参与拍卖并记录活动 ID、Item ID、Session ID。
6. 使用模拟支付接口推进订单，再分别完成商家发货和买家确认收货。
7. 对一笔已支付未发货订单发起基础退款，执行审核和模拟退款回调。
8. 创建一篇关联普通商品的社区帖子，从帖子详情进入商品详情。

可复用的请求体模板见 `script/db/demo/basic-fixtures.json`。其中所有 ID、Token 和幂等键都必须替换为本次运行实际值。

模板使用稳定的中文商家、商品和社区内容，并引用 `curmerce-web/public/demo/` 下的同源静态图片。`/demo/camera.png`、`/demo/coffee.png` 和 `/demo/camping.png` 不依赖后端文件端口或外部图片服务，适合截图和重复验收。素材来自 Google Noto Emoji，具体来源、校验值和许可证见该目录的 `NOTICE.md` 与 `NOTO-EMOJI-LICENSE`。

## 验证命令

```bash
mvn clean test
mvn -DskipTests package
cd curmerce-web && npm run build
```

运行验收、故障隔离和恢复发现命令见 [`../deploy/cloud/README.md`](../deploy/cloud/README.md)。所有 `curl` 命令都应设置超时，避免服务异常时验收脚本无限等待。

数据库迁移按 `script/db/README.md` 和文件名顺序执行。17、18、19 号迁移可以重复执行；19 号迁移为拍卖胜者支付超时增加结算失败状态和失败元数据。回滚脚本只用于一次性本地数据库，不要在包含重要演示数据的库上执行。
