# Feature Specification: 商品模块新增商品备注字段

**Feature Branch**: `001-product-remark`

**Created**: 2026-09-28

**Status**: Draft

**Input**: User description: "商品模块新增商品备注字段"

## 需求概述

为商品（Product）实体新增一个"备注"（remark）字段，使商品可以承载一段自由文本的补充说明。该字段默认允许为空，不参与排序、搜索与分页，不影响前台商品浏览逻辑，仅为后台维护商品提供了额外的备注能力。

本项目遵循宪法"基线优先 + 最小改动"原则：本 spec 只增加一个字段及其承载链路，不扩功能、不改既有字段语义。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 后台新增/编辑商品时可填写商品备注 (Priority: P1)

后台用户在新产品管理中打开"新增产品"或"编辑产品"表单时，能看到"商品备注"输入框，可自由输入一段备注文本（可空），提交后备注被保存到数据库。再次进入编辑时，表单能回显已保存的备注。

**Why this priority**: 这是该字段存在的核心价值——让后台能记录商品附加说明；无此能力则字段无意义。

**Independent Test**: 打开后台"编辑产品"表单 → 填写商品备注 → 提交 → 重新进入该产品编辑页，备注文本得以回显。

**Acceptance Scenarios**:

1. **Given** 后台已登录且进入某产品的"编辑产品"表单，**When** 在"商品备注"输入框填写一段文本并点击提交，**Then** 产品被保存成功，数据库中该产品的 remark 字段值为所填文本。
2. **Given** 某产品已保存过备注，**When** 再次打开"编辑产品"表单，**Then** 备注输入框回显已保存的备注文本。

---

### User Story 2 - 商品备注为空时不产生额外行为 (Priority: P2)

后台用户不填写商品备注直接提交时，产品仍能正常保存（备注为空），且既有的分类/产品列表、前台首页、详情页、搜索等功能不因新增字段而受任何影响。

**Why this priority**: 保证新增字段对存量流程零侵入，是"最小改动 + 命名不可漂移"约束的验收底线。

**Independent Test**: 不填写备注新增/编辑一个产品并提交 → 使用前台首页、详情、搜索、后台分类/产品列表，全部照常工作。

**Acceptance Scenarios**:

1. **Given** 用户在"编辑产品"表单中仅保留默认（空）备注，**When** 点击提交，**Then** 产品正常保存，remark 字段为 NULL，页面正常跳转回产品列表。
2. **Given** 若干含备注与不含备注的产品存在，**When** 访问前台首页、商品详情页、搜索页及后台各列表页，**Then** 页面渲染与分页行为与新增字段前完全一致。

---

### Edge Cases

- 备注字段留空（NULL）时如何渲染与回显？——输入框 value 为空字符串，不影响表单校验。
- 备注超长时如何处理？——按字段长度约束（varchar(255)）以内截断；超长输入需在表单层提示（可选增强，默认仅在数据库长度内正常保存）。
- 备注是否参与搜索、排序、分页、购物车、销量统计？——不参与，保持既有所有既有行为不变。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 允许后台在"新增产品/编辑产品"表单中录入商品备注文本。
- **FR-002**: 系统 MUST 将商品备注持久化到商品数据中，并能在再次编辑时正确回显。
- **FR-003**: 商品备注 MUST 允许为空（NULL / 空白），空备注不影响产品保存与既有流程。
- **FR-004**: 商品备注 MUST 不参与商品搜索、排序、分页、销量/评价统计，也不影响前台首页/详情/列表渲染。
- **FR-005**: 在线（存量）商品在未填写备注时，其备注 MUST 为 NULL，既有 85 条 seed 数据的其它列与行序 MUST 保持不变。
- **FR-006**: 所有新增的备注相关字段、列名与页面上可见文案 MUST 与既定基线命名一致（字段：`remark`；页面标签：商品备注），不得引入仅本项目不存在的自创机制。

### Key Entities *(include if feature involves data)*

- **Product（商品）**: 现有实体。新增一个可空文本属性 `remark`（varchar(255)，DEFAULT NULL），用于承载商品补充说明。该属性不影响既有 8 个持久化字段（id/name/subTitle/originalPrice/promotePrice/stock/cid/createDate）及其 `@ManyToOne category` 关联。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 后台在"编辑产品"表单中填写备注并提交后，重新进入该产品编辑页能 100% 回显所保存的备注。
- **SC-002**: 备注为空时保存产品，产品能正常保存且后续新增/编辑/分页列表操作成功率不受影响（100% 通过既有 smoke 路径）。
- **SC-003**: 新增字段后，既有前台首页、商品详情、搜索、后台分类与产品列表——五条既有页面路径 MUST 全部照常工作，无回归。
- **SC-004**: 存量 85 条商品 seed 数据的其它字段值与行序在新增字段后 MUST 逐字保持不变（仅各条 remark 为 NULL）。

## Assumptions

- 备注字段为可空自由文本，无格式、枚举或长度之外的校验要求（合理默认）。
- 备注仅面向后台维护人员，不在前台访客端暴露编辑能力；是否在前台页面展示未指定，默认不改变任何前台展示（避免扩需求）。
- 不引入搜索/基于备注的过滤/排序等机制——违反"最小改动"原则，默认排除。
- 复用项目既有 Spring/Struts2/Hibernate 与建表机制，不引入新依赖、不改技术栈版本（宪法原则 III）。
- 沿用宪法"建表脚本单一来源"：schema 由 `src/sql/tmall_ssh_h2.sql` 负责，新增列以 `ALTER TABLE ... ADD COLUMN` 追加，不改写 `CREATE TABLE product` 或既有 positional INSERT（宪法原则 V）。
- 存量逻辑与页面仅作最小新增，不重构不优化（宪法原则 II）。

---

## 项目规约适配说明 (Constitution Alignment — 涉及修改组件 / 字段设计 / 请求参数 / 页面改动点 / 业务逻辑约束 / 验收清单)

> 以下章节遵循本仓库宪法（`.specify/memory/constitution.md`）"Spec-First 交付闭环"与 8 章节要求，并为下游 `/speckit-clarify` 与 `/speckit-plan` 提供可落地的技术约束。它们描述**变更载体**（是什么、放哪里），不约束最终实现方式以外的东西。

### 涉及修改组件

- **实体类**：`src/com/caozhihu/tmall/pojo/Product.java` — 新增持久化字段 `private String remark;` 及 getter/setter（对齐既有字段风格），**不改动**其余字段与关联。
- **Schema/Seed 脚本**：`src/sql/tmall_ssh_h2.sql` — 在 product 的 positional INSERT 全部之后追加一段 `ALTER TABLE product ADD COLUMN remark varchar(255) DEFAULT NULL;`。**MUST NOT** 改写 `CREATE TABLE product`（其仅 8 列，而 85 个 INSERT 各 8 个 positional 值，改写会错位）。
- **后台页面**：新增/编辑商品所复用的表单 JSP — 在既有字段行中新增"商品备注"输入框（name 绑定 `product.remark`，回显 `${product.remark}`）。
  - 复核 `web/admin/editProduct.jsp`（编辑产品表单）与新增产品对应的 JSP（若新增与编辑共用表单则为同一处）。
  - 若"新增产品"仅为跳转编辑/update 流程，则改动收敛于该表单一处。
- **Service/DAO/Action**：本字段为普通持久化属性，走既有 `BaseServiceImpl`/`HibernateTemplate save/update` 契约与 `Action4Pojo`/OGNL 绑定，**MUST NOT** 新增 Service 方法或改动 Action 继承链（宪法 one-way dependency / Action 继承链只读）。

### 字段设计

| 属性 (Java) | 列 (SQL) | 类型 | 约束 | OGNL/JSP 绑定名 |
|---|---|---|---|---|
| `remark` | `remark` | `varchar(255)` | `DEFAULT NULL`，可空 | `product.remark`（表单 `name`）、`${product.remark}`（回显） |

- Java 侧为普通 `String` 属性，**非** `@Transient`（必须持久化）。
- SQL 侧按宪法原则 V：追加 `ALTER TABLE product ADD COLUMN remark varchar(255) DEFAULT NULL;`，位于 product 的 INSERT 之后、`CREATE TABLE productimage` 之前（与基线约定一致）。

### 请求参数

- **入参**：编辑/新增产品表单提交 `product.remark`（文本，可空）——经 `Action4Pojo.product` + Struts 参数绑定落到 Product 实例。
- **出参（回显）**：编辑页渲染 `${product.remark}` 到输入框 value。

### 页面改动点

- 新增/编辑商品表单 JSP：在既有 `产品小标题 subTitle` 与价格/库存等字段之间新增一行"商品备注"输入框（`checkEmpty` 之外的普通文本，可空，不需新增校验）。
- **不改动**：前台 storefront 首页/详情/搜索、`listProduct.jsp` 分页列表、`top.jsp`/购物车徽章、后台分类列表。新增备注不要求前台展示（默认不改前台，见 Assumptions）。

### 业务逻辑约束（从基线溯源）

- **命名字面量**：`remark` 作为新命名字面量，其载体包括 Product.java 字段/getter、JSP 的 `name="product.remark"` 与 `${product.remark}`、SQL 的 `ADD COLUMN remark`——四处必须在同一 commit 内同步（宪法原则 IV）。
- **Schema 唯一来源**：`hibernate.hbm2ddl.auto=none`，schema 只由 `src/sql/tmall_ssh_h2.sql` 决定；新列以 ALTER 追加，seed 数据逐字不动（宪法原则 V）。
- **不扩功能**：备注字段不参与搜索/排序/分页/销量评价统计，不新增 Service 公开方法，不新增端点，不改变 Action 继承链与拦截器栈（宪法原则 II / 架构）。
- **编码风格**：字段与 getter/setter 用中文注释、JDK8 兼容、`@Autowired` 风格不变、不引 Lombok/新依赖（宪法编码风格规范）。

### 人工验收检查清单

- [ ] `Product.java` 新增 `remark` 字段 + getter/setter，其余字段/关联零改动（原则 II）。
- [ ] `src/sql/tmall_ssh_h2.sql` 在 product INSERT 之后追加 `ALTER TABLE product ADD COLUMN remark varchar(255) DEFAULT NULL;`，未改写 `CREATE TABLE product` 或任何 INSERT（原则 V）。
- [ ] 后台新增/编辑产品表单出现"商品备注"输入框，`name="product.remark"`，可空提交成功。
- [ ] 保存备注后重进编辑页可回显（SC-001）。
- [ ] 空备注保存正常，前台首页/详情/搜索/后台分类与产品列表 5 条路径无回归（SC-002/SC-003）。
- [ ] 存量 85 条 product seed 其它列值/行序逐字未变，各条 remark 为 NULL（SC-004）。
- [ ] `grep -rn "remark" src web` 覆盖：Product.java、新 JSP、SQL，三/四处载体同步（原则 IV）。
- [ ] 未新增 Service 方法、未改 Action 继承链、未改拦截器栈、未改技术栈版本（原则 II/III/架构）。

## Checklist 备注

- 本 spec 无未决疑问（无 [NEEDS CLARIFICATION]），可进入 `/speckit-clarify` 复核或 `/speckit-plan` 规划。