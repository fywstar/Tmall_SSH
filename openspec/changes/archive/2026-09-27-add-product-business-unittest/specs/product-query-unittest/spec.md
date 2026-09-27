# Spec Delta

## Purpose

为 `ProductService` 查询业务建立单元测试能力：以自动化用例固化全量分页、分类分页、名称模糊搜索、分类+名称分页四组查询契约，使 `product-name-search` 等既有能力的 Service 层行为在业务源码零改动的前提下获得可重复的回归防线。测试形态与隔离方式完全跟随项目基线（JUnit 4 + Spring 上下文 + 事务回滚）。

## ADDED Requirements

### Requirement: 测试形态对齐项目既有测试方式

查询业务单元测试 SHALL 为 `src/com/caozhihu/tmall/test/` 下的 JUnit 4 测试类，使用 `SpringJUnit4ClassRunner` 加载 `classpath:applicationContext.xml`，通过 `@Autowired` 注入被测 Service；每个用例 SHALL 标注方法级 `@Test @Transactional` 以保证回滚；SHALL 只使用既有 `web/WEB-INF/lib` 中的依赖，SHALL NOT 引入新第三方依赖或修改业务源码与 Spring/建表配置。

#### Scenario: 加载真实 Spring 上下文并注入被测 Service

- **WHEN** 运行该测试类
- **THEN** 加载的是与 `web/WEB-INF/web.xml` 相同的根上下文（H2 数据源、dbInit 建表、SessionFactory、事务管理器），`ProductService` 以 `@Autowired` 注入而非手工构造

#### Scenario: 用例结束后不遗留任何数据

- **WHEN** 任一用例执行完成（无论通过或失败）
- **THEN** 该用例事务被回滚，用例中保存的 fixture 分类与商品不残留，同一 JVM 内重复运行结果一致

### Requirement: 全量维度查询契约

测试 SHALL 用带断言的用例固化全量维度行为：`list()` SHALL 返回全部商品且按 id 降序排列；`listByPage(Page)` SHALL 按页参数返回 id 降序的对应切片；`total()` SHALL 返回全量商品总数，且 SHALL 与 `listByPage` 逐页切片之和一致。

#### Scenario: 全量列表按 id 降序

- **WHEN** 用例内创建若干商品（id 递增）后调用 `productService.list()`
- **THEN** 返回结果包含全部 fixture 商品，且相邻元素 id 严格递减（新商品在前）

#### Scenario: 全量分页切片与计数一致

- **WHEN** 以 fixture 商品数构造 `Page`（count 取小于总数正值，start 取 0 与末页偏移）分别调用 `listByPage`
- **THEN** 各切片行数等于预期（末页为余数），切片拼接后与全量 id 降序序列一致，`total()` 返回 fixture 商品总数

### Requirement: 分类维度分页查询契约

测试 SHALL 固化 `list(Page, Object parent)` 与 `total(Object parent)` 的分类隔离行为：结果 SHALL 只包含该分类下的商品并按 id 降序排列，切片 SHALL 由 `page.start`/`page.count` 决定，分类计数 SHALL 与该分类切片总数一致，SHALL NOT 混入其他分类的商品。

#### Scenario: 分类隔离与 id 降序切片

- **WHEN** 用例内创建两个分类并各自挂若干商品，以其中一个分类调用 `productService.list(page, category)`
- **THEN** 返回结果仅含该分类商品、按 id 降序、行数与起始位置符合 `page.start`/`page.count`

#### Scenario: 分类计数与切片一致

- **WHEN** 对同一分类分别调用 `productService.total(category)` 与按全量行数多页切分的 `productService.list(page, category)`
- **THEN** `total` 等于该分类 fixture 商品数，且等于各页切片行数之和；另一分类的计数不受本分类 fixture 影响

### Requirement: 名称模糊搜索契约（全站维度）

测试 SHALL 固化 `search(keyword, start, count)` 的基线语义：匹配 SHALL 为名称子串包含（关键词任意位置命中）；范围 SHALL 为全站（SHALL NOT 按分类过滤）；结果窗口 SHALL 由 `start`/`count` 参数决定；结果 SHALL NOT 依赖稳定排序（断言按集合成员与窗口大小，SHALL NOT 断言行顺序）；通配符语义 SHALL 与基线 like 行为一致（不转义）。

#### Scenario: 子串命中且跨分类

- **WHEN** 两个不同分类下存在名称分别以关键词为前缀、中缀、后缀的商品，以及名称不含关键词的商品，以该关键词调用 `search(keyword, 0, 足够大的count)`
- **THEN** 三个命中商品全部出现在结果中，不含关键词的商品不出现，且结果不限于单一分类

#### Scenario: 窗口切片生效

- **WHEN** 命中商品数超过窗口时，分别以 `search(keyword, 0, n)` 与 `search(keyword, n, 足够大的count)` 调用
- **THEN** 前者返回 n 条且无重复，后者返回余下命中商品，两段并集为全部命中集合且无交集

#### Scenario: 通配符按 like 语义解释

- **WHEN** 以 `%` 作为关键词调用 `search`
- **THEN** 该关键词按 like 通配符语义解释（全部商品命中），系统不对其进行转义

### Requirement: 分类+名称维度分页查询契约

测试 SHALL 固化 `list(Page, Category, keyword)` 与 `total(Category, keyword)` 的组合过滤行为：结果 SHALL 同时满足分类相等与名称子串命中、按 id 降序排列并按页参数切片；`total` SHALL 为该分类内名称命中商品数并与切片总数一致；无命中 SHALL 返回空列表与 0；空字符串关键词 SHALL 等同该分类的全量分页查询。

#### Scenario: 分类隔离叠加名称子串过滤

- **WHEN** 分类 A 与分类 B 中都存在名称含同一关键词的商品，以分类 A + 关键词调用 `productService.list(page, categoryA, keyword)`
- **THEN** 结果仅含分类 A 中命中的商品（不含分类 B 命中商品、不含分类 A 未命中商品），按 id 降序排列

#### Scenario: 计数、切片与无命中

- **WHEN** 对同一分类与关键词分别调用 `total(categoryA, keyword)` 与 `list(page, categoryA, keyword)`
- **THEN** `total` 等于命中商品数且与切片行数之和一致；换一个无命中的关键词时 `total` 返回 0、`list` 返回空列表

#### Scenario: 空字符串关键词等同分类全量

- **WHEN** 以空字符串作为关键词调用 `productService.list(page, categoryA, "")` 与 `productService.total(categoryA, "")`
- **THEN** 行为与不带关键词的分类维度查询一致（该分类全量商品按 id 降序分页，计数为该分类商品总数）

### Requirement: 测试数据的自建与基线溯源

测试用例 SHALL 在用例内自建所需分类与商品 fixture（含名称命中位置的前缀/中缀/后缀设计），SHALL NOT 依赖种子脚本的具体行数或内容；每条用例的断言 SHALL 溯源到基线记录的 Service 行为（openwiki《Pagination and Search》《Service Layer》及主 specs），SHALL NOT 为通过测试而假设基线不存在的机制；已知基线缺口（`search` 无显式排序、计数与切片为两次独立查询）SHALL 作为约束体现在断言设计中，SHALL NOT 被当作缺陷修复。

#### Scenario: fixture 设计使断言不依赖种子数据

- **WHEN** 任一用例在种子数据之上执行
- **THEN** 所有计数与排序断言只基于用例自建 fixture 的相对关系（如相对 id 顺序、fixture 计数、集合成员），种子行的增减不影响用例通过

#### Scenario: 无排序路径按集合断言

- **WHEN** 对 `search` 结果进行断言
- **THEN** 断言其成员集合、条数与窗口大小，不断言行顺序
