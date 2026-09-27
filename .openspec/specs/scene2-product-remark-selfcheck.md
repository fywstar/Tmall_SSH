# 自查报告：scene2-product-remark（商品备注字段）

- 自查对象：本次迭代全部变更（Spec + 代码 + SQL + JSP）
- 报告日期：2026-09-27
- 状态：代码已应用，待人工运行时验收

## 1. 变更清单（已应用）

| # | 文件 | 改动 | 状态 |
|---|---|---|---|
| 1 | `.openspec/specs/scene2-product-remark.md` | 新增 Spec 文档（8 个小节，含 Wiki 溯源） | 已保存 |
| 2 | `src/com/caozhihu/tmall/pojo/Product.java` | 字段声明区新增 `private String remark;`；`setSubTitle` 与 `getOriginalPrice` 之间新增 `getRemark()/setRemark()` | 已应用 |
| 3 | `src/sql/tmall_ssh_h2.sql` | 第 962 条 product INSERT 之后、`CREATE TABLE productimage` 之前追加 `ALTER TABLE product ADD COLUMN remark varchar(255) DEFAULT NULL;` | 已应用 |
| 4 | `web/admin/listProduct.jsp` | 表头新增 `<th>备注</th>`；数据行新增 `<td>${p.remark}</td>`；新增表单新增 `name="product.remark"` 输入行 | 已应用 |
| 5 | `web/admin/editProduct.jsp` | 编辑表单新增备注输入行，`value="${product.remark}"` 回显 | 已应用 |
| 6 | `src/com/caozhihu/tmall/action/ProductAction.java` | **零改动**（OGNL 绑定 + 通用 CRUD 自动携带新字段） | 符合 Spec |
| 7 | Service / DAO 层 | **零改动** | 符合 Spec |

## 2. 合规项

| # | 合规项 | 说明 |
|---|---|---|
| 1 | 基线只读 | 全程仅引用 openwiki/ 文档，未修改/删除/重新生成任何基线文件 |
| 2 | Spec-First | Spec 先于代码产出并落盘，代码变更与 Spec 第 2/3/5 节逐条对应 |
| 3 | 严格溯源 | OGNL 绑定写法源自 admin-crud.md；`@Transient` 规则源自 domain-model.md §5；「schema 唯一来源是 tmall_ssh_h2.sql」源自 data-and-schema.md §1；`${product.xxx}` 回显写法源自 admin-crud.md |
| 4 | 代码改动最小化 | 仅 4 个文件增量修改；无新类、无新包、无新依赖、无新校验 |
| 5 | 数据库适配 | ALTER TABLE 追加列位于既有 85 条位置参数 INSERT 之后，列序兼容（追加列在表末尾，位置 INSERT 不失效）；未改写 CREATE TABLE |
| 6 | 工程规约 | 包结构 com.caozhihu.tmall.pojo 不变；实体字段无注解习惯一致；JSP 表单 OGNL 命名 `product.xxx` 与 form-control 样式一致；插入位置对齐实体字段顺序（subTitle 之后） |
| 7 | 禁止过度开发 | 未触碰分页、排序、搜索、价格计算、图片、属性、订单等无关逻辑 |
| 8 | 通用约束 | 全程简体中文；未执行 git、openwiki、编译、启动等终端命令 |

## 3. 潜在违规项（含缓解说明）

| # | 项 | 分析与缓解 |
|---|---|---|
| 1 | 列表页新增表单 JS 校验未含 remark | 与既有 subTitle 校验策略一致（仅非空校验由 name 承担），remark 定位为可选字段，刻意不加校验，符合最小改动约束；如需必填/长度校验需另行评审立项 |
| 2 | 列表「备注」列未设 width | 与「产品名称」「产品小标题」列写法一致（均无 width），视觉统一；长文本由表格自动换行，不引入功能问题 |
| 3 | IDE 临时警告「field remark is not used」 | 仅为 getter/setter 落盘前的瞬态诊断，getter/setter 已补齐，警告已消除 |

## 4. 风险点说明

1. **旧数据备注为空**：85 条种子数据在 ALTER TABLE 前已插入，remark 为 NULL，列表显示为空——符合预期（既有数据无备注）。
2. **ALTER TABLE 执行时机**：语句位于 product 数据块之后、productimage 建表之前，dbInit bean 顺序执行无依赖冲突；Hibernate `hbm2ddl.auto=none` 不做校验/DDL，无启动期约束风险。
3. **OGNL 绑定依赖**：remark 写入依赖 Action4Pojo.product 属性绑定与 Hibernate 脏检查持久化，当前代码无字段级白名单机制，已核对，风险低。
4. **重启即重置**：H2 为内存库、每次重启重放脚本，remark 数据不持久；如需留存需另行评审外部化方案（超出本次需求范围）。

## 5. 人工验收指引

按 Spec 第 8 节清单逐项执行，重点关注：
- H2 控制台（8082）确认 product 表存在 remark 列；
- 列表页新增/编辑全链路写入与回显；
- 不填备注的空值路径无报错；
- 既有字段功能回归。
