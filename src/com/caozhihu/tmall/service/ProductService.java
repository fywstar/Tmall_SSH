package com.caozhihu.tmall.service;

import com.caozhihu.tmall.pojo.Category;
import com.caozhihu.tmall.pojo.Product;
import com.caozhihu.tmall.service.BaseService;
import com.caozhihu.tmall.util.Page;

import java.util.List;

public interface ProductService extends BaseService {
    public void fill(List<Category> categories);
    public void fill(Category category);
    public void fillByRow(List<Category> categories);
    public void setSaleAndReviewNumber(Product product);
    public void setSaleAndReviewNumber(List<Product> products);
    public List<Product> search(String keyword, int start, int count);

    //查询某个分类下名称模糊匹配keyword的产品，按BaseServiceImpl分页机制切片
    public List<Product> list(Page page, Category category, String keyword);

    //统计某个分类下名称模糊匹配keyword的产品个数
    public int total(Category category, String keyword);
}
