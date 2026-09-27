# Design

## Context

被测对象为 `ProductService` 的查询业务：`BaseService` 继承来的全量/分类维度对（`list()`、`listByPage(Page)`、`total()`、`list(Page, Object parent)`、`total(Object parent)`）与 `ProductServiceImpl` 特有的三个方法（`search(keyword, start, count)`、`total(Category, keyword)`、`list(Page, Category, keyword)`）。动机见 proposal.md —— Why；用例级断言要求见 specs/product-query-unittest/spec.md。

决定实现方式的三个现状约束：

- 项目无构建工具，`StartJetty` 每次启动把 `src/` 下**全部** `.java`（含测试类）编译进 `web/WEB-INF/classes`，测试类必须可编译且依赖只来自既有 jar（openwiki《Testing and Verification Strategy》§1）。
- 基线唯一测试类 `TestTmall` 的形态是 `SpringJUnit4ClassRunner` + `classpath:applicationContext.xml` + 方法级 `@Test @Transactional`，回滚是项目唯一的测试隔离手段（同上 §2、§7）。
- 基线记录的行为缺口：`search` 无显式 `Order`（窗口内容无稳定顺序）、like 通配符不转义、计数与切片是两次独立查询（openwiki《Pagination and Search》）。测试固化这些语义，不修复、不绕开。

## Goals / Non-Goals

**Goals:**

- 用一个测试类覆盖四组查询契约（全量/分类/模糊搜索/分类+名称），断言全部可溯源到基线记录的行为。
- fixture 自建 + 事务回滚，使每个用例独立、可重复、不依赖种子行数。

**Non-Goals:**

- 不测 Action/UI 层（`ProductAction#list` 计数混用属 Action 层现状，不在被测面内）。
- 不覆盖 `fill`/`fillByRow`/`setSaleAndReviewNumber` 等级联回填与任何写路径业务（写路径仅作为 fixture 造数手段使用 `save`）。
- 不为通过测试而修改业务源码、Spring/建表配置或引入新依赖；不修复基线已记录的任何行为缺口。

## Decisions

1. **测试类命名与位置：`src/com/caozhihu/tmall/test/TestProductService.java`**
   与 `TestTmall` 同包同目录，沿用此前 `ProductService` 测试的命名。备选"test/ 顶层独立目录"被否决：项目无独立测试源集，launcher 只编译 `src/`，同包最省配置且符合 SDD Baseline 的放置规则。

2. **形态：`SpringJUnit4ClassRunner` + `classpath:applicationContext.xml` + `@Autowired ProductService` + 方法级 `@Test @Transactional`**
   完全对齐 `TestTmall` 形态，差异仅在于补上 `org.junit.Assert` 断言（junit-4.12 + hamcrest-core-1.3 已 vendored，无需新依赖）。备选"纯 JDBC/H2 直连造数 + 脱离 Spring 调用"被否决：那验证不到 Service 层的 criteria 词汇、反射 `clazz` 配对与事务边界，恰好是基线指出的无测试区。

3. **fixture 策略：每个用例自建两个分类 + 商品，断言只用相对关系**
   分类 A/B 各挂商品，名称按前缀/中缀/后缀命中位置设计（如 `公共商品A`/`B公共商品C`/`办公用品` 风格），通过 `productService.save` 保存。断言只用：保存顺序 ⇒ id 递增的相对顺序、fixture 计数、集合成员、切片相对位置。备选"直接用种子数据断言绝对值"被否决：种子行数与 id 是数据事实而非契约（openwiki《Data and Schema》），耦合它会破坏可重复性。
   - 全量维度：`total()` 断言为"≥ fixture 数且与 `listByPage` 逐页切片之和相等"，排序断言只校验 fixture 元素的相对 id 降序，不校验种子行的绝对位置。

4. **`search` 按集合断言，`list*` 按 id 降序断言**
   `search` 无 `Order`（基线明确"which 20 rows come back is whatever the database produces"），因此断言成员集合、条数、窗口大小，不断言顺序；有 `Order.desc("id")` 的 `list()`/`listByPage`/`list(page, parent)`/`list(page, category, keyword)` 则严格断言相邻元素 id 递减。备选"给 search 也断言顺序"被否决：那是在假设基线不存在的保证。

5. **通配符语义用 `%` 关键词用例固化**
   以 `%` 为关键词调用 `search` 与 `list(page, category, "%")`，断言全命中（like 语义、不转义），与主 spec `product-name-search` 的通配符场景一致。不设计大小写断言：大小写敏感性由 H2 MODE=MySQL 比较规则决定，基线未固定，fixture 名称大小写保持一致以避免误判。

6. **切片参数直接用 `new Page(start, count)`**
   `Page(int, int)` 二参构造在 web 层无调用方，但它是公开构造器，测试用它精确控制 `start`/`count`（含末页余数、跨页偏移），不依赖 `defaultCount=5`。切片拼接结果与全量序列逐位比对。

7. **`list(page, category, "")` 空串用例固定"等同无关键词过滤"**
   Service 层空串即 `like '%%'`，行为等同分类全量。`ForeAction` 的 `null → "null"` 拼接语义属 Action 层，不在被测面内（见 Non-Goals）。

## Risks / Trade-offs

- [launcher 编译耦合：测试类编译失败会阻断应用启动] → import 面严格限定为业务类 + vendored jar（与 `TestTmall` 同级）；任务清单含"仅用 org.junit/org.springframework/com.caozhihu.tmall 导入"的静态自查。
- [id 递增依赖 Hibernate identity 在事务回滚后仍前进而非复用] → 断言只用"保存顺序 ⇒ 相对 id 顺序"，绝不硬编码具体 id 数值，重复运行不受影响。
- [H2 方言下 like 行为与 MySQL 存在差异（大小写/空白）] → 不对大小写做断言；通配符用例只固化"不转义"这一基线明示语义。
- [计数与切片为两次独立查询，理论上可观察到不一致] → 测试运行在单事务内且无并发写，实际无此风险；不为测试引入同步机制。
- [测试类随业务类一起部署进 `web/WEB-INF/classes`] → 与 `TestTmall` 现状一致（基线 §7 已记录），接受该既有形态，不为此改动构建方式。

## Migration Plan

不适用部署/迁移：唯一产物是一个测试文件。回滚方式为删除 `TestProductService.java`；运行方式沿用此前验证过的两种（IDE 直接运行；命令行 `JUnitCore`，JDK 17 需 add-opens，classpath 含 `web/WEB-INF/classes` 与 `web/WEB-INF/lib/*`）。

## Open Questions

（无——测试范围已经需求方确认；其余不确定点均以上述决策固化。）
