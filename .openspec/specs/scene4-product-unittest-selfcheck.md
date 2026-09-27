# 自查报告：ProductService 商品业务单元测试（scene4-product-unittest）

> 状态：**运行时验收已通过**（2026-09-27 JUnitCore 实机运行 `OK (9 tests)`，Time: 1.842s；JDK 17.0.20.1，命令含 StartJetty.openJdk9PlusModules 同款 add-opens 清单；逐用例事务均正常回滚，日志无 testException）。
> 关联文档：`.openspec/specs/scene4-product-unittest.md`（下称 Spec）；基线：`openwiki/`（只读，未做任何修改）。

## 1. 变更清单（实际落盘）

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `.openspec/specs/scene4-product-unittest.md` | 新增 | Spec 文档（先于代码产出） |
| `src/com/caozhihu/tmall/test/TestProductService.java` | 新增 | **唯一代码文件**，9 条用例（U1–U9） |
| `.openspec/specs/scene4-product-unittest-selfcheck.md` | 新增 | 本自查报告 |

业务源码（Java/JSP/xml/SQL/配置）**零改动**；`openwiki/` 基线**零改动**；`web/WEB-INF/lib` 零改动。

## 2. 合规项

| # | 约束 | 合规情况 |
|---|---|---|
| 1 | 基线只读：严禁修改 openwiki/ | 合规——未读写删除任何 openwiki/ 文件，仅 Read 检索溯源 |
| 2 | 业务源码完全只读 | 合规——唯一代码变更为测试文件；ProductService(Impl)/BaseServiceImpl/Page/pojo/xml/SQL 全部只读 |
| 3 | 严格溯源，禁止脑补 | 合规——Spec §6 每条用例逐条标注基线出处（【服务层基线】§3/§4、【分页与搜索基线】切片与 like 契约、TestTmall 源码形态）；断言仅覆盖基线明文记载行为 |
| 4 | 使用现有测试框架，零新增依赖 | 合规——JUnit 4.12 + hamcrest-core-1.3 + spring-test-4.3.18.RELEASE 均为 vendored jar（openwiki/testing/verification.md §1）；未引入 mock 框架 |
| 5 | 遵循现有测试目录结构、命名、编码习惯 | 合规——落位 `src/com/caozhihu/tmall/test/`（openwiki/concepts/sdd-baseline.md §7 明文测试包）；类名 `Test` 前缀对齐 `TestTmall`；`@RunWith(SpringJUnit4ClassRunner.class)` + `@ContextConfiguration("classpath:applicationContext.xml")` + 方法级 `@Test @Transactional` + `@Autowired` 全部同款；注释简体中文 |
| 6 | 仅覆盖按分类查询、分页查询；不做 UI/Action 层 | 合规——In Scope 仅 `list()/listByPage/total()/listByParent/list(Page,Object)/total(Object)/list(Page,Category,String)/total(Category,String)`；`search`、`fill*`、写路径、Action、JSP 均在 Out of Scope |
| 7 | SDD Spec-First：先 Spec 后代码 | 合规——产出顺序 Spec → 测试代码 → 自查报告 |
| 8 | 禁止过度开发，不修存量 bug | 合规——发现的风险（R1–R6）仅在 Spec §10 与本报告 §4 登记，未触碰业务源码 |
| 9 | 全程简体中文、禁止执行终端命令 | 合规——文档与代码注释中文；未运行任何 git/openwiki/编译/启动命令 |

## 3. 潜在违规项与决策说明

| # | 事项 | 说明 |
|---|---|---|
| D1 | 需求字面"仅新增 test 目录下单元测试文件" vs 项目实际形态 | 项目无独立顶层 test 源集：基线明文 "Tests live in `com.caozhihu.tmall.test`"，且 StartJetty 单次编译 `src/` 全部 `.java`、`tmall_ssh.iml` 仅声明 `src` 为源根（openwiki/testing/verification.md §1、sdd-baseline.md §1/§7）。若新建顶层 `test/` 目录则不参与编译、不在类路径。故按"严格遵循项目现有测试目录结构"（需求代码开发要求第 1 条）落位 `src/com/caozhihu/tmall/test/`，与 TestTmall 同目录。**此为依基线作出的规范解释，请人工确认接受。** |
| D2 | Spec 文件名连字符规范化 | 需求中 `scene4‑product‑unittest.md` 含非断行连字符（U+2011），落盘采用 ASCII 连字符 `scene4-product-unittest.md`，与既有 `scene2-product-remark.md`、`scene3-product-search.md` 命名风格一致 |
| D3 | 测试注入 `CategoryService` 造数据 | 项目现有 bean（非新依赖）；`product.cid → category(id)` 外键（openwiki/operations/data-and-schema.md §3）要求先存分类再存产品 |
| D4 | 使用 `Page(int start, int count)` 二参构造器 | 构造器公开存在（Page.java#L16-L18）；基线载明"web 层未调用"仅是现状描述，测试直接构造分页参数属合法使用，不触碰 `defaultCount=5` |
| D5 | U6 空 keyword 用例（like `%%` 语义） | 源自实现字面量 `"%" + keyword + "%"`（ProductServiceImpl.java#L95/#L106），为 Service 层 like 契约的自然推论，非脑补"空白不过滤"的 Action 层约定（后者属 scene3 范围，未在此断言） |
| D6 | 测试数据可见性依赖同事务查询 | save 经 `BaseServiceImpl.save`（@Transactional）加入测试事务；IDENTITY 主键立即 insert（domain-model.md 明文"generated key is readable immediately afterwards"），同事务 criteria/HQL 可见。与 TestTmall save-then-query 同机制，但该方法级语义基线未写成契约，属合理推断，登记待运行时验证 |

## 4. 风险点说明（仅登记，未修复）

| # | 风险 | 基线出处 | 对本次测试的影响与处置 |
|---|---|---|---|
| R1 | `ProductAction#list` 用 `propertyService.total(category)` 计数驱动产品分页（页码虚增、可翻空表格） | pagination-and-search.md "ProductAction#list mixes them"【人工评审待确认】 | Action 层不在范围，不测不修 |
| R2 | like 不转义 `%`/`_` 通配符 | pagination-and-search.md "The keyword is interpolated into a LIKE pattern, not escaped" | 用例关键词均不含通配符；不把现状固化为断言 |
| R3 | count 与行查询两次独立语句、无共享快照 | pagination-and-search.md "three behavioural notes" | 测试单事务无并发，U1 的一致性断言在该前提下成立 |
| R4 | `BaseServiceImpl` 反射派生 `clazz` 失败仅打印、首次查询才失败 | service-layer.md §2 | 任一用例首次查询失败即暴露该 pairing 风险（测试的间接守护价值） |
| R5 | 测试类随业务代码编译部署进 `web/WEB-INF/classes`（"test code ships"） | verification.md §7 | 现状机制副作用，与 TestTmall 一致接受；是否拆分测试源集为基线遗留评审项 |
| R6 | `search` 无排序、固定 20 行窗口 | pagination-and-search.md storefront search 节 | 不在范围，不测不修 |
| R7 | ~~本次未能实际运行测试~~ **已消除**：2026-09-27 实机运行通过 `OK (9 tests)`（Time: 1.842s），完整命令与结果见 §5 | 本迭代约束 | 运行时验证已完成，D6 的同事务可见性推断一并得到实证 |

## 5. 运行时验收记录（2026-09-27）

实际执行命令（项目根目录，JDK 17.0.20.1，add-opens 清单照搬 `StartJetty.openJdk9PlusModules()`，`TestProductService.class` 已在 `web/WEB-INF/classes` 中）：

```bash
java --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED \
     --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED \
     --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED \
     --add-opens=java.base/java.nio.file=ALL-UNNAMED --add-opens=java.base/java.sql=ALL-UNNAMED \
     --add-opens=java.base/java.text=ALL-UNNAMED \
     -cp "web/WEB-INF/lib/*:web/WEB-INF/classes" \
     org.junit.runner.JUnitCore com.caozhihu.tmall.test.TestProductService
```

结果：`Time: 1.842` / `OK (9 tests)`；Spring 事务日志显示 9 条用例均 `testException = [null]` 且逐条 `Rolled back transaction for test`。

- [x] 9 条用例全部绿色（U1–U9），无 `ClassNotFoundException`（R4 pairing 风险未触发）；
- [x] 事务逐条回滚（无 `testException`），数据无污染（R7/D6 实证）；
- [ ] 接受 D1（测试落位 `src/com/caozhihu/tmall/test/` 的基线解释）；
- [ ] `git status` 复核仅 3 个新增文件、零业务源码改动；
- [ ] StartJetty 启动出横幅（测试类未破坏启动编译）；
- [ ] 多次重复运行结果稳定（回滚幂等，运行 1 次已通过，其余项待人工复核）。
