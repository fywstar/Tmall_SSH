# Tasks

## 1. 实体与建表脚本

- [x] 1.1 `src/com/caozhihu/tmall/pojo/Product.java` 新增 `private String remark;` 与 getter/setter，不加 `@Column`，写法对齐既有 `subTitle` 字段。验证：与 `createDate` 之前既有字段的声明风格逐项一致，未改动任何既有行。
- [x] 1.2 `src/sql/tmall_ssh_h2.sql` 在 product 种子 INSERT 段（#L154）之后、`CREATE TABLE productimage` 之前追加一条 `ALTER TABLE product ADD COLUMN remark varchar(255) DEFAULT NULL;`。验证：CREATE TABLE product 与 85 条 product INSERT 均未改动，仅新增一行。

## 2. 后台 JSP

- [x] 2.1 `web/admin/listProduct.jsp`：表头"库存数量"后加 `<th>备注</th>`，行内 `${p.stock}` 后加 `<td>${p.remark}</td>`；新增产品表单"库存"行后加备注输入行 `<input name="product.remark" type="text" class="form-control">`。验证：不触碰 `$("#addForm").submit` 校验代码，新增内容与相邻行缩进/结构一致。
- [x] 2.2 `web/admin/editProduct.jsp`："库存"行后加 `<input id="remark" name="product.remark" value="${product.remark}" type="text" class="form-control">`。验证：编辑页回显走 EL，`$("#editForm").submit` 校验代码未动。

## 3. 运行时验收（人工冒烟，对应 spec 场景）

- [x] 3.1 重启 `StartJetty`：启动横幅正常，无 SQL/约束异常。验证：控制台输出与既有启动基线一致。
- [x] 3.2 H2 控制台（http://localhost:8082，JDBC `jdbc:h2:mem:tmall_ssh;MODE=MySQL;DB_CLOSE_DELAY=-1`，用户 `sa`）执行 `SELECT remark FROM product WHERE id = 87;`。验证：返回 NULL，列存在。
- [x] 3.3 后台冒烟：`admin_product_list?category.id=83` 新增一个带备注的商品 → 列表备注列显示；再新增一个备注留空的商品 → 创建成功无报错；编辑该商品修改备注 → 保存后列表与编辑页展示新值。验证：逐条对应 `specs/product-remark/spec.md` 的四个后台场景。
- [x] 3.4 前台抽查：访问 `/forehome`、商品详情页与分类列表页，确认页面无备注内容、行为无异样。验证：对应 spec "前台不受影响" 场景。

## 4. 收尾核查

- [x] 4.1 确认改动仅限 1.1/1.2/2.1/2.2 四处：Action/Service/DAO、`sql/tmall_ssh.sql`、`web/WEB-INF/classes/`、`openwiki/` 均未触碰。验证：人工比对工作区变更清单。
