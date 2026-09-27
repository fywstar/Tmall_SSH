# Proposal

## Why

管理后台的商品数据目前缺少"备注"信息：运营人员无法在商品上记录仅内部可见的补充说明（如供货、活动备注）。按 SDD 演练流程，在商品模块上以最小改动扩展一个可选的持久化字段。

## What Changes

- `Product` 实体新增持久化字段 `remark`（String，可空），随 Hibernate 自动映射到 `product` 表同名列。
- H2 建表脚本 `src/sql/tmall_ssh_h2.sql` 为 `product` 表补充 `remark varchar(255) DEFAULT NULL` 列（脚本为唯一 schema 来源，`hibernate.hbm2ddl.auto=none`）。
- 后台商品列表页 `listProduct.jsp`：列表新增"备注"列，新增产品表单新增备注输入框。
- 后台商品编辑页 `editProduct.jsp`：新增备注输入框（回显 `${product.remark}`）。
- 不修改任何 Action/Service/DAO 代码：OGNL 按 `product.remark` 属性路径自动绑定，`ProductAction.add/update` 现有逻辑透传；不新增端点。
- 前台（storefront）页面不展示备注字段。

假设（记录待评审）：本次范围仅限管理后台；备注为可选字段，不做非空校验、不限制输入格式；`sql/tmall_ssh.sql`（原始 MySQL dump）保持不动，MySQL 回退模式下由 `hibernate.hbm2ddl.auto=update` 自动补列。

## Capabilities

### New Capabilities

- `product-remark`: 商品备注字段的持久化与后台管理行为——实体字段映射、建表脚本列定义、新增/编辑表单录入、商品列表展示。

### Modified Capabilities

（无——项目当前不存在既有 specs。）

## Impact

- 代码：`src/com/caozhihu/tmall/pojo/Product.java`（新增字段 + getter/setter）；Action/Service/DAO 零改动。
- SQL：`src/sql/tmall_ssh_h2.sql`（新增列）；`web/WEB-INF/classes/sql/` 为构建产物，禁止手改。
- JSP：`web/admin/listProduct.jsp`、`web/admin/editProduct.jsp`。
- 运行时：schema 变更经重启后由 `dbInit` 脚本生效；85 条 product 种子 INSERT 为无列名 positional 形式，需保证列定义追加方式不破坏其列序（见 design）。
- 基线溯源：遵循 OpenWiki `workflows/admin-crud.md`（OGNL 表单绑定、无服务端校验）与 `operations/data-and-schema.md` §8（schema 变更规程、positional INSERT 约束）。
