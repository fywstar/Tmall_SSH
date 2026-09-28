# Quickstart: 商品备注字段验证指南

> Phase 1 产物。运行/验证指南；实现细节见 `tasks.md` 与实施阶段。

## 验证目标

证明 `remark` 字段端到端可用：新增可录入 → 持久化 → 编辑回显 → 空备注零侵入。参考契约 `contracts/form-binding.md` 与数据模型 `data-model.md`。

## 前置条件

- 代码/脚本改动落到 4 个文件后，按宪法"无构建步骤"直接启动：
  ```
  java -cp "web/WEB-INF/lib/*" src/StartJetty.java
  ```
- 预期 banner 输出三个 URL：应用 `http://localhost:8080/`、后台、H2 控制台 `:8082`。

## 脚本/启动自检（只列 runnable 验证，不含实现）

由于《Tmall_SSH 宪法》明确"禁止执行 git/openwiki/编译/启动等终端命令"仅适用于现有基线维护流程，**本 feature 的验证属于交付前手动 smoke path，应在本仓库可运行环境人工执行**（宪法测试规范 §测试范围：UI/端点不经 JUnit，靠 smoke）。

### A. schema 生效检查（可选，H2 控制台）

1. 打开 H2 控制台 http://localhost:8082（JDBC `jdbc:h2:mem:tmall_ssh;MODE=MySQL;DB_CLOSE_DELAY=-1`，user `sa`，密码空）。
2. 执行 `SELECT COUNT(*) FROM product;` → 仍为 85（seed 未动）。
3. 执行 `SHOW COLUMNS FROM product;` → 列清单含 `remark VARCHAR(255) DEFAULT NULL`。

### B. 手动 smoke（后台表单）

1. **启动**：运行 `StartJetty.main()`。
2. **新增**：打开后台"新增产品"表单 → 填写名称等既有必填项，并在"商品备注"输入框填入"测试备注AAA" → 提交 → 应跳回产品列表列表。
3. **回读**：H2 控制台 `SELECT remark FROM product WHERE name='测试备注AAA';` → 返回 `测试备注AAA`。
4. **编辑回显**：在该产品行点"编辑" → 表单"商品备注"输入框 value 回显 `测试备注AAA` → 改为"测试备注BBB"提交 → 再进编辑确认回显为 `测试备注BBB`。
5. **空备注零侵入**：不填备注新增一个产品 → 保存成功；再遍历前台首页、某商品详情页、搜索、后台分类与产品列表 —— 全部正常渲染、分页行为不变；数据库该行 `remark` 为 NULL。

## 预期结果（对照 Success Criteria）

- SC-001：第 4 步回显 100% 成功。
- SC-002：第 5 步空备注保存成功。
- SC-003：第 5 步既有页面全路径无回归。
- SC-004：第 A.2/第 5 步 seed 85 行其余列值/行序不变、新行 remark 可空。

## 清理

- 上传/新增测试产生的 row 在重启后随内存库清空，无需手工处理；若测试涉及图片上传按宪法清理 untracked 文件（本次仅文本字段，通常无图片产物）。