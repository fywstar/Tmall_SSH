# Research: 商品模块新增商品备注字段

> Phase 0 产物。目标：将所有 spec/technical context 中的可决策点基于宪法与基线溯源到结论，消除全部 NEEDS CLARIFICATION。

## R1 — 字段类型 / 长度 / 可空性

- **Decision**: `String remark`（Java），SQL 列 `remark varchar(255) DEFAULT NULL`，可空。
- **Rationale**: 备注为自由文本的补充说明；`varchar(255)` 与既有 `name`/`subTitle` 的 255 长度一致，`DEFAULT NULL` 保证存量 85 条 seed 兼容。spec FR-003/FR-006 明确可空且命名 `remark`。
- **Alternatives considered**: `text`/`longtext` —— 超出现有字段长度习惯，且无长度上限进入数据库会更难管控，弃用；必填 —— 违背"可空、零侵入存量流程"。

## R2 — 列如何加入 Schema（宪法原则 V）

- **Decision**: 在 `src/sql/tmall_ssh_h2.sql` 的 product 全部 positional INSERT 之后、`CREATE TABLE productimage` 之前追加 `ALTER TABLE product ADD COLUMN remark varchar(255) DEFAULT NULL;`。
- **Rationale**: 宪法原则 V 强制 —— `CREATE TABLE product` 只有 8 列而 85 个 INSERT 各 8 个 positional 值，改 CREATE TABLE 会错位；`hibernate.hbm2ddl.auto=none`，schema 只由此脚本负责；脚本有 dbInit 每次执行，必须空库执行。
- **Alternatives considered**: 改 `CREATE TABLE product` —— 违宪法，弃；新增 `IF NOT EXISTS`/`DROP` —— 违基线"全量执行、无 IF NOT EXISTS、重启即回种"，弃。

## R3 — 字段如何绑定与持久化（Action/Service 层）

- **Decision**: `Product` 实体加普通持久化 `String remark` 属性（**非** `@Transient`）；表单 `name="product.remark"` → `Action4Pojo.product`（Struts 参数绑定）→ `ProductAction#update/add` 调 `productService.save/update`（继承 `BaseServiceImpl` 泛化 CRUD，反射推断 Product 实体）→ `DAOImpl(HibernateTemplate)` 持久化。回显 `${product.remark}`。
- **Rationale**: 普通属性，不新增 Service 方法、不改 Action 继承链、不新增端点，完全落在宪法架构 `Action → Service → DAO → 实体` 单向链内。`BaseServiceImpl`/`t2p` 反射按 `set+SimpleName` 拼 setter 已天然支持新属性。
- **Alternatives considered**: 新增专用 Service 方法 —— 违反"不扩功能"；在前台做展示逻辑 —— 超出范围，弃。

## R4 — 页面改动点（哪些 JSP）

- **Decision**: 同时改两处 —— `web/admin/listProduct.jsp` 的 `addForm`（`admin_product_add`"新增产品"）与 `web/admin/editProduct.jsp` 的 `editForm`（`admin_product_update`"编辑产品"）。各新增一行"商品备注"输入框。
- **Rationale**: "新增"与"编辑"是两条独立的表单（一个无 value 回显、一个回显 `${product.remark}`），需在 add/update 两个 Action 都覆盖，否则新增时无法录入、编辑时无法回显。两个 JSP 均为既有后台管理页，非前台。
- **Alternatives considered**: 只改 editProduct.jsp —— 漏掉新增链路，不满足 FR-001/FR-002；前置校验（`checkEmpty`）—— 备注可空，不加校验（与边 case 一致）。

## R5 — 是否新增单元测试

- **Decision**: 不新增单元测试。
- **Rationale**: 本次变更只新增一个实体字段，不新增任何 Service 公开方法、不改任何业务逻辑（宪法测试范围限定 Service 层公开接口契约）。新增字段走既有泛化 CRUD，无新契约可断言。验证以手动 smoke path 为准；既有 `TestTmall` 保持回归（context load 通过即证明字段可与 schema 对齐）。
- **Alternatives considered**: 为字段写 CRUD 测试 —— 等于复测既有泛化 save/update，且宪法禁止改业务源码、测试只可新增，意义有限；为 cover"字段持久化"而写 —— 需绕过 Service 直接碰 DAO，违宪法"单测对齐 Service 接口"，弃。

## 结论

所有决策点已闭合，无 NEEDS CLARIFICATION 残留；全部方案均在宪法冻结架构内部，零违规。