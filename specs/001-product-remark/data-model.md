# Data Model: 商品备注字段

> Phase 1 产物。仅记录本次变更的字段增量；其余实体/关联不在此重复（见宪法与基线）。

## Entity: Product

新增一个持久化属性，其余 9 个既有字段（id/name/subTitle/originalPrice/promotePrice/stock/cid/createDate + `@ManyToOne category`）零改动。

| 属性 | Java 类型 | 列 | SQL 类型 | 约束 | 可见性 | 说明 |
|---|---|---|---|---|---|---|
| `remark` | `String` | `remark` | `varchar(255)` | `DEFAULT NULL`，可空，非 `@Transient` | public getter/setter | 商品补充说明，自由文本 |

### 关系

- `remark` 是普通标量字段，不改变 `Product → Category`（`@ManyToOne`, `cid`）任何关系。
- 不引入新实体、不改变任何关联/Batch。

### Validation / 业务规则

- 可空；空值（NULL）合法，不参与任何校验、搜索、排序、分页、销量/评价统计（spec FR-003/FR-004）。
- 仅后台 add/update 表单录入；前台不改展示（spec Assumptions）。

### Schema 变更（单一来源 `src/sql/tmall_ssh_h2.sql`）

在 product 的全部 positional INSERT 之后、`CREATE TABLE productimage`（第 155 行）之前追加：

```sql
ALTER TABLE product ADD COLUMN remark varchar(255) DEFAULT NULL;
```

- 不改写 `CREATE TABLE product`，不改任何 INSERT（宪法原则 V）。
- 存量 85 条 seed 数据其余列值/行序逐字不变，各条 `remark=NULL`（spec SC-004）。
- 无需调整自增校准（`RESTART WITH`）——本变更不新增行。

### OGNL / 表单绑定

- 入参：`product.remark`（add/update 两个表单）。
- 回显：`${product.remark}`（edit 表单）。