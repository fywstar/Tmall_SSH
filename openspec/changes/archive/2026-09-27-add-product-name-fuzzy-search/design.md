# Design

## Context

- `admin_product_list`（`ProductAction#list`）按分类分页展示商品：`page.param = "&category.id=" + category.getId()` 维持翻页上下文，行数据来自 `productService.list(page, category)`（`BaseServiceImpl` 的 DetachedCriteria + `Order.desc("id")` 切片）；计数来自 `propertyService.total(category)`（基线已记录的计数混用现状，标记【人工评审待确认】）。
- `ProductService.search(keyword, start, count)` 是项目内唯一的名称模糊查询实现（`Restrictions.like("name", "%" + keyword + "%")`，无排序），前台 `foresearch` 以固定 0/20 窗口使用。
- `ProductAction` 经 `Action4Result → Action4Parameter` 继承链已持有 `keyword` 字段（getter/setter 齐全），Struts 可直接绑定同名请求参数，前台搜索表单即以此方式工作。
- 分页上下文的唯一载体是 `page.param`：必须以 `&` 开头、被 `adminPage.jsp` 拼进每一个翻页链接（基线《Pagination and Search》）。

## Goals / Non-Goals

**Goals:**

- 当前分类内按商品名称子串搜索，结果分页展示且翻页保持"分类 + 关键词"。
- 搜索路径的计数正确反映命中总数（新增分类 + 名称维度的计数方法）。
- 不带关键词的访问路径行为与变更前逐字节一致。

**Non-Goals:**

- 不修复基线已记录的 `ProductAction#list` 计数混用问题（`propertyService.total`），不扩散到非搜索路径。
- 不改动前台 `foresearch` / `ForeAction#search` 及其页面（其固定 20 条、无排序等基线特性保持原样）。
- 不引入 like 通配符转义、大小写规范化等新匹配规则（与基线既有 search 语义保持一致）。
- 不改 DAO 层、`Page`、`adminPage.jsp`、其余 admin 端点。

## Decisions

1. **扩展现有 `admin_product_list`，不新增搜索端点。**
   关键词作为可选参数并入 `list()`：有关键词走"分类 + 名称 like"分支，无关键词走原路径。
   备选（新增 `admin_product_search` 端点 + 独立结果页）被否：需要复制 result/view 接线、把一个列表拆成两个 URL，且 `page.param` 机制与 `adminPage.jsp` 均按"当前 URL 相对链接"设计，双端点会破坏"同一页面"体验。

2. **`ProductService` 新增两个分类 + 名称维度的方法**（重载，不改动既有签名）：
   - `int total(Category category, String keyword)` — HQL 计数，镜像 `BaseServiceImpl.total(Object parent)` 的写法：`select count(*) from Product bean where bean.category = ?0 and bean.name like ?1`，参数绑定 `%keyword%`。
   - `List<Product> list(Page page, Category category, String keyword)` — DetachedCriteria：`Restrictions.eq("category", category)` + `Restrictions.like("name", "%" + keyword + "%")`，**加 `Order.desc("id")`**（跟随 `BaseServiceImpl.list(page, parent)` 惯例）。
   备选（复用前台 `search(keyword, start, count)` 后在内存过滤分类）被否：分页切片语义被破坏；备选（改 `BaseServiceImpl` 加通用 like 支持）被否：改动面扩大到全部 Service，违反最小改动。
   加排序的原因：分页界面上无序切片会跨页重复/丢失行（基线对前台 search 无排序的缺陷有明确记录）；跟随既有列表惯例取 `id` 倒序即可，不新造排序规则。

3. **`page.param` 组合为 `&category.id=<id>&keyword=<URLEncoder.encode(keyword, "UTF-8")>`。**
   `keyword` 经 URL 编码保证含中文/空格/`&` 的关键词在翻页 GET 链接中可用；`category.id` 沿用现状不编码（数字）。与既有行为兼容：无关键词时 param 与现状完全相同。
   备选（不编码直接拼接）被否：中文关键词翻页后请求参数会乱码/截断。

4. **空关键词判定为 `keyword == null || keyword.isEmpty()`，不 trim。**
   与前台 search 不做 trim 的基线惯例一致；留空提交（表单必传 `keyword=`）等价于无关键词。空白串搜索属基线已知语义，不在本次引入新规则。

5. **JSP 搜索框：GET 表单置于 `listDataTableDiv` 之前，字段名 `keyword`，隐藏域 `category.id`，回显用 `${param.keyword}`。**
   回显机制照搬基线前台搜索框的既有做法（forward 后 `${param.keyword}` 仍可读），不引入新会话键或隐藏字段机制。

6. **搜索路径计数走新方法，非搜索路径保持 `propertyService.total(category)` 原样。**
   `list()` 内以关键词分支切换数据来源，两条路径互不影响；`page.setParam` 仍在 `t2p(category)` 之前调用（基线强调的顺序不变）。

## Risks / Trade-offs

- [like 通配符（`%`/`_`）不转义，关键词 `%` 命中分类内全部商品] → 与基线既有 search 语义一致并在 spec 中显式声明；不静默引入转义差异。
- [大小写敏感性取决于 H2 MODE=MySQL 的比较规则] → 与基线对 `product.name` 匹配的记录一致，spec 不承诺具体大小写行为。
- [`${page.param}` 与 `${param.keyword}` 在 JSP 中不转义输出，含 HTML 的关键词可注入标记] → 基线同源缺陷（adminPage.jsp 的 param 直出、前台搜索框 `${param.keyword}` 直出均有记录），本次跟随既有惯例不新增过滤，已列入人工评审待确认事项。
- [分类 + 关键词的计数查询与切片查询仍是两次独立查询，无共享快照] → 基线分页机制固有特性，维持现状。
- [H2 每次重启重置种子数据] → 验收时先构造含目标名称的商品再验证搜索。

## Migration Plan

纯增量改动，无数据迁移。回滚即还原三个文件（`ProductAction.java`、`ProductService.java` + `ProductServiceImpl.java`、`listProduct.jsp`）。启动方式不变（`StartJetty` 每次启动自动重编译 `src/`）。

## Open Questions

无——范围（仅当前分类内）已与需求方确认；其余歧义均按"跟随基线既有语义"收敛并记录于上。
