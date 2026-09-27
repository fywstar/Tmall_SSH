# Proposal

## Why

商品查询业务目前没有自动化单元测试：基线唯一的测试类 `TestTmall` 不做任何断言且绕过 Service 层（openwiki《Testing and Verification Strategy》明确记录"No service-layer test"覆盖缺口）。刚落地的分类+名称模糊搜索能力（`product-name-search`）的 Service 层行为目前只有人工验收背书；此前曾为 `ProductService` 建立过一组查询单元测试，但该文件已不在工程内，查询业务的回归防线归零。需要在业务源码零改动的前提下重建这层自动化验证。

## What Changes

- 新增商品查询业务单元测试类 `TestProductService`（`src/com/caozhihu/tmall/test/`），形态对齐既有 `TestTmall`：JUnit 4 + `SpringJUnit4ClassRunner` + `classpath:applicationContext.xml` + 方法级 `@Test @Transactional` 回滚；在既有形态上补充真实断言（`org.junit.Assert`，均在 vendored jar 内）。
- 用例覆盖四组查询契约（与基线记录的 Service 行为逐条对齐）：
  - **全量维度**：`list()` 全量按 id 倒序；`listByPage(Page)` 分页切片；`total()` 全量计数（重建此前缺失用例）。
  - **分类维度**：`list(Page, Object parent)` 分类隔离 + id 倒序 + 切片；`total(Object parent)` 分类计数与切片一致。
  - **模糊搜索**：`search(keyword, start, count)` 全站（跨分类）名称子串匹配、like 通配符语义、固定窗口切片、无排序保证（按集合成员断言而非顺序）。
  - **分类+名称维度**：`list(Page, Category, keyword)` 与 `total(Category, keyword)` 分类隔离 + 子串命中 + id 倒序 + 切片 + 计数一致；空字符串关键词等同分类全量；无命中返回 0。
- 测试数据在用例内自建 fixture（分类 + 商品），依赖 `@Transactional` 回滚保证隔离与可重复运行，不依赖种子行数。
- 明确不改动：业务源码零修改；不做 UI/Action 层测试；不覆盖 `fill`/`fillByRow`/`setSaleAndReviewNumber` 等级联回填与写路径；不引入任何新第三方依赖。

## Capabilities

### New Capabilities

- `product-query-unittest`: ProductService 查询业务单元测试能力——测试范围与方式、全量/分类/模糊搜索/分类+名称四组契约的用例级断言要求、测试数据隔离与可重复运行要求、基线溯源与不越界约束。

### Modified Capabilities

（无——`product-name-search` 与 `product-remark` 的需求不变；本变更只新增对既有行为的自动化验证。）

## Impact

- **新增代码**：仅 `src/com/caozhihu/tmall/test/TestProductService.java` 一个测试文件。注意 launcher 每次启动会编译 `src/` 下全部源码，该文件必须可编译且 import 全部来自既有 `web/WEB-INF/lib`（junit-4.12、hamcrest-core-1.3、spring-test-4.3.18 已 vendored）。
- **不受影响**：业务源码、`applicationContext.xml`、建表脚本、web 层、其余测试类。
- **已知风险（沿用基线既有语义，不额外引入新机制）**：like 通配符（`%`/`_`）不转义、大小写敏感性由 H2 比较规则决定、`search` 无显式排序（窗口内容不稳定）→ 断言按集合成员而非顺序；命令行以 JUnitCore 运行时沿用此前验证过的方式（JDK 17 需 add-opens 参数）；测试事务回滚，不产生持久数据。
