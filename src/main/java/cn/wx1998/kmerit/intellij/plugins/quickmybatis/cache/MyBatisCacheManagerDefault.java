package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.JavaElementInfo;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.XmlElementInfo;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.persistent.MyBatisCachePersistenceManager;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.parser.JavaParser;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.parser.JavaParserFactory;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.parser.MyBatisXmlParser;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.parser.MyBatisXmlParserFactory;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.services.JavaService;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.services.XmlService;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.TargetMethodsHolder;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.XmlTagLocator;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 缓存管理器默认实现，基于 MyBatisCacheConfig 管理缓存生命周期
 * 负责缓存的创建、更新、失效、扫描和统计
 */
public class MyBatisCacheManagerDefault implements MyBatisCacheManager {
    // 日志前缀
    private static final String CACHE_LOG_PREFIX = "[MyBatis缓存管理器] ";
    // 单例键（按项目隔离）
    private static final Key<MyBatisCacheManagerDefault> INSTANCE_KEY = Key.create("MyBatisCacheManager.Instance");
    // 日志实例
    private static final Logger LOG = Logger.getInstance(MyBatisCacheManagerDefault.class);
    // 定时扫描线程池
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    // 项目实例
    private final Project project;
    // 缓存有效性标记（true=有效，false=需刷新）
    private final Map<String, Boolean> cacheValidityMap = new ConcurrentHashMap<>();
    // 缓存统计信息
    private final CacheStatistics statistics = new DefaultCacheStatistics();
    // 防止重复扫描的锁
    private final transient Object scanLock = new Object();
    // 缓存版本号，用于增量更新
    private final AtomicLong cacheVersion = new AtomicLong(1);
    // 全局缓存核心配置（阶段一实现的缓存结构）
    private MyBatisCacheConfig cacheConfig;
    // 定时扫描间隔（5分钟，单位：毫秒）
    private long scanIntervalMs = 5 * 60 * 1000;

    /**
     * 私有构造器（单例模式）
     */
    MyBatisCacheManagerDefault(@NotNull Project project) {
        this.project = project;
        this.cacheConfig = MyBatisCacheConfig.getInstance(project);
        this.initialize();
    }

    /**
     * 获取单例实例（按项目隔离）
     */
    public static MyBatisCacheManagerDefault getInstance(@NotNull Project project) {
        MyBatisCacheManagerDefault instance = project.getUserData(INSTANCE_KEY);
        if (instance == null) {
            instance = new MyBatisCacheManagerDefault(project);
            project.putUserData(INSTANCE_KEY, instance);
        }
        return instance;
    }

    /**
     * 初始化：注册文件监听器和定时任务
     */
    private void initialize() {
        // 1. 注册VFS文件变化监听器（主动缓存驱逐）
        registerFileListener();
        // 2. 启动定时扫描任务（定时缓存驱逐）
        startPeriodicScan();
        LOG.debug(CACHE_LOG_PREFIX + "缓存管理器初始化完成，项目: " + project.getName());
    }

    /**
     * 注册VFS文件变化监听器，文件修改/删除时主动失效缓存
     */
    private void registerFileListener() {
        MessageBusConnection connection = project.getMessageBus().connect();
        // 获取项目文件索引
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                for (VFileEvent event : events) {
                    VirtualFile file = event.getFile();
                    if (file == null) continue;

                    // 1. 使用API判断文件是否被排除（.idea、target等目录会被自动识别）
                    if (fileIndex.isExcluded(file)) {
                        LOG.debug(CACHE_LOG_PREFIX + "忽略被排除的文件: " + file.getPath());
                        continue;
                    }


                    // 2. 可选：只处理源码目录中的文件（进一步精准过滤）
                    if (!fileIndex.isInSourceContent(file)) {
                        LOG.debug(CACHE_LOG_PREFIX + "忽略非源码目录文件: " + file.getPath());
                        continue;
                    }

                    // 3. 只处理Java和XML文件
                    String extension = file.getExtension();
                    if (extension == null) continue;
                    if (!extension.equals("java") && !extension.equals("xml")) continue;

                    // 4. 处理有效文件的变更
                    if (event instanceof VFileContentChangeEvent) {
                        invalidateFileCache(file.getPath());
                        LOG.debug(CACHE_LOG_PREFIX + "文件变化触发缓存失效: " + file.getPath());
                    }
                    if (event instanceof VFileDeleteEvent) {
                        invalidateFileCache(file.getPath());
                        LOG.debug(CACHE_LOG_PREFIX + "文件删除触发缓存失效: " + file.getPath());
                    }
                }
            }
        });
    }

    /**
     * 启动定时扫描任务，检查文件是否变更（基于文件摘要）
     */
    private void startPeriodicScan() {
        scheduler.scheduleAtFixedRate(() -> {
            if (project.isDisposed()) {
                scheduler.shutdown();
                return;
            }
            // 同步执行扫描，避免并发
            synchronized (scanLock) {
                scanForFileChanges();
            }
        }, scanIntervalMs, scanIntervalMs, TimeUnit.MILLISECONDS);
        LOG.debug(CACHE_LOG_PREFIX + "定时扫描任务启动，间隔: " + scanIntervalMs + "ms");
    }

    /**
     * 扫描文件变化，通过摘要对比判断是否需要刷新缓存
     */
    void scanForFileChanges() {
        LOG.debug(CACHE_LOG_PREFIX + "开始定时扫描文件变化");
        int changedCount = 0;

        // 遍历所有缓存的文件摘要
        if (cacheConfig.getAllFileDigest() != null) {
            for (Map.Entry<String, String> entry : cacheConfig.getAllFileDigest().entrySet()) {
                String filePath = entry.getKey();
                String oldDigest = entry.getValue();

                VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
                if (file == null || !file.exists()) {
                    // 文件已删除，清除缓存
                    clearFileCache(filePath);
                    changedCount++;
                    continue;
                }

                // 计算当前文件摘要
                String newDigest = calculateFileDigest(file);
                if (!newDigest.equals(oldDigest)) {
                    // 摘要不一致，文件已修改
                    LOG.debug(CACHE_LOG_PREFIX + "文件内容变更: " + filePath + "（旧摘要: " + oldDigest + ", 新摘要: " + newDigest + "）");
                    clearFileCache(filePath);
                    cacheConfig.saveFileDigest(file, newDigest); // 更新摘要
                    reparseAndCacheFile(file); // 重新解析
                    changedCount++;
                }
            }
        }

        LOG.debug(CACHE_LOG_PREFIX + "定时扫描完成，发现 " + changedCount + " 个变更文件");
    }

    /**
     * 计算文件摘要（基于文件内容的哈希值）
     */
    private String calculateFileDigest(@NotNull VirtualFile file) {
        try {
            byte[] content = file.contentsToByteArray();
            return Integer.toHexString(Arrays.hashCode(content));
        } catch (Exception e) {
            LOG.error(CACHE_LOG_PREFIX + "计算文件摘要失败: " + file.getPath(), e);
            return "";
        }
    }

    /**
     * 重新解析文件并更新缓存
     */
    private void reparseAndCacheFile(@NotNull VirtualFile file) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile == null) return;

        if (psiFile instanceof XmlFile xmlFile) {
            // 重新解析XML文件
            MyBatisXmlParser parser = MyBatisXmlParserFactory.getRecommendedParser(project);
            MyBatisXmlParser.MyBatisParseResult parse = parser.parse(xmlFile);
            syncToCacheManager(parse);
        } else if (psiFile instanceof PsiJavaFile psiJavaFile) {
            // 重新解析Java文件
            JavaParser parser = JavaParserFactory.getRecommendedParser(project);
            JavaParser.JavaParseResult parse = parser.parse(psiJavaFile);
            syncToCacheManager(parse);
        }
    }

    // ========================= 缓存操作核心方法 =========================

    @Override
    public void putClassXmlMapping(@NotNull String className, @NotNull String xmlFilePath) {
        LOG.debug(CACHE_LOG_PREFIX + "建立Class与Xml映射: " + className + " -> " + xmlFilePath);
    }

    @Override
    public void putMethodStatementMapping(@NotNull String className, @NotNull String methodName, @NotNull String statementId) {
        LOG.debug(CACHE_LOG_PREFIX + "建立方法与SqlId映射: " + className + "#" + methodName + " -> " + statementId);
    }

    @Override
    @Nullable
    public Set<String> getXmlFilesForClass(@NotNull String className) {
        // 从 cacheConfig 中查询Java类关联的所有XML文件路径
        Set<String> sqlIds = cacheConfig.getSqlIdsByJavaFile(className); // 假设类名作为Java文件路径的标识
        if (sqlIds == null) return null;

        return sqlIds.stream().flatMap(sqlId -> cacheConfig.getXmlElementsBySqlId(sqlId).stream()).map(XmlElementInfo::getFilePath).collect(Collectors.toSet());
    }

    @Override
    @Nullable
    public String getClassForXmlFile(@NotNull String xmlFilePath) {
        // 从 cacheConfig 中查询XML文件关联的Java类
        Set<String> sqlIds = cacheConfig.getSqlIdsByXmlFile(xmlFilePath);
        if (sqlIds == null || sqlIds.isEmpty()) return null;

        // 取第一个SQL ID关联的Java类（实际场景可能需要更复杂的逻辑）
        String firstSqlId = sqlIds.iterator().next();
        Set<JavaElementInfo> javaElements = cacheConfig.getJavaElementsBySqlId(firstSqlId);
        if (javaElements.isEmpty()) return null;

        return javaElements.iterator().next().getFilePath(); // 假设Java文件路径对应类名
    }

    @Override
    @Nullable
    public String getStatementIdForMethod(@NotNull String className, @NotNull String methodName) {
        // 生成SQL ID（与 JavaService 的计算逻辑一致）
        String sqlId = className + "." + methodName; // 需与配置的命名规则同步
        // 检查缓存中是否存在该SQL ID
        Set<XmlElementInfo> xmlElements = cacheConfig.getXmlElementsBySqlId(sqlId);
        return xmlElements.isEmpty() ? null : sqlId;
    }

    @Override
    @Nullable
    public String getMethodForStatementId(@NotNull String xmlFilePath, @NotNull String statementId) {
        // 从SQL ID反向查询Java方法
        Set<JavaElementInfo> javaElements = cacheConfig.getJavaElementsBySqlId(statementId);
        if (javaElements.isEmpty()) return null;

        // 过滤出当前XML文件关联的Java方法（简化逻辑）
        return javaElements.stream().filter(info -> "method".equals(info.getElementType())).map(JavaElementInfo::getSqlId).map(id -> id.substring(id.lastIndexOf(".") + 1)) // 从SQL ID中提取方法名
                .findFirst().orElse(null);
    }

    // ========================= 缓存失效与清理 =========================

    @Override
    public void clearClassCache(@NotNull String className) {
        // 清除类关联的所有缓存
        cacheConfig.clearJavaFileCache(className);
        cacheValidityMap.put(className, false);
        statistics.recordInvalidation();
        LOG.debug(CACHE_LOG_PREFIX + "清除类缓存: " + className);
    }

    @Override
    public void clearXmlFileCache(@NotNull String xmlFilePath) {
        // 清除XML文件关联的所有缓存
        cacheConfig.clearXmlFileCache(xmlFilePath);
        cacheValidityMap.put(xmlFilePath, false);
        statistics.recordInvalidation();
        LOG.debug(CACHE_LOG_PREFIX + "清除XML文件缓存: " + xmlFilePath);
    }

    @Override
    public void clearFileCache(@NotNull String filePath) {
        // 自动判断文件类型并清除缓存
        if (filePath.endsWith(".java")) {
            clearClassCache(filePath);
        } else if (filePath.endsWith(".xml")) {
            clearXmlFileCache(filePath);
        }
    }

    @Override
    public void clearAllCache() {
        cacheConfig.clearAllCache();
        cacheValidityMap.clear();
        statistics.reset();
        LOG.debug(CACHE_LOG_PREFIX + "清除所有缓存");
    }

    @Override
    public void invalidateFileCache(@NotNull PsiFile file) {
        String filePath = file.getVirtualFile().getPath();
        invalidateFileCache(filePath);
    }

    @Override
    public void invalidateFileCache(@NotNull String filePath) {
        // 标记缓存无效，等待下次扫描刷新
        cacheValidityMap.put(filePath, false);
        statistics.recordMiss(); // 缓存失效视为未命中
        LOG.debug(CACHE_LOG_PREFIX + "标记文件缓存无效: " + filePath);
    }

    @Override
    public boolean isCacheValid(@NotNull String filePath) {
        // 缓存默认有效，若被标记为无效则返回false
        return cacheValidityMap.getOrDefault(filePath, true);
    }

    @Override
    public void refreshInvalidatedCaches() {
        LOG.debug(CACHE_LOG_PREFIX + "开始刷新无效缓存");
        int refreshed = 0;

        // 收集所有无效的文件
        List<String> invalidFiles = cacheValidityMap.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey).toList();

        for (String filePath : invalidFiles) {
            VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
            if (file == null || !file.exists()) {
                clearFileCache(filePath); // 文件不存在，直接清除
                continue;
            }

            // 重新解析文件并更新缓存
            reparseAndCacheFile(file);
            cacheValidityMap.put(filePath, true); // 标记为有效
            refreshed++;
        }

        LOG.debug(CACHE_LOG_PREFIX + "刷新完成，共处理 " + refreshed + " 个无效文件");
    }

    // ========================= 配置与统计 =========================

    @Override
    @NotNull
    public MyBatisCacheConfig getCacheConfig() {
        return cacheConfig;
    }

    @Override
    public void setCacheConfig(@NotNull MyBatisCacheConfig config) {
        this.cacheConfig = config;
        LOG.debug(CACHE_LOG_PREFIX + "更新缓存配置");
    }

    @Override
    @NotNull
    public CacheStatistics getCacheStatistics() {
        return statistics;
    }

    @Override
    public void setScanInterval(long intervalMs) {
        this.scanIntervalMs = intervalMs;
        // 重启定时任务
        scheduler.shutdownNow();
        startPeriodicScan();
        LOG.debug(CACHE_LOG_PREFIX + "更新扫描间隔为: " + intervalMs + "ms");
    }

    /**
     * 执行全局缓存刷新（带进度提示）
     */
    public void performFullCacheRefresh() {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "刷新插件MyBatis缓存") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("正在清除旧缓存...");
                clearAllCache();

                // 设置进度条为不确定性
                indicator.setIndeterminate(false);
                // 初始化进度
                indicator.setFraction(0.0);

                // 进度
                double[] progress = {0.0};

                indicator.setText("正在重新解析所有MyBatis文件...");
                reparseAllMyBatisFiles(indicator, 0.3, progress);

                indicator.setText("正在处理Java文件...");
                processAllJavaFiles(indicator, 0.3, progress);

                indicator.setText("正在扫描MyBatis方法调用...");
                processAllJavaMyBatisMethodCall(indicator, 0.3, progress);

                indicator.setText("正在更新缓存版本...");
                incrementCacheVersion();

                indicator.setText("缓存刷新完成");
                MyBatisCachePersistenceManager.manualSaveCache(project);
            }
        });
    }

    /**
     * 重新解析所有MyBatis相关文件
     *
     * @param indicator  @See {@link com.intellij.openapi.progress.ProgressIndicator}
     * @param proportion 占用的总进度
     * @param progress   进度上限文
     */
    private void reparseAllMyBatisFiles(ProgressIndicator indicator, double proportion, double[] progress) {
        // 解析所有XML文件
        XmlService xmlService = XmlService.getInstance(project);
        // 获取所有 MyBatisXml 文件
        List<XmlFile> myBatisXmlFiles = xmlService.getMyBatisXmlFiles();
        // 获取 MyBatisXml 解析器
        MyBatisXmlParser parser = MyBatisXmlParserFactory.getRecommendedParser(project);
        // 计算每个文件的百分占比
        double step = proportion / (myBatisXmlFiles.size() + 1);
        // 遍历所有 MyBatisXml
        for (XmlFile xmlFile : myBatisXmlFiles) {
            // 获取当前文件的绝对路径
            String filePath = xmlFile.getVirtualFile().getPath();
            // 获取项目的绝对路径
            String basePath = project.getBasePath() == null ? "" : project.getBasePath();
            // 从文件绝对路径中删除项目绝对路径展示给用户
            String showText = filePath.replace(basePath, "");
            // 设置进度条上方显示的进度文本
            indicator.setText("解析XML文件: " + showText);
            // 设置进度条下显示的进度详细信息文本
            indicator.setText2(filePath);
            // 调用Xml解析器拿到结果
            MyBatisXmlParser.MyBatisParseResult parse = parser.parse(xmlFile);
            //更新缓存
            LOG.debug("MyBatis XML文件解析完成: " + filePath);
            syncToCacheManager(parse);
            LOG.debug("MyBatis XML同步到缓存完成: " + filePath);
            // 更新进度
            progress[0] += step;
            // 设置进度
            indicator.setFraction(progress[0]);
        }
    }

    /**
     * 处理所有相关Java文件
     */
    private void processAllJavaFiles(ProgressIndicator indicator, double proportion, double[] progress) {
        // 解析所有JAVA文件
        JavaService javaService = JavaService.getInstance(project);
        // 获取所有 Java 文件
        List<PsiJavaFile> javaFiles = javaService.getAllJavaFiles();
        // 获取 Java 解析器
        JavaParser parser = JavaParserFactory.getRecommendedParser(project);
        // 计算每个文件的百分占比
        double step = proportion / (javaFiles.size() + 1);
        // 获取所有映射的Java文件路径
        for (PsiJavaFile psiJavaFile : javaFiles) {
            // 获取当前文件的绝对路径
            String filePath = psiJavaFile.getVirtualFile().getPath();
            // 获取项目的绝对路径
            String basePath = project.getBasePath() == null ? "" : project.getBasePath();
            // 从文件绝对路径中删除项目绝对路径展示给用户
            String showText = filePath.replace(basePath, "");
            // 设置进度条上方显示的进度文本
            indicator.setText("解析Java文件: " + showText);
            // 设置进度条下显示的进度详细信息文本
            indicator.setText2(filePath);
            // 调用Xml解析器拿到结果
            JavaParser.JavaParseResult parse = parser.parse(psiJavaFile);
            //更新缓存
            LOG.debug("Java 文件解析完成: " + filePath);
            syncToCacheManager(parse);
            LOG.debug("Java 同步到缓存完成: " + filePath);
            // 更新进度
            progress[0] += step;
            // 设置进度
            indicator.setFraction(progress[0]);
        }
    }


    /**
     * 处理并缓存项目中所有MyBatis相关的方法调用。
     * 该方法会在后台线程中执行，并更新进度条。
     *
     * @param indicator  进度指示器
     * @param proportion 分配给该任务的总进度比例
     * @param progress   进度数组，用于与外部进度同步
     */
    public void processAllJavaMyBatisMethodCall(@NotNull ProgressIndicator indicator, double proportion, double[] progress) {
        indicator.setText("正在搜索MyBatis方法调用...");
        indicator.setText2("准备搜索...");

        TargetMethodsHolder targetHolder = TargetMethodsHolder.getInstance(project);
        Set<PsiMethod> targetMethods = targetHolder.getTargetMethods();

        if (targetMethods.isEmpty()) {
            indicator.setText("未找到任何目标方法，跳过搜索。");
            progress[0] += proportion;
            indicator.setFraction(progress[0]);
            return;
        }

        // 计算每个方法的进度步长
        double step = proportion / targetMethods.size();

        // 这里假设该方法已经在一个后台任务中被调用
        for (PsiMethod targetMethod : targetMethods) {
            // 更新进度信息
            indicator.setText2(String.format("正在搜索方法: %s", targetMethod.getName()));

            // 搜索项目中所有对该方法的调用
            Query<PsiReference> query = MethodReferencesSearch.search(targetMethod, GlobalSearchScope.allScope(project), true);

            // 使用 forEach 结合 Processor 来处理每个搜索结果
            query.forEach(new Processor<PsiReference>() {
                @Override
                public boolean process(PsiReference psiReference) {
                    // 检查用户是否点击了取消
                    if (indicator.isCanceled()) {
                        return false; // 返回 false 停止遍历
                    }

                    PsiElement element = psiReference.getElement();
                    PsiMethodCallExpression callExpr = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
                    if (callExpr != null) {
                        PsiExpressionList argumentList = callExpr.getArgumentList();

                        PsiExpression[] arguments = argumentList.getExpressions();
                        PsiExpression firstArgument = arguments[0];
                        String sqlId = JavaService.parseExpression(firstArgument);

                        if (sqlId != null && !sqlId.isEmpty()) {
                            syncToCacheManager(sqlId, callExpr);
                        }

                    }
                    return true; // 返回 true 继续遍历
                }

            });

            // 更新进度
            progress[0] += step;
            indicator.setFraction(progress[0]);
        }

        indicator.setText("MyBatis方法调用搜索完成。");
    }

    private void syncToCacheManager(String sqlId, PsiMethodCallExpression element) {
        // 检查是否有id属性
        ReadAction.run(() -> {
            PsiElement originalElement = element.getOriginalElement();

            JavaElementInfo javaElementInfo = XmlTagLocator.createJavaElementInfo(originalElement, sqlId, JavaService.TYPE_CLASS);
            cacheConfig.addJavaElementMapping(sqlId, javaElementInfo);
        });
    }

    /**
     * 增量刷新缓存（只刷新变更的文件）
     */
    public void performIncrementalRefresh() {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "增量刷新MyBatis缓存") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("正在检查变更文件...");
                scanForFileChanges(); // 调用父类的文件变更扫描
                indicator.setText("增量刷新完成");
                incrementCacheVersion();
                MyBatisCachePersistenceManager.manualSaveCache(project);
            }
        });
    }

    /**
     * 增加缓存版本号
     */
    public void incrementCacheVersion() {
        cacheVersion.incrementAndGet();
    }

    /**
     * 获取当前缓存版本号
     */
    public long getCurrentCacheVersion() {
        return cacheVersion.get();
    }

    /**
     * 检查缓存是否需要更新（与其他模块同步）
     */
    public boolean isCacheUpToDate(long lastKnownVersion) {
        return cacheVersion.get() == lastKnownVersion;
    }

    /**
     * 同步解析结果到缓存管理器
     */
    private void syncToCacheManager(MyBatisXmlParser.MyBatisParseResult result) {
        if (result == null) return;

        // 检查是否有id属性
        ReadAction.run(() -> {

            String namespaceName = result.getNamespaceName();
            XmlTag rootTag = result.getNamespace();

            XmlElementInfo xmlElementInfoRootTag = XmlTagLocator.createXmlElementInfo(rootTag, namespaceName, rootTag.getName());
            if (xmlElementInfoRootTag != null) {
                cacheConfig.addXmlElementMapping(namespaceName, xmlElementInfoRootTag);
            }

            Map<String, List<XmlTag>> statements = result.getStatements();
            Set<String> keySet = statements.keySet();
            for (String attributeName : keySet) {
                List<XmlTag> list = statements.get(attributeName);
                for (XmlTag tag : list) {
                    String sqlKey = namespaceName + '.' + attributeName;
                    XmlElementInfo xmlElementInfo = XmlTagLocator.createXmlElementInfo(tag, sqlKey, tag.getName());
                    if (xmlElementInfo != null) {
                        cacheConfig.addXmlElementMapping(sqlKey, xmlElementInfo);
                    }
                }
            }
        });
    }

    /**
     * 同步解析结果到缓存管理器
     */
    private void syncToCacheManager(JavaParser.JavaParseResult result) {
        if (result == null) return;

        // 检查是否有id属性
        ReadAction.run(() -> {

            Map<String, PsiClass> classes = result.getClasses();
            classes.forEach((key, psiClass) -> {
                JavaElementInfo javaElementInfo = XmlTagLocator.createJavaElementInfo(psiClass, key, JavaService.TYPE_CLASS);
                if (javaElementInfo != null) {
                    cacheConfig.addJavaElementMapping(key, javaElementInfo);
                }
            });

            Map<String, PsiClass> interfaces = result.getInterfaces();
            interfaces.forEach((key, psiClass) -> {
                JavaElementInfo javaElementInfo = XmlTagLocator.createJavaElementInfo(psiClass, key, JavaService.TYPE_INTERFACE_CLASS);
                if (javaElementInfo != null) {
                    cacheConfig.addJavaElementMapping(key, javaElementInfo);
                }
            });
//            Map<String, List<PsiMethod>> allClassMethods = result.getAllClassMethods();
//            allClassMethods.forEach((key, list) -> {
//                list.forEach(classMethod -> {
//                    JavaElementInfo javaElementInfo = XmlTagLocator.createJavaElementInfo(classMethod, key, JavaService.TYPE_METHOD);
//                    if (javaElementInfo != null) {
//                        cacheConfig.addJavaElementMapping(key, javaElementInfo);
//                    }
//                });
//            });
            Map<String, List<PsiMethod>> allInterfaceMethods = result.getAllInterfaceMethods();
            allInterfaceMethods.forEach((key, list) -> {
                list.forEach(psiMethod -> {
                    JavaElementInfo javaElementInfo = XmlTagLocator.createJavaElementInfo(psiMethod, key, JavaService.TYPE_INTERFACE_METHOD);
                    if (javaElementInfo != null) {
                        cacheConfig.addJavaElementMapping(key, javaElementInfo);
                    }
                });
            });
            Map<String, List<PsiField>> methodCall = result.getStaticStringField();
            methodCall.forEach((key, list) -> {
                list.forEach(psiField -> {
                    JavaElementInfo javaElementInfo = XmlTagLocator.createJavaElementInfo(psiField, key, JavaService.TYPE_METHOD_CALL);
                    if (javaElementInfo != null) {
                        cacheConfig.addJavaElementMapping(key, javaElementInfo);
                    }
                });
            });
        });
    }

    /**
     * 缓存统计默认实现
     */
    private static class DefaultCacheStatistics implements CacheStatistics {
        private final Object lock = new Object();
        private long hitCount;
        private long missCount;
        private long invalidationCount;

        @Override
        public long getHitCount() {
            synchronized (lock) {
                return hitCount;
            }
        }

        @Override
        public long getMissCount() {
            synchronized (lock) {
                return missCount;
            }
        }

        @Override
        public long getInvalidationCount() {
            synchronized (lock) {
                return invalidationCount;
            }
        }

        @Override
        public double getHitRate() {
            synchronized (lock) {
                long total = hitCount + missCount;
                return total == 0 ? 0 : (double) hitCount / total;
            }
        }

        @Override
        public void recordHit() {
            synchronized (lock) {
                hitCount++;
            }
        }

        @Override
        public void recordMiss() {
            synchronized (lock) {
                missCount++;
            }
        }

        @Override
        public void recordInvalidation() {
            synchronized (lock) {
                invalidationCount++;
            }
        }

        @Override
        public void reset() {
            synchronized (lock) {
                hitCount = 0;
                missCount = 0;
                invalidationCount = 0;
            }
        }
    }


}