# Tasks

## 1. Service 层：分类 + 名称维度的计数与切片

- [x] 1.1 在 `ProductService` 接口新增两个方法签名：`int total(Category category, String keyword)` 与 `List<Product> list(Page page, Category category, String keyword)`；验证：接口文件编译通过、既有方法签名未被改动。
- [x] 1.2 在 `ProductServiceImpl` 实现上述两个方法：total 镜像 `BaseServiceImpl.total(Object parent)` 的 HQL 风格（`bean.category = ?0 and bean.name like ?1`，参数绑定 `%keyword%`）；list 用 DetachedCriteria（`eq("category", …)` + `like("name", "%keyword%")` + `Order.desc("id")`）。验证：与 `BaseServiceImpl.list(page, parent)` 逐行比对风格一致，无新增依赖 import 之外的改动。

## 2. Action 层：admin_product_list 支持可选 keyword

- [x] 2.1 修改 `ProductAction#list`：按 `keyword == null || keyword.isEmpty()` 分支——有关键词时 `page.setParam("&category.id=" + category.getId() + "&keyword=" + URLEncoder.encode(keyword, "UTF-8"))`、`total` 取 `productService.total(category, keyword)`、行数据取 `productService.list(page, category, keyword)`；无关键词时保持原三行语句逐字不变（含 `propertyService.total(category)`）。验证：无关键词分支的 diff 与原实现完全一致；`setParam` 仍发生在 `t2p(category)` 之前。
- [x] 2.2 确认未新增字段（复用 `Action4Parameter` 既有 `keyword`）且未触碰 `ForeAction#search` / `foresearch`。验证：`git diff` 仅涉及 `ProductAction.java` 的 `list()` 方法体。（项目约束禁止执行 git 命令，以静态文件比对代替：仅 `ProductAction#list` 方法体与两个 import 变更，其余方法未动）
- [x] 3.1 在 `web/admin/listProduct.jsp` 的 `listDataTableDiv` 之前新增 GET 搜索表单：`action="admin_product_list"`、文本框 `name="keyword"`（`value="${param.keyword}"` 回显）、隐藏域 `name="category.id"`（`value="${category.id}"`）、提交按钮。验证：浏览器打开产品管理页可见搜索框，输入关键词提交后列表变化且搜索框回显关键词。（代码已落地，浏览器可见性待 4.x 人工验收）

## 4. 端到端验证

- [x] 4.1 按 spec 场景人工验收：启动应用（`StartJetty`），在当前分类构造名称含"公共"的商品后验证——子串命中、其他分类同名词不出现、`%` 关键词全命中、无匹配正常渲染、翻页保持分类与关键词且页数按命中总数计算、留空提交等同无关键词、不带关键词访问行为与变更前一致。验证：逐条对照 `specs/product-name-search/spec.md` 的场景清单，全部通过。（人工验收已完成，2026-09-27）
- [x] 4.2 复核非搜索路径回归：商品新增/编辑/删除后重定向回列表、既有翻页链接（`page.param` 仅含 `category.id`）、前台 `foresearch` 搜索结果页。验证：行为与基线记录一致，无本次改动痕迹。（人工验收已完成，2026-09-27）
