# Tasks

## 1. 测试类骨架与公共 fixture

- [x] 1.1 新建 `src/com/caozhihu/tmall/test/TestProductService.java`：`@RunWith(SpringJUnit4ClassRunner.class)` + `@ContextConfiguration("classpath:applicationContext.xml")`，`@Autowired` 注入 `ProductService` 与 `CategoryService`（fixture 造数用）。import 面严格限定为 `org.junit` / `org.springframework` / `com.caozhihu.tmall`，全部来自既有 vendored jar，不引入新依赖、不改任何业务源码与配置。验证：文件位于 `src/com/caozhihu/tmall/test/`，import 清单与 `TestTmall` 同级（仅多 `org.junit.Assert` 等 vendored API）。
- [x] 1.2 实现公共 fixture 辅助方法：用例内创建两个分类 A/B，并在各自分类下保存名称按前缀/中缀/后缀命中位置设计的商品（如"公共商品A""B公共商品C""办公用品"风格），保存后按保存顺序记录期望的相对 id 递增关系。验证：fixture 只通过 `save` 落库、依赖 `@Transactional` 回滚清理，不触碰种子数据，不硬编码任何 id 数值。

## 2. 全量维度查询用例

- [x] 2.1 全量列表与分页切片：调用 `productService.list()` 断言包含全部 fixture 商品且相邻元素 id 严格递减；以 `new Page(start, count)` 分别取首片（start=0）、跨页偏移与末页余数调用 `productService.listByPage(page)`，断言各切片行数正确、切片拼接后与全量 id 降序序列逐位一致。验证：该用例运行通过，且不依赖种子行数（断言全部基于 fixture 相对关系）。
- [x] 2.2 全量计数一致性：调用 `productService.total()` 断言其值等于 2.1 中 `listByPage` 逐页切片行数之和，且不小于 fixture 商品数。验证：该用例运行通过，与 2.1 互为勾稽。

## 3. 分类维度分页用例

- [x] 3.1 分类隔离与切片：以分类 A 调用 `productService.list(page, categoryA)`，断言结果仅含 A 分类 fixture 商品、按 id 降序、行数与起始位置符合 `page.start`/`page.count`；同参数换分类 B 再调一次，断言两分类结果互不混入。验证：该用例运行通过。
- [x] 3.2 分类计数一致性：调用 `productService.total(categoryA)` 断言等于 A 分类 fixture 商品数且与 3.1 的切片行数之和一致；对分类 B 同样断言，证明计数互相隔离。验证：该用例运行通过。

## 4. 名称模糊搜索用例（search）

- [x] 4.1 子串命中与跨分类：在 A、B 两分类分别放置名称含关键词（前缀/中缀/后缀位置）与不含关键词的商品，以窗口参数（start=0，count 取大于命中总数）调用 `productService.search(keyword, start, count)`，断言三个命中商品全部在结果、不含关键词的商品不在结果、结果覆盖两个分类（集合断言，不断言行顺序）。验证：该用例运行通过。
- [x] 4.2 窗口切片：分别以 `search(keyword, 0, n)` 与 `search(keyword, n, 足够大的count)` 调用，断言前者恰返回 n 条且无重复，后者返回余下命中商品，两段并集等于 4.1 的命中集合且交集为空。验证：该用例运行通过。
- [x] 4.3 通配符语义：以 `%` 为关键词调用 `search("%", 0, 足够大的count)`，断言按 like 语义全部商品命中（含 fixture 与种子行合计大于 fixture 数），固化不转义语义。验证：该用例运行通过，且与主 spec `product-name-search` 的通配符场景口径一致。

## 5. 分类+名称维度分页用例

- [x] 5.1 组合过滤：以分类 A + 关键词调用 `productService.list(page, categoryA, keyword)`，断言结果仅含 A 分类中命中的商品（不含 B 分类命中商品、不含 A 分类未命中商品）、按 id 降序、切片行数符合页参数。验证：该用例运行通过。
- [x] 5.2 计数与无命中：调用 `productService.total(categoryA, keyword)` 断言等于 A 分类命中数且与 5.1 切片行数之和一致；换一个无命中的关键词断言 `total` 返回 0、`list` 返回空列表。验证：该用例运行通过。
- [x] 5.3 空关键词回落：以空字符串分别调用 `list(page, categoryA, "")` 与 `total(categoryA, "")`，断言行为与不带关键词的 `list(page, categoryA)` / `total(categoryA)` 完全一致（同为 A 分类全量按 id 降序分页）。验证：该用例运行通过。

## 6. 运行时验收与基线自查

- [x] 6.1 全类运行与可重复性：以 IDE 直接运行（或命令行 `java -cp "web/WEB-INF/lib/*:web/WEB-INF/classes" org.junit.runner.JUnitCore com.caozhihu.tmall.test.TestProductService`，JDK 17 按此前验证方式补 add-opens 参数）连续运行两次，全部用例两次均通过，证明事务回滚隔离有效、无残留数据。验证：两次连续运行全绿（用户已明确授权 agent 执行；2026-09-27 两次运行均 OK (10 tests)，Time 2.274s，全部事务回滚）。
- [x] 6.2 基线溯源自查：对照 `specs/product-query-unittest/spec.md` 的场景清单逐条勾稽用例覆盖；确认业务源码零改动（仅新增测试文件）、无新增依赖、`search` 按集合断言、`%` 用例固化不转义语义、无对基线不存在机制的假设。验证：勾稽清单全部对上，产出简要自查记录（可记录在任务备注或对话总结中，不新增文档文件）。
