# 启动指南（嵌入式 Jetty + H2 内存库）

本项目已完成改造：**无需安装 Tomcat、无需安装 MySQL**，运行一个 main 方法即可启动完整电商网站。

## 环境要求

- JDK 8 / 11 / 17 任一（验证环境为 JDK 17）
- 无其他依赖，数据库与 Web 容器均已内置（H2 1.4.200 + Jetty 9.4.58，jar 全在 `web/WEB-INF/lib`）

## 启动方式

### 方式 A：IDE（IntelliJ IDEA / Eclipse）

1. 直接打开/导入项目根目录；
2. 找到 `src/StartJetty.java`，运行其 `main()` 方法（IDEA 用户首次运行时把 Working Directory 设为项目根目录）；
3. 看到如下横幅即启动成功：

```
========================================================
 Tmall_SSH 已启动 (Jetty 9 嵌入式, H2 内存库)
   前台商城 : http://localhost:8080/
   后台管理 : http://localhost:8080/admin_category_list
   H2控制台 : http://localhost:8082
========================================================
```

> 首次启动约 5–8 秒（含源码编译与初始化 SQL 灌数据）。启动类会自动把 `src` 编译到 `web/WEB-INF/classes` 并同步资源，无需手工构建。

### 方式 B：命令行（项目根目录执行）

```bash
java -cp "web/WEB-INF/lib/*" src/StartJetty.java
```

JDK 8 的命令行方式不支持单文件源码运行，请改用 IDE，或先编译：

```bash
javac -encoding UTF-8 -cp "web/WEB-INF/lib/*" -d web/WEB-INF/classes src/StartJetty.java
java -cp "web/WEB-INF/lib/*:web/WEB-INF/classes" StartJetty
```

## 访问地址

| 服务 | 地址 | 说明 |
|---|---|---|
| 前台商城 | http://localhost:8080/ | 电商前台页面 |
| 前台入口（Action） | http://localhost:8080/forehome | 同首页 |
| 后台管理 | http://localhost:8080/admin_category_list | 分类管理等后台功能 |
| H2 数据库控制台 | http://localhost:8082 | 浏览器直接打开 |

## H2 数据库控制台使用

打开 http://localhost:8082 ，登录参数：

| 项 | 值 |
|---|---|
| JDBC URL | `jdbc:h2:mem:tmall_ssh;MODE=MySQL;DB_CLOSE_DELAY=-1` |
| 用户名 | `sa` |
| 密码 | （留空） |

登录后可直接执行 SQL 查看数据，例如：

```sql
SELECT * FROM category;
SELECT * FROM product WHERE cid = 1;
```

> 注意：H2 控制台运行在**独立端口 8082**（Struts2 过滤器会拦截应用内路径，故未挂到 8080）。
> 内存库按 JVM 隔离，只有本应用的 H2 控制台能连到业务数据；应用停止后数据即清空，重启时自动重建。

## 数据说明

- 数据库为纯内存库，每次启动自动执行 `src/sql/tmall_ssh_h2.sql`（建表 + 灌入演示数据）；
- 该脚本由原始 `sql/tmall_ssh.sql` 最小适配而来，表结构与数据一致；
- 新插入行的自增主键已校准为与原 MySQL 相同（如 category 从 84 起）。

## 常见问题排查

| 现象 | 处理 |
|---|---|
| 端口被占用 `Port 8080 already in use` | `lsof -ti:8080,8082 | xargs kill` 后重启 |
| jar 报 `zip END header not found` | 对应 jar 损坏，重新下载（镜像 `https://maven.aliyun.com/repository/public/...`）；批量检查：`for f in web/WEB-INF/lib/*.jar; do unzip -tq "$f" >/dev/null \|\| echo "$f 损坏"; done` |
| JSP 报 `getTldCache() is null` / `TldCache cannot be cast` | 勿改动 StartJetty 中 Configuration 链与 `addSystemClass` 配置 |
| H2 控制台地址填 8080 报 `no action mapped` | 控制台在 **8082** 端口 |
| 启动日志出现 `PROPERTYVALUE FOREIGN KEY(PID)` 约束异常 | 无害警告（Hibernate 补加实体外键时对存量数据的已知提示），不影响功能 |
| `Table "XXX" not found` | 确认 `applicationContext.xml` 中 dbInit bean 存在且 `sf` 带 `depends-on="dbInit"` |

更多技术细节见 [MIGRATION.md](MIGRATION.md)。
