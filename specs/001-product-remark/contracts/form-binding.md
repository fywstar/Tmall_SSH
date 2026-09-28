# Contract: 商品备注表单绑定

> Phase 1 产物。本项目为 Struts2 + JSP Web 应用，"对外接口"即浏览器表单 → `admin_product_*` 端点的绑定契约。本次仅新增一个可空文本字段，其余表单契约不变。

## 表单契约

### 新增产品（`web/admin/listProduct.jsp` addForm → `admin_product_add`）

| 字段名 (name) | 来源 | 必填 | 说明 |
|---|---|---|---|
| `product.category.id` | hidden | 是 | 所属分类 |
| `product.name` / `product.subTitle` | input | 名称必填 | 既有 |
| `product.originalPrice` / `product.promotePrice` / `product.stock` | input | 既有 JS 校验 | 既有 |
| **`product.remark`** | input | 否 | **新增**，可空文本 |

### 编辑产品（`web/admin/editProduct.jsp` editForm → `admin_product_update`）

| 字段名 (name) | 来源 | 必填 | 说明 |
|---|---|---|---|
| `product.id` | hidden | 是 | 主键 |
| `product.category.id` | hidden | 是 | 所属分类 |
| `product.name` / `product.subTitle` | input | 名称必填 | 既有 |
| `product.originalPrice` / `product.promotePrice` / `product.stock` | input | 既有 JS 校验 | 既有 |
| **`product.remark`** | input | 否 | **新增**；回显 `${product.remark}` |

## 端点契约

- `admin_product_add`（POST）：新增产品，备注为空串/NULL 均可，成功后 redirect 回产品列表。
- `admin_product_update`（POST）：更新产品（含备注），成功后 redirect 回产品列表。
- 两端点均走 `Action4Pojo.product` 绑定 + `productService.save/update`；无 `@Result`/拦截器栈/命名改动（宪法原则 IV）。

## 异常/边界

- 备注留空 → `remark=NULL`，保存成功，不影响任何既有校验与流程。
- 超长 → 按 `varchar(255)` 截断（数据库/驱动层面），不做额外前端限制（可空、非核心）。