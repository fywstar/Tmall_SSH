package com.caozhihu.tmall.test;

import com.caozhihu.tmall.pojo.Category;
import com.caozhihu.tmall.pojo.Product;
import com.caozhihu.tmall.service.CategoryService;
import com.caozhihu.tmall.service.ProductService;
import com.caozhihu.tmall.util.Page;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * ProductService 商品业务单元测试：验证商品基础查询、分类过滤、分页查询行为。
 * 用例、断言、前置条件均对齐 OpenWiki 基线（openwiki/architecture/service-layer.md、
 * openwiki/workflows/pagination-and-search.md），逐条溯源见
 * .openspec/specs/scene4-product-unittest.md，测试只读业务源码，仅新增本文件。
 *
 * 数据策略：每条用例自建 Category/Product（IDENTITY 主键 save 后 id 立即可读），
 * 方法级 @Transactional 回滚，与既有 TestTmall 同款风格，不污染种子数据。
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:applicationContext.xml")
public class TestProductService {

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryService categoryService;

    // ---------- 测试数据构造辅助 ----------

    private Category saveCategory(String name) {
        Category c = new Category();
        c.setName(name);
        categoryService.save(c);
        return c;
    }

    private Product saveProduct(Category category, String name) {
        Product p = new Product();
        p.setCategory(category);
        p.setName(name);
        productService.save(p);
        return p;
    }

    private List<Integer> toIds(List<Product> products) {
        List<Integer> ids = new ArrayList<>();
        for (Product p : products) {
            ids.add(p.getId());
        }
        return ids;
    }

    private boolean isDescOrder(List<Integer> ids) {
        for (int i = 1; i < ids.size(); i++) {
            if (ids.get(i - 1) <= ids.get(i)) {
                return false;
            }
        }
        return true;
    }

    // ---------- U1 商品基础查询：list() 全量、id 降序、与 total() 一致 ----------

    @Test
    @Transactional
    public void listAllReturnsAllProductsInDescIdOrder() {
        Category c1 = saveCategory("单测分类U1");
        Product p1 = saveProduct(c1, "单测产品一");
        Product p2 = saveProduct(c1, "单测产品二");
        Product p3 = saveProduct(c1, "单测产品三");

        List<Product> all = productService.list();
        assertNotNull(all);
        //两次独立语句（count HQL 与 criteria 全量）在无并发事务中数量一致
        assertEquals(productService.total(), all.size());
        //层内默认排序：Order.desc("id")，新插入的 id 最大，故自建三条 id 严格递增且全部在结果中
        assertTrue(p1.getId() < p2.getId() && p2.getId() < p3.getId());
        List<Integer> ids = toIds(all);
        assertTrue(isDescOrder(ids));
        assertTrue(ids.contains(p1.getId()));
        assertTrue(ids.contains(p2.getId()));
        assertTrue(ids.contains(p3.getId()));
    }

    // ---------- U2 分页查询：listByPage 按降序全序切片 ----------

    @Test
    @Transactional
    public void listByPageSlicesNewestFirst() {
        Category c1 = saveCategory("单测分类U2");
        saveProduct(c1, "单测产品一");
        saveProduct(c1, "单测产品二");
        saveProduct(c1, "单测产品三");

        List<Product> all = productService.list();
        List<Integer> allIds = toIds(all);
        //第一页窗口：start=0,count=2，等于降序全序的前两条
        List<Product> page0 = productService.listByPage(new Page(0, 2));
        assertEquals(2, page0.size());
        assertEquals(allIds.subList(0, 2), toIds(page0));
        //第二页窗口：start=2,count=2，等于降序全序的第3、4条（种子85条+自建3条，全序足够取窗）
        List<Product> page1 = productService.listByPage(new Page(2, 2));
        assertEquals(2, page1.size());
        assertEquals(allIds.subList(2, 4), toIds(page1));
    }

    // ---------- U3 分类过滤：listByParent 仅返回该分类产品、total 按分类计数 ----------

    @Test
    @Transactional
    public void listByParentReturnsOnlyThatCategory() {
        Category c1 = saveCategory("单测分类U3A");
        Product p1 = saveProduct(c1, "单测产品一");
        Product p2 = saveProduct(c1, "单测产品二");
        Product p3 = saveProduct(c1, "单测产品三");
        Category c2 = saveCategory("单测分类U3B");
        Product other = saveProduct(c2, "单测产品他类");

        List<Product> inC1 = productService.listByParent(c1);
        assertEquals(3, inC1.size());
        List<Integer> ids = toIds(inC1);
        assertTrue(isDescOrder(ids));
        assertTrue(ids.contains(p1.getId()));
        assertTrue(ids.contains(p2.getId()));
        assertTrue(ids.contains(p3.getId()));
        assertFalse(ids.contains(other.getId()));
        for (Product p : inC1) {
            assertEquals(c1.getId(), p.getCategory().getId());
        }
        //父范围计数：HQL where bean.category = ?0
        assertEquals(3, productService.total(c1));
        assertEquals(1, productService.total(c2));
    }

    // ---------- U4 分类过滤 + 分页：list(page, parent) 父范围切片 ----------

    @Test
    @Transactional
    public void listPageParentSlicesWithinCategory() {
        Category c1 = saveCategory("单测分类U4");
        saveProduct(c1, "单测产品一");
        saveProduct(c1, "单测产品二");
        saveProduct(c1, "单测产品三");

        List<Integer> inC1 = toIds(productService.listByParent(c1));
        assertEquals(3, inC1.size());
        //第一页：C1 内 id 最大的两条
        List<Product> page0 = productService.list(new Page(0, 2), c1);
        assertEquals(2, page0.size());
        assertEquals(inC1.subList(0, 2), toIds(page0));
        //第二页：剩余一条
        List<Product> page1 = productService.list(new Page(2, 2), c1);
        assertEquals(1, page1.size());
        assertEquals(inC1.subList(2, 3), toIds(page1));
    }

    // ---------- U5 分类过滤 + 名称模糊：list(page, category, keyword) 与 total(category, keyword) ----------

    @Test
    @Transactional
    public void listByCategoryAndKeywordFiltersWithinCategory() {
        Category c1 = saveCategory("单测分类U5A");
        Product p1 = saveProduct(c1, "测试产品甲");
        Product p2 = saveProduct(c1, "测试产品乙");
        saveProduct(c1, "普通产品");
        Category c2 = saveCategory("单测分类U5B");
        saveProduct(c2, "测试产品丙");

        List<Product> matched = productService.list(new Page(0, 5), c1, "测试");
        assertNotNull(matched);
        assertEquals(2, matched.size());
        List<Integer> ids = toIds(matched);
        assertTrue(isDescOrder(ids));
        assertTrue(ids.contains(p1.getId()));
        assertTrue(ids.contains(p2.getId()));
        for (Product p : matched) {
            assertTrue(p.getName().contains("测试"));
            assertEquals(c1.getId(), p.getCategory().getId());
        }
        assertEquals(2, productService.total(c1, "测试"));
    }

    // ---------- U6 名称模糊契约：空 keyword 生成 like '%%'，等价于不做名称过滤 ----------

    @Test
    @Transactional
    public void emptyKeywordMeansNoNameFilter() {
        Category c1 = saveCategory("单测分类U6");
        saveProduct(c1, "测试产品甲");
        saveProduct(c1, "普通产品乙");
        saveProduct(c1, "普通产品丙");

        List<Integer> allInC1 = toIds(productService.listByParent(c1));
        assertEquals(3, allInC1.size());
        //like 模式为 "%"+keyword+"%"，空串即 "%%"，匹配全部非空 name
        List<Product> matched = productService.list(new Page(0, 5), c1, "");
        assertNotNull(matched);
        assertEquals(3, matched.size());
        assertEquals(allInC1, toIds(matched));
        assertEquals(3, productService.total(c1, ""));
    }

    // ---------- U7 无命中：返回空列表（非 null）与计数 0 ----------

    @Test
    @Transactional
    public void noMatchReturnsEmptyListAndZeroCount() {
        Category c1 = saveCategory("单测分类U7");
        saveProduct(c1, "测试产品甲");
        saveProduct(c1, "普通产品");

        List<Product> matched = productService.list(new Page(0, 5), c1, "不存在词xyz");
        assertNotNull(matched);
        assertTrue(matched.isEmpty());
        //count HQL 空结果走 return 0 分支
        assertEquals(0, productService.total(c1, "不存在词xyz"));
    }

    // ---------- U8 分页行为：跨页切片不重不漏、与 total 配对一致 ----------

    @Test
    @Transactional
    public void paginationAcrossPagesIsDisjointAndComplete() {
        Category c1 = saveCategory("单测分类U8");
        Product m1 = saveProduct(c1, "测试产品一");
        Product m2 = saveProduct(c1, "测试产品二");
        Product m3 = saveProduct(c1, "测试产品三");
        Product m4 = saveProduct(c1, "测试产品四");
        Product m5 = saveProduct(c1, "测试产品五");
        saveProduct(c1, "普通产品");

        List<Integer> page0 = toIds(productService.list(new Page(0, 2), c1, "测试"));
        List<Integer> page1 = toIds(productService.list(new Page(2, 2), c1, "测试"));
        List<Integer> page2 = toIds(productService.list(new Page(4, 2), c1, "测试"));
        //切片行数 2/2/1，合计等于计数查询结果
        assertEquals(2, page0.size());
        assertEquals(2, page1.size());
        assertEquals(1, page2.size());
        assertEquals(5, productService.total(c1, "测试"));
        //页间无重复、并集恰为 5 条命中产品
        Set<Integer> union = new HashSet<>();
        union.addAll(page0);
        union.addAll(page1);
        union.addAll(page2);
        assertEquals(5, union.size());
        Set<Integer> expected = new HashSet<>();
        expected.add(m1.getId());
        expected.add(m2.getId());
        expected.add(m3.getId());
        expected.add(m4.getId());
        expected.add(m5.getId());
        assertEquals(expected, union);
        //每页 id 降序，第一页首条为 5 条中 id 最大者（跨页序连续）
        assertTrue(isDescOrder(page0));
        assertTrue(isDescOrder(page1));
        assertTrue(isDescOrder(page2));
        assertEquals(m5.getId(), page0.get(0).intValue());
    }

    // ---------- U9 分页边界：start 越界返回空列表且不抛错 ----------

    @Test
    @Transactional
    public void outOfRangeStartReturnsEmptyList() {
        Category c1 = saveCategory("单测分类U9");
        saveProduct(c1, "测试产品甲");
        saveProduct(c1, "测试产品乙");
        saveProduct(c1, "普通产品");

        //全表 offset 取总数，越界为空
        List<Product> beyondAll = productService.listByPage(new Page(productService.total(), 5));
        assertNotNull(beyondAll);
        assertTrue(beyondAll.isEmpty());
        //C1 命中 2 条，start=2 恰越界
        List<Product> beyondMatched = productService.list(new Page(2, 2), c1, "测试");
        assertNotNull(beyondMatched);
        assertTrue(beyondMatched.isEmpty());
    }
}
