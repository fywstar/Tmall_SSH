---

description: "Task list for 商品模块新增商品备注字段"

---

# Tasks: 商品模块新增商品备注字段

**Input**: Design documents from `/specs/001-product-remark/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

**Tests**: 本 feature 无新增单元测试（仅新增实体字段、无新增 Service 方法，research.md R5 结论）；验证走手动 smoke path + 既有 `TestTmall` 回归。

**Organization**: Tasks grouped by user story for independent implementation/testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to（US1/US2）
- Include exact file paths in descriptions

## Path Conventions

本项目为单仓库 Web 应用：业务源码在 `src/`，页面在 `web/`，建表脚本 `src/sql/`，无构建步骤（`StartJetty` 启动时自动编译，见 constitution 原则 VII）。

## Phase 1: Setup（本项目无新工程脚手架，仅记录交付边界）

**Purpose**: 明确本次交付零新增目录/构建，改动收敛于 4 个既有文件（见 plan.md）

- [x] T001 确认变更范围仅限 4 个既有文件（`src/com/caozhihu/tmall/pojo/Product.java`、`src/sql/tmall_ssh_h2.sql`、`web/admin/listProduct.jsp`、`web/admin/editProduct.jsp`），不新增文件、不引入新依赖（constitution 原则 II/III）

---

## Phase 2: Foundational（阻断前置，两个 User Story 都依赖）

**Purpose**: Product 实体字段与 Schema 列是本 feature 的共同地基，必须先完成

**⚠️ CRITICAL**: 未完成本阶段不得开始任何 User Story

- [x] T002 在 `src/com/caozhihu/tmall/pojo/Product.java` 新增持久化属性 `private String remark;`（**非** `@Transient`），并为 `remark` 添加 public getter/setter；中文注释；**MUST NOT** 改动该实体其它 9 个字段与 `@ManyToOne category` 关联（constitution 原则 II/IV，字段约束见 data-model.md "备注可空、非 @Transient"）
- [x] T003 在 `src/sql/tmall_ssh_h2.sql` 的 product 全部 positional INSERT 之后、`CREATE TABLE productimage`（第 155 行）之前追加一行 `ALTER TABLE product ADD COLUMN remark varchar(255) DEFAULT NULL;`；**MUST NOT** 改写 `CREATE TABLE product`（其仅 8 列）或任何 INSERT 的行序/值（constitution 原则 V）

**Checkpoint**: 实体字段 + 建表脚本就绪；可先启动 `StartJetty` 跑一次 `TestTmall` 回归确认 context load 且 Hibernate mapping 对齐含 remark 的 schema（详情见 quickstart.md A 节）→ 进入 User Story 实现

---

## Phase 3: User Story 1 - 后台新增/编辑商品时可填写商品备注（Priority: P1）🎯 MVP

**Goal**: 后台"编辑产品"与"新增产品"两个表单均提供"商品备注"输入框，可提交并回显（spec SC-001/FR-001/FR-002）

**Independent Test**: 后台进入某产品"编辑产品"表单 → 填入备注 → 提交 → 重进该编辑页，备注得以回显；"新增产品"表单填写备注提交成功（quickstart.md B 节）

### Implementation for User Story 1

- [x] T004 [P] [US1] 在 `web/admin/editProduct.jsp` 的 `editForm`（action=`admin_product_update`）中、`stock` 行之后新增一行"商品备注"输入框：`<input id="remark" name="product.remark" type="text" value="${product.remark}" class="form-control">`；不新增表单校验（备注可空）
- [x] T005 [P] [US1] 在 `web/admin/listProduct.jsp` 的 `addForm`（action=`admin_product_add`）中、`stock` 行之后新增一行"商品备注"输入框：`<input id="remark" name="product.remark" type="text" class="form-control">`（无 value 回显，为空）；不新增表单校验

**Checkpoint**: US1 独立可用——两个表单均可录入/回显备注；执行 quickstart.md B 节第 2-4 步验证

---

## Phase 4: User Story 2 - 商品备注为空时不产生额外行为（Priority: P2）

**Goal**: 空备注保存正常，既有前台/后台全路径无回归（spec SC-002/SC-003/FR-003/FR-004）

**Independent Test**: 不填备注新增/编辑产品并提交成功；前台首页/详情/搜索、后台分类与产品列表全部照常（quickstart.md B 节第 5 步）

### Implementation for User Story 2

- [x] T006 [US2] 人工执行 smoke：不填备注新增一个产品 → 提交成功且 `remark` 为 NULL；遍历前台首页、某商品详情页、搜索、后台分类与产品列表共 5 条既有路径，确认渲染与分页行为与改动前一致（无回归）

**Checkpoint**: US1 + US2 均独立可用

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: 命名载体同步核验、验收清单对齐、交付自查

- [x] T007 [P] 命名核验：`grep -rn "remark" src web` 输出的载体四处（`Product.java` 字段+getter/setter、`listProduct.jsp` `name="product.remark"`、`editProduct.jsp` `name="product.remark"`+`${product.remark}`、`src/sql/tmall_ssh_h2.sql` `ADD COLUMN remark`）在同一次变更内全部命中并同步（constitution 原则 IV）
- [x] T008 [P] 更新 `src/sql/tmall_ssh_h2.sql` 校验：确认未改动 `CREATE TABLE product`、85 条 product INSERT 的行序与其它列值逐字未变，新增行各 `remark=NULL`（constitution 原则 V，spec SC-004）
- [x] T009 依据 spec.md「人工验收检查清单」与 quickstart.md 复核全部验收项并在 `selfcheck` 记录；本次若无新增逻辑则 `TestTmall` 保持回归即可，不必新增单测（research.md R5）

**Checkpoint**: 全部 User Story 完成、命名载体同步、验收清单闭合

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (P1)**: T001 —— 无前置，可直接开始
- **Foundational (P2)**: T002→T003（各自独立，可并行）—— **BLOCKS** 所有 User Story
- **User Story 1 (P3)**: 依赖 Foundational；T004/T005 并行，互不依赖
- **User Story 2 (P4)**: 依赖 Foundational（与 US1 无强依赖，可并行验证）
- **Polish (P5)**: 依赖 US1/US2 完成

### User Story Dependencies

- **US1 (P1)**: Foundational 后即可开始；无对其它 story 依赖
- **US2 (P2)**: Foundational 后即可开始；独立可测

### Within Each User Story

- 实体/Schema（Foundational）→ 表单实现（US1）→ 空值回归（US2）→ 命名与验收闭环（Polish）
- story 完成后再进入下一优先级

### Parallel Opportunities

- T002 / T003 可并行
- T004 / T005 可并行（不同 JSP 文件、互不依赖）
- T007 / T008 可并行

---

## Parallel Example

```bash
# Foundational 并行：
Task: "T002 在 src/com/caozhihu/tmall/pojo/Product.java 新增 remark 字段+getter/setter"
Task: "T003 在 src/sql/tmall_ssh_h2.sql 追加 ALTER TABLE product ADD COLUMN remark varchar(255) DEFAULT NULL;"

# US1 并行：
Task: "T004 在 web/admin/editProduct.jsp 的 editForm 新增商品备注输入框"
Task: "T005 在 web/admin/listProduct.jsp 的 addForm 新增商品备注输入框"
```

---

## Implementation Strategy

### MVP First（仅 User Story 1）

1. Phase 2: Product.remark 字段 + SQL ALTER（地基）
2. Phase 3: US1 —— 两个表单录入/回显
3. **STOP and VALIDATE**: 依 quickstart.md B 节第 2-4 步独立验证 US1

### Incremental Delivery

1. Foundational 完成 → 地基就绪
2. US1 → 独立验证 → MVP 交付
3. US2 → 空备注零回归独立验证
4. Polish → 命名同步/验收清单闭合

---

## Notes

- 本项目宪法禁止在基线维护流程执行 git/openwiki 等命令；本 feature 的手动 smoke/运行验证均为人工程序，不依赖自动化单测（constitution 测试规范：UI/端点不经 JUnit）。
- 每完成一个 Task 或逻辑组提交一次变更；在 checkpoint 处可独立验证 story。
- 避免过度开发：只改 4 个文件，不新增 Service 方法、不扩前台展示、不改搜索/排序/分页语义。