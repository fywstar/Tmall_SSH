# Proposal

## Why

后台产品管理列表（`admin_product_list`）目前只能按分类整表翻页浏览，商品多时无法按名称快速定位目标商品。项目已具备按名称 like 模糊匹配的服务能力（`ProductService.search`，前台 `foresearch` 在用），但后台列表没有搜索入口，属于能力缺口。

## What Changes

- 后台产品管理列表新增**商品名称模糊搜索**：在 `listProduct.jsp` 页面提供搜索框，提交后按**当前分类内**的商品名称进行 `%keyword%` like 模糊匹配（范围已与需求方确认为仅当前分类内，非全站）。
- `admin_product_list` 支持可选 `keyword` 参数：有关键词时按"分类 + 名称模糊"过滤并返回匹配商品的分页切片与正确计数；关键词为空/缺失时回落到现有列表行为，**不改变非搜索路径的任何现有行为**（包括基线已记录的 `ProductAction#list` 计数混用现状，本次不修复、不扩散）。
- 搜索结果沿用现有 `adminPage.jsp` 分页控件；关键词通过 `page.param` 机制在翻页链接中保持，分类上下文保持不变。
- 搜索框为 GET 表单，隐藏域携带 `category.id`，与页面现有分类上下文一致。
- 前台（storefront）搜索 `foresearch` 及其页面不做任何改动。

## Capabilities

### New Capabilities

- `product-name-search`: 后台产品管理列表的商品名称模糊搜索能力——搜索入口、匹配语义（当前分类内、名称 like、通配符语义与基线既有 search 一致）、空关键词回落、分页保持关键词与分类上下文、前台不受影响。

### Modified Capabilities

（无——`product-remark` 的需求不受本次变更影响。）

## Impact

- **代码**：
  - `src/com/caozhihu/tmall/action/ProductAction.java`：`list()` 增加 keyword 分支（改）。
  - `src/com/caozhihu/tmall/service/ProductService.java` / `impl/ProductServiceImpl.java`：新增"分类 + 名称模糊"维度的计数与分页查询方法（增）。
  - `web/admin/listProduct.jsp`：新增搜索框表单（改）。
- **不受影响**：DAO 层、`Page` 工具类、`adminPage.jsp`、前台 `ForeAction#search` / `foresearch`、其余 22 个 `admin_*` 端点、实体类与建表脚本。
- **已知风险（沿用基线既有语义，不额外引入新机制）**：like 通配符（`%`/`_`）不做转义、大小写敏感性由数据库比较规则决定，与基线记录的 `ProductService.search` 行为一致；`keyword` 拼入 `page.param` 时需 URL 编码以保证含中文/特殊字符的翻页链接可用。
