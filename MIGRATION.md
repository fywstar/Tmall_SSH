# 变更摘要：嵌入式 Jetty 启动 + H2 内存数据库改造

> 改造目标：本存量项目（Struts2 2.5.14.1 + Spring 4.3.18 + Hibernate 5.3.7 + JSP）无需外置 Tomcat 与本地 MySQL，
> 运行 `StartJetty.main()` 即可一键启动，前端页面与 H2 控制台均正常可用。
> 用途：SDD 存量项目演练。

## 硬性约束遵守情况

- 原有业务 Java（53 个）、JSP、web.xml、目录结构、Action/Service/DAO/实体类**一行未改**；
- 未升级/替换任何框架版本：Spring 4.3.18.RELEASE、Struts2 2.5.14.1、Hibernate 5.3.7.Final 与项目源码要求一致；
- Jetty 固定 9.4.x（9.4.58.v20250814），H2 固定 1.4.x（1.4.200）。

## 变更文件清单

| 类型 | 文件 | 说明 |
|---|---|---|
| 新增 | `src/StartJetty.java` | 唯一新增的业务无关启动类（含 main） |
| 修改 | `src/applicationContext.xml` | 数据源/方言/SQL 自动执行（改动处均有 `【H2改造-N】` 注释标注，原 MySQL 配置完整注释保留在文件尾部） |
| 新增 | `src/sql/tmall_ssh_h2.sql` | 原始 `sql/tmall_ssh.sql` 的 H2 适配版（原始脚本未动） |
| 新增 | `web/WEB-INF/lib/*.jar`（65 个） | 原仓库 lib 为空，本次重建全套依赖 jar（含同版本 jetty-rewrite，用于根路径入口重写） |
| 修改 | `tmall_ssh.iml` | 失效的 Windows 绝对路径（D:/myRepository 等）替换为 `web/WEB-INF/lib` 内逐 jar 引用（IDE 元数据，非业务代码） |
| 新增 | `.vscode/settings.json` | TRAE/VSCode 的 Java 语言服务依赖声明（不识别 .iml，消除 IDE 假阳性编译错误） |
| 新增 | `.gitignore` | 忽略编译产物与构建输出 |
| 新增 | `MIGRATION.md` / `STARTUP.md` | 本文档与启动指南 |

## 技术要点

### 1. 启动类 StartJetty.java
- 自动把 `src` 编译到 `web/WEB-INF/classes`（UTF-8，-source/-target 8）并同步 xml/sql 资源，**clone 后无需任何手工编译**；
- `WebAppContext` 直接加载原生 `web/WEB-INF/web.xml` 部署；
- 显式设置完整 Configuration 链（含 `AnnotationConfiguration`）——Jetty 嵌入模式默认链不含它，缺了会导致 JSP 的 `JasperInitializer`（SCI）不执行，JSP 编译报 `getTldCache() is null`；
- 独立端口 8082 启动 H2 控制台（`org.h2.tools.Server`）——`web.xml` 里 Struts2 过滤器拦截 `/*`，把控制台挂到应用内会报 `no action mapped`；
- 用 `RewriteHandler` 把根路径 `/` 转给 `/forehome`：原 web.xml 无 `<welcome-file-list>`，且 Jetty 的 DefaultServlet 默认不把 welcome file 交给 JSP servlet（与 Tomcat 行为不同），导致访问 `/` 404。注意用的是匿名 `Rule`（仅精确匹配 `/`）而非 `RewritePatternRule`——后者的 pattern 是正则/前缀语义，pattern `/` 会把 `/img/**` 等所有路径都重写成 `/forehome`，导致全站图片返回 HTML；
- `addSystemClass` 强制 log4j / h2 / jasper / el / jsp-api 由父加载器统一加载：所有 jar 同时位于 JVM classpath 与 WEB-INF/lib（命令行 `-cp` 启动方式所致），双份类会导致 `ServiceConfigurationError` 与 `TldCache ClassCastException`。

### 2. H2 内存数据库（applicationContext.xml）
- URL：`jdbc:h2:mem:tmall_ssh;MODE=MySQL;DB_CLOSE_DELAY=-1`
  - `MODE=MySQL`：最大化兼容原 MySQL 建表语句；
  - `DB_CLOSE_DELAY=-1`：内存库随 JVM 常驻，H2 控制台可连到同一个库。
- `dbInit` bean（Spring `DataSourceInitializer`）在启动时执行 `classpath:sql/tmall_ssh_h2.sql` 建表灌数据；
- `sf`（SessionFactory）加 `depends-on="dbInit"`，保证建表脚本先于 Hibernate 执行；
- 方言改为 `org.hibernate.dialect.H2Dialect`；原 MySQL 数据源 + MySQL5Dialect 配置**注释保留**，切回 MySQL 时对调两段配置即可（mysql-connector-java-8.0.13.jar 已在 lib 中）。

### 3. 建表脚本适配（tmall_ssh_h2.sql）
对原始脚本只做最小语法适配，业务表结构与全部 INSERT 数据逐字未动：
- 删除 `DROP DATABASE` / `CREATE DATABASE` / `USE` 三行（含首行 UTF-8 BOM 处理）；
- 剥离 MySQL 特有的 `) ENGINE=InnoDB AUTO_INCREMENT=n DEFAULT CHARSET=utf8` 尾缀 → `)`；
- `int(11)` → `int`；
- 尾部追加自增校准 `ALTER TABLE ... ALTER COLUMN id RESTART WITH n`（category=84 / product=963 / productimage=10211 / property=258 / propertyvalue=14092），保证新插入行主键与原 MySQL 行为一致。

### 4. JDK 版本兼容
JDK 8 / 11 / 17 均可运行。当前在 **JDK 17（Temurin 17.0.20.1）** 上完成全部验证，已处理 JDK 17 移除项的影响：
- `javax.annotation-api-1.3.2.jar`：@Resource 注解在 JDK 11+ 被移除；
- `jaxb-api-2.3.1.jar`：Hibernate 5.3 依赖 JAXB，JDK 11+ 被移除；
- asm 9.7.1 替代 5.2：JDK 17 下 Struts convention 插件读 JDK 类（jrt:/ 虚拟文件系统 v61 字节码）需 ASM 9；
- `StartJetty.openJdk9PlusModules()` 编程式 add-opens 防御 Spring 4.x 反射访问 JDK 内部包。

### 5. 依赖 jar 说明（64 个）
原仓库 `web/WEB-INF/lib` 为空目录，本次按项目框架版本重建全套依赖，与原始 pom 时代的版本对应关系：
- freemarker-2.3.26 原版已从 Maven Central 移除 → 用同一版本线的 2.3.26-incubating；
- jstl 无单 jar 发布 → jstl-api-1.2 + jstl-impl-1.2 双 jar；
- 其余 jar 均为各框架官方发布版本。

## 已知无害警告

~~启动日志中可能出现：~~（已消除，见下）
```
org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException:
Referential integrity constraint violation: "FK...: PUBLIC.PROPERTYVALUE FOREIGN KEY(PID) REFERENCES PUBLIC.PRODUCT(ID)"
```
原因：Hibernate `hbm2ddl.auto=update` 会按实体注解自动补建外键（如 `propertyvalue.pid`），而原始 MySQL 库本无此外键、存量演示数据不满足约束。**已把 applicationContext.xml 中 `hibernate.hbm2ddl.auto` 由 `update` 改为 `none`**（schema 完全由初始化脚本 `tmall_ssh_h2.sql` 负责，与原 MySQL 行为一致），该异常不再出现。

## 验收结果（实测，JDK 17）

| 验收项 | 结果 |
|---|---|
| `StartJetty.main()` 一键启动（无 Tomcat / 无 MySQL） | 通过 |
| 前台 `http://localhost:8080/forehome`（渲染真实分类/商品数据） | 200 |
| 后台 `http://localhost:8080/admin_category_list` | 200 |
| 静态资源 | 200 |
| H2 控制台 `http://localhost:8082` 登录查询（9 张业务表） | 通过 |
| 自增主键校准（新插入 category id=84，与原 MySQL AUTO_INCREMENT 一致） | 通过 |
| 存量业务源码零改动 | 确认 |

---

## 变更摘要（2026-09-28）：商品模块新增商品备注字段（specs/001-product-remark）

### 需求
商品实体新增可空备注字段 `remark`，后台"新增/编辑产品"表单可录入并回显，不影响前台/搜索/分页。

### 硬性约束遵守情况
- 本次为 spec 驱动迭代，按本案宪法 v1.2 原则 II 背书，仅修改 `Product.java` 与两个 admin JSP；其余业务源码/Action/Service/DAO/拦截器/继承链零改动；
- 未升级/替换任何框架版本；H2 仍 1.4.x，JDBC URL 仍含 `MODE=MySQL;DB_CLOSE_DELAY=-1`；原 MySQL 数据源配置注释保留。

### 变更文件清单
| 类型 | 文件 | 说明 |
|---|---|---|
| 修改 | `src/com/caozhihu/tmall/pojo/Product.java` | 新增 `String remark` 字段 + getter/setter（含中文注释） |
| 修改 | `src/sql/tmall_ssh_h2.sql` | 在 product 最后一个 INSERT 后追加 `ALTER TABLE product ADD COLUMN remark varchar(255) DEFAULT NULL;`，未改写 `CREATE TABLE product` 与任何 INSERT |
| 修改 | `web/admin/editProduct.jsp` | editForm（admin_product_update）新增"商品备注"输入框，`${product.remark}` 回显 |
| 修改 | `web/admin/listProduct.jsp` | addForm（admin_product_add）新增"商品备注"输入框 |
| 新增 | `specs/001-product-remark/{spec,plan,tasks,research,data-model,quickstart,contracts/form-binding,selfcheck}.md` | speckit 交付闭环工件 |

### 技术要点
- **命名载体同步（原则 IV）**：`remark` 命中 Java / JSP / SQL 三类载体共 4 处，同一 commit 同步（grep 确认）；StartJetty、web.xml 两载体与此字段无关（N/A）。
- **Schema 单一来源（原则 V）**：`hbm2ddl.auto=none`，新列以 `ALTER TABLE ... ADD COLUMN` 追加在 product 的 positional INSERT 之后、`CREATE TABLE productimage` 之前。
- **无新增 Service 方法**：字段走既有 `BaseServiceImpl` 泛化 save/update + `Action4Pojo.product` OGNL 绑定，不扩功能。

### 验收结果
| 验收项 | 结果 |
|---|---|
| 代码侧（静态 grep）：`remark` 四处载体同步、85 条 product INSERT 行序/列值逐字未变、CREATE TABLE product 未动 | 通过（本机核验） |
| 运行时：后台备注录入→持久化→回显、`SHOW COLUMNS FROM product` 含 remark、TestTmall 回归、前台 5 路径无回归 | 待人工 smoke 回填（本环境宪法禁止 `编译/启动`） |
