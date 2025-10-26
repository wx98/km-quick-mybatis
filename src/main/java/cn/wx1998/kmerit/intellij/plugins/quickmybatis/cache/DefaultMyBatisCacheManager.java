package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.JavaElementInfo;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.XmlElementInfo;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.persistent.MyBatisCachePersistenceManager;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.parser.MyBatisXmlParser;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.parser.MyBatisXmlParserFactory;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.services.JavaService;
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
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
public class DefaultMyBatisCacheManager implements MyBatisCacheManager {
    // 日志前缀
    private static final String CACHE_LOG_PREFIX = "[MyBatis缓存管理器] ";
    // 单例键（按项目隔离）
    private static final Key<DefaultMyBatisCacheManager> INSTANCE_KEY = Key.create("MyBatisCacheManager.Instance");
    // 日志实例
    private static final Logger LOG = Logger.getInstance(DefaultMyBatisCacheManager.class);
    // 定时扫描线程池
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    // 项目实例
    private final Project project;
    // 全局缓存核心配置（阶段一实现的缓存结构）
    private MyBatisCacheConfig cacheConfig;
    // 缓存有效性标记（true=有效，false=需刷新）
    private final Map<String, Boolean> cacheValidityMap = new ConcurrentHashMap<>();
    // 缓存统计信息
    private final CacheStatistics statistics = new DefaultCacheStatistics();
    // 防止重复扫描的锁
    private final transient Object scanLock = new Object();
    // 定时扫描间隔（5分钟，单位：毫秒）
    private long scanIntervalMs = 5 * 60 * 1000;
    // 缓存版本号，用于增量更新
    private final AtomicLong cacheVersion = new AtomicLong(1);

    /**
     * 私有构造器（单例模式）
     */
    DefaultMyBatisCacheManager(@NotNull Project project) {
        this.project = project;
        this.cacheConfig = MyBatisCacheConfig.getInstance(project);
        this.initialize();
    }

    /**
     * 获取单例实例（按项目隔离）
     */
    public static DefaultMyBatisCacheManager getInstance(@NotNull Project project) {
        DefaultMyBatisCacheManager instance = project.getUserData(INSTANCE_KEY);
        if (instance == null) {
            instance = new DefaultMyBatisCacheManager(project);
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
                    if (event instanceof VFileDeleteEvent || event instanceof VFileContentChangeEvent) {
                        invalidateFileCache(file.getPath());
                        LOG.debug(CACHE_LOG_PREFIX + "文件变化触发缓存失效: " + file.getPath());
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

        if (psiFile instanceof XmlFile) {
            // 重新解析XML文件
            MyBatisXmlParser parser = MyBatisXmlParserFactory.getRecommendedParser(project);
            parser.parse((XmlFile) psiFile);
        } else if (psiFile instanceof PsiJavaFile) {
            // 重新处理Java文件
            JavaService javaService = new JavaService(project);
            PsiClass[] classes = ((PsiJavaFile) psiFile).getClasses();
            for (PsiClass aClass : classes) {
                javaService.processClass(aClass);
            }
        }
    }

    // ========================= 缓存操作核心方法 =========================

    @Override
    public void putClassXmlMapping(@NotNull String className, @NotNull String xmlFilePath) {
        // 委托给 MyBatisCacheConfig 存储（阶段一的双向映射）
        // 此处逻辑已在阶段一的 addJavaElementMapping/addXmlElementMapping 中实现
        // 本方法作为高层封装，供外部调用
        LOG.debug(CACHE_LOG_PREFIX + "建立Class与Xml映射: " + className + " -> " + xmlFilePath);
    }

    @Override
    public void putMethodStatementMapping(@NotNull String className, @NotNull String methodName, @NotNull String statementId) {
        // 方法与SQL ID的映射通过 JavaService 处理后同步到 cacheConfig
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
        List<String> invalidFiles = cacheValidityMap.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey).collect(Collectors.toList());

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
     * 缓存统计默认实现
     */
    private static class DefaultCacheStatistics implements CacheStatistics {
        private long hitCount;
        private long missCount;
        private long invalidationCount;
        private final Object lock = new Object();

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


    /**
     * 执行全局缓存刷新（带进度提示）
     */
    public void performFullCacheRefresh() {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "刷新MyBatis缓存") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("正在清除旧缓存...");
                clearAllCache();

                indicator.setText("正在重新解析所有MyBatis文件...");
                reparseAllMyBatisFiles(indicator);

                indicator.setText("正在更新缓存版本...");
                incrementCacheVersion();

                indicator.setText("缓存刷新完成");
                MyBatisCachePersistenceManager.manualSaveCache(project);
            }
        });
    }

    /**
     * 重新解析所有MyBatis相关文件
     */
    private void reparseAllMyBatisFiles(ProgressIndicator indicator) {
        // 解析所有XML文件
        JavaService javaService = JavaService.getInstance(project);
        List<XmlFile> myBatisXmlFiles = javaService.getMyBatisXmlFiles();
        MyBatisXmlParser parser = MyBatisXmlParserFactory.getRecommendedParser(project);

        indicator.setIndeterminate(false);
        indicator.setFraction(0.0);
        double step = 1.0 / (myBatisXmlFiles.size() + 1);
        double progress = 0.0;

        for (XmlFile xmlFile : myBatisXmlFiles) {
            indicator.setText("解析XML文件: " + xmlFile.getVirtualFile().getPath());
            parser.parse(xmlFile);
            progress += step;
            indicator.setFraction(progress);
        }

        // 解析所有相关Java文件
        indicator.setText("正在处理Java文件...");
        processAllJavaFiles(indicator, progress, step);
    }

    /**
     * 处理所有相关Java文件
     */
    private void processAllJavaFiles(ProgressIndicator indicator, double progress, double step) {
        JavaService javaService = JavaService.getInstance(project);

        // 获取所有映射的Java文件路径
        for (String javaFilePath : getCacheConfig().getJavaFileToSqlIds().keySet()) {
            VirtualFile file = LocalFileSystem.getInstance().findFileByPath(javaFilePath);
            if (file == null || !file.exists()) continue;

            indicator.setText("处理Java文件: " + file.getPath());
            PsiJavaFile psiFile = (PsiJavaFile) PsiManager.getInstance(project).findFile(file);
            if (psiFile != null) {
                for (var cls : psiFile.getClasses()) {
                    javaService.processClass(cls);
                }
            }

            progress += step;
            if (progress > 1.0) progress = 1.0;
            indicator.setFraction(progress);
        }
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


}