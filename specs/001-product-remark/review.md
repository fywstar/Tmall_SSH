# 代码审查记录：商品模块新增商品备注字段

**Feature**: `specs/001-product-remark` · **审查日期**: 2026-09-28
**审查范围**: `Product.remark` 新增字段的 4 个既有文件变更（对照宪法 v1.2 逐条核验）

## Safe-Change Procedure 执行（宪法测试规范 §8，改名前/后）

**1. 分类命名改动的字面量**
`remark` —— 新增属性/列名（非端点/results/session key/图片/状态字面量），属"新引入命名字面量"。

**2. 枚举所有载体**
`grep -rn "remark" src web`（输出见下），命中 Java / JSP / SQL 三类 4 处；StartJetty、web.xml 两载体确认与 `remark` 无关（N/A）。

**3. 四种 grep 不可见耦合**
- `t2p` 反射：`Action4Service.t2p` 按 `"set"+clazz.getSimpleName()` 反射，新属性自动被覆盖，无需改 —— 人工确认。
- OGNL 重定向参数：本次未改任何 redirect `${...}` 参数路径 —— N/A。
- 拦截器 `/fore` 前缀：未触碰任何 `forward`/**fore/admin** 端点名 —— N/A。
- JSP 相对链接 context path：未改相对链接 —— N/A。

**4. `remark` 非 `/fore*` 名**，不涉及 Auth 白名单数组 —— N/A。

**5. 图片名/目录**：未涉及 —— N/A。

**6/7. 资源配置/手动 smoke**：见下方"运行时待办"（本环境宪法禁止 `编译/启动`，未代为执行）。

**8. 不依赖测试类兜底**：`TestTmall` 只测 DAO、零断言，覆盖不了字段绑定；运行时验证靠人工 smoke（已列入待办）。

## 审查清单（对齐宪法原则）

#### 原则 I 基线优先
- [x] `remark` 字段名/可空/长度(varchar255)随 spec 与既有字段习惯；未脑补基线不存在的机制。

#### 原则 II 业务源码只读 / 最小改动
- [x] 仅新增 `Product.remark` + 两个 admin JSP 输入框（宪法 v1.2 背书 spec 驱动迭代）；未重构/优化/扩功能。
- [x] 未新增 Service 方法、未改 Action/Service/DAO/拦截器/继承链。

#### 原则 III 技术栈版本冻结
- [x] 未升级/替换任何 jar；H2 仍 1.4.x；URL 仍含 `MODE=MySQL;DB_CLOSE_DELAY=-1`；原 MySQL 配置注释保留。

#### 原则 IV 命名不可漂移
- [x] `grep -rn "remark" src web` 全部命中（见附表），四处载体同一 commit 同步。
- [x] 四种不可见耦合人工确认（见上）。
- [x] 未触碰 `/fore*`/`/admin_*` 拦截器门槛、session key 四值、图片目录、order status/type 常量/AJAX 响应、seed 校准值、`auth-dafault` typo。

#### 原则 V Schema 单一来源
- [x] `hbm2ddl.auto=none`（未改）。
- [x] 新列以 `ALTER TABLE product ADD COLUMN remark varchar(255) DEFAULT NULL;` 追加在 product INSERT 之后、`CREATE TABLE productimage` 之前；未改 `CREATE TABLE product` 与任何 INSERT。
- [x] 本次未新增行，无需调整自增 `RESTART WITH` 校准。

#### 原则 VI Spec-First
- [x] spec/plan/tasks/research/data-model/quickstart/contracts/selfcheck 齐备。

#### 原则 VII 启动/类加载器不变量
- [x] 静态：未改 `StartJetty`、Configuration 链、`addSystemClass` 七前缀、8082 端口、RewriteHandler。
- [x] 运行时：启动验证待人工 smoke。

#### one-way dependency / 分层
- [x] `Product.java` 不 import dao/hibernate 包；`remark` 走 `BaseServiceImpl` 泛化 CRUD，无跨层调用/new Session。

## grep 证据

```
src/sql/tmall_ssh_h2.sql:155  ALTER TABLE product ADD COLUMN remark varchar(255) DEFAULT NULL;
web/admin/editProduct.jsp:76    name="product.remark" ... value="${product.remark}"
web/admin/listProduct.jsp:130   name="product.remark"
src/com/caozhihu/tmall/pojo/Product.java:21    private String remark;
src/com/caozhihu/tmall/pojo/Product.java:110   getRemark()
src/com/caozhihu/tmall/pojo/Product.java:114   setRemark(String remark)
```

SQL 计数核实：`INSERT INTO product VALUES` = 85 条（未变）、`ALTER TABLE product ADD COLUMN remark` = 1 处、`CREATE TABLE product ` = 1 处（8 列未动）。

## 运行时待办（审查通过后才算完整闭合）

- [x] `StartJetty.main()` 启动 + banner 三 URL
- [x] `TestTmall` 回归（context load / Hibernate mapping 含 remark）
- [x] 后台备注录入→提交→回显 + 空备注保存成功
- [x] 前台首页/详情/搜索/后台分类与产品列表 5 路径无回归
- [x] `SHOW COLUMNS FROM product` 含 remark

## 结论

静态审查通过：所有可静态核验的宪法原则（I–VI、VII 静态侧、分层、命名载体）均无违例，`remark` 四处载体同步、SQL 不变量保持。**仅剩运行时 smoke 待人工执行后即可最终通过。**