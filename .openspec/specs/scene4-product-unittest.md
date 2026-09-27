# Spec：ProductService 商品业务单元测试（scene4-product-unittest）

> 模式：SDD Spec-First 轻量规范。本 Spec 为本次迭代代码变更的唯一设计依据，所有测试用例、断言、前置条件逐条溯源至项目 OpenWiki 基线（`openwiki/`，只读，不做任何修改）。
> 主溯源文档：`openwiki/architecture/service-layer.md`（简称【服务层基线】）、`openwiki/workflows/pagination-and-search.md`（简称【分页与搜索基线】）、`openwiki/testing/verification.md`（简称【验证基线】）、`openwiki/concepts/sdd-baseline.md`（简称【SDD规约】）、`openwiki/operations/data-and-schema.md`（简称【数据基线】）、`openwiki/concepts/domain-model.md`（简称【领域模型基线】）。

## 1. 需求概述

为商品模块 `ProductService` 编写一组单元测试，验证其**商品基础查询、分类过滤、分页查询**行为。测试断言完全对齐基线记录的 Service 接口契约、分页切片语义、分类过滤语义，不脑补任何基线未记载的业务行为。

范围边界（禁止扩散）：仅新增测试代码文件；**禁止修改任何业务源码（Java/JSP/xml/配置文件全部只读）**；不引入新第三方依赖；不做 UI、Action 层测试；不修复任何基线标记的存量 bug（发现的缺陷仅在 Spec 与自查报告登记）。

## 2. 测试范围（In Scope）

仅覆盖 `ProductService` 对外公开接口中与"按分类查询、分页查询"直接相关的读路径方法（基线【服务层基线】§3 方法表 + 【分页与搜索基线】"The service and DAO side of the slice" 一节）：

| # | 方法 | 来源 | 契约要点（基线原文语义） |
|---|---|---|---|
| 1 | `List list()` | `BaseService` | `DetachedCriteria.forClass(clazz)` + `Order.desc("id")`，全量、**id 降序**（【服务层基线】§4："Descending id — newest first — is therefore the layer-wide default ordering"） |
| 2 | `List listByPage(Page page)` | `BaseService` | 同上排序，`page.getStart()`/`page.getCount()` 作为 offset/limit 切片，"**Newest row first**, one slice"（【分页与搜索基线】§"The service and DAO side of the slice"） |
| 3 | `int total()` | `BaseService` | 独立 HQL `select count(*) from <clazz FQN>`，与行查询是**两次独立语句**（【分页与搜索基线】："The count and the slice are two independent queries"） |
| 4 | `List listByParent(Object parent)` | `BaseService` | `Restrictions.eq(uncapitalize(父类简单名), parent)`（`Category`→`"category"`）+ `Order.desc("id")`（【服务层基线】§4 父范围查询表：`productService.listByParent(category)` → `product.category`） |
| 5 | `int total(Object parent)` | `BaseService` | HQL `select count(*) from %s bean where bean.%s = ?0`（同上溯源） |
| 6 | `List list(Page page, Object parent)` | `BaseService` | 父范围切片（【服务层基线】§3："parent-scoped criteria with offset/limit"） |
| 7 | `List list(Page page, Category category, String keyword)` | `ProductService` 自有 | `eq("category")` + `like("name","%"+keyword+"%")` + `Order.desc("id")` + 按 page 切片（`ProductServiceImpl.java#L91-L98` 实现与注释"查询某个分类下名称模糊匹配keyword的产品，限定category范围后like匹配，按page切片返回"；【分页与搜索基线】"the fixed 20-row keyword search"节记载 like 写法 `%"+keyword+"%` 为参数绑定） |
| 8 | `int total(Category category, String keyword)` | `ProductService` 自有 | HQL count，`bean.category = ?0 and bean.name like ?1`（`ProductServiceImpl.java#L101-L111` 与接口注释"统计某个分类下名称模糊匹配keyword的产品个数"） |

## 3. 不测试范围（Out of Scope）

| 不测项 | 理由 |
|---|---|
| `search(String,int,int)` | 属前台无分类过滤的固定 20 行搜索路径（【分页与搜索基线】"The storefront search path"），需求明确本次仅覆盖"按分类查询、分页查询" |
| `fill`/`fillByRow`/`setSaleAndReviewNumber` | 涉及 `productImageService`/`orderItemService`/`reviewService` 跨服务协作与图片路径，超出本次范围 |
| `save`/`delete`/`update`/`get` 写路径与单条读 | 需求仅要求"基础查询、分类过滤、分页查询"；`save` 仅作为测试数据构造手段使用，不作为被测断言对象 |
| Action 层（`ProductAction`/`ForeAction`）、JSP、分页控件 | 需求明确不做 UI、Action 层测试 |
| keyword 大小写敏感性 | 基线明文"case sensitivity is whatever the database's comparison rules are rather than something the repository fixes"（【分页与搜索基线】），属未定义行为，禁止脑补断言 |
| keyword 含 `%`/`_` 通配符的行为 | 基线记载不转义属现状事实，但本次不把通配符泄漏语义固化为测试断言（避免过度开发；风险登记于 §10） |

## 4. 被测组件与接口列表

| 组件 | 文件 | 角色 |
|---|---|---|
| `ProductService`（接口） | `src/com/caozhihu/tmall/service/ProductService.java` | 被测接口（只读） |
| `ProductServiceImpl` | `src/com/caozhihu/tmall/service/impl/ProductServiceImpl.java` | 被测实现（只读；`@Service("productService")`，继承 `BaseServiceImpl` 反射派生 `clazz=Product`） |
| `BaseServiceImpl` | `src/com/caozhihu/tmall/service/impl/BaseServiceImpl.java` | 继承的通用查询实现（只读） |
| `CategoryService` | `src/com/caozhihu/tmall/service/CategoryService.java` | 仅用于测试数据中创建 `Category` 行（项目现有 bean，不新增依赖） |
| `Page` | `src/com/caozhihu/tmall/util/Page.java` | 分页参数载体（只读；使用二参构造器 `Page(int start, int count)`，【分页与搜索基线】载明该构造器存在、web 层未调用，测试直接构造属合法使用） |

## 5. 测试前置条件

1. **运行环境**：JUnit 4.12 + hamcrest-core-1.3 + spring-test-4.3.18.RELEASE，均为 `web/WEB-INF/lib` 既有 vendored jar（【验证基线】§1 表），**零新增依赖**。
2. **测试落位**：`src/com/caozhihu/tmall/test/`——基线【SDD规约】§1/§7 明文 "Tests live in `com.caozhihu.tmall.test`"，且 StartJetty 以单次 `javac` 编译 `src/` 全部 `.java`（【验证基线】§1："There is no separate test source set"），故测试类必须位于该既有 test 包内（即需求所述"test 目录"的项目实际形态），否则不参与编译。
3. **上下文加载**：`@ContextConfiguration("classpath:applicationContext.xml")` 加载真实根上下文；`dbInit` 每次上下文刷新执行 `classpath:sql/tmall_ssh_h2.sql` 建库播种（【验证基线】§2 表），种子库为 `category` 17 行（id 60–83）、`product` 85 行（id 87–962）+ 追加 `remark` 列（【数据基线】§3/§3.1）。
4. **测试数据策略**：每条用例**自建**独立 `Category` 与 `Product`（IDENTITY 主键，save 后 id 立即可读——【领域模型基线】§"identity generation, so an INSERT executes while save() runs and the generated key is readable immediately afterwards"），不依赖种子行数；全部用例方法级 `@Transactional` 回滚（TestTmall 同款，【验证基线】§2："Spring's test transaction support rolls them back"），测试不污染数据。
5. **自建数据可见性**：`save` 经 `BaseServiceImpl.save`（`@Transactional`）加入测试事务，同事务内 criteria/HQL 查询可见（Hibernate IDENTITY 立即 insert + FlushMode.AUTO 语义，TestTmall 的 save-then-query 同款机制）。
6. **分类-产品外键**：`product.cid → category(id)`（【数据基线】§3 表），测试中先存 `Category` 再存其 `Product`。

## 6. 测试用例（输入 / 预期输出）

测试类：`com.caozhihu.tmall.test.TestProductService`（命名跟随既有 `TestTmall` 的 `Test` 前缀惯例）。方法级 `@Test @Transactional`；`@Autowired ProductService productService; @Autowired CategoryService categoryService;`。

约定记号：`C1`/`C2` 为自建分类；`p1..pn` 为自建产品（依次 save，id 递增）；关键词均为不含 `%`/`_`/大小写歧义的普通中文子串。

| # | 用例 | 输入（自建数据） | 预期输出（断言） | 基线溯源 |
|---|---|---|---|---|
| U1 | `listAllReturnsAllProductsInDescIdOrder` | C1 下依序 save `p1,p2,p3` | `list()` 非空；`size == total()`（两次独立语句在无并发事务中一致）；全列表按 id 严格降序；`p1..p3` 的 id 均在结果中 | 【服务层基线】§4 `list()` 契约；§3 total 独立 HQL；【分页与搜索基线】"two independent queries" |
| U2 | `listByPageSlicesNewestFirst` | C1 下 save 3 产品 | `listByPage(page(0,2))` 返回 2 条且等于 `list()` 的前 2 条（同一降序全序的首窗口）；`listByPage(page(2,2))` 返回 2 条且等于 `list()` 的第 3、4 条（自建 3 条 + 种子 ≥85 条，全序长度足够取窗口） | 【分页与搜索基线】"`listByPage(page)` — … `Order.desc("id")`, `findByCriteria(dc, page.getStart(), page.getCount())`. **Newest row first**, one slice"；切片为 SQL 级 limit/offset |
| U3 | `listByParentReturnsOnlyThatCategory` | C1 save 3 产品、C2 save 1 产品 | `listByParent(C1)` 返回 3 条，每条 `getCategory().getId() == C1.getId()`；id 降序；不含 C2 产品 id；`total(C1) == 3`、`total(C2) == 1`（`BaseService.total(Object)`） | 【服务层基线】§4 父范围查询表（`product.category` 属性约定）与 §3 `total(Object)` HQL 形态 |
| U4 | `listPageParentSlicesWithinCategory` | C1 save 3 产品 | `list(page(0,2), C1)` 返回 2 条且等于 `listByParent(C1)` 前 2 条；`list(page(2,2), C1)` 返回 1 条且等于第 3 条 | 【服务层基线】§3 "`list(Page, Object)` — parent-scoped criteria with offset/limit" |
| U5 | `listByCategoryAndKeywordFiltersWithinCategory` | C1：`测试产品甲`、`测试产品乙`、`普通产品`；C2：`测试产品丙`（跨分类对照） | `list(page(0,5), C1, "测试")` 返回 2 条：全含"测试"、全属 C1、id 降序；**不含 C2 产品**（分类过滤优先）；`total(C1, "测试") == 2` | `ProductService.java#L18-L22` 接口注释；`ProductServiceImpl.java#L91-L111` 实现（eq+like+desc+切片 / count HQL） |
| U6 | `emptyKeywordMeansNoNameFilter` | 同 U5 | `list(page(0,5), C1, "")` 与 `listByParent(C1)` 同集合同序（空 keyword 生成 like `%%` 匹配全部非空 name）；`total(C1, "") == 3` | 实现字面量 `"%" + keyword + "%"`（`ProductServiceImpl.java#L95/#L106`）；场景3 spec §4 同款写法引用；此为 Service 层 like 契约的自然推论，非 Action 层空白判定约定 |
| U7 | `noMatchReturnsEmptyListAndZeroCount` | 同 U5 | `list(page(0,5), C1, "不存在词xyz")` 非 null 且 `isEmpty()`；`total(C1, "不存在词xyz") == 0`（count HQL 空结果走 `return 0` 分支，`BaseServiceImpl.java#L63-L64` 同款防御） | `BaseServiceImpl.total*` 空结果返回 0 的既有形态；findByCriteria 返回空列表非 null |
| U8 | `paginationAcrossPagesIsDisjointAndComplete` | C1 save 5 条名称均含"测试"的产品（`测试产品一`~`测试产品五`，依序 save）+ 1 条不含 | 以 `count=2` 连续切片 `list(page(0,2)/page(2,2)/page(4,2), C1, "测试")`：三页行数 2/2/1；三页 id 集合并集等于 `total(C1,"测试")==5` 对应的全集、两两无重复；每页 id 降序，且第一页首条 id 为 5 条中最大（跨页序连续） | 【分页与搜索基线】切片 limit/offset 语义 + desc("id") 排序；count 与行查询配对（total 与行查询同一过滤条件，pairing 一致性） |
| U9 | `outOfRangeStartReturnsEmptyList` | C1 save 3 产品（含 2 条命中"测试"） | `listByPage(page(total(), 5))` 非 null 且空（offset 越界 SQL limit/offset 自然返回空集）；`list(page(2, 2), C1, "测试")` 非 null 且空（命中 2 条，start=2 恰越界） | 切片经 `ServiceDelegateDAO.findByCriteria(dc, firstResult, maxResults)` → SQL 级 limit/offset（【分页与搜索基线】"The slice itself is not performed in Java"） |

> 断言强度说明：全部断言只使用基线明文记载或源码字面实现可直接推出的行为；对基线标注【人工评审待确认】或属未定义行为的点（大小写、通配符、并发下 count/ slice 漂移），不设断言。

## 7. 测试代码落位与形态

- 文件：`src/com/caozhihu/tmall/test/TestProductService.java`（**唯一新增文件**）。
- 形态完全跟随既有唯一测试类 `TestTmall.java`（【SDD规约】§7："Tests live in `com.caozhihu.tmall.test` and use one pattern: JUnit 4 with `@RunWith(SpringJUnit4ClassRunner.class)` and `@ContextConfiguration("classpath:applicationContext.xml")`, `@Test @Transactional` methods, and `@Autowired` beans"）：
  - `@RunWith(SpringJUnit4ClassRunner.class)` + `@ContextConfiguration("classpath:applicationContext.xml")`；
  - `@Autowired` 注入接口（`ProductService`、`CategoryService`）；
  - 每个用例方法 `@Test @Transactional`（方法级，与 TestTmall 一致）；
  - 断言仅用 `org.junit.Assert`（JUnit 4.12 自带，hamcrest-core-1.3 在 lib）；
  - 私有辅助方法 `saveCategory`/`saveProduct` 仅做数据构造，不承载断言逻辑；
  - 注释使用简体中文、风格同现有代码。

## 8. 项目规约适配说明

1. **零业务源码改动**：仅新增 1 个测试文件；`Page`/`BaseService`/`BaseServiceImpl`/`ProductService(Impl)`/`applicationContext.xml`/`web.xml`/SQL 脚本一律只读。
2. **测试落位即需求"test 目录"的项目实际形态**：`src/com/caozhihu/tmall/test/`（基线载明的唯一测试包）。不新建顶层 `test/` 目录——基线明文无独立测试源集，顶层新目录不参与 launcher 编译也不在 `tmall_ssh.iml` 源根内。
3. **不引入新依赖**：JUnit/spring-test/hamcrest 均为 vendored jar；不添加 mock 框架（测试走真实 H2 上下文，与 TestTmall 同款集成式单元测试风格）。
4. **启动编译兼容**：测试随 `src/` 一起被 StartJetty 编译，仅 import vendored classpath 内的类，不会破坏启动编译（【验证基线】§1："anything a test imports has to stay on the `web/WEB-INF/lib` classpath"）。
5. **t2p 等项目特有语法**：本测试不涉及 Action 层，无 t2p 使用点；不触碰基线载明的任何字符串契约（endpoint/result name/bean name/session key）。

## 9. 人工验收检查清单

运行方式（基线【验证基线】§3 同款命令，需先确保 `web/WEB-INF/classes` 存在，本次开发过程不执行任何终端命令）：

```bash
java -cp "web/WEB-INF/lib/*:web/WEB-INF/classes" org.junit.runner.JUnitCore com.caozhihu.tmall.test.TestProductService
```

或 IDE 内直接运行测试类（`src` 为源根、65 个 jar 均在模块类路径，【验证基线】§3）。

- [ ] 1. 9 条用例全部绿色（U1–U9），无异常栈输出 `ClassNotFoundException`（若出现即 `clazz` 反射派生 pairing 被破坏，属业务侧风险触发）；
- [ ] 2. 运行后经 H2 控制台（`jdbc:h2:mem:tmall_ssh;MODE=MySQL;DB_CLOSE_DELAY=-1`，端口 8082 需随应用启动）复查 `SELECT COUNT(*) FROM product;` 仍为 85——测试回滚未污染数据；
- [ ] 3. `git status` 确认本次仅新增 `src/com/caozhihu/tmall/test/TestProductService.java` 与 `.openspec/specs/scene4-product-unittest*.md`，无业务源码改动；
- [ ] 4. StartJetty 正常启动出横幅（测试类随编译通过，未破坏启动编译）；
- [ ] 5. 多次重复运行测试类结果稳定（自建数据 + 回滚保证幂等）。

## 10. 风险登记（仅登记，不在本次修复）

| # | 风险 | 基线出处 | 处置 |
|---|---|---|---|
| R1 | `ProductAction#list` 的 total 取自 `propertyService.total(category)`（属性计数驱动产品分页页码，可翻出空表格） | 【分页与搜索基线】"`ProductAction#list` mixes them"【人工评审待确认】 | 不测不修；Action 层本就不在范围 |
| R2 | like 不转义 `%`/`_`，keyword 含通配符时语义为模式匹配 | 【分页与搜索基线】"The keyword is interpolated into a LIKE pattern, not escaped" | 用例关键词避开通配符；不把现状固化为契约断言 |
| R3 | count 与行查询为两次独立语句，无共享快照 | 【分页与搜索基线】"three behavioural notes" | 测试运行于单事务无并发，不模拟并发漂移 |
| R4 | `BaseServiceImpl` 反射派生 `clazz` 失败时仅打印、首次查询才失败 | 【服务层基线】§2 失效模式 | U1–U9 任一首次查询失败即暴露该风险（测试的间接价值） |
| R5 | 测试类随业务代码编译部署进 `web/WEB-INF/classes`（现状单编译单元机制的副作用） | 【验证基线】§7 "test code ships" | 现状事实，随既有 TestTmall 一致接受；"test 类是否应部署"为基线遗留评审项 |
| R6 | `search` 无排序、固定 20 行 | 【分页与搜索基线】storefront search 节 | 不在范围，不测不修 |
