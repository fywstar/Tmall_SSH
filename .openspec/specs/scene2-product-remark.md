# Spec：商品模块新增【商品备注】字段

- 迭代代号：scene2-product-remark
- 基线来源：openwiki/（只读，本次开发唯一权威规范）
- 模式：SDD Spec-First 轻量规范（先 Spec，后代码）
- 涉及代码范围：仅商品模块增量，不扩功能、不重构、不优化无关逻辑

## 1. 需求概述
在现有商品模块为商品实体新增一个可编辑、可展示的「商品备注」字段（remark）：
- 新增商品时可填写备注；
- 编辑商品时可修改并回显备注；
- 商品列表页展示备注列。

## 2. 涉及修改组件
| 组件 | 文件 | 改动类型 | 溯源依据 |
|---|---|---|---|
| 实体 | src/com/caozhihu/tmall/pojo/Product.java | 新增普通映射字段 remark + getter/setter | domain-model.md「Product — table product」字段清单；§5 @Transient 只用于渲染数据，remark 为真实列，不加 @Transient |
| 数据库脚本 | src/sql/tmall_ssh_h2.sql | 新增 ALTER TABLE 语句 | data-and-schema.md §1：唯一 schema 来源是该脚本（hbm2ddl.auto=none）；表/列只能由脚本决定 |
| Action | src/com/caozhihu/tmall/action/ProductAction.java | **零改动** | admin-crud.md：JSP 表单字段是 OGNL 属性路径，直接绑定到 Action4Pojo.product；add/update 走通用 CRUD，新字段自动被持久化 |
| Service/DAO | 全部 | **零改动** | 同上：BaseServiceImpl 通用 add/update 不做字段级白名单，自动携带新字段 |
| JSP（列表+新增） | web/admin/listProduct.jsp | 表头、数据列、新增表单各加一行 | admin-crud.md：listProduct 为列表壳 + 新增表单双用途；OGNL 字段名形如 product.xxx |
| JSP（编辑） | web/admin/editProduct.jsp | 编辑表单加一行并回显 | admin-crud.md：编辑页回显写法 `${product.xxx}` |

## 3. 字段设计
| 项 | 值 | 溯源依据 |
|---|---|---|
| Java 字段名 | `remark`（String） | domain-model.md：列名即 camelCase 字段名（与 name/subTitle 同风格） |
| 实体注解 | 无注解（与 name/subTitle 一致，不写 @Column/@Transient） | Product.java 既有字段均无注解，跟随既有习惯 |
| 数据库列 | `remark varchar(255) DEFAULT NULL` | 与既有字符串字段 subTitle（varchar(255)）同宽；DEFAULT NULL 与其余列一致 |
| 是否必填 | 否 | 与 subTitle 等一致，不做必填校验 |

## 4. 请求参数
- 新增（admin_product_add，POST）：新增 `product.remark`
- 更新（admin_product_update，POST）：新增 `product.remark`
- 编辑回显（admin_product_edit，GET）：通过 `product.remark` 取值
- 列表（admin_product_list，GET）：通过 `p.remark` 取值
- 溯源：admin-crud.md「add form 的字段名是 OGNL 属性路径：product.name / product.subTitle / …」，新字段按同一模式追加 `product.remark`

## 5. 页面改动点
1. listProduct.jsp 表头：在「产品小标题」之后插入 `<th>备注</th>`（对齐实体字段顺序）；
2. listProduct.jsp 数据列：在 `${p.subTitle}` 之后插入 `<td>${p.remark}</td>`；
3. listProduct.jsp 新增产品表单：在「产品小标题」行之后插入备注输入行（`id="remark"` / `name="product.remark"`，form-control 样式）；
4. editProduct.jsp 编辑表单：在「产品小标题」行之后插入备注输入行，`value="${product.remark}"` 回显。
- 溯源：admin-crud.md「edit() 调用 t2p(product) 后用 ${product.subTitle} 等回显」；新增/编辑表单均不新增必填校验（JS 校验保持现状，仅 name/subTitle/价格/库存）。

## 6. 业务逻辑约束
- remark 为纯展示/编辑字段，不参与价格、库存、图片、属性、订单任何计算；
- 不参与列表分页、排序、搜索逻辑；
- 不新增校验规则，不修改既有 JS 校验函数；
- createDate 维护逻辑不变（add 由 ProductAction 打戳、update 重新读取拷贝），remark 不做特殊处理；
- 数据库改动必须用 ALTER TABLE 追加列，禁止改写 CREATE TABLE（保持既有 85 条位置参数 INSERT 不失效）。

## 7. 项目规约适配说明
- 包结构：仅改 com.caozhihu.tmall.pojo.Product，无新类、无新包；
- 命名：字段/列/表单 name 统一 `remark`；JSP 标签、样式类均沿用既有写法；
- 调用链：JSP(name=product.remark) → OGNL → Action4Pojo.product → Hibernate Session → product 表 remark 列，全链路复用既有机制，零新增代码；
- 数据库：仅追加 1 条 ALTER TABLE，符合「schema 只由 tmall_ssh_h2.sql 决定」。

## 8. 人工验收检查清单
- [ ] 启动应用后 product 表存在 remark 列（H2 控制台 8082 验证）
- [ ] 商品列表页显示「备注」表头与各商品备注值（旧数据为 NULL/空）
- [ ] 在列表页新增商品填写备注 → 保存后列表可见
- [ ] 编辑页备注回显正确 → 修改保存后列表更新
- [ ] 不填备注新增/编辑 → 正常保存，remark 为空，无报错
- [ ] 既有字段（名称/小标题/价格/库存/图片/属性）功能回归无异常
