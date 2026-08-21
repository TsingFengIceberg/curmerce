# 本地基础演示

## 服务端

先加载本机私有环境文件，不要把文件内容复制到终端记录或仓库：

```bash
set -a
source /home/wugang/.config/curmerce-services/credentials.env
set +a
export CURMERCE_SERVER_PORT=48082
export CURMERCE_LOG_FILE=/tmp/curmerce-yudao-server-48082.log
java -jar yudao-server/target/yudao-server.jar --spring.profiles.active=local
```

如果 `48082` 已被占用，换用其他大于 `1024` 的本地端口，并同步设置前端 `NEXT_PUBLIC_API_BASE_URL`。不要终止其他用户已经启动的后端进程。

## 前端

```bash
cd curmerce-web
export NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:48082
npm run dev -- --port 3002
```

打开 `http://127.0.0.1:3002`。前端只绑定本机；服务器没有开放公网 HTTP 端口时，使用 SSH 隧道：

```bash
ssh -p 2002 -L 3002:127.0.0.1:3002 -L 48082:127.0.0.1:48082 user@47.99.117.47
```

## 演示数据顺序

先使用管理员账号完成商家审核，再使用商家账号完成下列操作：

1. 创建店铺、平台分类和一个有库存的普通商品。
2. 提交商品审核，管理员通过后上架。
3. 使用普通用户创建收货地址，将商品加入购物车。
4. 创建普通订单并记录订单号；再创建一个限时发售活动和一个拍卖场次。
5. 使用第二个普通用户购买限时发售商品、参与拍卖并记录活动 ID、Item ID、Session ID。
6. 使用模拟支付接口推进订单，再分别完成商家发货和买家确认收货。
7. 对一笔已支付未发货订单发起基础退款，执行审核和模拟退款回调。
8. 创建一篇关联普通商品的社区帖子，从帖子详情进入商品详情。

可复用的请求体模板见 `script/db/demo/basic-fixtures.json`。其中所有 ID、Token 和幂等键都必须替换为本次运行实际值。

## 验证命令

```bash
mvn -pl curmerce-module-commerce,curmerce-module-community -am \
  -Dtest='*Test' -Dsurefire.failIfNoSpecifiedTests=false test
cd curmerce-web && npm run build
```

数据库迁移按 `script/db/README.md` 和文件名顺序执行。18 号迁移可以重复执行；回滚脚本只用于一次性本地数据库，不要在包含重要演示数据的库上执行。
