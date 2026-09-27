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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * ProductService 查询业务单元测试（openspec 变更：add-product-business-unittest）。
 * 形态对齐 TestTmall：JUnit4 + SpringJUnit4ClassRunner + classpath:applicationContext.xml + 方法级 @Transactional 回滚；
 * 断言只依赖用例内自建 fixture 的相对关系（相对 id 顺序、fixture 计数、集合成员），不依赖种子行数。
 * 被测契约溯源：openwiki《Pagination and Search》《Service Layer》与主 spec product-name-search。
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:applicationContext.xml")
public class TestProductService {

    /** 模糊搜索关键词：fixture 名称按前缀/中缀/后缀命中该词设计 */
    private static final String KEYWORD = "公共";

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryService categoryService;//仅用于 fixture 造数（保存分类），不属被测范围

    // fixture 引用（JUnit4 每个用例新建测试类实例，字段天然隔离）
    private Category ca;
    private Category cb;
    private Product aPrefix;//A分类-前缀命中
    private Product aInfix;//A分类-中缀命中
    private Product aSuffix;//A分类-后缀命中
    private Product aMiss1;//A分类-不命中
    private Product aMiss2;//A分类-不命中
    private Product bHit;//B分类-命中
    private Product bMiss;//B分类-不命中

    /**
     * 公共 fixture：两个新分类 A/B + 7 个商品。保存顺序即 id 递增顺序，
     * 全部依赖 @Transactional 回滚清理，不触碰种子数据，不硬编码任何 id 数值。
     */
    private void createFixtures() {
        ca = new Category();
        ca.setName("单元测试分类A");
        categoryService.save(ca);

        cb = new Category();
        cb.setName("单元测试分类B");
        categoryService.save(cb);

        aPrefix = saveProduct("公共商品A", ca);//前缀命中
        aInfix = saveProduct("B公共商品C", ca);//中缀命中
        aSuffix = saveProduct("特惠公共", ca);//后缀命中
        aMiss1 = saveProduct("办公用品", ca);//不命中
        aMiss2 = saveProduct("收纳用品", ca);//不命中
        bHit = saveProduct("公共商品B", cb);//B分类命中
        bMiss = saveProduct("其他商品", cb);//B分类不命中
    }

    private Product saveProduct(String name, Category category) {
        Product product = new Product();
        product.setName(name);
        product.setCategory(category);
        productService.save(product);
        return product;
    }

    // ---------- 2. 全量维度（list / listByPage / total） ----------

    /** 2.1 list() 全量 id 降序且含全部 fixture；listByPage 各切片行数正确、拼接后与全量降序序列逐位一致 */
    @Test
    @Transactional
    public void testListFullOrderAndSlices() {
        createFixtures();

        List<Product> all = productService.list();

        // 全量列表整体按 id 严格降序（基线：DetachedCriteria + Order.desc("id")）
        for (int i = 0; i + 1 < all.size(); i++) {
            assertTrue("list() 应按 id 降序", all.get(i).getId() > all.get(i + 1).getId());
        }

        // 全部 fixture 出现，且在全量降序序列中的相对顺序与保存顺序相反（id 递增 => 后保存者在前）
        Map<Integer, Integer> indexById = indexById(all);
        List<Product> fixturesInReverseSaveOrder = Arrays.asList(bMiss, bHit, aMiss2, aMiss1, aSuffix, aInfix, aPrefix);
        int lastIndex = -1;
        for (Product fixture : fixturesInReverseSaveOrder) {
            Integer index = indexById.get(fixture.getId());
            assertNotNull("list() 应包含 fixture 商品", index);
            assertTrue("fixture 在全量降序序列中的相对顺序应符合保存顺序", index > lastIndex);
            lastIndex = index;
        }

        // 分页切片：行数正确，拼接后与全量 id 降序序列逐位一致
        List<Integer> joinedIds = new ArrayList<>();
        int count = 5;
        for (int start = 0; start < all.size(); start += count) {
            List<Product> slice = productService.listByPage(new Page(start, count));
            assertEquals("listByPage 切片行数应符合 start/count", Math.min(count, all.size() - start), slice.size());
            joinedIds.addAll(idList(slice));
        }
        assertEquals("listByPage 各切片拼接后应与全量 id 降序序列逐位一致", idList(all), joinedIds);
    }

    /** 2.2 total() 全量计数 = listByPage 逐页切片行数之和，且不小于 fixture 商品数 */
    @Test
    @Transactional
    public void testTotalMatchesFullSlices() {
        createFixtures();

        int total = productService.total();
        int count = 5;
        int slicedSum = 0;
        for (int start = 0; start < total; start += count) {
            slicedSum += productService.listByPage(new Page(start, count)).size();
        }
        assertEquals("total() 应等于 listByPage 逐页切片行数之和", total, slicedSum);
        assertTrue("total() 应不小于 fixture 商品数", total >= 7);
    }

    // ---------- 3. 分类维度（list(Page, parent) / total(parent)） ----------

    /** 3.1 分类隔离：list(page, category) 只含该分类商品、id 降序、切片行数符合页参数；A/B 分类互不混入 */
    @Test
    @Transactional
    public void testListByCategoryIsolationAndSlice() {
        createFixtures();
        Set<Integer> aIds = fixtureIds(aPrefix, aInfix, aSuffix, aMiss1, aMiss2);
        Set<Integer> bIds = fixtureIds(bHit, bMiss);

        int count = 2;
        List<Integer> joinedIds = new ArrayList<>();
        for (int start = 0; start < aIds.size(); start += count) {
            List<Product> slice = productService.list(new Page(start, count), ca);
            assertEquals("分类 A 切片行数应符合 start/count", Math.min(count, aIds.size() - start), slice.size());
            for (Product product : slice) {
                assertTrue("分类 A 的切片不应混入其他分类商品", aIds.contains(product.getId()));
                assertFalse("分类 A 的切片不应混入分类 B 商品", bIds.contains(product.getId()));
                joinedIds.add(product.getId());
            }
        }
        assertEquals("分类 A 各切片拼接应恰为其全部 fixture 商品", aIds, new HashSet<>(joinedIds));
        assertIdDesc(joinedIds);

        // 同参数换分类 B：结果与分类 A 互不混入
        List<Product> rowsB = productService.list(new Page(0, count), cb);
        assertFalse(rowsB.isEmpty());
        for (Product product : rowsB) {
            assertTrue("分类 B 的切片不应混入其他分类商品", bIds.contains(product.getId()));
        }
    }

    /** 3.2 分类计数：total(category) 等于该分类 fixture 商品数并与切片行数之和一致，分类之间互相隔离 */
    @Test
    @Transactional
    public void testTotalByCategoryMatchesSlices() {
        createFixtures();

        int totalA = productService.total(ca);
        assertEquals("total(categoryA) 应等于 A 分类 fixture 商品数", 5, totalA);
        int count = 2;
        int slicedSumA = 0;
        for (int start = 0; start < totalA; start += count) {
            slicedSumA += productService.list(new Page(start, count), ca).size();
        }
        assertEquals("total(categoryA) 应等于切片行数之和", totalA, slicedSumA);

        int totalB = productService.total(cb);
        assertEquals("total(categoryB) 应等于 B 分类 fixture 商品数", 2, totalB);
    }

    // ---------- 4. 名称模糊搜索（search，全站维度） ----------

    /** 4.1 全站子串命中：前缀/中缀/后缀命中、跨 A/B 分类、未命中排除（集合断言，不依赖行顺序） */
    @Test
    @Transactional
    public void testSearchSubstringAcrossCategories() {
        createFixtures();

        List<Product> hits = productService.search(KEYWORD, 0, 1000);
        Set<Integer> hitIds = idSet(hits);

        assertEquals("search 结果不应有重复行", hits.size(), hitIds.size());
        assertTrue("前缀/中缀/后缀命中与 B 分类命中商品应全部出现",
                hitIds.containsAll(Arrays.asList(aPrefix.getId(), aInfix.getId(), aSuffix.getId(), bHit.getId())));
        for (Product miss : Arrays.asList(aMiss1, aMiss2, bMiss)) {
            assertFalse("名称不含关键词的商品不应命中", hitIds.contains(miss.getId()));
        }
        assertTrue("搜索应跨分类：A 与 B 的命中商品同时出现", hitIds.contains(aPrefix.getId()) && hitIds.contains(bHit.getId()));
    }

    /** 4.2 窗口切片：前段与后段并集等于全量命中集合、交集为空（基线：固定窗口切片、无排序保证） */
    @Test
    @Transactional
    public void testSearchWindowSlice() {
        createFixtures();

        Set<Integer> fullIds = idSet(productService.search(KEYWORD, 0, 1000));
        assertTrue("命中集合应至少包含 fixture 的 4 条命中", fullIds.size() >= 4);

        List<Product> first = productService.search(KEYWORD, 0, 2);
        assertEquals("窗口切片应恰返回 n 条", 2, first.size());
        Set<Integer> firstIds = idSet(first);
        assertEquals("窗口切片不应有重复", 2, firstIds.size());

        Set<Integer> restIds = idSet(productService.search(KEYWORD, 2, 1000));
        Set<Integer> union = new HashSet<>(firstIds);
        union.addAll(restIds);
        assertEquals("前段与后段并集应等于全量命中集合", fullIds, union);
        assertEquals("前段与后段交集应为空", fullIds.size(), firstIds.size() + restIds.size());
    }

    /** 4.3 通配符语义：% 关键词按 like 通配符语义全部命中、不转义（与主 spec product-name-search 口径一致） */
    @Test
    @Transactional
    public void testSearchWildcardPercent() {
        createFixtures();

        List<Product> hits = productService.search("%", 0, 10000);
        Set<Integer> hitIds = idSet(hits);

        assertEquals("% 作为关键词应按 like 通配符语义命中全部商品", productService.total(), hits.size());
        assertTrue("全部命中应包含全部 fixture 商品",
                hitIds.containsAll(fixtureIds(aPrefix, aInfix, aSuffix, aMiss1, aMiss2, bHit, bMiss)));
    }

    // ---------- 5. 分类+名称维度（list(Page, Category, keyword) / total(Category, keyword)） ----------

    /** 5.1 组合过滤：仅 A 分类命中商品、id 降序、切片行数符合页参数 */
    @Test
    @Transactional
    public void testListByCategoryAndKeyword() {
        createFixtures();
        Set<Integer> aHitIds = fixtureIds(aPrefix, aInfix, aSuffix);

        List<Product> rows = productService.list(new Page(0, 10), ca, KEYWORD);
        assertEquals("A 分类命中商品应恰为 3 条", 3, rows.size());
        assertEquals("结果应恰为 A 分类命中商品集合（排除 B 分类命中与 A 分类未命中）", aHitIds, idSet(rows));
        assertIdDesc(idList(rows));

        // 切片参数生效：首片 2 条、第二片余下 1 条，拼接仍为命中集合
        List<Product> page1 = productService.list(new Page(0, 2), ca, KEYWORD);
        List<Product> page2 = productService.list(new Page(2, 2), ca, KEYWORD);
        assertEquals(2, page1.size());
        assertEquals(1, page2.size());
        Set<Integer> joinedIds = idSet(page1);
        joinedIds.addAll(idSet(page2));
        assertEquals("各切片拼接应恰为命中集合", aHitIds, joinedIds);
    }

    /** 5.2 计数与无命中：total(category, keyword) 等于命中数并与切片之和一致；无命中返回 0 与空列表 */
    @Test
    @Transactional
    public void testTotalByCategoryAndKeyword() {
        createFixtures();

        int total = productService.total(ca, KEYWORD);
        assertEquals("total(categoryA, keyword) 应等于 A 分类命中数", 3, total);
        int count = 2;
        int slicedSum = 0;
        for (int start = 0; start < total; start += count) {
            slicedSum += productService.list(new Page(start, count), ca, KEYWORD).size();
        }
        assertEquals("total 应等于切片行数之和", total, slicedSum);

        String noHitKeyword = "绝无仅有测七词";
        assertEquals("无命中关键词 total 应返回 0", 0, productService.total(ca, noHitKeyword));
        assertTrue("无命中关键词 list 应返回空列表", productService.list(new Page(0, 5), ca, noHitKeyword).isEmpty());
    }

    /** 5.3 空关键词回落：空字符串关键词与不带关键词的分类维度查询行为完全一致（Service 层 like '%%' 不产生额外过滤） */
    @Test
    @Transactional
    public void testEmptyKeywordFallsBackToCategoryFullList() {
        createFixtures();

        assertEquals("空关键词 total 应等同不带关键词", productService.total(ca), productService.total(ca, ""));

        int count = 2;
        int totalA = productService.total(ca);
        for (int start = 0; start < totalA; start += count) {
            List<Integer> withoutKeyword = idList(productService.list(new Page(start, count), ca));
            List<Integer> withEmptyKeyword = idList(productService.list(new Page(start, count), ca, ""));
            assertEquals("空关键词切片应与不带关键词切片逐位一致", withoutKeyword, withEmptyKeyword);
        }
    }

    // ---------- 断言辅助 ----------

    private Set<Integer> fixtureIds(Product... fixtures) {
        Set<Integer> ids = new HashSet<>();
        for (Product fixture : fixtures) {
            ids.add(fixture.getId());
        }
        return ids;
    }

    private Set<Integer> idSet(List<Product> products) {
        return new HashSet<>(idList(products));
    }

    private List<Integer> idList(List<Product> products) {
        List<Integer> ids = new ArrayList<>();
        for (Product product : products) {
            ids.add(product.getId());
        }
        return ids;
    }

    private Map<Integer, Integer> indexById(List<Product> products) {
        Map<Integer, Integer> index = new HashMap<>();
        for (int i = 0; i < products.size(); i++) {
            index.put(products.get(i).getId(), i);
        }
        return index;
    }

    /** 相邻 id 严格递减（对应基线 Order.desc("id")） */
    private void assertIdDesc(List<Integer> ids) {
        for (int i = 0; i + 1 < ids.size(); i++) {
            assertTrue("应按 id 降序排列", ids.get(i) > ids.get(i + 1));
        }
    }
}
