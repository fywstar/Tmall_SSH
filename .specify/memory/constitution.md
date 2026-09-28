# Tmall_SSH Constitution

## Core Principles

### I. 基线绝对优先（Baseline-First）
本项目以 `openwiki/` 生成的基线文档为唯一权威规范来源。所有业务代码、字段设计、页面写法、调用方式（含 t2p 等项目特有语法）**MUST** 完全跟随基线 Wiki，禁止脑补、禁止自创项目不存在的机制。源码与测试才是最终判定权威；brief 中的未知项与 review 项是待验证缺口，而非自动需求。**openwiki/ 基线本身为只读**：严禁手工修改、删除、更新或重新生成；如有偏差，改源文件让定时工作流重新生成。

### II. 业务源码只读（Source Read-Only）
业务 Java（53 个）、JSP、web.xml、目录结构、Action/Service/DAO/实体类代码 **MUST** 保持零改动。允许的新增与微调仅限：新增启动类、复制 jar 到 `web/WEB-INF/lib`、修改 spring-hibernate 配置 xml、微调建表 SQL 脚本。**MUST NOT** 重构旧代码、**MUST NOT** 优化无关逻辑、**MUST NOT** 过度开发（只实现当前需求，不扩功能）。注意：此冻结规则只描述原始迁移基线——后续 scene2-product-remark 等 spec 驱动迭代在基线溯源后修改了 Product.java、两个 admin JSP 和适配 SQL。

### III. 技术栈与版本冻结（Tech-Lock, NON-NEGOTIABLE）
不得升级或替换项目原有的 Spring 4.3.18 / Struts2 2.5.14.1 / Hibernate 5.3.7 jar 版本，原 `web/WEB-INF/lib` 下所有 jar 原样保留。新引入运行时 **MUST** 固定：Jetty 9.4.x（禁止 10+），H2 1.4.x（禁止 2.x）。H2 JDBC URL **MUST** 追加参数 `MODE=MySQL;DB_CLOSE_DELAY=-1`。原 MySQL 数据源与 MySQL5Dialect 配置 **MUST** 完整注释保留，不得删除；切回 MySQL 时对调两段配置即可。

### IV. 命名不可漂移（Naming Invariants）
本项目 `struts.xml` 不声明任何 `<action>`，全部端点名（47 个）、结果名、重定向参数、OGNL 可绑定属性、session key、图片文件名、seed id 与类型/状态/响应字面值 **仅以字符串字面量**分散在 Java 注解、JSP/jQuery、SQL seed 脚本、启动类与拦截器白名单中——**无一由编译器校验，无一有 XML schema 约束，无一有启动时检查**。任何字面量改名 **MUST** 在同一个 commit 内同步修改其**六类载体**（Java 注解与常量、JSP/jQuery 链接与 Ajax url、`src/sql/tmall_ssh_h2.sql` 中的 seed 值、`StartJetty.java` 的 rewrite/banner、`web/WEB-INF/web.xml`、所有 `AGENTS.md`/`STARTUP.md`/`README.md` 文档）。

**关键命名清单**（基线枚举，改动时逐项 grep）：
- **端点名**：47 个 `@Action` 值（`ForeAction` 24 个 `fore*`，admin 7 类 23 个 `admin_*`），URL 家族前缀 `/fore*` 与 `/admin_*` 为拦截器门槛 load-bearing 约定
- **结果名与重定向**：36 个 `@Result` 名（`Action4Result` 共享块），10 个 redirect 带 `${...}` OGNL 参数路径——两端同时改动
- **Session key 四值**：`user`（登录态，AuthInterceptor + 2 业务拦截器 + ForeAction 8 个方法 + top.jsp）、`orderItems`（购物车 checkout 中间态，唯一 writer `ForeAction#buy`）、`cs`（分类名条，CategoryNamesBelowSearchInterceptor 每次 `/fore*` 重算）、`cartTotalItemNumber`（购物车徽章，CartTotalItemNumberInterceptor 每次 `/fore*` 重算）
- **图片命名**：`<id>.jpg` 强制，目录 `img/category/`、`img/productSingle/`、`img/productSingle_small/`（56×56）、`img/productSingle_middle/`（217×190）、`img/productDetail/`、`img/lunbo/`——writer（`CategoryAction#add/update`、`ProductImageAction#add`）+ 所有 JSP reader 同步改
- **ProductImage 类型常量**：`type_single` / `type_detail`——三位置同步：Java 常量（`ProductImageService`）、JSP hidden input（`listProductImage.jsp`）、seed SQL 每行列值
- **Order status 六值**：`waitPay` / `waitDelivery` / `waitConfirm` / `waitReview` / `finish` / `delete`——Java 常量（`OrderService`）+ JSP `<c:if test="${o.status=='...'}">`（`boughtPage.jsp`、`listOrder.jsp`）同步改
- **AJAX 响应体**：`success` / `fail`——`success.jsp` / `fail.jsp` 里的裸字，前端 JS 据此判断登录/加购物车/改属性
- **Interceptor stack typo**：`auth-dafault`（不是 default）——`struts.xml` 的 `<interceptor-stack name="auth-dafault">` 和 `<default-interceptor-ref name="auth-dafault">` 两处拼写 **MUST** 保持同一 typo，**MUST NOT** "修正"
- **Seed 自增校准**：5 表固定值 `category=84`、`product=963`、`productimage=10211`、`property=258`、`propertyvalue=14092`——`src/sql/tmall_ssh_h2.sql` 尾部 `RESTART WITH n` 语句，加 row 后 **MUST** 同步校准
- **`dbInit` bean 位置**：`src/` 下（不是根目录或 web/），因为 launcher 只复制 `src/**` 资源到 classpath

改名前运行 `grep -rn "<literal>" src web` 是完整检查；以下 **四种耦合对 grep 不可见**，必须人工阅读确认：`Action4Service.t2p` 反射拼接 `"set" + clazz.getSimpleName()`、OGNL 重定向参数（接收方的属性名而非发送方字面量）、三个拦截器的 `/fore` 前缀测试（原始 URI 而非 URL）、JSP 相对链接的 context path 依赖。

### V. Schema 与 Seed 单一来源（Single Schema Source）
`hibernate.hbm2ddl.auto` **MUST** 为 `none`——schema **MUST** 完全由 `src/sql/tmall_ssh_h2.sql` 负责，Hibernate **MUST NOT** 自动补建外键（否则存量数据不满足约束，启动日志出现 FK 异常）。Spring 配置 **MUST** 提供 `dbInit` bean（`DataSourceInitializer` + `ResourceDatabasePopulator`）在启动时执行 `classpath:sql/tmall_ssh_h2.sql` 建表灌数据；Hibernate `SessionFactory` **MUST** 声明 `depends-on="dbInit"` 保证建表先于 Hibernate 初始化。

**建表脚本约束**（来自基线 operations/data-and-schema）：
- **CREATE TABLE + INSERT 都是 positional 形式**（无 column list，如 `INSERT INTO category VALUES (60,'安全座椅');`）——新增列 **MUST** 在 `CREATE TABLE` 之后用 `ALTER TABLE ... ADD COLUMN ...` 追加，**MUST NOT** 改写 `CREATE TABLE`（否则 85 个 product INSERT 的 8 列 positional 值错位）
- **Seed SQL 只有 5 表有数据**：category(17 行)、product(85 行)、productimage(929 行)、property(257 行)、propertyvalue(13321 行)；user / order_ / review / orderitem 四表空——**MUST NOT** 假设 demo account 存在（登录表单不能成功直到有人注册）
- **product.remark 的位置**：在 product INSERT 之后（`#L155`）追加 `ALTER TABLE product ADD COLUMN remark varchar(255) DEFAULT NULL;`——因为 CREATE TABLE product 只有 8 列而 85 个 INSERT 各 8 个 positional 值
- **脚本每次刷新全量执行**：无 `DROP TABLE`、无 `IF NOT EXISTS`、无 `continueOnError`——**MUST** 在空数据库上执行，重启是唯一回种子方式

该建表脚本由原始 `sql/tmall_ssh.sql` 最小语法适配而来（删除 `DROP DATABASE/USE` 三行、剥离 `ENGINE=InnoDB AUTO_INCREMENT=n DEFAULT CHARSET=utf8` 尾缀、`int(11)` → `int`、尾部追加 `ALTER TABLE ... ALTER COLUMN id RESTART WITH n` 校准自增），业务表结构与全部 INSERT 数据逐字未动。原始 `sql/tmall_ssh.sql` **MUST** 原样保留作为 MySQL 基线。

### VI. Spec-First 交付闭环（Spec-First Delivery）
所有功能变更 **MUST** 以 spec 为先。Spec 落地 `.openspec/specs/` 目录，命名 `scene<N>-<feature>.md`。每条 spec **MUST** 包含：需求概述、涉及修改组件（实体/Action/Service/JSP）、字段设计、请求参数、页面改动点、业务逻辑约束、项目规约适配说明、人工验收检查清单。单元测试 spec 额外 **MUST** 包含：测试范围与非范围、被测组件与接口列表、测试前置条件、每条用例的输入/预期输出、基线溯源依据。自查报告落地 `scene<N>-<feature>-selfcheck.md`。

### VII. 可复现启动与类加载器不变量（Reproducible Launch & Classloader Invariants）
**无构建步骤**：`StartJetty` 在每次启动时自动把 `src/` 编译到 `web/WEB-INF/classes`（UTF-8, `-encoding UTF-8 -nowarn -source 8 -target 8`，classpath = `web/WEB-INF/lib/*.jar` + `WEB-INF/classes`，无 build descriptor）并同步 xml/sql 资源，clone 后 **MUST** 能直接 `java -cp "web/WEB-INF/lib/*" src/StartJetty.java` 启动。**三面独立**：Jetty 在 8080 端口托管应用（根路径 `/` Rewrite 到 `/forehome`）；H2 控制台在 **独立端口 8082**（`web.xml` 中 Struts2 过滤器拦截 `/*`，挂到 8080 会报 `no action mapped`）。**H2 内存库按 JVM 隔离**：只有同一 JVM 的 H2 控制台能连到业务数据，应用停止后数据清空。**类加载器统一**：`StartJetty.addSystemClass` **MUST** 强制 log4j、h2、juli、jasper、el、jsp-api、jetty.apache.jsp 七个前缀由父加载器统一加载——命令行 `-cp` 启动导致这些 jar 同时在 JVM classpath 与 `WEB-INF/lib`，双份类会触发 `ServiceConfigurationError`、`TldCache ClassCastException` 与 H2 连到空数据库。**JDK 兼容性**：JDK 8/11/17 均可运行，`StartJetty.openJdk9PlusModules()` **MUST** 编程式 add-opens 防御 Spring 4.x 反射访问 JDK 内部包。

---

## 架构规范

**MUST** 遵循既定分层架构（Action → Service → DAO → 实体），Service 通过 `@Autowired` 注入 DAO 或 Service 接口，**MUST NOT** 跨层直接调用（Action **MUST NOT** 直接操作 Session，实体 **MUST NOT** 携带 Service 逻辑）。

### one-way dependency（强制单向）
```
Action (action/*.java)
  -> Service interface (service/*.java)          @Autowired 注入
    -> BaseServiceImpl / ServiceDelegateDAO      泛化 CRUD / Criteria / 委托
      -> DAOImpl extends HibernateTemplate       唯一碰 Session 的 bean
        -> SessionFactory sf -> DataSource ds -> H2 in-memory database
```
**MUST NOT** 打破此链：`action/` 包下 **MUST NOT** import `com.caozhihu.tmall.dao.*` 或 `org.hibernate.*`（只有 `ServiceDelegateDAO` 一个文件 import `DAOImpl`）；**MUST NOT** 在 Service 实现里直接 new `Session`；DAO 层无接口（按 README 设计决策）。

### 分层与包结构
| 层 | 包 | 职责 | Bean 注册 |
|---|---|---|---|
| Action | `com.caozhihu.tmall.action` | Struts2 端点（47 个 @Action 方法），不声明 `<action>` | `@Component`（仅 `Action4Service` 带 stereotype）+ Struts convention 插件自动发现 |
| Service 接口 | `com.caozhihu.tmall.service` | 业务契约 | — |
| Service 实现 | `com.caozhihu.tmall.service.impl` | 业务逻辑 + HibernateCriteria 拼装 | `@Service`（默认以类名首字母小写命名） |
| DAO | `com.caozhihu.tmall.dao.impl` | HibernateTemplate 单例 | `@Repository("dao")`（注入 `sf` SessionFactory） |
| 实体 | `com.caozhihu.tmall.pojo` | 9 个 POJO（Category/Product/Property/PropertyValue/ProductImage/Review/User/Order/OrderItem） | — |
| 拦截器 | `com.caozhihu.tmall.interceptor` | AuthInterceptor + 2 个业务拦截器 | `struts.xml` 拦截器栈配置 |
| 工具 | `com.caozhihu.tmall.util` | Page（分页）、ImageUtil（图片格式转换） | — |
| 启动 | `src/StartJetty.java` | 嵌入式 Jetty + 源码编译 + H2 控制台 | 无，纯 main |

### Action 层继承链（只读不变）
```
Action4Result (@Namespace("/"), @ParentPackage("basicstruts"), 36 个 @Result 共享块)
  └─ Action4Parameter (msg, sort, contextPath, keyword, num, oiid, oiids, total, showonly)
       └─ Action4Pagination (Page page)
            └─ Action4Pojo (9 个实体字段 + 10 个 List 字段)
                 └─ Action4Service (@Autowired 9 个 Service, t2p 反射, saveWithJpg)
                      └─ Action4Upload (File img + imgFileName + imgContentType)
                           └─ 具体 Action 类 (ForeAction 24 端点 + 7 个 admin 类 23 端点)
```
**MUST NOT** 改变此继承链——Action4Pojo 字段数量、Action4Service 反射拼接机制、`t2p(Object o)` 方法签名均为下游 JSP/OGNL 的 load-bearing 约定。注意 `Action4Service` 是唯一带 `@Component` 的链节点，具体 Action 类无任何 Spring/Struts 注解。

### Service 层 BaseServiceImpl 反射推断
`BaseServiceImpl` 构造函数通过**异常栈反射**推断实体类：抛出 Exception → 取栈顶第 2 帧 → 去掉 `ServiceImpl` 后缀 → 拼 `.pojo` 包 → `Class.forName(pojoFullName)`。**吞异常**：`ClassNotFoundException` 被 catch 后只 e.printStackTrace，bean 以 `clazz = null` 启动，第一次 query 才 NPE。Service 实现类命名 **MUST** 遵循 `<Entity>ServiceImpl` 且位于 `com.caozhihu.tmall.service.impl` 包下，否则反射链断裂。

### DAO 层单例委托
`DAOImpl` 继承 `HibernateTemplate`，`@Repository("dao")` 按名称注入 `sf` SessionFactory（`@Resource(name = "sf")`）。所有 Service 方法最终委托到 DAO 的 `findByCriteria`、`find`、`save`、`delete` 等——**MUST NOT** 在 Service 实现里直接操作 Session。

### 拦截器门槛约定
三个拦截器（AuthInterceptor、CartTotalItemNumberInterceptor、CategoryNamesBelowSearchInterceptor）**MUST** 通过 `uri.startsWith("/fore")` 测试决定是否运行。AuthInterceptor 用原始 `getRequestURI()`（**MUST NOT** 去 context path，只匹配部署在根路径 `/`）；另两个拦截器先 strip context path。拦截器在 `struts.xml` 的 `auth-dafault` 栈中顺序为 auth → category names → cart total → defaultStack（defaultStack last 负责参数绑定和 multipart），**MUST NOT** 调整。

### 启动类 Configuration 链不变量
`StartJetty.WebAppContext` **MUST** 显式包含完整 Configuration 链：WebInf → WebXml → MetaInf → Fragment → Env → Plus → Annotation → JettyWebXml。Jetty 嵌入模式默认链不含 `AnnotationConfiguration`，缺它导致 `JasperInitializer`（JSP SCI）不执行，JSP 编译报 `getTldCache() is null`。

### 演示图片集生命周期
`web/img/` 下的六个目录同时是 **checked-in 演示资产**和**运行时写入目标**（上传 endpoints 写进同一目录）。`.gitignore` **不排除** `web/img`——fresh checkout 重现 demo 界面，session 内上传是 untracked 新增，重启后数据库 row 消失但文件残留（同 id 下会被后续 session 覆盖）。row 无对应文件 → 浏览器显示 broken image，**MUST NOT** 有服务端 fallback。

---

## 编码风格规范

**MUST** 遵循统一命名与格式化规则（与存量代码保持一致），**MUST NOT** 绕过既定风格（如引入新注解、改字段注入为构造器注入、Lombok 等）。

### Struts2 注解风格
- 每个端点一个 `public String` 方法，`@Action("<url值>")` **MUST** 精确等于 URL（convention 插件不回退类名映射），47 个端点分布：`ForeAction` 24 个（`fore*`），其余 7 个 admin Action 类 23 个（`admin_*`）
- `@Namespace("/")` 在 `Action4Result` 上声明一次，子类 **MUST NOT** 覆盖
- `@Result` / `@Results` **MUST** 放在 `Action4Result` 共享块（36 个 name），**MUST NOT** 在 `struts.xml` 声明或下沉到子类
- **Typo 不可修**：`@ParentPackage("basicstruts")` 必须匹配 `struts.xml` 的 package 名；interceptor stack 名 `auth-dafault`（不是 default）在 `<interceptor-stack>` 和 `<default-interceptor-ref>` 两处拼写完全相同，**MUST NOT** 修正

### OGNL 绑定约定
- Action 层字段 MUST 提供标准 getter/setter（`Action4Pojo` 已有 9 个实体字段 + 10 个 List 字段的完整 getter/setter）
- 通用类型擦除：`protected List productSingleImages`（无泛型），**MUST NOT** 改为 `List<ProductImage>`——OGNL 运行时按元素类型推断，泛型擦除是项目既定约定
- JSP/EL 中引用字段 **MUST** 用驼峰名（如 `productSingleImages` 而非 `product_single_images`）
- 无 call per-action parameter 列表——bindable surface 就是链上所有 public setter

### Page 类拼写错误不可修复
`com.caozhihu.tmall.util.Page.isHasPreviouse()` 方法名 **MUST NOT** 纠正为 `hasPrevious`——`adminPage.jsp` 只通过 `hasPreviouse` 绑定分页控件，改名后 `<c:if>` 测试不再 resolve。`defaultCount = 5` 是所有分页页面的唯一 count 来源，**MUST NOT** 改动否则五个 admin 列表每页条数同时变化。

### 反射拼接禁止硬编码
- `Action4Service.t2p(Object o)` 用 `"set" + clazz.getSimpleName()` 动态调用 setter，**MUST NOT** 在方法里硬编码具体实体类名
- `BaseServiceImpl` 反射推断实体类通过异常栈，**MUST NOT** 在 Service 实现类里硬编码 clazz

### 拦截器白名单硬编码位置
`AuthInterceptor.noNeedAuthPage` 数组 **MUST** 保持在 `AuthInterceptor.intercept()` 方法内部硬编码（8 个值：home/checkLogin/register/loginAjax/login/product/category/search），**MUST NOT** 抽到配置文件——白名单与拦截器 URI 前缀 `startsWith("/fore")` 共同构成 auth gate 边界，改动时必须同步检查。

### Bean name 字符串匹配
以下 bean name **MUST** 在多处保持一致（改一处漏一处会上下文启动失败）：
- `ds` / `dbInit` / `sf` / `transactionManager`：`applicationContext.xml` 声明 + DAOImpl 的 `@Resource(name = "sf")` / ServiceDelegateDAO 的 `@Resource(name = "dao")`
- `basicstruts` / `auth-dafault`：`struts.xml` package/interceptor-stack 名 + `Action4Result` 的 `@ParentPackage("basicstruts")`

### 异常处理风格
- Service/DAO 层 **MUST NOT** 吞异常，`@Transactional` 靠 Spring 声明式回滚
- `Action4Service.t2p` 当前用 `catch (Exception e) { e.printStackTrace(); }`——**此为存量代码**，只读原则下不强制重构
- 新代码 **SHOULD** 用 SLF4J `log.error()` 替代 `e.printStackTrace()`，但 **MUST NOT** 修改现有存量代码
- `saveWithJpg(File)` 也吞 IOException + ImageIO.write 失败仅打栈——**此为存量代码**

### import 与注释
- Spring 注入用 `@Autowired` 字段注入，**MUST NOT** 改成构造器注入（与存量代码风格一致）
- 业务注释 **MUST** 用中文（存量代码全部中文注释）
- **MUST NOT** 引入 Lombok/Record 等新语法——目标 JDK 8 兼容 + 存量代码风格
- **MUST NOT** 引入新构建工具（无 pom.xml / build.gradle / CI）——所有 `.jar` 通过 `web/WEB-INF/lib/*.jar` glob 发现

### 启动类编译参数
`StartJetty.ensureCompiledClasses` 编译命令 **MUST** 保留：`javac -encoding UTF-8 -nowarn -source 8 -target 8`，classpath = `web/WEB-INF/lib/*.jar` + `WEB-INF/classes`，输出 `web/WEB-INF/classes`。所有 `src/**/*.java`（含 test 包）一次编译进同一目录。

---

## 测试规范

**MUST** 新增逻辑必须有对应测试（Service 层契约级单测），**MUST NOT** 跳过测试提交或绕过测试框架。

### 验证三层模型（来自 openwiki testing/verification）
本项目的"回归测试"不是一组单元测试而是**三层递进验证**：

| 层 | 谁执行 | 能发现什么 | 不能发现什么 |
|---|---|---|---|
| **Spring context load**（TestTmall） | 人 + JUnit 4 runner | bean wiring、H2 datasource、seed script 语法、Hibernate mapping 基本正确 | HTTP 请求、@Action URL、JSP 渲染、拦截器 auth gate、session key、图片路径、Service 反射推断 |
| **静态 grep**（改名前） | 人 | 跨文件字符串契约（端点、result 名、bean name、session key、图片目录、seed 值、order status、AJAX response、type 常量） | JSP EL 运行时绑定、OGNL 重定向参数、反射拼接、拦截器前缀 |
| **手动 smoke path**（浏览器/curl） | 人 | 前两层看不见的一切：JSP 渲染、参数绑定、上传写入、前台/后台屏幕、重定向链 | 自动化但需要执行 |

**MUST NOT** 依赖 TestTmall 覆盖端点/结果名/session key/图片路径——它只测 DAO，断言 nothing（不 import `org.junit.Assert`），两个 `@Transactional` 方法全部回滚。

### TestTmall 局限性（基线已记录）
- **绕过 Service 层**：`@Autowired DAOImpl dao` 直接调 `findByCriteria`、`save`、`delete`，9 个 `XxxServiceImpl` 都不跑
- **零断言**：green run = "context refreshed + 没抛"，不是 "数据对"
- **test() 方法是 no-op**：`cs.isEmpty()` 正常运行时跳过，且 `(++i)` 双重递增使 insert 分支只跑 5 次
- **一个 cached context、一个 dbInit、一个 JVM 内存库**：两方法共享

### 测试框架与约束
新增单元测试 **MUST** 复用项目既有框架，**MUST NOT** 引入新第三方依赖：
- Runner：`@RunWith(SpringJUnit4ClassRunner.class)`
- 上下文：`@ContextConfiguration("classpath:applicationContext.xml")`
- 事务回滚：方法级 `@Test` + `@Transactional`（自动回滚，避免 H2 内存库数据残留）
- 注入方式：`@Autowired` Service 接口注入（不注入 DAOImpl，不绕过 Service 层）

### 测试范围与非范围
| 范围 | 说明 | 非范围 |
|---|---|---|
| Service 公开接口契约 | 按分类查询、分页查询、`total()`、`listByParent()`、CRUD 等既有公开方法 | UI 页面、JSP 渲染、Action 层端点 |
| 分页行为语义 | `Page.start/count/total`、`isHasPreviouse()`/`isHasNext()`、`getLast()`、`getTotalPage()` 计算 | Jetty/Servlet 容器行为 |
| 分类过滤语义 | `PropertyValueService` 的 `propertyValue.listByProperty()` 等 parent 关联查询 | Hibernate 内部 Session 状态管理 |

### 测试文件约束
- 测试文件 **MUST** 位于 `src/com/caozhihu/tmall/test/`（与 TestTmall 同 package，同一启动类编译）
- 业务源码 **MUST NOT** 修改——只读原则适用
- 断言 **MUST** 对齐 openwiki 基线记录的 Service 契约、分页行为与分类过滤语义
- **MUST NOT** import 新依赖——所有断言必须用 `org.junit.Assert`（已 vendored）

### 运行 JUnitCore 的命令（基线 implied）
```bash
# 1. 先跑 StartJetty 把 src 编译进 web/WEB-INF/classes（Ctrl-C 停掉 server.join 即可）
java -cp "web/WEB-INF/lib/*" src/StartJetty.java

# 2. 单独跑 TestTmall（JVM-local context，独立内存库，不需要 Jetty 运行）
java -cp "web/WEB-INF/lib/*:web/WEB-INF/classes" org.junit.runner.JUnitCore com.caozhihu.tmall.test.TestTmall
```

### JVM 参数（JDK 17 实测）
运行 JUnitCore 时 **MUST** 带以下 add-opens 参数（Spring 4.x 反射访问 JDK 内部包）：
```
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.lang.reflect=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
--add-opens=java.base/java.io=ALL-UNNAMED
```

### 手动 smoke path（审查后 MUST 执行）
基线完整 smoke 路径：
1. **Start**：Run `StartJetty.main()` — banner 输出三个 URL
2. **根路径 + 静态资源**：`curl -i http://localhost:8080/` → storefront HTML（RewriteHandler 生效）；`curl -i http://localhost:8080/img/site/logo.gif` → image bytes（不是 HTML）——第二探针证明 rewrite 规则仍然精确匹配
3. **前台**：`curl -i http://localhost:8080/forehome` → 200，真实分类/产品数据
4. **后台列表**：`curl -i http://localhost:8080/admin_category_list` → 200，5 行 + `<img src="img/category/<id>.jpg">`
5. **后台 CRUD round-trip**：新增分类（name + img file upload）→ 保存并写图片 → redirect 回列表 → H2 控制台确认新 row 的 id 等于校准值
6. **H2 控制台** 8082 登录（JDBC URL `jdbc:h2:mem:tmall_ssh;MODE=MySQL;DB_CLOSE_DELAY=-1`，user sa，密码空），执行 `SELECT COUNT(*) FROM category; -- 17`、`SELECT MAX(id) FROM product; -- 962`
7. **清理**：删除测试上传产生的 untracked 文件（重启后 row 消失但文件残留）

---

## 安全规范

**MUST** 认证授权走既定机制（AuthInterceptor + session key `"user"`），**MUST NOT** 硬编码密钥、记录 PII 或绕过现有 auth gate。

### 认证边界（auth-dafault 拦截器栈）
| 端点族 | 拦截器 | 登录保护 | 现状 |
|---|---|---|---|
| `/fore*`（24 个前台端点） | AuthInterceptor + 2 个业务拦截器 | ✅ 白名单放行：home/checkLogin/register/loginAjax/login/product/category/search；其余需 `session["user"]` 非空 | Auth 白名单硬编码在 `AuthInterceptor.intercept()` 内（8 个值） |
| `/admin_*`（23 个后台端点） | **无拦截器** | ❌ 无登录保护 | admin 端点裸露，**MUST NOT** 在现有拦截器上硬加 auth gate（只读原则），如需登录保护 **SHOULD** 新增独立拦截器 |
| `/*`（H2 控制台） | 独立端口 8082，不在 web.xml 过滤器范围内 | ❌ 无密码保护（sa/空密码） | StartJetty 硬编码，生产部署 **SHOULD** 禁用 |

**AuthInterceptor 前缀测试**用原始 `getRequestURI()` 不 strip context path——应用部署路径 **MUST** 保持 `/`，改 context path 会让 auth gate 静默失效。

### Session 四值约定
- `user`：登录态，AuthInterceptor / CartTotalItemNumberInterceptor / CategoryNamesBelowSearchInterceptor / ForeAction 8 个方法 / top.jsp 都读它，**MUST NOT** 改动 key
- `orderItems`：checkout 中间态，唯一 writer `ForeAction#buy`，checkout 时被 read → null 解引用 → 流程中断
- `cs`：分类名条，CategoryNamesBelowSearchInterceptor 每次 `/fore*` request 重算，改 key 后 search/simpleSearch 两 JSP fragment 渲染空
- `cartTotalItemNumber`：购物车徽章，CartTotalItemNumberInterceptor 每次 `/fore*` request 重算，改 key 后 top.jsp 徽章渲染空

### 重定向参数安全
OGNL `${...}` 参数值来自**发送方 action 的属性路径**（如 `property.category.id` 只因为 `Action4Pojo.property` 是 `Property` 有 `category` 关联），参数名是**接收方的 setter path**——两端 **MUST** 同时改。`empty ${...}` 绑定 int 属性的行为未被 repo 确认（【人工评审待确认】），改参数前 **MUST** 先看 receiver 方法体。

### 数据安全
- H2 内存库按 JVM 隔离，重启即清空——**MUST NOT** 引入持久化文件模式（`jdbc:h2:file:`），保持演练级数据安全
- 数据库无连接池安全配置（启动时无密码）——启动类为本地演练设计，**MUST NOT** 用于生产
- 原始 MySQL 数据源配置 **MUST** 注释保留但 **MUST NOT** 启用密码硬编码在 xml 中
- **无 demo account**：fresh checkout 的 user 表为空，登录表单不能成功直到有人注册

### 启动安全不变量
- `StartJetty` 的 H2 控制台硬编码 `-webPort 8082`，用户 `sa`、密码空——**此为本地演练约定**，**MUST NOT** 让生产部署的控制台暴露到公网
- `RewriteHandler` 仅精确匹配 `/` 重写到 `/forehome`——**MUST NOT** 改成 RewritePatternRule（正则语义会重写所有 `/img/**` 等路径，导致全站图片返回 HTML）
- 演示图片集写入目录同时被 `.gitignore` **不排除**——上传测试后 **MUST** 清理 untracked 文件（重启后 row 消失、文件残留、后续 session 可能覆盖）

---

## 文档规范

**MUST** 公共 API 必须有文档（spec 8 章节 + 自查报告），**MUST NOT** 文档与实现长期不一致或手工修改自动生成的基线。

### Spec 文件落地
所有功能变更 **MUST** 先写 spec，落地 `.openspec/specs/scene<N>-<feature>.md`（N 为场景序号递增）。每条 spec **MUST** 包含以下章节：
1. 需求概述（做什么、为什么）
2. 涉及修改组件（实体类/Action/Service/JSP/配置文件清单）
3. 字段设计（DDL 变更、实体类注解、OGNL 绑定属性）
4. 请求参数（URL 参数、表单字段、OGNL 属性名）
5. 页面改动点（JSP 文件、EL 表达式、Ajax url）
6. 业务逻辑约束（从基线溯源的不可漂移规则）
7. 项目规约适配说明（如何满足本宪法各原则）
8. 人工验收检查清单

### 自查报告
功能交付后 **MUST** 附自查报告，落地 `.openspec/specs/scene<N>-<feature>-selfcheck.md`，**MUST** 声明每条宪法原则的遵守情况、命名漂移六载体同步清单、运行时不变量端口/URL 检查结果。

### 运行时指导文档
- `STARTUP.md`：启动方式、访问地址、H2 控制台使用、常见问题排查表——**MUST** 随启动类变更同步更新
- `MIGRATION.md`：变更摘要、硬性约束遵守情况、技术要点、验收结果——**MUST** 在每次启动类/配置/数据库脚本变更后追加
- `README.md`：项目简介、技术栈、表结构、开发流程——**SHOULD** 保持与基线一致
- `AGENTS.md`：openwiki 使用约定——**MUST NOT** 手动修改，由定时工作流管理

### openwiki 基线（只读）
- **MUST NOT** 手动编辑 `openwiki/` 下任何文件——它们由 GitHub Actions 定时生成并刷新
- 基线偏差时：改源文件（Java/xml/sql/md），让定时工作流重新生成
- 无 openwiki 检索工具时：读 `openwiki/quickstart.md` 并按其链接获取相关基线页

### 文档中涉及命名的不变量
文档里出现的端点名（如 `STARTUP.md` 的 `/forehome`、`/admin_category_list`、H2 JDBC URL、端口号）**MUST** 与源码一致——改名时文档是六类载体之一，**MUST** 在同一 commit 内同步

---

## 代码审查规范

**MUST** 所有变更经审查方可合并，**MUST NOT** 绕过审查直接推主干。

### 审查触发条件
- 所有代码变更（含新增、删除、配置修改）**MUST** 经过至少 1 人对照本宪法逐条核验
- 涉及命名字面量的变更（原则 IV）**MUST** 在审查记录里附 `grep -rn "<literal>" src web` 的完整输出

### Safe-Change Procedure（基线 runtime-invariants §8，改名前 **MUST** 执行）
1. **Classify the literal.** 端点？result 名？bindable name？Page 属性？session key？whitelist？图片名/目录？状态/类型/AJAX literal？seed/config？launcher setting？
2. **Enumerate every carrier.** `grep -rn "<literal>" src web` + 文档（README/STARTUP/MIGRATION + banner）
3. **Read the four grep-invisible couplings** before renaming: t2p reflection, OGNL `${...}` path + receiver setter, relative links depending on context path, BaseServiceImpl 反射推断
4. **For a `/fore*` name, check the whitelist array first**——那一步把"改链接"变成了 auth 变更
5. **For an image name or directory, change the writer and every reader in the same commit**，同时记住 seed 值和 checked-in 文件
6. **For a config or launcher setting, restart and read the banner**，然后 `GET /` 和 `GET /img/site/logo.gif`——rewrite 回归只在 image URL 答 HTML 时可见
7. **Run the manual smoke path**（见测试规范末尾），然后清理 untracked 文件
8. **Do not rely on the test class.** TestTmall 覆盖不了任何端点/result/session/image

### 审查清单（对齐宪法原则）

#### 原则 I：基线优先
- [ ] 所有代码/页面/字段/调用方式与 openwiki 基线一致
- [ ] 未脑补基线不存在的机制
- [ ] 如需新机制，先确认 openwiki/quickstart.md 未覆盖

#### 原则 II：业务源码只读
- [ ] 业务 Java（53 个）、JSP、web.xml、Action/Service/DAO/实体类 **零改动**
- [ ] 变更仅限：启动类新增、jar 复制到 lib、spring-hibernate xml 微调、SQL 微调
- [ ] 未重构旧代码、未优化无关逻辑、未扩功能

#### 原则 III：技术栈版本冻结
- [ ] 未升级/替换 Spring/Struts2/Hibernate jar
- [ ] Jetty 版本 9.4.x、H2 版本 1.4.x
- [ ] H2 JDBC URL 含 `MODE=MySQL;DB_CLOSE_DELAY=-1`
- [ ] 原 MySQL 数据源配置注释保留

#### 原则 IV：命名不可漂移
- [ ] `grep -rn "<literal>" src web` 六类载体全部命中并同步
- [ ] 四种 grep 不可见耦合人工确认：`t2p` 反射、OGNL 重定向 + receiver setter、拦截器 `/fore` 前缀、JSP 相对链接
- [ ] `/fore*` / `/admin_*` 拦截器门槛未改动
- [ ] Session key 四值（user/orderItems/cs/cartTotalItemNumber）全部同步
- [ ] 图片目录名 + `<id>.jpg` + 56×56/217×190 尺寸常量全部同步
- [ ] Order status 六值 / type_single+type_detail / AJAX success+fail / seed 校准值 全部同步
- [ ] `auth-dafault` typo 两处拼写保持一致（未被"修正"）

#### 原则 V：Schema 单一来源
- [ ] `hibernate.hbm2ddl.auto=none`
- [ ] `dbInit` bean 存在且在 `src/` 下、`sf` 带 `depends-on="dbInit"`
- [ ] `src/sql/tmall_ssh_h2.sql` 是原始 `sql/tmall_ssh.sql` 的最小适配（逐字比对）
- [ ] 加新列用 `ALTER TABLE ... ADD COLUMN ...`（在 CREATE TABLE 之后、positional INSERT 之后），**MUST NOT** 改 CREATE TABLE 或改现有 INSERT 的列序
- [ ] 加 row 后同步校准自增语句（category=84/product=963/productimage=10211/property=258/propertyvalue=14092）

#### 原则 VI：Spec-First
- [ ] spec 文件齐备（含全部 8 个章节）
- [ ] 自查报告齐备
- [ ] 单测框架对齐既有：SpringJUnit4ClassRunner + applicationContext.xml + @Transactional

#### 原则 VII：启动/类加载器不变量
- [ ] 启动类 Configuration 链完整（含 AnnotationConfiguration）
- [ ] `addSystemClass` 七个前缀未动
- [ ] RewriteHandler 是匿名 Rule 精确匹配 `/`（非 RewritePatternRule）
- [ ] H2 控制台端口 8082

### one-way dependency 审查
- [ ] action/ 包下 **MUST NOT** import `com.caozhihu.tmall.dao.*` 或 `org.hibernate.*`
- [ ] Service 实现类通过接口注入 DAO，**MUST NOT** 直接 new Session

### 运行时验证
审查后 **MUST** 执行完整 manual smoke path（见测试规范末尾 7 步）。

### 审查通过标准
**Safe-change 8 步全部执行 + 审查清单全部通过 + 手动 smoke path 通过**，否则不得合入。

---

## Governance

本宪法凌驾于其它局部实践之上，冲突时以本宪法的只读、冻结、最小改动、命名不可漂移与基线优先原则为准。

**修订流程**：任何原则变更 **MUST** 以文档方式记录、经审阅批准并说明迁移影响后施行；涉及技术栈/版本/命名等 NON-NEGOTIABLE 原则的修订 **MUST** 经过独立技术评审。

**版本号**按语义化递增：
- **MAJOR**：不可向后兼容的原则删除或重定义（如变更"业务源码只读"为允许修改）
- **MINOR**：新增原则/章节或实质扩展（如本次基于完整 openwiki 基线补充大量治理级约束）
- **PATCH**：澄清、措辞修正、非语义化 refinement

**合规评审**：所有代码与交付物变更 **MUST** 由 reviewer 对照本宪法的原则逐条核验；未满足门槛的改动不得合入。运行时开发指导以 `AGENTS.md`、`STARTUP.md` 与基线 Wiki 为准。

**Version**: 1.2.0 | **Ratified**: 2026-09-28 | **Last Amended**: 2026-09-28
