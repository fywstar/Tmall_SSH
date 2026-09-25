---
type: architecture
title: "Service Layer: BaseService, Reflection-Derived Entity Class, and DAO Delegation"
description: "The business-layer plumbing of Tmall_SSH: the eleven-method BaseService surface, the exception-and-stacktrace trick that derives each subclass's entity class (clazz), the criteria/HQL vocabulary the generic methods emit, ServiceDelegateDAO's full forwarding of the single dao bean, the XxxService/XxxServiceImpl/pojo pairing rules that must hold for any of it to work, and where @Transactional actually sits."
tags: [service-layer, spring, hibernate, criteria, hql, delegation, reflection, transactions, architecture]
verified:
  - by: openwiki/0.6.0
    at: 2026-09-25T05:09:04.985Z
sources:
  - id: openwiki-source-23775c3de52f3ab95a13cb8b
    resource: repo://README.md
  - id: openwiki-source-97a00a3efb3029e8cfe5025a
    resource: repo://src/com/caozhihu/tmall/action/Action4Service.java
  - id: openwiki-source-40b340eb2396ea635e7cfced
    resource: repo://src/com/caozhihu/tmall/action/ForeAction.java
  - id: openwiki-source-4bc53db59628da3ed1afd4f5
    resource: repo://src/com/caozhihu/tmall/action/OrderAction.java
  - id: openwiki-source-75c6d1214aa9b847cdcd9df5
    resource: repo://src/com/caozhihu/tmall/action/ProductImageAction.java
  - id: openwiki-source-b9e44ebd81372b1246ac1521
    resource: repo://src/com/caozhihu/tmall/dao/impl/DAOImpl.java
  - id: openwiki-source-b1df92f0e1c191c487cad76a
    resource: repo://src/com/caozhihu/tmall/interceptor/CartTotalItemNumberInterceptor.java
  - id: openwiki-source-6ac26f17eb5ea4be7110b6d5
    resource: repo://src/com/caozhihu/tmall/pojo/Order.java
  - id: openwiki-source-8f4976c89bb74b856e98cafb
    resource: repo://src/com/caozhihu/tmall/pojo/OrderItem.java
  - id: openwiki-source-535dabafb9f4fcf2952aba1c
    resource: repo://src/com/caozhihu/tmall/pojo/Product.java
  - id: openwiki-source-33b2cdd8ec7341193d5eb8a9
    resource: repo://src/com/caozhihu/tmall/pojo/PropertyValue.java
  - id: openwiki-source-27896c4b1b5d5bcfde550809
    resource: repo://src/com/caozhihu/tmall/pojo/Review.java
  - id: openwiki-source-40894efb48d7b7ace83c43b7
    resource: repo://src/com/caozhihu/tmall/service/BaseService.java
  - id: openwiki-source-aac0c381b7c67b93dc063535
    resource: repo://src/com/caozhihu/tmall/service/impl/BaseServiceImpl.java
  - id: openwiki-source-7fc623c366177b232a6b46bf
    resource: repo://src/com/caozhihu/tmall/service/impl/CategoryServiceImpl.java
  - id: openwiki-source-42976d75a5d7b2e86011cb52
    resource: repo://src/com/caozhihu/tmall/service/impl/OrderServiceImpl.java
  - id: openwiki-source-70e35fd1248750d5425b7bda
    resource: repo://src/com/caozhihu/tmall/service/impl/ProductImageServiceImpl.java
  - id: openwiki-source-eb4a9988dc16fb216fcacc4c
    resource: repo://src/com/caozhihu/tmall/service/impl/ProductServiceImpl.java
  - id: openwiki-source-f0227c978b90674d8262bb02
    resource: repo://src/com/caozhihu/tmall/service/impl/PropertyServiceImpl.java
  - id: openwiki-source-74a54306aef3fcdcab547a1e
    resource: repo://src/com/caozhihu/tmall/service/impl/PropertyValueServiceImpl.java
  - id: openwiki-source-a79b1ba0ed2bd8ca126fdb6b
    resource: repo://src/com/caozhihu/tmall/service/impl/ReviewServiceImpl.java
  - id: openwiki-source-f5703781f9113b3064a987d8
    resource: repo://src/com/caozhihu/tmall/service/impl/ServiceDelegateDAO.java
  - id: openwiki-source-e5c0582cb2f7f0caff2525cc
    resource: repo://src/com/caozhihu/tmall/service/impl/UserServiceImpl.java
  - id: openwiki-source-206c3b599f3307350744b22a
    resource: repo://src/com/caozhihu/tmall/service/OrderService.java
  - id: openwiki-source-8aab91c41a7e3a8c7cbcc7ad
    resource: repo://src/com/caozhihu/tmall/service/ProductImageService.java
  - id: openwiki-source-4bcc3c5a1b7232852519345e
    resource: repo://src/com/caozhihu/tmall/service/PropertyService.java
  - id: openwiki-source-8e6eda0d5afc3d32cfcfe5b6
    resource: repo://src/com/caozhihu/tmall/test/TestTmall.java
  - id: openwiki-source-36420abb1600f20d66381988
    resource: repo://web/admin/listProductImage.jsp
  - id: openwiki-source-f70af8dc562c9a9466b3bd4b
    resource: repo://web/include/cart/boughtPage.jsp
generated: { by: "openwiki/0.6.0", at: "2026-09-25T06:00:02.513Z" }
---

# Service Layer: BaseService, Reflection-Derived Entity Class, and DAO Delegation

Between the Struts actions and the single Hibernate template there is one abstraction layer, and it is
almost entirely generic. Nine interfaces in `com.caozhihu.tmall.service` all extend `BaseService`, nine
`*ServiceImpl` classes in `com.caozhihu.tmall.service.impl` all extend `BaseServiceImpl`, and the CRUD,
paging and count behaviour of all nine is inherited code that never mentions a concrete entity. What
makes that possible is a constructor that derives its own entity class by reading a deliberately thrown
exception's stack trace.

This page owns the service layer: the `BaseService` surface, the `clazz` derivation and the naming
constraints it imposes, the query vocabulary the generic methods emit, the delegation class that turns
`dao.doX()` into `doX()`, the status/type string constants other layers consume, and the transaction
annotations as they are actually placed. The DAO bean itself, the SessionFactory wiring and the entity
mapping are [Persistence Layer](/openwiki/architecture/persistence-layer.md)'s subject; how actions call
these methods is [Action Layer](/openwiki/architecture/action-layer.md)'s.

## 1. The shape of the layer

<!-- openwiki: mermaid parse failed and this diagram was converted to a text fence so it does not break rendering. Fix the diagram source and restore the mermaid fence. Parser error: Heuristic: an unescaped angle bracket inside a label breaks rendering; rephrase the label. -->
```text
classDiagram
    class BaseService {
        <<interface>>
        +List list()
        +List listByPage(Page page)
        +int total()
        +List listByParent(Object parent)
        +List list(Page page, Object parent)
        +int total(Object parent)
        +Integer save(Object object)
        +void delete(Object object)
        +Object get(Class clazz, int id)
        +Object get(int id)
        +void update(Object object)
        +List list(Object... pairParams)
    }
    class CategoryService {
        <<interface>>
    }
    class OrderService {
        <<interface>>
        +String waitPay
        +String delete
        +float createOrder(Order order, List ois)
        +List listByUserWithoutDelete(User user)
    }
    class BaseServiceImpl {
        #Class clazz
        +BaseServiceImpl()
    }
    class CategoryServiceImpl {
        <<Service categoryService>>
    }
    class OrderServiceImpl {
        <<Service orderService>>
    }
    class ServiceDelegateDAO {
        -DAOImpl dao
        +Serializable save(Object entity)
        +void update(Object entity)
        +void delete(Object entity)
        +List findByCriteria(DetachedCriteria dc)
        +List find(String hql, Object... values)
        +void setSessionFactory(SessionFactory sf)
        +SessionFactory getSessionFactory()
    }
    class DAOImpl {
        <<Repository dao>>
    }
    BaseService <|.. CategoryService
    BaseService <|.. OrderService
    BaseService <|.. BaseServiceImpl
    CategoryService <|.. CategoryServiceImpl
    OrderService <|.. OrderServiceImpl
    BaseServiceImpl <|-- CategoryServiceImpl
    BaseServiceImpl <|-- OrderServiceImpl
    BaseServiceImpl --|> ServiceDelegateDAO
    ServiceDelegateDAO --> DAOImpl : forwards every method
```

*The inheritance chain that gives every service its CRUD methods, and the single DAO bean the chain delegates to. `CategoryServiceImpl` and `OrderServiceImpl` stand in for all nine concrete services, which repeat the same two edges.*

- **One generic interface.** `BaseService` declares eleven methods and no entity type anywhere
  (`src/com/caozhihu/tmall/service/BaseService.java#L7-L24`). Every service interface extends it, and the
  only entity-aware additions are domain methods such as `ProductService.search`, `OrderService.createOrder`
  or `PropertyService.total(Category)`.
- **One generic implementation, nine empty classes.** `CategoryServiceImpl` is the extreme case: ten
  lines, a `@Service` name, and no method body at all — "CategoryService 接口仅仅继承了 BaseService 接口，
  无新增方法" (`src/com/caozhihu/tmall/service/impl/CategoryServiceImpl.java#L6-L10`).
- **The layer is entity-agnostic and that leaks.** Because the inherited methods carry no entity, nothing
  stops a call from being routed through the wrong service:
  `ProductImageAction.delete` deletes a `ProductImage` through `propertyService`. That behaviour is
  recorded as a defect in [Admin CRUD](/openwiki/workflows/admin-crud.md); the point here is that the
  service layer cannot detect it.
- **Who consumes the layer.** `Action4Service` autowires all nine interfaces by type
  (`src/com/caozhihu/tmall/action/Action4Service.java#L19-L44`) and the interceptors autowire the two they
  need (`CartTotalItemNumberInterceptor.java#L32-L44`,
  `CategoryNamesBelowSearchInterceptor.java#L31-L34`). Nothing injects `BaseService` itself.
- **Bean names are explicit but unused.** Every concrete service is annotated
  `@Service("<camelCaseName>Service")`, while `BaseServiceImpl` is annotated with a bare `@Service`
  (`src/com/caozhihu/tmall/service/impl/BaseServiceImpl.java#L16-L18`). Since all injection is
  by type (`@Autowired` on an interface field), no string name in the service layer is referenced
  anywhere; the two string names that *are* load-bearing (`dao`, `sf`) belong to
  `ServiceDelegateDAO` (§5).

## 2. Deriving the entity class from the subclass name

`clazz` is the only state in the layer, and it is computed once per service instance in the base
constructor (`src/com/caozhihu/tmall/service/impl/BaseServiceImpl.java#L20-L43`):

```java
protected Class clazz;

//clazz对应继承了BaseServiceImpl的pojoServiceImpl的pojo类对象
public BaseServiceImpl() {
    try {
        throw new Exception();
    } catch (Exception e) {
        StackTraceElement stackTraceElement[] = e.getStackTrace();
        String serviceImpleCalssName = stackTraceElement[1].getClassName();
        try {
            Class serviceImplClazz = Class.forName(serviceImpleCalssName);
            String serviceImpleClassSimpleName = serviceImplClazz.getSimpleName();
            String pojoSimpleName = serviceImpleClassSimpleName.replaceAll("ServiceImpl", "");
            String pojoPackageName = serviceImplClazz.getPackage().getName().replaceAll(".service.impl", ".pojo");
            String pojoFullName = pojoPackageName + "." + pojoSimpleName;
            clazz = Class.forName(pojoFullName);
        } catch (ClassNotFoundException e1) {
            e1.printStackTrace();
        }
    }
}
```

The mechanism, step by step:

1. The constructor throws and immediately catches an `Exception`, purely to obtain a stack trace. This
   is the documented design: 实例化子类时父类构造方法一定被调用，所以在父类里故意抛出一个异常
   (`README.md#L270-L277`).
2. `getStackTrace()[1]` is the *caller* of the constructor. For an instantiating subclass whose implicit
   `super()` runs first, frame 0 is `BaseServiceImpl.<init>` and frame 1 is `<X>ServiceImpl.<init>`, so
   frame 1 yields the concrete service class name.
3. `Class.forName` on that name, then `getSimpleName()` giving e.g. `CategoryServiceImpl`.
4. `replaceAll("ServiceImpl", "")` strips the suffix → `Category`. Note this is a *regex* replacement
   applied globally, not a literal `replace`.
5. The package is rewritten: `Class#getPackage().getName()` is `com.caozhihu.tmall.service.impl`, and
   `replaceAll(".service.impl", ".pojo")` yields `com.caozhihu.tmall.pojo`. Here too the `.` characters
   are regex wildcards, so the match also consumes the character before `service` — which is exactly what
   turns `...tmall.service.impl` into `...tmall.pojo`.
6. `Class.forName("com.caozhihu.tmall.pojo.Category")` and assign to `clazz`.

**Hard constraints.** The whole layer rests on this convention, and nothing checks it:

| Constraint | Consequence of breaking it |
|---|---|
| The service implementation lives in a package ending `.service.impl` | the rewritten package name points at a nonexistent package |
| The entity lives in the sibling `.pojo` package | same |
| The class is named `<Entity>ServiceImpl`, matching the entity's simple name exactly | `ClassNotFoundException` |
| The subclass constructor is the direct caller of the base constructor | frame 1 is not the subclass, so the derived name is unrelated to the entity |
| The entity's identifier stays a primitive `int` | see the `save` cast in §6 |

**Failure mode.** The `ClassNotFoundException` is caught and only printed
(`BaseServiceImpl.java#L39-L41`). Construction succeeds with `clazz == null`, no bean creation fails, the
Spring context loads, and the damage surfaces later as a failure inside the first call that uses `clazz`
— which is every `list*`, `total*` and `get(int)` path. There is no assertion anywhere in the codebase
that checks `clazz` after construction, and the only automated context load
(`src/com/caozhihu/tmall/test/TestTmall.java#L15-L17`) asserts nothing about it.

【人工评审待确认】 `BaseServiceImpl` is itself a concrete `@Service` bean, so the constructor also runs
for a bean that has no instantiating subclass; frame 1 in that case is Spring's own reflective
constructor invocation, and the resulting `clazz` on that unused `baseServiceImpl` bean is meaningless.
Whether the annotation on `BaseServiceImpl` is intentional (to hold the shared code in a bean) or a
leftover is not documented.

## 3. The generic method set, and who calls it

| `BaseService` method | What it emits | Representative callers |
|---|---|---|
| `list()` | `DetachedCriteria.forClass(clazz)` + `Order.desc("id")` | `CategoryNamesBelowSearchInterceptor`, admin list screens |
| `listByPage(Page)` | same criteria with `page.getStart()` / `page.getCount()` as offset and limit | every paged admin screen |
| `total()` | HQL `select count(*) from <clazz FQN>` | `CategoryAction`, `OrderAction`, `UserAction` |
| `listByParent(Object)` | criteria with `Restrictions.eq(<uncapitalized parent simple name>, parent)` | `OrderItemServiceImpl.fill`, `ProductServiceImpl.fill`, `PropertyValueAction`, `ForeAction` |
| `list(Page, Object)` | parent-scoped criteria with offset/limit | (the `PropertyServiceImpl` Category overload is the used one) |
| `total(Object)` | HQL `select count(*) from <clazz FQN> bean where bean.<property> = ?0` | `ReviewServiceImpl` via `ProductServiceImpl.setSaleAndReviewNumber` |
| `save(Object)` | `HibernateTemplate.save`, result cast to `Integer` | every insert: `CategoryAction`, `ProductAction`, `PropertyAction`, `ProductImageAction`, `ForeAction` |
| `delete(Object)` | `HibernateTemplate.delete` (inherited, not overridden) | admin deletes |
| `get(Class, int)` / `get(int)` | `HibernateTemplate.get(clazz, id)` | `Action4Service.t2p()`, `ForeAction`, `ProductAction` |
| `update(Object)` | `HibernateTemplate.update` (inherited, not overridden) | every edit and status transition |
| `list(Object...)` | criteria with one `Restrictions.eq`/`isNull` per key/value pair | `UserServiceImpl`, `ProductImageServiceImpl`, `ProductServiceImpl`, `PropertyValueServiceImpl`, `ForeAction`, `CartTotalItemNumberInterceptor` |

The pagination pair (`total()` and `listByPage`) and the `Page` bean's arithmetic are covered in
[Workflow: Pagination and Search](/openwiki/workflows/pagination-and-search.md).

## 4. The query vocabulary

Everything the generic methods read with is one of two dialects, and both address Java property names,
never column names.

**`DetachedCriteria` with a fixed default order.** `list()`, `listByPage`, `listByParent`, `list(Page,
parent)` and `list(Object...)` all build `DetachedCriteria.forClass(clazz)` and add
`Order.desc("id")` (`BaseServiceImpl.java#L45-L57`, `#L83-L91`, `#L128-L146`). Descending id — newest
first — is therefore the layer-wide default ordering, and `ProductServiceImpl.search` is the one
read that deviates: it adds `Restrictions.like("name", "%" + keyword + "%")` and no order at all
(`src/com/caozhihu/tmall/service/impl/ProductServiceImpl.java#L81-L86`).

**Parent-scoped queries assume the property name equals the parent class name**, uncapitalized:

```java
String parentName = parent.getClass().getSimpleName();
String parentNameWithFirstLetterLower = StringUtils.uncapitalize(parentName);
DetachedCriteria dc = DetachedCriteria.forClass(clazz);
dc.add(Restrictions.eq(parentNameWithFirstLetterLower, parent));
```

(`src/com/caozhihu/tmall/service/impl/BaseServiceImpl.java#L70-L81`; `total(Object parent)` does the same
with an HQL string, `#L93-L106`.)

This works today only because every child names its association after the parent class:

| Call | Parent runtime class | Property compared |
|---|---|---|
| `orderItemService.listByParent(order)` | `Order` | `orderItem.order` |
| `productService.listByParent(category)` | `Category` | `product.category` |
| `propertyService.listByParent(category)` | `Category` | `property.category` |
| `reviewService.listByParent(product)` | `Product` | `review.product` |
| `propertyValueService.listByParent(product)` | `Product` | `propertyValue.product` |

Because the property name comes from `parent.getClass().getSimpleName()` at runtime, renaming a parent
entity class without renaming the association property silently changes the query, and passing anything
other than the concrete mapped class (a subclass, or a proxy whose class name is a Hibernate-generated
one) produces a property name that does not exist.

**Counting is done in HQL, not criteria.** `total()` concatenates the entity's fully qualified class name
into `select count(*) from <FQN>` and casts the single result to `Long`
(`BaseServiceImpl.java#L59-L68`); `total(Object parent)` formats
`select count(*) from %s bean where bean.%s = ?0` and binds the parent as the positional parameter
(`#L93-L106`). `PropertyServiceImpl.total(Category)` is a hand-written copy of the same shape
(`src/com/caozhihu/tmall/service/impl/PropertyServiceImpl.java#L33-L44`). The count and the page of rows
it belongs to are two separate statements, so a paged screen can count a different number of rows than it
shows.

**`list(Object... pairParams)` folds pairs into criteria.** Each even-index argument is a criteria
property name and the next is its value; a null value becomes `Restrictions.isNull(key)`, anything else
`Restrictions.eq(key, value)` (`BaseServiceImpl.java#L128-L146`). Properties:

- The pairs are folded into a `HashMap` first, so the emitted restrictions are ANDed in no particular
  order, duplicate keys overwrite, and a null *key* would throw `NullPointerException` on `toString()`.
- An odd argument count leaves the last key without a value and throws `ArrayIndexOutOfBoundsException`.
- Called with no arguments it degenerates into `list()` without the ordering guarantee — no caller does so.
- The null-means-`IS NULL` convention is what expresses "still in the cart":
  `orderItemService.list("user", user, "order", null)`
  (`src/com/caozhihu/tmall/action/ForeAction.java#L152-L160`,
  `src/com/caozhihu/tmall/interceptor/CartTotalItemNumberInterceptor.java#L32-L44`).
- In-memory helpers build on it: `UserServiceImpl.get(name, password)` is
  `list("name", name, "password", password)` with the first row returned and `null` for an empty result,
  and `isExit(name)` is `list("name", name)` plus an `isEmpty()` test
  (`src/com/caozhihu/tmall/service/impl/UserServiceImpl.java#L11-L27`). Both hand back every matching row
  rather than narrowing the query.
- `ProductServiceImpl.setSaleAndReviewNumber` is where the vocabulary shows its limits: `saleCount` is
  summed in Java from `orderItemService.list("product", product)` because the generic
  `orderItemService.total(product)` counts order-item *rows*, not units. Both the old call and the reason
  are left in the file as comments (`src/com/caozhihu/tmall/service/impl/ProductServiceImpl.java#L58-L72`).

## 5. Delegation: `ServiceDelegateDAO`

`ServiceDelegateDAO` is the class that lets a service call `findByCriteria(dc)` instead of
`dao.findByCriteria(dc)`. It holds the single DAO bean and mirrors the `HibernateTemplate` API, roughly a
hundred forwarding methods, each one line (`src/com/caozhihu/tmall/service/impl/ServiceDelegateDAO.java#L19-L335`).
The README states the intent directly: 调用者只知道 ServiceDelegateDAO 这个类的 delete(Object entity) 方法，
而意识不到 dao 的存在 (`README.md#L252-L266`).

- **The DAO arrives by name.** `@Resource(name = "dao") private DAOImpl dao;` — the literal string
  `"dao"` is the contract with `@Repository("dao")` on `DAOImpl`
  (`ServiceDelegateDAO.java#L19-L25`, `src/com/caozhihu/tmall/dao/impl/DAOImpl.java#L9-L18`). The in-file
  comment records that `@Autowired` injected `null` here and that `@Resource` was the fix; whether that
  observation still holds for the current wiring is 【人工评审待确认】.
- **The SessionFactory is pushed into the DAO a second time.**
  `@Resource(name = "sf") public void setSessionFactory(SessionFactory sessionFactory)` calls
  `dao.setSessionFactory(sessionFactory)` (`ServiceDelegateDAO.java#L27-L30`), duplicating the injection
  `DAOImpl` already performs on itself. `getSessionFactory()` forwards to `dao`, and both are exposed to
  subclasses through the same class (`#L32-L34`).
- **Subclasses therefore reach the whole template surface.** `BaseServiceImpl.list()` calls
  `findByCriteria(dc)` unqualified; `createOrder` calls `save(order)`; `get(int)` calls `super.get(clazz,
  id)` (`BaseServiceImpl.java#L45-L50`, `#L118-L126`, `OrderServiceImpl.java#L23-L34`). No service ever
  names `dao`.
- **Only a small slice is used.** `load`, `loadAll`, `refresh`, `contains`, `evict`, `initialize`,
  `enableFilter`, `lock`, `replicate`, `persist`, `merge`, `deleteAll`, `flush`, `clear`,
  `findByNamedQuery*`, `findByExample*`, `iterate`, `closeIterator`, `bulkUpdate`, `execute` and the
  cache/fetch-size/filter setters have no caller under `src/` (`ServiceDelegateDAO.java#L96-L102`,
  `#L307-L333`). They are inert surface, not a safety net — see
  [Persistence Layer](/openwiki/architecture/persistence-layer.md) §3 for the used subset.
- **The DAO is not otherwise reachable from above.** `DAOImpl` is imported only by
  `ServiceDelegateDAO` and by the test class, so this delegation class is the only production path to the
  database.

## 6. Pairing rules for `XxxService` / `XxxServiceImpl`

Adding a service means satisfying four names at once. All four are conventions with no compiler or
configuration check behind them:

| Element | Rule | Example |
|---|---|---|
| Interface | `com.caozhihu.tmall.service.XxxService`, extending `BaseService` | `CategoryService` |
| Implementation | `com.caozhihu.tmall.service.impl.XxxServiceImpl`, extending `BaseServiceImpl`, annotated `@Service("<xxx>Service")` | `CategoryServiceImpl` |
| Entity | `com.caozhihu.tmall.pojo.Xxx` — derived by rewriting `.service.impl` → `.pojo` and stripping the `ServiceImpl` suffix | `Category` |
| Parent-scoped queries | for `listByParent(parent)` on this service, the entity's association property must equal the uncapitalized simple name of the parent class | `Product.category` for `productService.listByParent(category)` |

Only the first three are enforced — and only as a printed stack trace (§2). The fourth is enforced by
runtime SQL failure.

Two consequences worth stating explicitly:

- **Entity-agnostic CRUD plus a per-service entity class means one impl cannot serve two entities.** A
  second service for the same entity (say a read-optimized variant) would derive its entity class from its
  own name and would need the corresponding `pojo` class to exist under that name.
- **The insert path returns the generated key as `Integer`.**
  `BaseServiceImpl.save` wraps the inherited `save` and casts its `Serializable` return value
  (`BaseServiceImpl.java#L108-L116`). That cast is valid only while the mapped identifier is a primitive
  `int` — every entity currently uses `@Id private int id` — and it is unused at every call site, so
  switching an entity to `long` would introduce a `ClassCastException` on the first insert without any
  benefit being lost first.

## 7. Status and type strings as shared vocabulary

Two interfaces export string constants that are not implementation details of their own service but
cross-layer vocabulary:

**`OrderService` status constants** — `waitPay`, `waitDelivery`, `waitConfirm`, `waitReview`, `finish`,
`delete` (`src/com/caozhihu/tmall/service/OrderService.java#L12-L17`). They are written into
`Order.status` (a plain `String` column, `src/com/caozhihu/tmall/pojo/Order.java#L31`) by the actions:
`ForeAction` sets `waitPay`, `waitDelivery`, `waitReview`, `delete` and `finish` at the five points of the
purchase flow (`src/com/caozhihu/tmall/action/ForeAction.java#L257-L325`) and `OrderAction` sets
`waitConfirm` (`src/com/caozhihu/tmall/action/OrderAction.java#L27`). They are read back in three places:

- `Order.getStatusDesc()` — a `switch (status)` over the constants, inside the *entity*, which therefore
  imports `com.caozhihu.tmall.service.OrderService` (`src/com/caozhihu/tmall/pojo/Order.java#L40-L65`).
  This is the one place where the domain model depends on a service interface.
- `OrderServiceImpl.listByUserWithoutDelete` uses `Restrictions.ne("status", OrderService.delete)` — the
  only place a status constant appears inside a query
  (`src/com/caozhihu/tmall/service/impl/OrderServiceImpl.java#L36-L42`).
- The views, as literals rather than constants: `web/include/cart/boughtPage.jsp` compares
  `o.status=='waitPay'` (`web/include/cart/boughtPage.jsp#L163`).

**`ProductImageService` type constants** — `type_single` and `type_detail`
(`src/com/caozhihu/tmall/service/ProductImageService.java#L8-L11`), stored in the unconstrained
`ProductImage.type` string. They are read into queries by `ProductImageServiceImpl.setFirstProductImage`
(`src/com/caozhihu/tmall/service/impl/ProductImageServiceImpl.java#L16-L26`), `ForeAction` and
`ProductImageAction`, and written as the literal `type_single` by the hidden field in
`web/admin/listProductImage.jsp#L63`.

Because both vocabularies end up as literals in JSPs, the constant values — not merely their names — are
part of the contract; changing a constant's value silently desynchronizes a view.

【人工评审待确认】 The `delete` status is a persisted string, and `listByUserWithoutDelete` filters it out
rather than removing rows, yet `OrderServiceImpl` also inherits a real `delete(Object)`. Whether "delete"
is intended as a soft-delete state (and whether anything ever hard-deletes an order) is not stated
anywhere in the code.

## 8. Where `@Transactional` actually sits

Transaction advice is bound by `<tx:annotation-driven transaction-manager="transactionManager"/>`
(`src/applicationContext.xml#L14-L17`), and within this layer the annotations are placed at three levels

| Method | Annotation | Effect |
|---|---|---|
| `ServiceDelegateDAO.save(Object)` and the `save`/`update`/`delete`/`saveOrUpdate` overloads (`ServiceDelegateDAO.java#L176-L241`) | `@Transactional(readOnly = false)` | one entity per call; applies to the inherited, un-overridden `update` / `delete` |
| `BaseServiceImpl.save` (`BaseServiceImpl.java#L112-L116`) | `@Transactional` | overrides the base method, so `save` calls take the annotation from *this* declaration, not from `ServiceDelegateDAO` |
| `OrderServiceImpl.createOrder` (`OrderServiceImpl.java#L23-L34`) | `@Transactional(propagation = Propagation.REQUIRED, rollbackForClassName = "Exception")` | the `Order` insert plus one `update` per `OrderItem` in one unit |
| `ReviewServiceImpl.saveReviewAndUpdateOrderStatus` (`ReviewServiceImpl.java#L18-L23`) | `@Transactional(propagation = Propagation.REQUIRED, rollbackForClassName = "Exception")` | the `Order` update plus the `Review` insert in one unit |
| `PropertyValueServiceImpl.init` (`PropertyValueServiceImpl.java#L22-L35`) | `@Transactional(readOnly = false)` | the read-then-insert loop that seeds missing `PropertyValue` rows |

- **Only composite writes get a boundary of their own.** Every single-entity write — `save`, `update`,
  `delete` on any of the nine services — is transactional because the inherited declaration carries
  `@Transactional(readOnly = false)`; the per-endpoint consequences are tabulated in
  [Order Lifecycle](/openwiki/workflows/order-lifecycle.md) and
  [Admin CRUD](/openwiki/workflows/admin-crud.md).
- **Self-calls do not open new transactions.** Inside `createOrder`, `save(order)` is a `this` call that
  bypasses the proxy; the transaction that covers it is the one `createOrder` itself opened. The same
  pattern is present in `init` and `saveReviewAndUpdateOrderStatus`, where the outer annotation is the
  only reason the composite operation is atomic.
- **Reads are unannotated on purpose or by omission — the code does not say.** `list*`, `total*`, `get`
  and the in-memory `fill`/`setFirstProductImage`/`setSaleAndReviewNumber` helpers carry no annotation.
  【人工评审待确认】 whether leaving reads outside an explicit boundary is a deliberate convention here or
  simply where nobody needed one, and whether the two different annotation styles for writes
  (`@Transactional` versus `@Transactional(readOnly = false)`) reflect a decision or the order in which
  fixes were applied.
- **`rollbackForClassName = "Exception"` has no visible effect on the two annotated flows.** Both only
  propagate unchecked exceptions (`DataAccessException` from the template), which already roll back by
  default. 【人工评审待确认】 whether it was intended to cover future checked exceptions.

## 9. Extending the layer

- **A new entity with plain CRUD needs one interface and one empty class**, plus the `pojo` class and its
  `.service.impl` / `.pojo` pair. That is the whole extension contract, and it is the reason
  `CategoryServiceImpl` is empty (`README.md#L281-L295`).
- **Extra queries go in the subclass, using the inherited `clazz` and the inherited template methods.**
  `PropertyServiceImpl.listByCategory` and `ProductServiceImpl.search` are the templates to copy: build a
  `DetachedCriteria`, add `Order.desc("id")` and return `findByCriteria(...)`
  (`src/com/caozhihu/tmall/service/impl/PropertyServiceImpl.java#L16-L30`,
  `ProductServiceImpl.java#L81-L86`). Note that `PropertyServiceImpl` also redeclares
  `list(Page, Category)` and `total(Category)`: these *overload* rather than override the
  `BaseServiceImpl` `Object` versions, so which one runs depends on the static type the caller holds.
- **An override that changes `clazz` is not possible.** The field is `protected` and assigned only in the
  base constructor, so a subclass cannot point its CRUD methods at a different entity; it can only add
  methods that build their own criteria.
- **Cross-service orchestration belongs in the service that owns the transaction boundary** —
  `OrderServiceImpl.createOrder` and `ReviewServiceImpl.saveReviewAndUpdateOrderStatus` inject sibling
  services with `@Autowired` and call them through their proxies
  (`OrderServiceImpl.java#L20-L21`, `ReviewServiceImpl.java#L15-L16`,
  `ProductServiceImpl.java#L18-L23`).
- **Do not add a `@Service` for a class whose name does not follow the convention.** No test, no
  configuration and no startup check will fail; the first symptom is a broken query long after startup.

## 10. What verifies this layer

Nothing does, directly. `src/com/caozhihu/tmall/test/TestTmall.java` is the only automated entry point and
it drives `DAOImpl` — not a single service
(`src/com/caozhihu/tmall/test/TestTmall.java#L15-L31`). Its one incidental contribution is that
`@ContextConfiguration("classpath:applicationContext.xml")` instantiates all nine service beans, so the
`clazz` derivation runs once per implementation during that context load; a naming violation would print
nine `ClassNotFoundException` traces and still let the test pass, because neither test method issues a
service query. The reflection contract, the `list(Object...)` pair folding and the parent-property
convention are verified only by the running application. See
[Verification](/openwiki/testing/verification.md).

## See also

- [Persistence Layer](/openwiki/architecture/persistence-layer.md) — HibernateTemplate, the `dao` and `sf`
  bean names, the mapping the criteria rely on, and the transaction gaps.
- [Action Layer](/openwiki/architecture/action-layer.md) — how actions obtain and call these services.
- [Domain Model](/openwiki/concepts/domain-model.md) — the nine entities `clazz` resolves to and their
  association property names.
- [Workflow: Pagination and Search](/openwiki/workflows/pagination-and-search.md) — `Page`, the
  `total()`/`listByPage` pair and the unpaged product search.
- [Workflow: Order Lifecycle](/openwiki/workflows/order-lifecycle.md) — the status vocabulary in motion.
- [SDD Baseline](/openwiki/concepts/sdd-baseline.md) — the layering and naming rules this page documents
  the enforcement of.
