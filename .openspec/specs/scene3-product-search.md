# Spec：商品列表商品名称模糊搜索（scene3-product-search）

> 模式：SDD Spec-First 轻量规范。本 Spec 为本次迭代代码变更的唯一设计依据，所有写法逐条溯源至项目 OpenWiki 基线（`openwiki/`，只读，不做任何修改）。
> 主溯源文档：`openwiki/workflows/pagination-and-search.md`（下文简称【分页与搜索基线】），辅以 `openwiki/workflows/admin-crud.md`、`openwiki/conventions/runtime-invariants.md`。

## 1. 需求概述

后台商品列表 `admin_product_list` 当前仅支持按分类（`category.id`）分页展示产品。本次在其上新增**商品名称（`name`）单字段模糊搜索**能力：

1. 查询侧支持按商品名称 `keyword` 做 like 模糊匹配；
2. `ProductAction` 接收 `keyword` 请求参数；
3. `listProduct.jsp` 增加搜索输入框；
4. 搜索兼容原有分页机制；`keyword` 为空/空白字符串时不做名称过滤，返回全部商品（即维持现状：该分类下全部商品分页展示）；
5. 搜索条件在分页跳转（上一页/下一页/页码/首尾页）时携带保留。

范围边界（禁止扩散）：仅 `name` 单字段、仅 like 模糊、仅后台商品列表；不新增全文检索、高级过滤、多字段搜索、排序；不改前台 `foresearch` 路径；不修任何基线标注的既有缺陷（见 §8）。

## 2. 涉及修改组件

| 组件 | 文件 | 变更类型 | 说明 |
|---|---|---|---|
| Service 接口 | `src/com/caozhihu/tmall/service/ProductService.java` | 增量新增 | 新增 2 个方法签名 |
| Service 实现 | `src/com/caozhihu/tmall/service/impl/ProductServiceImpl.java` | 增量新增 | 新增 2 个实现（本项目查询逻辑所在层，见 §4 落点说明） |
| Action | `src/com/caozhihu/tmall/action/ProductAction.java` | 修改 `list()` | keyword 空白走原逻辑，非空白走搜索逻辑 |
| JSP | `web/admin/listProduct.jsp` | 增量新增 | 面包屑后插入搜索表单 |

**不修改**：`Page` 工具类、`adminPage.jsp`、`BaseService`/`BaseServiceImpl`、`Action4Result`、其它任何 Action/Service/DAO/JSP。

> **"DAO 查询方法"落点说明**：本项目持久层为通用 `DAOImpl`（`HibernateTemplate` 封装），业务查询一律以 `DetachedCriteria` 构建于 ServiceImpl，经 `ServiceDelegateDAO.findByCriteria` 委托执行（【分页与搜索基线】"The service and DAO side of the slice" 一节及其 sources：`DAOImpl.java`、`ServiceDelegateDAO.java`）。故本次"Product 对应的 DAO 查询方法支持模糊 like"的落点是 `ProductServiceImpl`，先例即同类方法 `ProductServiceImpl.search`（【分页与搜索基线】"The storefront search path" 一节）。

## 3. 参数设计

| 参数 | 类型 | 来源 | 绑定机制 | 说明 |
|---|---|---|---|---|
| `keyword` | `String` | 搜索表单 GET / 分页链接查询串 | Struts2 OGNL 绑定到 `Action4Parameter.keyword`（`ProductAction` 继承链上**已存在**该字段及 `getKeyword()/setKeyword()`，无需新增） | 溯源：【分页与搜索基线】"keyword is a plain `String` field on `Action4Parameter`, inherited by `ForeAction`"；本项目代码 `Action4Parameter.java` 第 14 行 |
| `category.id` | `Integer` | 搜索表单隐藏域 / 分页链接 `page.param` | OGNL 绑定到 Action 的 `category` | 现状必要参数，搜索必须携带（见 §5、§7） |
| `page.start` | `int` | `adminPage.jsp` 分页链接 | `Action4Pagination.setPage` | 现状机制，不变 |

参数处理约定：`keyword` 进入查询前执行 `trim()`；`trim()` 后为空白（含 `null`、`""`、纯空白）则视为"无搜索条件"，不做名称过滤。空白判定使用纯 JDK 写法 `keyword != null && !keyword.trim().isEmpty()`（项目 Action 层无判空工具先例，`ForeAction.search` 甚至无判空；本约定为需求第 4 条的显式要求，非脑补机制）。

`page.param` 取值设计（拼接规则严格沿用现状先例 `page.setParam("&category.id=" + category.getId());`，见【分页与搜索基线】"page.param: the filter suffix" 一节）：

- 无搜索条件：`"&category.id=" + category.getId()`（与现状完全一致）；
- 有搜索条件：`"&category.id=" + category.getId() + "&keyword=" + keyword`（首字符必须是 `&`——基线载明 "It must begin with `&`"，因 `adminPage.jsp` 将其直接拼接到 `href="?page.start=…"` 之后，无分隔符逻辑）。

> 说明：`page.param` 由 Action 每次请求重新计算并 `setParam`，不在代码中使用 `URLEncoder`（项目树中不存在该机制，浏览器会对地址栏非 ASCII 字符按 UTF-8 自动编码，服务端 UTF-8 解码后 OGNL 正常还原）。

## 4. 数据库查询逻辑

新增 `ProductServiceImpl` 两个方法（与既有 `listByCategory`/`list(Page, Category)`/`total(Category)` 同层同风格；`Category` 为具体类型签名的先例是 `PropertyServiceImpl#list(Page, Category)` 与 `total(Category)`，字面量属性名 `"category"` 先例同见该类与【分页与搜索基线】"ProductServiceImpl inherits this pair unchanged; PropertyServiceImpl overrides both with a literal `\"category\"`"）：

**行查询** `list(Page page, Category category, String keyword)` —— 形态完全跟随 `BaseServiceImpl#list(Page, Object)`（`DetachedCriteria` + `eq` + `Order.desc("id")` + `findByCriteria(dc, start, count)`）与 `ProductServiceImpl#search` 的 like 写法：

```java
DetachedCriteria dc = DetachedCriteria.forClass(clazz);
dc.add(Restrictions.eq("category", category));
dc.add(Restrictions.like("name", "%" + keyword + "%"));
dc.addOrder(Order.desc("id"));
return (List<Product>) findByCriteria(dc, page.getStart(), page.getCount());
```

**计数查询** `total(Category category, String keyword)` —— 跟随 `PropertyServiceImpl#total(Category)` 的 `String.format` HQL 风格，附加 like 条件：

```java
String sqlFormat = "select count(*) from %s bean where bean.category = ?0 and bean.name like ?1";
String hql = String.format(sqlFormat, clazz.getName());
List<Long> l = (List<Long>) this.find(hql, category, "%" + keyword + "%");
if (l.isEmpty()) { return 0; }
return l.get(0).intValue();
```

要点（均沿用基线既有行为，不新增机制）：

- like 模式为 `"%"+keyword+"%"`，**参数绑定**而非字符串拼进 HQL（与 `ProductServiceImpl.search` 一致，基线载明这是参数绑定，无注入风险）；
- 不转义 `%`/`_` 通配符、不加 `lower()`、不设排序以外的条件——与基线 `search` 行为一致（基线明确记录了不转义与无 lower 的现状）；
- 行查询保留 `Order.desc("id")`（跟随 `BaseServiceImpl#list(Page, Object)`，区别于无排序的 `search`——本查询是分页切片，必须排序才能与基线分页机制 `start/count` 切片语义一致）；
- count 与行查询为两次独立查询，由 Action 先 total 后 list 调用（【分页与搜索基线】"The count and the slice are two independent queries"）；
- 切片经 `ServiceDelegateDAO.findByCriteria(dc, firstResult, maxResults)` → `HibernateTemplate` → SQL 级 `limit/offset`（基线 "The slice itself is not performed in Java"）。

## 5. 页面改动点（listProduct.jsp）

在面包屑 `<ol class="breadcrumb">…</ol>` 之后、`.listDataTableDiv` 之前插入搜索表单：

```jsp
<div style="margin-bottom:10px;">
    <form action="admin_product_list" method="get" class="form-inline">
        <input type="hidden" name="category.id" value="${category.id}">
        <div class="form-group">
            <input name="keyword" type="text" class="form-control"
                   value="${param.keyword}" placeholder="输入产品名称搜索">
        </div>
        <button type="submit" class="btn btn-primary">搜索</button>
    </form>
</div>
```

写法溯源：

- 表单直发 `admin_product_list`、GET 方式：与分页链接同路径同参数族（分页链接即 `?page.start=…` GET 请求），搜索后 `page.start` 未携带则由 Action 的 `if (page == null)` 兜底从第 0 条开始（基线 "The `if (page == null)` guard is what makes the first, unpaged visit work"）；
- 隐藏域 `category.id`：必须携带——基线载明接收方 "need `category.id` before they can do anything else"，缺失会导致 Action 首次解引用即失败；隐藏域写法先例为本页新增产品表单 `<input type="hidden" name="product.category.id" value="${category.id}">`；
- 输入框 `name="keyword"`、回填 `value="${param.keyword}"`：完全跟随 `web/include/search.jsp`（基线："Each POSTs to `foresearch` with a single text field named `keyword`, and each re-populates the box from `${param.keyword}`, which is how the typed term survives the forward into the result page"——本页为 GET 提交 + 链接传参，同样经 `${param.keyword}` 回填）；
- `form-control`/`btn` 样式类：跟随本页既有表单（`addForm`）所用 Bootstrap 类族；
- `adminPage.jsp` **零改动**：其全部链接已自动追加 `${page.param}`，搜索条件携带即由此实现（§7）。

## 6. 业务逻辑约束

1. `keyword` 为空/空白（`null`、`""`、纯空白，`trim()` 后判定）：不做名称过滤，仅按分类分页展示该分类下全部产品；
2. `keyword` 非空白：`trim()` 后用于查询与 `page.param`；total 改由 `productService.total(category, keyword)` 提供、行查询由 `productService.list(page, category, keyword)` 提供；
3. **total 来源（经用户确认调整）**：开发中发现无搜索路径现状 total 来自 `propertyService.total(category)`（分类**属性**数，基线登记的既有缺陷【人工评审待确认】："ProductAction#list mixes them"），导致页码由属性数驱动、翻页空表格（实测复现：分类 74 属性 20 条 / 产品仅 5 条，`page.start=5` 起无数据）。**经用户确认，本次一并修复**：无搜索分支 total 改为 `productService.total(category)`（`BaseService` 既有方法，行所属 service 的计数），与搜索分支统一满足基线 Extension Points "a `total` from *the service that owns the rows*"。此为用户明确授权的缺陷修复，不属于擅自扩改；
4. 搜索仍限定在当前分类内（`eq("category", …)` 与 like 同时成立），与列表页上下文一致；
5. 搜索结果行仍逐行 `productImageService.setFirstProductImage(product)`（现状循环不动，位于 `t2p(category)` 之前，顺序不变）；
6. `t2p(category)` 时机不变（查询之后调用），与现状一致；
7. 搜索路径与无搜索路径共用同一 `return "listProduct"` 与同一分页片段，不新增 Result 映射。

## 7. 分页兼容说明

复用既有 `PageBean`（`com.caozhihu.tmall.util.Page`）机制，零机制改动：

- `Page` 四字段 `start/count/total/param` 语义不变，`defaultCount=5` 不变（基线 "The `Page` bean" 一节）；
- 分页跳转携带搜索条件的**唯一通道**就是 `page.param`——基线 Extension Points 明文："Anything that must keep a query-string context across a page change does it through `page.param`; there is no hidden form field, no session key and no bookmark-preserving mechanism beyond that string." `adminPage.jsp` 的六类链接（«、‹、页码 n、›、»）均输出 `?page.start=<n>${page.param}`，因此只要 Action 在搜索时把 `keyword` 追加进 `page.param`，所有分页链接自动携带，无需改动 `adminPage.jsp`；
- `param` 必须以 `&` 开头、必须由 Action 在每次请求重设（链接整体替换 query string，不累积）——两者均为基线载明的载荷性质，本设计遵循；
- 派生页码（`totalPage`/`last`/`hasPreviouse`/`hasNext`）全部由 `page.total` 推导，搜索路径传入了正确的产品计数，页码与结果集自然一致；
- 搜索表单 GET 提交不带 `page.start`，首次搜索从第 0 条开始（`page == null` 兜底新建 `Page`），随后分页链接接棒携带 `keyword`。

## 8. 项目规约适配说明

- **分层与包结构**：查询实现落 `service.impl.ProductServiceImpl`、签名落 `service.ProductService`、参数接收落 `action.ProductAction`、页面落 `web/admin/listProduct.jsp`——全部为既有文件增量修改，不新建 Java 文件、不新建 JSP；
- **Action 写法**：`@Action("admin_product_list")` 注解路由、`page` 字段自 `Action4Pagination` 继承、`keyword` 自 `Action4Parameter` 继承、返回 `"listProduct"` 走 `Action4Result` 既有 `@Result`——均为现状机制，零新增注解；
- **t2p 语法**：`t2p(category)` 调用方式与时机保持现状（【分页与搜索基线】"both actions set it *before* `t2p(category)` replaces the id-only object with the persistent row" 同款次序）；
- **命名与风格**：方法名沿用 `list(Page, Category)` / `total(Category)` 命名族；HQL 别名 `bean`、`?0`/`?1` 占位符、`String.format` 写法跟随 `PropertyServiceImpl#total`；注释使用简体中文、风格同现有代码；
- **不触碰项**：不修改 `Page.java`（含拼错的 `hasPreviouse`——基线载明双侧拼写必须保持一致，动了即坏）、不修改 `adminPage.jsp`、不修改 `web.xml`/Spring 配置、不修改其它列表页；基线已知缺陷（`${param.keyword}` 未转义、like 通配符不转义）均**原样保留**，仅在本 Spec 与自查报告中登记，不在本次迭代修复（禁止过度开发）；`propertyService.total` 计数混用缺陷经用户确认后按 §6.3 一并修复，属唯一例外。

## 9. 人工验收检查清单

准备：某分类（记 id=C）下录入 ≥11 条产品，名称分别含共同子串（如"测试"）与不含该子串的名称；分页每页 5 条。

- [ ] 1. 无搜索打开 `admin_product_list?category.id=C`：列表正常；页码总数 = 该分类**产品数** ÷ 5 向上取整（缺陷修复后以产品计数驱动，不再被属性数虚增）；新增/编辑/删除跳转正常；
- [ ] 2. 搜索框输入"测试"提交：仅显示名称含"测试"的产品，第一页 5 条，从第 0 条开始；
- [ ] 3. 搜索结果页点击第 2 页：URL 含 `page.start=5&category.id=C&keyword=测试`，仍为过滤后结果；点击 »/›/‹/« 同样保留 `keyword` 与 `category.id`；
- [ ] 4. 搜索结果页页码总数 = 名称含"测试"的产品数 ÷ 5 向上取整（与属性数无关），切片无重复、无遗漏；
- [ ] 5. 搜索结果页清空输入框再提交：返回该分类全部商品（无名称过滤）；
- [ ] 6. 直接访问 `admin_product_list?category.id=C&keyword=`（空值）与 `&keyword=%20%20`（纯空白）：均不过滤，等价于无搜索；
- [ ] 7. 搜索框回填：搜索跳页后输入框仍显示当前 `keyword`（`${param.keyword}` 回填）；
- [ ] 8. 新增产品表单仍正常提交（隐藏域、校验脚本未被搜索表单影响）；
- [ ] 9. 搜索无命中：显示空表格与正确页码（totalPage 由 total=0 推导为 1）；
- [ ] 10. 中文关键字（如"笔记本"）搜索、跳页、再次搜索均正常（依赖容器 UTF-8 URI 编码）；
- [ ] 11. 前台 `foresearch` 行为不受影响（本次未触碰该路径）。
