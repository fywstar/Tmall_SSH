# 自查报告：商品名称模糊搜索（scene3-product-search）

> 对照物：`.openspec/specs/scene3-product-search.md`（本迭代 Spec）与 OpenWiki 基线 `openwiki/`（只读）。
> 检查范围：本次 4 个文件的全部变更。

## 一、变更文件清单

| 文件 | 变更 |
|---|---|
| `.openspec/specs/scene3-product-search.md` | 新增（Spec 先行产出） |
| [ProductService.java](file:///Users/youwei.fu/Codes/Tmall_SSH/src/com/caozhihu/tmall/service/ProductService.java) | 增量新增 2 个方法签名 + `Page` import |
| [ProductServiceImpl.java](file:///Users/youwei.fu/Codes/Tmall_SSH/src/com/caozhihu/tmall/service/impl/ProductServiceImpl.java) | 增量新增 2 个实现 + `Page`/`Order` import |
| [ProductAction.java](file:///Users/youwei.fu/Codes/Tmall_SSH/src/com/caozhihu/tmall/action/ProductAction.java) | 仅改 `list()`：keyword 分支；另经用户确认修复无搜索分支 total 计数混用缺陷（`propertyService.total` → `productService.total`，1 行） |
| [listProduct.jsp](file:///Users/youwei.fu/Codes/Tmall_SSH/web/admin/listProduct.jsp) | 面包屑后插入搜索表单 |

未触碰：`openwiki/`、`Page.java`、`adminPage.jsp`、`BaseService`/`BaseServiceImpl`、`Action4Parameter`/`Action4Result`/`Action4Pagination`、`DAOImpl`、`web.xml`、Spring 配置、其它列表页与前台 `foresearch` 全链路。

## 二、合规项（逐条溯源）

1. **基线只读**：未对 `openwiki/` 做任何读写删除，Spec 引用均为只读溯源。
2. **Spec 先行**：先产出并落盘 `scene3-product-search.md`（含需求概述/涉及组件/参数设计/查询逻辑/页面改动/业务约束/分页兼容/规约适配/验收清单 9 节），代码后行。
3. **like 查询写法**：`Restrictions.like("name", "%" + keyword + "%")`、参数绑定不拼串 —— 与基线《Pagination and Search》"storefront search path" 记载的 `ProductServiceImpl.search` 逐字一致（同一文件既有方法直接可对照）。
4. **子类重载形态**：`list(Page, Category, String)`/`total(Category, String)`、字面量属性名 `"category"`、`String.format("select count(*) from %s bean where …")`、`?0/?1` 占位符、`find` 返回 `List<Long>` 判空取 `intValue()` —— 全部跟随 `PropertyServiceImpl#list(Page, Category)`/`#total(Category)` 先例（基线同页记载 "PropertyServiceImpl overrides both with a literal `\"category\"`"）。
5. **切片机制**：`Order.desc("id")` + `findByCriteria(dc, page.getStart(), page.getCount())` —— 跟随 `BaseServiceImpl#list(Page, Object)`（基线 "Newest row first, one slice"），经 `ServiceDelegateDAO` → `HibernateTemplate` 落 SQL `limit/offset`，未在 Java 内存分页。
6. **total 与行查询分离、先 count 后 rows**：Action 顺序 `total → setTotal → list`，与基线 round trip 完全一致。
7. **total 来自行所属 service**：搜索分支用 `productService.total(category, keyword)`，依据基线 Extension Points 明文 "a `total` from *the service that owns the rows*"。
8. **`page.param` 机制**：首字符 `&`、Action 每请求重设、追加 `&keyword=` 携带搜索条件 —— 依据基线 "page.param: the filter suffix"（"It must begin with `&`"）与 Extension Points（"Anything that must keep a query-string context across a page change does it through `page.param`"）；`adminPage.jsp` 六类链接自动追加 `${page.param}`，故分页跳转携带搜索条件零改动实现。
9. **keyword 参数绑定**：复用 `Action4Parameter` 既有 `keyword` 字段及 getter/setter（基线 "keyword is a plain `String` field on `Action4Parameter`"），Action 零新增字段。
10. **搜索框写法**：`name="keyword"` + `value="${param.keyword}"` 回填 —— 跟随 `web/include/search.jsp`（基线 "each re-populates the box from `${param.keyword}`"）；隐藏域 `category.id` 跟随本页 `addForm` 既有 hidden 写法；`form-control`/`btn` 样式类为本页既有 Bootstrap 类族。
11. **空白不过滤**：`keyword != null && !keyword.trim().isEmpty()` 纯 JDK 判定，空白时 else 分支与改动前代码逐行一致（含 `propertyService.total(category)` 现状），满足需求第 4 条。
12. **禁止过度开发**：仅 name 单字段、仅 like、仅后台商品列表；无全文检索/多字段/排序/转义增强；基线登记的既有缺陷一律未修（见 §四）。
13. **t2p 时机与 Result 映射**：`t2p(category)` 位置、`return "listProduct"`、`for` 循环 `setFirstProductImage` 均未变动；未新增 `@Result`。
14. **项目特有语法**：未自创任何机制；`t2p`、OGNL 参数绑定（`category.id`/`page.start`）、注解路由 `@Action` 均为现状用法。

## 三、潜在违规项（主动登记）

1. **无搜索分支 total 数据源变更（用户授权）**：开发验收中发现无搜索路径现状 total 来自 `propertyService.total(category)`（分类属性计数，基线标注的既有缺陷【人工评审待确认】"ProductAction#list mixes them"），并实测复现危害——分类 74 属性 20 条 / 产品仅 5 条，页码虚高 4 页、第 2 页起空表格。**经用户确认本次一并修复**：改为 `productService.total(category)`（`BaseService` 既有方法），与搜索分支统一满足基线 Extension Points "a `total` from *the service that owns the rows*"。此为唯一一处对现状行为的变更，属用户明确授权，非擅自扩改；Spec §6.3 已同步。
2. **`keyword.trim()`**：非空白 keyword 去首尾空白后用于查询与 `page.param`。需求未明文要求，系"空白字符串不过滤"判定的自然延伸并已在 Spec §3 固化；不影响中间空格语义。
3. **Spec 文件名连字符**：需求原文中的 `‑` 为 U+2011 非断行连字符，实际落盘采用 ASCII `-`（`scene3-product-search.md` / `-selfcheck.md`），与上一迭代 `scene2-product-remark.md` 命名惯例一致。
4. **ProductService 既有冗余 import**：`import com.caozhihu.tmall.service.BaseService`（同包导入，无用）为原文件既有内容，按"不重构无关代码"保留，未清理。

## 四、风险点说明

1. **中文 keyword 的 URL 编码**：`page.param` 直接拼接中文（项目无 URLEncoder 先例，未自创）。依赖浏览器对地址栏非 ASCII 的 UTF-8 编码与服务端解码（本项目运行于 Jetty 9.x，query 默认 UTF-8），常规场景可用；已列入验收清单第 10 项人工确认。
2. **like 通配符不转义**：keyword 含 `%`/`_` 时按通配符解释（如 `%` 匹配全部）——与基线 `search` 现状一致，参数绑定无注入，本次不增强。
3. **`${param.keyword}` 未转义**：回填存在反射型 XSS 面，与基线 `search.jsp`、`adminPage.jsp` 的现状一致（基线同页登记 admin 屏无鉴权且未转义【人工评审待确认】），按禁止过度开发不修。
4. **count 与行查询无共享快照**：两次独立查询间并发写可致页码与切片轻微不一致——基线既有特性，非本次引入。
5. **搜索无命中时**：total=0，`Page` 派生算术得 totalPage=1、last=0，页码显示"1"且表格为空——`Page` 既有边界行为，非本次引入。
6. **无搜索分支计数缺陷已修复**：原风险"页码多于产品可填页数"已按 §三.1 经用户确认修复（total 改用产品计数）；修复后分类 74 页码将由虚高的 4 页变为真实的 1 页，属预期行为变化。
7. **未执行编译/启动验证**（任务约束禁止终端命令）：新增 Java 代码已逐一对照既有先例（签名/imports/泛型风格）；IDE 对 `ProductServiceImpl` 的"缺实现"诊断出现在编辑中间态，最终文件已含实现；unchecked 警告与项目既有风格一致，无新增错误级问题。请以人工验收清单为准。

## 五、待人工确认事项

- [ ] 按 Spec §9 验收清单（11 项）完成人工验收，重点第 1 项（页码由产品计数驱动）与第 3 项（分页跳转携带 keyword）、第 5/6 项（空白不过滤）。
- [ ] 复核 §三.1 修复效果：分类 74（产品 5 条）无搜索时页码应为 1、第 1 页 5 条；换产品较多的分类（如预置产品多的"电视机"分类）搜索"海信"并跳页，确认 URL 持续携带 `keyword`。
- [ ] 确认 §四.1：目标部署容器对中文 query 的 UTF-8 处理。
