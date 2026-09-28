# Implementation Plan: 商品模块新增商品备注字段

**Branch**: `001-product-remark` | **Date**: 2026-09-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-product-remark/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

为商品实体新增一个可空文本"备注"（`remark`）字段，后台在"新增产品"与"编辑产品"两个表单中提供"商品备注"输入框，提交后随既有 `admin_product_add` / `admin_product_update` 流程持久化并回显。技术方案（溯源宪法/基线）：实体类加 `String remark` 属性 → SQL 以 `ALTER TABLE ... ADD COLUMN remark varchar(255) DEFAULT NULL` 追加（不改写 `CREATE TABLE product` 或既有 positional INSERT）→ 两个表单 JSP 各加一行输入框（`name="product.remark"` / `${product.remark}`）。字段走既有 `BaseServiceImpl.save/update` + `Action4Pojo.product` OGNL 绑定，不新增 Service 方法、不改 Action 继承链与拦截器栈，不改前台展示，不影响搜索/排序/分页。

## Technical Context

**Language/Version**: Java / JDK 8 源码级别（`-source 8 -target 8`，经 `StartJetty.ensureCompiledClasses` 编译）

**Primary Dependencies**: 既有 Spring 4.3.18 / Struts2 2.5.14.1 / Hibernate 5.3.7 / H2 1.4.x（版本冻结，不新增）

**Storage**: H2 内存库（`jdbc:h2:mem:tmall_ssh;MODE=MySQL;DB_CLOSE_DELAY=-1`）；schema 唯一来源 `src/sql/tmall_ssh_h2.sql`，`hibernate.hbm2ddl.auto=none`

**Testing**: 无新增逻辑（仅新增字段，不新增 Service 方法），故无新增单测；验证走手动 smoke path（宪法测试规范）＋既有 `TestTmall`（回归，零断言）

**Target Platform**: 嵌入式 Jetty 9.4.x，本机 8080 应用 / 8082 H2 控制台

**Project Type**: Struts2 + Spring + Hibernate Web 应用（JSP/jQuery 前端）

**Performance Goals**: 不适用（新增单个可空字段，无性能影响）

**Constraints**: 宪法 NON-NEGOTIABLE —— 业务源码最小改动、SQL 以 ALTER 追加、命名字面量六载体同步、不扩功能

**Scale/Scope**: 1 个实体字段 + 2 个后台表单 + 1 条 SQL ALTER；存量 85 条 product seed 数据不变量保持

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | 状态 | 说明 |
|---|---|---|
| **原则 I 基线优先** | ✅ 满足 | 字段名 `remark` 为 spec 定义；改动方式全部跟随基线/宪法既有约定，未脑补新机制 |
| **原则 II 业务源码只读 / 最小改动** | ✅ 满足 | 仅新增 `Product.remark` 字段/getter/setter ＋ 两个表单一行输入框；不重构、不优化、不扩功能；`ProductAction`/Service/拦截器零改动 |
| **原则 III 技术栈版本冻结** | ✅ 满足 | 不升级/替换任何 jar；H2 仍 1.4.x、URL 仍带 `MODE=MySQL;DB_CLOSE_DELAY=-1`；原 MySQL 配置注释保留 |
| **原则 IV 命名不可漂移** | ✅ 满足 | 新命名字面量 `remark` 的载体：`Product.java` + SQL `ADD COLUMN remark` + `listProduct.jsp` + `editProduct.jsp` 共 4 处同步；改名前 grep；不触碰 47 端点/36 result/session key/图片/order status 等既有字面量 |
| **原则 V Schema 单一来源** | ✅ 满足 | `hibernate.hbm2ddl.auto=none`；新列用 `ALTER TABLE product ADD COLUMN remark ...`，位于 product 的 positional INSERT 之后、`CREATE TABLE productimage` 之前；不改 `CREATE TABLE product` 与任何 INSERT 行序 |
| **原则 VI Spec-First** | ✅ 满足 | spec 存在于 `specs/001-product-remark/spec.md`；本 plan 即设计工件 |
| **原则 VII 启动/类加载器不变量** | ✅ 满足 | 本次不新增启动类/不启动 jar；`StartJetty`、Configuration 链、addSystemClass 七前缀、8082 端口均不动 |

> 无 violation，无需填写 Complexity Tracking 的"复杂度合理性"表。

## Project Structure

### Documentation (this feature)

```text
specs/001-product-remark/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output（表单绑定契约）
└── tasks.md             # Phase 2 output（/speckit-tasks 生成）
```

### Source Code (repository root)

```text
src/
├── com/caozhihu/tmall/pojo/Product.java   # [EDIT] 新增 String remark + getter/setter
└── sql/tmall_ssh_h2.sql                    # [EDIT] product INSERT 后追加 ALTER ADD COLUMN remark

web/admin/
├── listProduct.jsp                         # [EDIT] 新增产品 addForm 加"商品备注"输入框
└── editProduct.jsp                         # [EDIT] 编辑产品 editForm 加"商品备注"输入框
```

**Structure Decision**: 遵循既有目录结构零新增文件——纯就地编辑 4 个既有文件（实体、SQL、两个 JSP）。无新包、无新模块、无前端构建。

## Complexity Tracking

> 无宪法 violation 需豁免，此表留空（`product-remark` 变更在既定架构内是单字段点缀，不引入新项目/新模式）。

---

## Phase 0 — Research (research.md)

见下方独立工件：`specs/001-product-remark/research.md`。核心决策已基于宪法与基线溯源，无 NEEDS CLARIFICATION 残留。

## Phase 1 — Design (data-model.md / contracts/ / quickstart.md)

见下方独立工件：`data-model.md`、`contracts/form-binding.md`、`quickstart.md`。

## Constitution Re-Check (post-design)

上述各 Gate 在任何设计工件中均未引入新 violation：未新增 Service 方法、未改 Action 继承链/拦截器栈/命名清单/schema 来源/启动不变量。设计完全落在冻结架构内部。✅