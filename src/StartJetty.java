import org.eclipse.jetty.annotations.AnnotationConfiguration;
import org.eclipse.jetty.plus.webapp.EnvConfiguration;
import org.eclipse.jetty.plus.webapp.PlusConfiguration;
import org.eclipse.jetty.rewrite.handler.Rule;
import org.eclipse.jetty.rewrite.handler.RewriteHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.FragmentConfiguration;
import org.eclipse.jetty.webapp.JettyWebXmlConfiguration;
import org.eclipse.jetty.webapp.MetaInfConfiguration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebInfConfiguration;
import org.eclipse.jetty.webapp.WebXmlConfiguration;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 嵌入式 Jetty 9.x 启动器 —— 仅供本地演练，用于替代外置 Tomcat + MySQL。
 *
 * 功能：
 *   1. 自动把 src 下的源码编译进 web/WEB-INF/classes，并同步 src 下的 xml/sql 等资源；
 *   2. 加载原生 web/WEB-INF/web.xml 启动整个 Web 应用（业务代码零改动）；
 *   3. 在独立端口 8082 启动 H2 数据库控制台（避免改动 web.xml，也绕开 Struts2 过滤器拦截）；
 *   4. 数据库为 H2 内存库，启动时由 Spring 执行 classpath:sql/tmall_ssh_h2.sql 自动建表。
 *
 * 启动方式（任选其一，均为运行本类的 main 方法）：
 *   A. IDE 中直接运行 StartJetty；
 *   B. 命令行：java -cp "web/WEB-INF/lib/*" src/StartJetty.java
 *
 * 访问地址：
 *   前台商城    http://localhost:8080/
 *   后台管理    http://localhost:8080/admin_category_list
 *   H2控制台    http://localhost:8082  (JDBC URL: jdbc:h2:mem:tmall_ssh;MODE=MySQL;DB_CLOSE_DELAY=-1  用户 sa  密码空)
 */
public class StartJetty {

    static final int PORT = 8080;
    static final int H2_CONSOLE_PORT = 8082;
    static final String CONTEXT_PATH = "/";

    public static void main(String[] args) throws Exception {
        openJdk9PlusModules();
        File projectRoot = locateProjectRoot();
        File webRoot = new File(projectRoot, "web");
        File webXml = new File(webRoot, "WEB-INF/web.xml");
        File libDir = new File(webRoot, "WEB-INF/lib");
        File classesDir = new File(webRoot, "WEB-INF/classes");
        if (!webXml.isFile()) {
            throw new IllegalStateException("找不到 web/WEB-INF/web.xml，请确认项目根目录: " + projectRoot);
        }

        ensureCompiledClasses(projectRoot, libDir, classesDir);

        Server server = new Server(PORT);
        WebAppContext context = new WebAppContext();
        context.setContextPath(CONTEXT_PATH);
        context.setResourceBase(webRoot.getAbsolutePath());
        context.setDescriptor(webXml.getAbsolutePath());
        // 由于全部依赖jar同时位于 JVM classpath 与 WEB-INF/lib，
        // 强制 log4j / h2 由父(容器)加载器统一加载，避免双份类导致的 ServiceConfigurationError 与内存库隔离问题
        context.addSystemClass("org.apache.logging.log4j.");
        context.addSystemClass("org.h2.");
        context.addSystemClass("org.apache.juli.");
        // JSP(Jasper)/EL 相关类同样双份存在于 classpath 与 WEB-INF/lib，
        // 必须统一由父加载器加载，否则 TldCache 触发 ClassCastException（webapp 直接 unavailable）
        context.addSystemClass("org.apache.jasper.");
        context.addSystemClass("org.apache.el.");
        context.addSystemClass("javax.servlet.jsp.");
        context.addSystemClass("org.eclipse.jetty.apache.jsp.");

        // 显式指定完整 Configuration 链：Jetty 嵌入模式的默认链不含 AnnotationConfiguration，
        // 缺它时 ServletContainerInitializer（JSP 的 JasperInitializer）不会执行，JSP 编译会报 getTldCache() is null
        context.setConfigurations(new Configuration[]{
                new WebInfConfiguration(), new WebXmlConfiguration(), new MetaInfConfiguration(),
                new FragmentConfiguration(), new EnvConfiguration(), new PlusConfiguration(),
                new AnnotationConfiguration(), new JettyWebXmlConfiguration()});

        // 访问 "/" 直接转给 /forehome：web.xml 无 welcome-file-list，且 Jetty 的 DefaultServlet 默认
        // 不把 welcome file 交给 JSP servlet（与 Tomcat 行为不同），welcome 机制对 index.jsp 无效；
        // web.xml 禁止修改，故用 RewriteHandler 实现。注意不能用 RewritePatternRule——其 pattern 是正则
        // 语义（前缀匹配），pattern "/" 会把 /img/** 等所有路径都重写成 /forehome，导致全站图片变成 HTML。
        // 这里用匿名 Rule 做「仅精确匹配 /」的重写。
        RewriteHandler rewrite = new RewriteHandler();
        rewrite.addRule(new Rule() {
            @Override
            public String matchAndApply(String target, javax.servlet.http.HttpServletRequest request,
                                        javax.servlet.http.HttpServletResponse response) {
                return "/".equals(target) ? "/forehome" : null;
            }
        });
        rewrite.setHandler(context);
        server.setHandler(rewrite);
        server.setStopAtShutdown(true);

        // H2 控制台用独立端口启动：web.xml 中的 Struts2 过滤器会拦截 /h2-console/* 报 “no action mapped”
        org.h2.tools.Server.createWebServer("-webPort", String.valueOf(H2_CONSOLE_PORT)).start();

        server.start();
        System.out.println();
        System.out.println("========================================================");
        System.out.println(" Tmall_SSH 已启动 (Jetty 9 嵌入式, H2 内存库)");
        System.out.println("   前台商城 : http://localhost:" + PORT + CONTEXT_PATH);
        System.out.println("   后台管理 : http://localhost:" + PORT + "/admin_category_list");
        System.out.println("   H2控制台 : http://localhost:" + H2_CONSOLE_PORT);
        System.out.println("     - JDBC URL : jdbc:h2:mem:tmall_ssh;MODE=MySQL;DB_CLOSE_DELAY=-1");
        System.out.println("     - 用户名   : sa      密码 : (留空)");
        System.out.println("========================================================");
        System.out.println();
        server.join();
    }

    /** 定位项目根目录（包含 web/ 目录的那一级）。 */
    static File locateProjectRoot() {
        // 1. 优先用当前工作目录及其父目录
        File dir = new File("").getAbsoluteFile();
        for (int i = 0; i < 4 && dir != null; i++) {
            if (new File(dir, "web/WEB-INF/web.xml").isFile()) return dir;
            dir = dir.getParentFile();
        }
        // 2. 退而求其次：从本类所在位置向上找（兼容 IDE 不同 working directory）
        try {
            Path codePath = Paths.get(StartJetty.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path cur = codePath.toAbsolutePath().getParent();
            for (int i = 0; i < 6 && cur != null; i++) {
                if (cur.resolve("web/WEB-INF/web.xml").toFile().isFile()) return cur.toFile();
                cur = cur.getParent();
            }
        } catch (Exception ignore) {
        }
        throw new IllegalStateException("无法定位项目根目录（未找到 web/WEB-INF/web.xml），请将 working directory 设为项目根目录");
    }

    /**
     * 把 src 下全部源码编译到 web/WEB-INF/classes，并同步非 .java 资源文件。
     * 每次启动增量执行，保证与源码一致；纯运行时也可用 IDE 的编译产物，此处统一走自编译，避免依赖 IDE 配置。
     */
    static void ensureCompiledClasses(File projectRoot, File libDir, File classesDir) throws Exception {
        File srcDir = new File(projectRoot, "src");
        if (!srcDir.isDirectory()) throw new IllegalStateException("找不到源码目录 src/");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.out.println("[StartJetty] 当前是 JRE 环境，跳过编译，直接使用 web/WEB-INF/classes 已有产物");
            return;
        }
        List<Path> javaFiles;
        try (Stream<Path> s = Files.walk(srcDir.toPath())) {
            javaFiles = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        if (javaFiles.isEmpty()) return;

        classesDir.mkdirs();
        List<String> options = new ArrayList<>(Arrays.asList(
                "-encoding", "UTF-8",
                "-nowarn",
                "-source", "8", "-target", "8",
                "-cp", buildLibClasspath(libDir) + File.pathSeparator + classesDir.getAbsolutePath(),
                "-d", classesDir.getAbsolutePath()
        ));
        List<File> files = javaFiles.stream().map(Path::toFile).collect(Collectors.toList());
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
            JavaCompiler.CompilationTask task = compiler.getTask(null, fm, diagnostic -> {
                        if (diagnostic.getKind() == javax.tools.Diagnostic.Kind.ERROR) {
                            System.err.println("[StartJetty] 编译错误: " + diagnostic);
                        }
                    }, options, null, fm.getJavaFileObjectsFromFiles(files));
            Boolean ok = task.call();
            if (!Boolean.TRUE.equals(ok)) {
                throw new IllegalStateException("源码编译失败，请查看上方编译错误");
            }
        }
        syncResources(srcDir, classesDir);
        System.out.println("[StartJetty] 已编译 " + javaFiles.size() + " 个源码文件 -> " + classesDir.getAbsolutePath());
    }

    /** 同步 src 下非 .java 资源（xml/sql/properties 等）到 WEB-INF/classes。 */
    static void syncResources(File srcDir, File classesDir) throws Exception {
        try (Stream<Path> s = Files.walk(srcDir.toPath())) {
            s.filter(p -> !p.toString().endsWith(".java"))
             .filter(Files::isRegularFile)
             .forEach(p -> {
                 Path rel = srcDir.toPath().relativize(p);
                 Path target = classesDir.toPath().resolve(rel);
                 try {
                     if (!Files.exists(target) || Files.getLastModifiedTime(target).toMillis() < Files.getLastModifiedTime(p).toMillis()) {
                         Files.createDirectories(target.getParent());
                         Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                     }
                 } catch (Exception e) {
                     throw new RuntimeException("复制资源失败: " + p, e);
                 }
             });
        }
    }

    static String buildLibClasspath(File libDir) {
        File[] jars = libDir.listFiles((d, n) -> n.toLowerCase().endsWith(".jar"));
        if (jars == null) return "";
        Arrays.sort(jars);
        return Stream.of(jars).map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator));
    }

    /**
     * JDK 9+ 上旧框架(Spring 4.x / cglib 等)可能通过反射访问 JDK 内部包，
     * 这里以编程方式等价于 --add-opens，JDK 8 上为空操作。
     */
    static void openJdk9PlusModules() {
        try {
            Class<?> moduleClass = Class.forName("java.lang.Module");
            Object thisModule = StartJetty.class.getClass().getMethod("getModule").invoke(StartJetty.class);
            Object baseModule = Class.class.getMethod("getModule").invoke(Class.class);
            Method addOpens = moduleClass.getMethod("addOpens", String.class, moduleClass);
            String[] pkgs = {"java.lang", "java.util", "java.lang.reflect", "java.net",
                    "java.io", "java.nio", "java.nio.file", "java.sql", "java.text"};
            for (String pkg : pkgs) {
                try {
                    addOpens.invoke(baseModule, pkg, thisModule);
                } catch (Exception ignore) {
                }
            }
        } catch (Throwable ignore) {
            // JDK 8：没有模块系统，忽略
        }
    }
}
