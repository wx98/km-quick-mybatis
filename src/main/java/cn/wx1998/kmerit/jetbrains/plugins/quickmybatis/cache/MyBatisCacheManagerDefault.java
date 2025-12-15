package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache;

import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.info.JavaElementInfo;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.info.XmlElementInfo;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.parser.JavaParser;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.parser.JavaParserFactory;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.parser.MyBatisXmlParser;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.parser.MyBatisXmlParserFactory;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.services.JavaService;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.services.XmlService;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util.NotificationUtil;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util.ProjectFileUtils;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util.TagLocator;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util.TargetMethodsHolder;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util.TimeStrFormatter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReference;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 缓存管理器默认实现，基于 MyBatisCacheDefault 管理缓存生命周期
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
    // 防止重复扫描的锁
    private final transient Object scanLock = new Object();
    // 缓存版本号，用于增量更新
    private final AtomicLong cacheVersion = new AtomicLong(1);
    // 全局缓存核心配置
    private MyBatisCache myBatisCache;
    // 定时扫描间隔（5分钟，单位：毫秒）
    private long scanIntervalMs = 5 * 60 * 1000;

    /**
     * 通知标记
     */
    public static boolean notifyFlag = true;

    /**
     * 私有构造器（单例模式）
     */
    MyBatisCacheManagerDefault(@NotNull Project project) {
        this.project = project;
        this.myBatisCache = MyBatisCacheFactory.getRecommendedParser(project);
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

                    // 索引没有就绪，直接跳过
                    if (DumbService.getInstance(project).isDumb()) {
                        return;
                    }

                    // 4. 处理有效文件的变更
                    if (event instanceof VFileContentChangeEvent) {
                        LOG.debug(CACHE_LOG_PREFIX + "文件变化触发缓存失效: " + file.getPath());
                        ReadAction.run(() -> refreshInvalidatedCaches(file.getPath()));
                    }
                    if (event instanceof VFileDeleteEvent) {
                        LOG.debug(CACHE_LOG_PREFIX + "文件删除触发缓存失效: " + file.getPath());
                        ReadAction.run(() -> refreshInvalidatedCaches(file.getPath()));
                    }
                }
            }
        });
    }

    /**
     * 启动定时扫描任务，检查文件是否变更（基于文件摘要）
     */
    private void startPeriodicScan() {
        // 定时全面扫描
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
        int newCount = 0;
        //得到所有文件
        List<String> filePathList = ProjectFileUtils.getFilePathListByTypeInSourceRoots(project, "xml", "java");
        // 遍历所有缓存的文件摘要
        if (myBatisCache.getAllFileDigest() != null) {
            for (Map.Entry<String, String> entry : myBatisCache.getAllFileDigest().entrySet()) {
                String filePath = entry.getKey();
                String oldDigest = entry.getValue();
                // 从所有文件中删除
                filePathList.remove(filePath);

                VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
                if (file == null || !file.exists()) {
                    // 文件已删除，清除缓存
                    clearFileCache(filePath);
                    changedCount++;
                    continue;
                }

                // 计算当前文件摘要
                String newDigest = ProjectFileUtils.calculateFileDigest(filePath);
                if (!newDigest.equals(oldDigest)) {
                    // 摘要不一致，文件已修改
                    LOG.info(CACHE_LOG_PREFIX + "文件内容变更: " + filePath + "（旧摘要: " + oldDigest + ", 新摘要: " + newDigest + "）");
                    clearFileCache(filePath);
                    myBatisCache.saveFileDigest(file, newDigest); // 更新摘要
                    reparseAndCacheFile(file); // 重新解析
                    changedCount++;
                }
            }
        }
        // 剩下的就是新增文件
        for (String filePath : filePathList) {
            LOG.debug(CACHE_LOG_PREFIX + "发现 " + filePathList.size() + " 个新增文件");
            clearFileCache(filePath);
            String newDigest = ProjectFileUtils.calculateFileDigest(filePath);
            VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
            if (file != null) {
                newCount++;
                myBatisCache.saveFileDigest(file, newDigest); // 更新摘要
                reparseAndCacheFile(file); // 重新解析
            }
        }

        LOG.info(CACHE_LOG_PREFIX + "定时扫描完成，发现 " + changedCount + " 个变更文件，" + newCount + " 个新增文件");
    }

    /**
     * 刷新单个文件的缓存
     */
    private void reparseAndCacheFile(@NotNull VirtualFile file) {
        ReadAction.run(() -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            Project project = null;
            if (psiFile != null) {
                project = psiFile.getProject();
            }
            if (project != null && DumbService.getInstance(project).isDumb()) {
                DumbService.getInstance(project).runWhenSmart(() -> {
                    ApplicationManager.getApplication().executeOnPooledThread(() -> {
                        doReparseAndCache(file);
                    });
                });
            }
        });

        // 索引就绪时直接执行
        doReparseAndCache(file);
    }

    private void doReparseAndCache(VirtualFile file) {
        try {
            ReadAction.run(() -> {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                if (psiFile == null) return;
                if (psiFile instanceof XmlFile xmlFile) {
                    // 重新解析XML文件
                    MyBatisXmlParser parser = MyBatisXmlParserFactory.getRecommendedParser(project);
                    MyBatisXmlParser.MyBatisParseResult parse = parser.parse(xmlFile);
                    List<XmlElementInfo> xmlElementInfos = syncToCacheManager(parse);
                    myBatisCache.addXmlElementMapping(xmlElementInfos);
                } else if (psiFile instanceof PsiJavaFile psiJavaFile) {
                    // 重新解析Java文件
                    JavaParser parser = JavaParserFactory.getRecommendedParser(project);
                    JavaParser.JavaParseResult parse = parser.parseEverything(psiJavaFile);
                    List<JavaElementInfo> javaElementInfos = syncToCacheManager(parse);
                    myBatisCache.addJavaElementMapping(javaElementInfos);
                }
            });
        } catch (IndexNotReadyException e) {
            // 兜底：即使漏检，也捕获异常避免崩溃
            LOG.warn("索引未就绪，跳过文件解析: " + file.getName(), e);
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
        // 从  myBatisCache 中查询Java类关联的所有XML文件路径
        Set<String> sqlIds = myBatisCache.getSqlIdsByJavaFile(className);
        return sqlIds.stream().flatMap(sqlId -> myBatisCache.getXmlElementsBySqlId(sqlId).stream()).map(XmlElementInfo::getFilePath).collect(Collectors.toSet());
    }

    @Override
    @Nullable
    public String getClassForXmlFile(@NotNull String xmlFilePath) {
        // 从  myBatisCache 中查询XML文件关联的Java类
        Set<String> sqlIds = myBatisCache.getSqlIdsByXmlFile(xmlFilePath);
        if (sqlIds.isEmpty()) return null;

        // 取第一个SQL ID关联的Java类（实际场景可能需要更复杂的逻辑）
        String firstSqlId = sqlIds.iterator().next();
        Set<JavaElementInfo> javaElements = myBatisCache.getJavaElementsBySqlId(firstSqlId);
        if (javaElements.isEmpty()) return null;

        return javaElements.iterator().next().getFilePath(); // 假设Java文件路径对应类名
    }

    @Override
    @Nullable
    public String getStatementIdForMethod(@NotNull String className, @NotNull String methodName) {
        // 生成SQL ID（与 JavaService 的计算逻辑一致）
        String sqlId = className + "." + methodName; // 需与配置的命名规则同步
        // 检查缓存中是否存在该SQL ID
        Set<XmlElementInfo> xmlElements = myBatisCache.getXmlElementsBySqlId(sqlId);
        return xmlElements.isEmpty() ? null : sqlId;
    }

    @Override
    @Nullable
    public String getMethodForStatementId(@NotNull String xmlFilePath, @NotNull String statementId) {
        // 从SQL ID反向查询Java方法
        Set<JavaElementInfo> javaElements = myBatisCache.getJavaElementsBySqlId(statementId);
        if (javaElements.isEmpty()) return null;

        // 过滤出当前XML文件关联的Java方法（简化逻辑）
        return javaElements.stream().filter(info -> "method".equals(info.getElementType())).map(JavaElementInfo::getSqlId).map(id -> id.substring(id.lastIndexOf(".") + 1)) // 从SQL ID中提取方法名
                .findFirst().orElse(null);
    }

    // ========================= 缓存失效与清理 =========================

    @Override
    public void clearClassCache(@NotNull String className) {
        // 清除类关联的所有缓存
        myBatisCache.clearJavaFileCache(className);
        LOG.debug(CACHE_LOG_PREFIX + "清除类缓存: " + className);
    }

    @Override
    public void clearXmlFileCache(@NotNull String xmlFilePath) {
        // 清除XML文件关联的所有缓存
        myBatisCache.clearXmlFileCache(xmlFilePath);
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
        myBatisCache.clearAllCache();
        LOG.debug(CACHE_LOG_PREFIX + "清除所有缓存");
    }

    @Override
    public void refreshInvalidatedCaches(String filePath) {
        try {
            LOG.debug(CACHE_LOG_PREFIX + "开始刷新无效缓存");

            // 从缓存获取当前文件涉及的所有sqlId
            Set<String> allSqlIdList = myBatisCache.getAllSqlIdByFilePath(filePath);

            // 拿到sql涉及的所有文件
            Set<String> fileList = myBatisCache.getAllFilePathsBySqlIdList(allSqlIdList);

            // 删除涉及的所有旧缓存
            myBatisCache.removeBySqlIdList(allSqlIdList);

            // 刷新一遍涉及的所有文件
            for (String file : fileList) {
                // 重新解析文件并更新缓存
                VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(file);
                if (virtualFile != null) {
                    reparseAndCacheFile(virtualFile);
                }
            }

            LOG.debug(CACHE_LOG_PREFIX + "刷新无效缓存完成");
        } catch (IndexNotReadyException e) {
            // 记录日志，跳过本次刷新
            LOG.warn("索引未就绪，跳过缓存刷新", e);
            // 延迟到索引就绪后重试（可选）
            DumbService.getInstance(project).runWhenSmart(new Runnable() {
                @Override
                public void run() {
                    refreshInvalidatedCaches(filePath);
                }
            });
        }
    }

    // ========================= 配置与统计 =========================

    @Override
    @NotNull
    public MyBatisCache getCacheConfig() {
        return myBatisCache;
    }

    @Override
    public void setCacheConfig(@NotNull MyBatisCache myBatisCache) {
        this.myBatisCache = myBatisCache;
        LOG.debug(CACHE_LOG_PREFIX + "更新缓存配置");
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
    @Override
    public void performFullCacheRefresh(int numberOfRefreshes) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "刷新插件MyBatis缓存") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                long start = System.currentTimeMillis();

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

                notifyFlag = true;

                long end = System.currentTimeMillis();
                long ms = (end - start);

                // 通知用户缓存刷好了
                String context0 = "ヾ(ｏ･ω･)ﾉ 太好了，缓存刷新完毕，一共花了" + TimeStrFormatter.format(ms) + " <br/>你也可以按 ctrl + alt + r 再次刷新";
                String context1 = "⊙(・◇・)？缓存又刷新好了，这次花了" + TimeStrFormatter.format(ms) + "<br/>你也可以按 ctrl + alt + r 再次刷新";
                String context = numberOfRefreshes > 0 ? context1 : context0;
                String leftBtnText = "干得好!";
                String rightBtnText = "再刷一下?";
                NotificationUtil.showCustomNotification(project, this.getClass().getName(), "km-quick-mybatis", context, leftBtnText, (pro, not) -> {
                    //左侧按钮什么都不敢
                }, rightBtnText, (pro, not) -> {
                    // 右侧按钮调用刷新按钮，并且刷新次数+1
                    int count = (numberOfRefreshes + 1);
                    performFullCacheRefresh(count);
                }, false);
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
        int size = myBatisXmlFiles.size();
        double step = proportion / (size + 1);
        // 遍历所有 MyBatisXml
        List<XmlElementInfo> xmlElementInfos = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            XmlFile xmlFile = myBatisXmlFiles.get(i);
            // 获取当前文件的绝对路径
            String filePath = xmlFile.getVirtualFile().getPath();
            // 获取项目的绝对路径
            // 从文件绝对路径中删除项目绝对路径展示给用户
            String showText = xmlFile.getVirtualFile().getName();
            // 设置进度条上方显示的进度文本
            indicator.setText(showText + ":解析XML文件:(" + i + "/" + size + ")");
            // 设置进度条下显示的进度详细信息文本
            indicator.setText2("(" + size + "/" + i + "):" + filePath);
            // 调用Xml解析器拿到结果
            MyBatisXmlParser.MyBatisParseResult parse = parser.parse(xmlFile);
            //更新缓存
            LOG.debug("MyBatis XML文件解析完成: " + filePath);
            List<XmlElementInfo> tmp = syncToCacheManager(parse);
            xmlElementInfos.addAll(tmp);
            LOG.debug("MyBatis XML同步到缓存完成: " + filePath);
            // 更新进度
            progress[0] += step;
            // 设置进度
            indicator.setFraction(progress[0]);
        }
        indicator.setText("正在保存" + xmlElementInfos.size() + "个 Xml缓存...");
        myBatisCache.addXmlElementMapping(xmlElementInfos);
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
        int size = javaFiles.size();
        double step = proportion / (size + 1);
        // 获取所有映射的Java文件路径
        List<JavaElementInfo> javaElementInfosAll = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            PsiJavaFile psiJavaFile = javaFiles.get(i);
            // 获取当前文件的绝对路径
            String filePath = psiJavaFile.getVirtualFile().getPath();
            // 从文件绝对路径中删除项目绝对路径展示给用户
            String showText = psiJavaFile.getVirtualFile().getName();
            // 设置进度条上方显示的进度文本
            indicator.setText(showText + ":解析Java文件:(" + i + "/" + size + ")");
            // 设置进度条下显示的进度详细信息文本
            indicator.setText2("(" + size + "/" + i + "):" + filePath);
            // 调用Xml解析器拿到结果
            JavaParser.JavaParseResult parse = parser.parse(psiJavaFile);
            //更新缓存
            LOG.debug("Java 文件解析完成: " + filePath);
            List<JavaElementInfo> tmp = syncToCacheManager(parse);
            javaElementInfosAll.addAll(tmp);
            LOG.debug("Java 同步到缓存完成: " + filePath);
            // 更新进度
            progress[0] += step;
            // 设置进度
            indicator.setFraction(progress[0]);
        }
        indicator.setText("正在保存" + javaElementInfosAll.size() + "个 Java缓存...");
        myBatisCache.addJavaElementMapping(javaElementInfosAll);
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
        ReadAction.run(() -> {
            List<JavaElementInfo> javaElementInfos = doActualSearch(indicator, proportion, progress);
            myBatisCache.addJavaElementMapping(javaElementInfos);
        });
    }

    private List<JavaElementInfo> doActualSearch(ProgressIndicator indicator, double proportion, double[] progress) {
        indicator.setText("正在搜索MyBatis方法调用...");
        indicator.setText2("准备搜索...");

        TargetMethodsHolder targetHolder = new TargetMethodsHolder(project);
        Set<PsiMethod> targetMethods = targetHolder.getTargetMethods();

        if (targetMethods.isEmpty()) {
            indicator.setText("未找到任何目标方法，跳过搜索。");
            progress[0] += proportion;
            indicator.setFraction(progress[0]);
            return Collections.emptyList();
        }

        int size = targetMethods.size();

        // 计算每个方法的进度步长
        double step = proportion / size;

        List<JavaElementInfo> javaElementInfoList = new ArrayList<>();

        // 这里假设该方法已经在一个后台任务中被调用
        int index = 0;
        for (PsiMethod targetMethod : targetMethods) {
            index++;
            // 更新进度信息
            String showText = "(" + size + "/" + index + ")" + Objects.requireNonNull(targetMethod.getContainingClass()).getQualifiedName() + "." + targetMethod.getName();
            indicator.setText2(String.format("正在搜索方法: %s", showText));

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

                            // 检查是否有id属性
                            ReadAction.run(() -> {
                                PsiElement originalElement = element.getOriginalElement();
                                JavaElementInfo javaElementInfo = TagLocator.createJavaElementInfo(originalElement, sqlId, JavaService.TYPE_METHOD_CALL);
                                javaElementInfoList.add(javaElementInfo);
                            });

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
        return javaElementInfoList;
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
//                MyBatisCachePersistenceManager.manualSaveCache(project);
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
    @Override
    public long getCurrentCacheVersion() {
        return cacheVersion.get();
    }

    /**
     * 检查缓存是否需要更新（与其他模块同步）
     */
    @Override
    public boolean isCacheUpToDate(long lastKnownVersion) {
        return cacheVersion.get() == lastKnownVersion;
    }

    /**
     * 同步解析结果到缓存管理器
     */
    private List<XmlElementInfo> syncToCacheManager(MyBatisXmlParser.MyBatisParseResult result) {
        if (result == null) return Collections.emptyList();

        // 检查是否有id属性
        return ReadAction.compute(() -> {

            String namespaceName = result.getNamespaceName();
            XmlTag rootTag = result.getNamespace();

            List<XmlElementInfo> xmlElementInfoList = new ArrayList<>();
            XmlElementInfo xmlElementInfoRootTag = TagLocator.createXmlElementInfo(rootTag, namespaceName, "", rootTag.getName());
            if (xmlElementInfoRootTag != null) {
                xmlElementInfoList.add(xmlElementInfoRootTag);
            }

            Map<String, List<XmlTag>> statements = result.getStatements();
            Set<String> keySet = statements.keySet();
            for (String attributeName : keySet) {
                List<XmlTag> list = statements.get(attributeName);
                for (XmlTag tag : list) {
                    String sqlId = namespaceName + '.' + attributeName;
                    String databaseId = tag.getAttributeValue("databaseId");
                    databaseId = databaseId != null ? databaseId : "";
                    XmlElementInfo xmlElementInfo = TagLocator.createXmlElementInfo(tag, sqlId, databaseId, tag.getName());
                    if (xmlElementInfo != null) {
                        xmlElementInfoList.add(xmlElementInfo);
                    }
                }
            }

            return xmlElementInfoList;
        });
    }

    /**
     * 同步解析结果到缓存管理器
     */
    private List<JavaElementInfo> syncToCacheManager(JavaParser.JavaParseResult result) {
        if (result == null) return Collections.emptyList();

        // 检查是否有id属性
        return ReadAction.compute(() -> {

            List<JavaElementInfo> javaElementInfoList = new ArrayList<>();

            Map<String, PsiClass> classes = result.getClasses();
            classes.forEach((key, psiClass) -> {
                JavaElementInfo javaElementInfo = TagLocator.createJavaElementInfo(psiClass, key, JavaService.TYPE_CLASS);
                if (javaElementInfo != null) {
                    javaElementInfoList.add(javaElementInfo);
                }
            });

            Map<String, PsiClass> interfaces = result.getInterfaces();
            interfaces.forEach((key, psiClass) -> {
                JavaElementInfo javaElementInfo = TagLocator.createJavaElementInfo(psiClass, key, JavaService.TYPE_INTERFACE_CLASS);
                if (javaElementInfo != null) {
                    javaElementInfoList.add(javaElementInfo);
                }
            });
            Map<String, List<PsiMethod>> allClassMethods = result.getAllClassMethods();
            allClassMethods.forEach((key, list) -> {
                list.forEach(classMethod -> {
                    JavaElementInfo javaElementInfo = TagLocator.createJavaElementInfo(classMethod, key, JavaService.TYPE_METHOD);
                    if (javaElementInfo != null) {
                        javaElementInfoList.add(javaElementInfo);
                    }
                });
            });
            Map<String, List<PsiMethod>> allInterfaceMethods = result.getAllInterfaceMethods();
            allInterfaceMethods.forEach((key, list) -> {
                list.forEach(psiMethod -> {
                    JavaElementInfo javaElementInfo = TagLocator.createJavaElementInfo(psiMethod, key, JavaService.TYPE_INTERFACE_METHOD);
                    if (javaElementInfo != null) {
                        javaElementInfoList.add(javaElementInfo);
                    }
                });
            });
            Map<String, List<PsiMethodCallExpression>> classMethodCall = result.getClassMethodCall();
            classMethodCall.forEach((key, list) -> {
                list.forEach(expression -> {
                    JavaElementInfo javaElementInfo = TagLocator.createJavaElementInfo(expression, key, JavaService.TYPE_METHOD_CALL);
                    if (javaElementInfo != null) {
                        javaElementInfoList.add(javaElementInfo);
                    }
                });
            });
            Map<String, List<PsiField>> staticStringField = result.getStaticStringField();
            staticStringField.forEach((key, list) -> {
                list.forEach(psiField -> {
                    JavaElementInfo javaElementInfo = TagLocator.createJavaElementInfo(psiField, key, JavaService.TYPE_FIELD);
                    if (javaElementInfo != null) {
                        javaElementInfoList.add(javaElementInfo);
                    }
                });
            });
            return javaElementInfoList;
        });
    }

    @Override
    public boolean checkForCacheInvalidationAndNotify(Project project) {
        int countElementJavaTable = myBatisCache.countElementJavaTable();
        int countElementXmlTable = myBatisCache.countElementXmlTable();
        int countFileDigestTable = myBatisCache.countFileDigestTable();

        int countElementJavaTableByMethodCall = myBatisCache.countElementJavaTableByMethodCall();

        // java 为空 xml 不为空
        boolean flag1 = countElementJavaTable == 0 && countElementXmlTable != 0;
        // java 不为空 xml 为空
        boolean flag2 = countElementJavaTable != 0 && countElementXmlTable == 0;
        // java 和 xml 都为空
        boolean flag3 = countElementJavaTable == 0 && countElementXmlTable == 0;
        // java 和 xml 任意一个不为空 但是 FileDigest 为空
        boolean flag4 = (countElementJavaTable != 0 || countElementXmlTable != 0) && countFileDigestTable == 0;
        // 没有 methodCall 类型的数据
        boolean flag5 = countElementJavaTableByMethodCall == 0;

        String showText = "检测到缓存可能失效,原因是:";
        String notificationKey = this.getClass().getSimpleName();
        if (flag1) {
            showText += "【Xml缓存不为空但是Java为空】";
            notificationKey += "-flag1";
        } else if (flag2) {
            showText += "【Java缓存不为空但是Xml为空】";
            notificationKey += "-flag2";
        } else if (flag3) {
            showText += "【Java缓存和Xml缓存都为空】";
            notificationKey += "-flag3";
        } else if (flag4) {
            showText += "【Java和Xml缓存都不为空但是文件摘要缓存为空】";
            notificationKey += "-flag4";
        } else if (flag5) {
            showText += "【没有Java方法调用类型的缓存】";
            notificationKey += "-flag5";
        }

        if ((flag1 || flag2 || flag3 || flag4 || flag5) && notifyFlag) {
            notifyFlag = false;
            NotificationUtil.showCacheRefreshNotification(project, notificationKey, "km-quick-mybatis提示", showText, "刷新缓存", "不再建议");
            return true;
        } else {
            return false;
        }
    }
}