# Design

## Context

技术约束来自 OpenWiki 基线（`operations/data-and-schema.md`、`workflows/admin-crud.md`）：

- `hibernate.hbm2ddl.auto=none`，schema 唯一来源是 `src/sql/tmall_ssh_h2.sql`，实体注解不建列也不校验映射——实体加字段而脚本不加列，只会推迟到首次查询时报 SQL 错误。
- 该脚本中 product 的 85 条种子 INSERT 全部为无列名 positional 形式，列序即加载契约；H2 对值个数不匹配的行直接拒绝，启动即失败。
- 后台表单字段是 OGNL 属性路径（`product.name`、`product.stock`……），`admin_product_add`/`admin_product_update` 把绑定的整个 `product` 对象交给 service 保存；除 `createDate` 补偿外无逐字段处理。
- 全后端无服务端校验，仅 `adminHeader.jsp` 的 jQuery 辅助函数按页面接线。

## Goals / Non-Goals

**Goals:**

- 以最小改动为商品增加可选备注：实体、建表脚本、两个后台 JSP。
- 零 Action/Service/DAO 改动，零新端点，零新依赖。
- 启动、种子数据、前台行为完全不受影响。

**Non-Goals:**

- 前台展示备注；备注检索、导出、审计。
- 引入服务端校验或修改既有校验体系。
- 触碰 `sql/tmall_ssh.sql`（原始 MySQL dump）与 `web/WEB-INF/classes/` 构建产物。
- 修复基线已记录的既有缺陷（如 `ProductAction.list` 计数混用、EL 原样输出）。

## Decisions

1. **实体字段采用隐式映射**：`Product` 新增 `private String remark;` + getter/setter，不加 `@Column`。与 `name`/`subTitle`/`stock` 等现有字段风格一致（仅 `id`/`cid` 用显式注解），Hibernate 5.3 隐式命名策略将字段名直映为列名 `remark`。
   - 备选：显式 `@Column(name="remark", length=255)` —— 信息与脚本 DDL 重复且偏离现有实体写法，弃。

2. **建表脚本用 ALTER 追加列，不改 CREATE TABLE、不动种子 INSERT**：在 `src/sql/tmall_ssh_h2.sql` 的 product 种子 INSERT 段（约 #L154）之后追加一条 `ALTER TABLE product ADD COLUMN remark varchar(255) DEFAULT NULL;`。脚本每次在全新内存库上执行，ALTER 随 `dbInit`（先于 `sf`，`depends-on` 保证）一并生效，存量行得 NULL，后续 Hibernate 写入含 `remark` 列无冲突。
   - 备选：在 `CREATE TABLE product` 中加列——则 85 条 positional INSERT 全部需要补第 12 个值，diff 面扩大 85 行且任一漏改都让启动失败；正是基线 §8.3 点名的陷阱。按"最小改动 + 微调建表脚本"约束选 ALTER。

3. **JSP 按既有表格/表单样式插入，不加校验**：
   - `listProduct.jsp`：表头在"库存数量"后加 `<th>备注</th>`，行内在 `${p.stock}` 后加 `<td>${p.remark}</td>`；新增产品表单在"库存"行后加一行 `<input name="product.remark" class="form-control">`；`$("#addForm").submit` 校验逻辑不动（备注可选）。
   - `editProduct.jsp`：在"库存"行后加 `<input name="product.remark" value="${product.remark}" class="form-control">`，回显走 EL；提交校验同样不动。
   - 写法对齐 admin-crud 基线记录的 OGNL 属性路径约定与既有 Bootstrap 3 表单结构。

4. **Action/Service 零改动**：Struts2 OGNL 按 `product.remark` 自动填充绑定对象，`ProductAction.add/update` 现有 `save/update` 调用随实体持久化全字段；写后重定向 `listProductPage` 依赖 `product.category.id`，与新字段无关。

## Risks / Trade-offs

- [实体与脚本映射无任何启动期校验] → 验收清单加入重启后控制台核对 `product` 表含 `remark` 列，并在后台录入一条备注确认读写成功。
- [ALTER 语句位置错误（落在 CREATE TABLE 之前或脚本末尾之外）会导致启动 SQL 失败] → 明确插入锚点为 product INSERT 段之后；启动横幅无异常 + H2 控制台可查列为验收项。
- [`${p.remark}` 为 EL 原样输出，备注含 HTML 时被浏览器解释（XSS）] → 与项目所有既有字段（name/subTitle）渲染方式一致，属继承性项目级特征，本次不顺带修复；登记为人工评审项。
- [两份 SQL 脚本自此 diverge（`sql/tmall_ssh.sql` 无该列）] → 原始 dump 定位为上游只读基线；MySQL 回退模式由 `hbm2ddl.auto=update` 自动补列，已在 proposal 记录该假设。
- [长度仅靠 `varchar(255)` 约束，超长输入由数据库报错] → 与现有字段同等约束水平，符合"无服务端校验"基线，不额外处理。

## Migration Plan

无存量数据迁移：内存库随重启按脚本重建，新列在下次启动自动出现。回滚即移除四处新增（实体字段、ALTER 语句、两处 JSP 片段）后重启。流程：改 `src/sql/tmall_ssh_h2.sql` → 重启 `StartJetty` → H2 控制台（:8082）核列 → 后台手工冒烟（新增带备注 / 备注留空 / 编辑改备注 / 列表查看 / 前台抽查）。

## Open Questions

- 种子商品是否需要演示用备注值？默认为"全部留空"（spec 场景已按空备注断言）；如需演示值，属种子数据变更，需另行决定并保持校准块诚实。
