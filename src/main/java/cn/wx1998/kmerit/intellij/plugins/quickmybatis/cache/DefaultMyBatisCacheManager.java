package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MyBatis 缓存管理器的默认实现
 * 实现缓存机制、定期扫描文件以及缓存失效处理等功能
 */

public class DefaultMyBatisCacheManager implements MyBatisCacheManager {
    private static final Logger LOGGER = Logger.getInstance(DefaultMyBatisCacheManager.class);
    private static final String CACHE_LOG_PREFIX = "[MyBatis Cache] ";
    private static final Key<Long> FILE_TIMESTAMP_KEY = Key.create("MYBATIS_FILE_TIMESTAMP");

    private final Project project;
    private final CacheStatistics statistics;
    private final ScheduledExecutorService scheduler;
    // 缓存映射关系
    private final Map<String, Set<String>> classToXmlMap; // className -> xmlFilePaths
    private final Map<String, String> xmlToClassMap;     // xmlFilePath -> className
    private final Map<String, Map<String, String>> methodToStatementMap; // className -> methodName -> statementId
    private final Map<String, Map<String, String>> statementToMethodMap; // xmlFilePath -> statementId -> methodName
    // 缓存有效性跟踪
    private final Map<String, Boolean> cacheValidityMap;
    private final Map<String, Long> fileTimestampMap;
    private final AtomicBoolean isScanning = new AtomicBoolean(false);
    private MyBatisCacheConfig config;

    /**
     * 构造函数
     *
     * @param project 当前项目
     */
    public DefaultMyBatisCacheManager(@NotNull Project project) {
        this.project = project;
        this.config = MyBatisCacheConfig.createDefault();
        this.statistics = new CacheStatistics();

        // 使用ConcurrentHashMap确保线程安全
        this.classToXmlMap = new ConcurrentHashMap<>();
        this.xmlToClassMap = new ConcurrentHashMap<>();
        this.methodToStatementMap = new ConcurrentHashMap<>();
        this.statementToMethodMap = new ConcurrentHashMap<>();
        this.cacheValidityMap = new ConcurrentHashMap<>();
        this.fileTimestampMap = new ConcurrentHashMap<>();

        // 创建定时任务调度器
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = Executors.defaultThreadFactory().newThread(r);
            thread.setName("MyBatis-Cache-Scanner");
            thread.setDaemon(true);
            return thread;
        });

        LOGGER.debug(CACHE_LOG_PREFIX + "初始化缓存管理器，项目: " + project.getName());
        // 初始化并启动定期扫描任务
        initialize();
    }

    /**
     * 初始化缓存管理器
     */
    private void initialize() {
        LOGGER.debug(CACHE_LOG_PREFIX + "开始初始化缓存管理器组件");
        // 注册文件变更监听器
        registerFileChangeListener();

        // 启动定期扫描任务
        startPeriodicScan();

        LOGGER.info(CACHE_LOG_PREFIX + "MyBatis缓存管理器初始化完成，配置: " + config);
    }

    /**
     * 注册文件变更监听器
     */
    private void registerFileChangeListener() {
        LOGGER.debug(CACHE_LOG_PREFIX + "注册文件变更监听器");
        Application application = ApplicationManager.getApplication();
        application.getMessageBus().connect(project).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                for (VFileEvent event : events) {
                    if (event instanceof VFileContentChangeEvent) {
                        VirtualFile file = event.getFile();
                        String filePath = file.getPath();
                        if (isRelevantFile(file)) {
                            LOGGER.debug(CACHE_LOG_PREFIX + "检测到相关文件变更: " + filePath);
                            SwingUtilities.invokeLater(() -> invalidateFileCache(filePath));
                        }
                    }
                }
            }
        });
    }

    /**
     * 启动定期扫描任务
     */
    private void startPeriodicScan() {
        LOGGER.debug(CACHE_LOG_PREFIX + "启动定期扫描任务，间隔: " + config.getScanIntervalSeconds() + "秒");
        scheduler.scheduleAtFixedRate(this::scanForFileChanges, config.getScanIntervalSeconds(), config.getScanIntervalSeconds(), TimeUnit.SECONDS);
    }

    /**
     * 扫描文件变更
     */
    private void scanForFileChanges() {
        if (!isScanning.compareAndSet(false, true)) {
            LOGGER.debug(CACHE_LOG_PREFIX + "定期扫描任务跳过，上次扫描仍在进行中");
            return; // 上一次扫描还在进行中
        }

        try {
            if (project.isDisposed()) {
                LOGGER.debug(CACHE_LOG_PREFIX + "项目已释放，关闭缓存管理器");
                shutdown();
                return;
            }

            LOGGER.debug(CACHE_LOG_PREFIX + "开始定期扫描文件变更");
            int editorCount = 0;
            int checkedFiles = 0;

            // 检查当前打开的文件
            for (FileEditor editor : FileEditorManager.getInstance(project).getAllEditors()) {
                editorCount++;
                VirtualFile file = editor.getFile();
                if (file != null && isRelevantFile(file)) {
                    checkedFiles++;
                    checkAndInvalidateFile(file);
                }
            }

            LOGGER.debug(CACHE_LOG_PREFIX + "扫描完成，检查编辑器数: " + editorCount + ", 相关文件数: " + checkedFiles);

            // 刷新失效的缓存
            refreshInvalidatedCaches();

            // 更新统计信息
            updateStatistics();
            LOGGER.debug(CACHE_LOG_PREFIX + "统计信息更新完成，缓存项总数: " + statistics.getCurrentSize());
        } catch (Exception e) {
            LOGGER.warn(CACHE_LOG_PREFIX + "文件扫描过程中出错: " + e.getMessage(), e);
        } finally {
            isScanning.set(false);
        }
    }

    /**
     * 检查并标记文件缓存失效
     *
     * @param file 要检查的文件
     */
    private void checkAndInvalidateFile(@NotNull VirtualFile file) {
        String filePath = file.getPath();
        long currentTimestamp = file.getTimeStamp();
        Long lastTimestamp = fileTimestampMap.get(filePath);

        if (lastTimestamp == null) {
            LOGGER.debug(CACHE_LOG_PREFIX + "首次检测文件，记录时间戳: " + filePath);
            fileTimestampMap.put(filePath, currentTimestamp);
        } else if (currentTimestamp > lastTimestamp) {
            LOGGER.debug(CACHE_LOG_PREFIX + "文件已修改，时间戳变更: " + filePath + " (" + lastTimestamp + " -> " + currentTimestamp + ")");
            fileTimestampMap.put(filePath, currentTimestamp);
            invalidateFileCache(filePath);
        }
    }

    /**
     * 判断文件是否与MyBatis相关
     *
     * @param file 要判断的文件
     * @return 如果是相关文件则返回true
     */
    private boolean isRelevantFile(@NotNull VirtualFile file) {
        String extension = file.getExtension();
        if (extension == null) {
            return false;
        }

        if ("xml".equalsIgnoreCase(extension)) {
            // MyBatis XML文件
            return true;
        } else if ("java".equalsIgnoreCase(extension)) {
            // Java接口文件（可能是Mapper接口）
            return true;
        }

        return false;
    }

    /**
     * 更新统计信息
     */
    private void updateStatistics() {
        int totalCacheItems = classToXmlMap.size() + xmlToClassMap.size() + methodToStatementMap.values().stream().mapToInt(Map::size).sum() + statementToMethodMap.values().stream().mapToInt(Map::size).sum();

        statistics.updateCurrentSize(totalCacheItems);

        // 简单估算内存使用量（实际项目中可使用更精确的计算方法）
        long estimatedMemoryUsage = totalCacheItems * 256L; // 假设每个缓存项平均占用256字节
        statistics.updateMemoryUsage(estimatedMemoryUsage);
    }

    /**
     * 关闭缓存管理器
     */
    public void shutdown() {
        scheduler.shutdown();
        clearAllCache();
        LOGGER.info("MyBatis cache manager shut down. Final statistics: " + statistics);
    }

    @Override
    public MyBatisCacheManager getInstance(@NotNull Project project) {
        return ServiceManager.getService(project, DefaultMyBatisCacheManager.class);
    }

    @Override
    public Set<String> getXmlFilesForClass(@NotNull String className) {
        Set<String> xmlFiles = classToXmlMap.get(className);
        if (xmlFiles != null) {
            LOGGER.debug(CACHE_LOG_PREFIX + "缓存命中 - 获取类对应的XML文件: " + className + " -> " + xmlFiles.size() + "个文件");
            statistics.recordHit();
            return new HashSet<>(xmlFiles); // 返回副本以防止并发修改
        } else {
            LOGGER.debug(CACHE_LOG_PREFIX + "缓存未命中 - 类无对应的XML文件: " + className);
            statistics.recordMiss();
            return Collections.emptySet();
        }
    }

    @Override
    public String getClassForXmlFile(@NotNull String xmlFilePath) {
        String className = xmlToClassMap.get(xmlFilePath);
        if (className != null) {
            LOGGER.debug(CACHE_LOG_PREFIX + "缓存命中 - 获取XML文件对应的类: " + xmlFilePath + " -> " + className);
            statistics.recordHit();
        } else {
            LOGGER.debug(CACHE_LOG_PREFIX + "缓存未命中 - XML文件无对应的类: " + xmlFilePath);
            statistics.recordMiss();
        }
        return className;
    }

    @Override
    public String getStatementIdForMethod(@NotNull String className, @NotNull String methodName) {
        Map<String, String> methodMap = methodToStatementMap.get(className);
        String statementId = methodMap != null ? methodMap.get(methodName) : null;

        if (statementId != null) {
            LOGGER.debug(CACHE_LOG_PREFIX + "缓存命中 - 获取方法对应的Statement ID: " + className + "." + methodName + " -> " + statementId);
            statistics.recordHit();
        } else {
            LOGGER.debug(CACHE_LOG_PREFIX + "缓存未命中 - 方法无对应的Statement ID: " + className + "." + methodName);
            statistics.recordMiss();
        }
        return statementId;
    }

    @Override
    public String getMethodForStatementId(@NotNull String xmlFilePath, @NotNull String statementId) {
        Map<String, String> statementMap = statementToMethodMap.get(xmlFilePath);
        String methodName = statementMap != null ? statementMap.get(statementId) : null;

        if (methodName != null) {
            LOGGER.debug(CACHE_LOG_PREFIX + "缓存命中 - 获取Statement ID对应的方法: " + xmlFilePath + "#" + statementId + " -> " + methodName);
            statistics.recordHit();
        } else {
            LOGGER.debug(CACHE_LOG_PREFIX + "缓存未命中 - Statement ID无对应的方法: " + xmlFilePath + "#" + statementId);
            statistics.recordMiss();
        }
        return methodName;
    }

    @Override
    public void putClassXmlMapping(@NotNull String className, @NotNull String xmlFilePath) {
        // 检查缓存大小限制
        if (classToXmlMap.size() > config.getMaxCacheSize()) {
            LOGGER.debug(CACHE_LOG_PREFIX + "缓存大小超过限制，触发驱逐策略: " + classToXmlMap.size() + " > " + config.getMaxCacheSize());
            evictOldCache();
        }

        // 存储映射关系
        classToXmlMap.computeIfAbsent(className, k -> {
            LOGGER.debug(CACHE_LOG_PREFIX + "创建新的类-XML映射集合: " + className);
            return new ConcurrentSkipListSet<>();
        }).add(xmlFilePath);
        xmlToClassMap.put(xmlFilePath, className);

        // 标记缓存有效
        cacheValidityMap.put(className, true);
        cacheValidityMap.put(xmlFilePath, true);
        LOGGER.debug(CACHE_LOG_PREFIX + "添加类-XML映射: " + className + " -> " + xmlFilePath);
    }

    @Override
    public void putMethodStatementMapping(@NotNull String className, @NotNull String methodName, @NotNull String statementId) {
        // 存储映射关系
        methodToStatementMap.computeIfAbsent(className, k -> {
            LOGGER.debug(CACHE_LOG_PREFIX + "创建新的方法-Statement映射集合: " + className);
            return new ConcurrentHashMap<>();
        }).put(methodName, statementId);
        LOGGER.debug(CACHE_LOG_PREFIX + "添加方法-Statement映射: " + className + "." + methodName + " -> " + statementId);

        // 获取对应的XML文件路径
        Set<String> xmlFiles = classToXmlMap.get(className);
        if (xmlFiles != null && !xmlFiles.isEmpty()) {
            String xmlFilePath = xmlFiles.iterator().next(); // 假设一个类只对应一个XML文件
            statementToMethodMap.computeIfAbsent(xmlFilePath, k -> {
                LOGGER.debug(CACHE_LOG_PREFIX + "创建新的Statement-方法映射集合: " + xmlFilePath);
                return new ConcurrentHashMap<>();
            }).put(statementId, methodName);
            LOGGER.debug(CACHE_LOG_PREFIX + "添加Statement-方法映射: " + xmlFilePath + "#" + statementId + " -> " + methodName);
        } else {
            LOGGER.debug(CACHE_LOG_PREFIX + "无法添加Statement-方法映射，未找到对应的XML文件: " + className);
        }
    }

    @Override
    public void clearClassCache(@NotNull String className) {
        LOGGER.debug(CACHE_LOG_PREFIX + "清理类缓存: " + className);
        Set<String> xmlFiles = classToXmlMap.remove(className);
        if (xmlFiles != null) {
            for (String xmlFilePath : xmlFiles) {
                LOGGER.debug(CACHE_LOG_PREFIX + "从XML-类映射中移除: " + xmlFilePath);
                xmlToClassMap.remove(xmlFilePath);
                LOGGER.debug(CACHE_LOG_PREFIX + "移除Statement-方法映射集合: " + xmlFilePath);
                statementToMethodMap.remove(xmlFilePath);
                cacheValidityMap.remove(xmlFilePath);
            }
            LOGGER.debug(CACHE_LOG_PREFIX + "清理了" + xmlFiles.size() + "个关联的XML文件缓存");
        }

        LOGGER.debug(CACHE_LOG_PREFIX + "移除方法-Statement映射集合: " + className);
        methodToStatementMap.remove(className);
        cacheValidityMap.remove(className);
        statistics.recordInvalidation();
    }

    @Override
    public void clearXmlFileCache(@NotNull String xmlFilePath) {
        LOGGER.debug(CACHE_LOG_PREFIX + "清理XML文件缓存: " + xmlFilePath);
        String className = xmlToClassMap.remove(xmlFilePath);
        if (className != null) {
            LOGGER.debug(CACHE_LOG_PREFIX + "XML文件关联的类: " + className);
            Set<String> xmlFiles = classToXmlMap.get(className);
            if (xmlFiles != null) {
                xmlFiles.remove(xmlFilePath);
                LOGGER.debug(CACHE_LOG_PREFIX + "从类-XML映射中移除: " + className + " -> " + xmlFilePath);
                if (xmlFiles.isEmpty()) {
                    LOGGER.debug(CACHE_LOG_PREFIX + "类的XML文件集合为空，移除类-XML映射: " + className);
                    classToXmlMap.remove(className);
                    LOGGER.debug(CACHE_LOG_PREFIX + "移除关联的方法-Statement映射集合: " + className);
                    methodToStatementMap.remove(className);
                    cacheValidityMap.remove(className);
                }
            }
        }

        LOGGER.debug(CACHE_LOG_PREFIX + "移除Statement-方法映射集合: " + xmlFilePath);
        statementToMethodMap.remove(xmlFilePath);
        cacheValidityMap.remove(xmlFilePath);
        statistics.recordInvalidation();
    }

    @Override
    public void clearAllCache() {
        LOGGER.debug(CACHE_LOG_PREFIX + "清理所有缓存数据");
        classToXmlMap.clear();
        xmlToClassMap.clear();
        methodToStatementMap.clear();
        statementToMethodMap.clear();
        cacheValidityMap.clear();
        fileTimestampMap.clear();
        statistics.reset();
        LOGGER.info(CACHE_LOG_PREFIX + "所有MyBatis缓存已清空");
    }

    @Override
    public void invalidateFileCache(@NotNull PsiFile file) {
        String filePath = file.getVirtualFile().getPath();
        LOGGER.debug(CACHE_LOG_PREFIX + "使PsiFile缓存失效: " + filePath);
        invalidateFileCache(filePath);
    }

    private void invalidateFileCache(@NotNull String filePath) {
        LOGGER.debug(CACHE_LOG_PREFIX + "标记文件缓存失效: " + filePath);
        cacheValidityMap.put(filePath, false);
        statistics.recordInvalidation();

        // 如果是XML文件，还需要标记对应的Java类缓存失效
        String className = xmlToClassMap.get(filePath);
        if (className != null) {
            LOGGER.debug(CACHE_LOG_PREFIX + "同时标记关联的类缓存失效: " + className);
            cacheValidityMap.put(className, false);
        }
    }

    @Override
    public boolean isCacheValid(@NotNull String filePath) {
        Boolean valid = cacheValidityMap.get(filePath);
        boolean isValid = valid != null && valid;
        LOGGER.debug(CACHE_LOG_PREFIX + "检查缓存有效性: " + filePath + " -> " + (isValid ? "有效" : "无效"));
        return isValid;
    }

    @Override
    public void refreshInvalidatedCaches() {
        // 收集所有失效的文件路径
        List<String> invalidatedPaths = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : cacheValidityMap.entrySet()) {
            if (!entry.getValue()) {
                invalidatedPaths.add(entry.getKey());
                // 限制每次刷新的数量，避免一次处理太多文件
                if (invalidatedPaths.size() >= config.getCleanupBatchSize()) {
                    break;
                }
            }
        }

        LOGGER.debug(CACHE_LOG_PREFIX + "开始刷新失效的缓存，数量: " + invalidatedPaths.size());
        // 处理失效的缓存
        for (String path : invalidatedPaths) {
            // 在实际项目中，这里需要重新解析文件并更新缓存
            // 目前我们只是简单地移除失效标记
            LOGGER.debug(CACHE_LOG_PREFIX + "刷新缓存: " + path);
            cacheValidityMap.put(path, true);
        }
    }

    @Override
    @NotNull
    public MyBatisCacheConfig getCacheConfig() {
        return config;
    }

    @Override
    public void setCacheConfig(@NotNull MyBatisCacheConfig config) {
        LOGGER.info(CACHE_LOG_PREFIX + "更新MyBatis缓存配置: " + config);
        this.config = config;
    }

    @Override
    @NotNull
    public CacheStatistics getCacheStatistics() {
        return statistics;
    }

    /**
     * 驱逐旧缓存以控制缓存大小
     */
    private void evictOldCache() {
        // 简单的LRU驱逐策略（实际项目中可以使用更复杂的驱逐策略）
        int evictCount = Math.min(classToXmlMap.size() / 10, 100); // 驱逐10%或最多100项
        LOGGER.debug(CACHE_LOG_PREFIX + "执行缓存驱逐策略，计划驱逐: " + evictCount + "项");

        int actualEvictCount = 0;
        Iterator<String> iterator = classToXmlMap.keySet().iterator();
        for (int i = 0; i < evictCount && iterator.hasNext(); i++) {
            String className = iterator.next();
            clearClassCache(className);
            statistics.recordEviction();
            actualEvictCount++;
        }

        LOGGER.debug(CACHE_LOG_PREFIX + "缓存驱逐完成，实际驱逐: " + actualEvictCount + "项");
    }

    /**
     * 根据文件路径获取PsiFile对象
     *
     * @param filePath 文件路径
     * @return PsiFile对象，如果文件不存在则返回null
     */
    @Nullable
    private PsiFile getPsiFile(@NotNull String filePath) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath);
        if (virtualFile != null) {
            return PsiManager.getInstance(project).findFile(virtualFile);
        }
        return null;
    }
}