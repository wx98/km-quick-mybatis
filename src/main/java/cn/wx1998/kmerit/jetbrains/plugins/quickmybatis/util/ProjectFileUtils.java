package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;


public class ProjectFileUtils {

    private static final Logger LOG = Logger.getInstance(ProjectFileUtils.class);

    /**
     * 获取项目中指定类型的所有文件的路径
     *
     * @param project    project
     * @param extensions 类型列表范围
     * @return 符合类型列表范围的文件列表
     */
    public static List<String> getFilePathListByTypeInSourceRoots(@NotNull Project project, @NotNull String... extensions) {
        return ReadAction.compute(() -> {
            List<PsiFile> mybatisFiles = new ArrayList<>();
            for (VirtualFile contentRoot : ProjectRootManager.getInstance(project).getContentSourceRoots()) {
                if (contentRoot != null && contentRoot.isDirectory() && contentRoot.isValid()) {
                    findXmlFilesRecursively(project, contentRoot, mybatisFiles, extensions);
                }
            }
            List<String> filePathList = new ArrayList<>();
            mybatisFiles.forEach(file -> filePathList.add(file.getVirtualFile().getPath()));
            return filePathList;
        });
    }

    /**
     * 获取项目中指定类型的所有文件
     *
     * @param project    project
     * @param extensions 类型列表范围
     * @return 符合类型列表范围的文件列表
     */
    public static List<PsiFile> getVirtualFileListByTypeInSourceRoots(@NotNull Project project, @NotNull String... extensions) {
        return ReadAction.compute(() -> {
            List<PsiFile> mybatisFiles = new ArrayList<>();
            VirtualFile[] contentSourceRoots = ProjectRootManager.getInstance(project).getContentSourceRoots();
            for (VirtualFile contentRoot : contentSourceRoots) {
                if (contentRoot != null && contentRoot.isDirectory() && contentRoot.isValid()) {
                    findXmlFilesRecursively(project, contentRoot, mybatisFiles, extensions);
                }
            }
            return mybatisFiles;
        });
    }

    private static void findXmlFilesRecursively(@NotNull Project project, VirtualFile directory, List<PsiFile> result, String... extensions) {
        // 递归退出条件
        if (directory == null || !directory.isDirectory() || !directory.isValid() || extensions == null || extensions.length == 0)
            return;

        // 类型范围列表
        Set<String> extensionRange = new HashSet<>(Arrays.asList(extensions));

        VirtualFile[] children = directory.getChildren();
        // 循环当前目录内容
        for (VirtualFile file : children) {
            // 如果是文件夹则递归
            if (file.isDirectory()) {
                findXmlFilesRecursively(project, file, result, extensions);
            } else {
                String extension = file.getExtension() != null ? file.getExtension().toLowerCase() : null;
                // 如果在类型范围内则处理
                if (extensionRange.contains(extension)) {
                    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
                    // 当前文件已经忽略则跳过
                    if (fileIndex.isExcluded(file)) continue;
                    // 不是源码目录文件则跳过
                    if (!fileIndex.isInSourceContent(file)) continue;
                    // 加入结果中
                    result.add(psiFile);
                }
            }
        }
    }

    public static String calculateFileDigest(String filePath) {
        File file = new File(filePath);
        // 校验文件合法性
        if (!file.exists() || !file.isFile()) {
            return "";
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            // 获取消息摘要实例
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192]; // 8KB缓冲区，平衡内存和效率
            int readBytes;
            // 分块读取文件并更新摘要（避免大文件加载到内存）
            while ((readBytes = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, readBytes);
            }

            StringBuilder hexString = new StringBuilder();
            for (byte b : digest.digest()) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // 算法不支持时返回空
            LOG.error("不支持的哈希算法：SHA-256", e);
            return "";
        } catch (IOException e) {
            // 文件读取异常时返回空
            LOG.error("读取文件失败：" + filePath + "，异常：", e);
            return "";
        }
    }

    /**
     * 并行计算文件摘要（线程安全 + 可控并发 + 防重复计算）
     *
     * @param filePaths 路径列表
     * @return 线程安全的文件路径->摘要映射
     */
    public static Map<String, String> calculateFileDigestsParallel(Set<String> filePaths) {
        // 步骤1：健壮性
        if (filePaths.isEmpty()) {
            return Collections.emptyMap();
        }
        // 步骤2：初始化线程安全的缓存（防止重复计算）
        LoadingCache<String, String> digestCache = CacheBuilder.newBuilder().concurrencyLevel(Runtime.getRuntime().availableProcessors()) // 并发级别=CPU核心数
                .maximumSize(filePaths.size()) // 缓存大小=文件数
                .build(new CacheLoader<>() {
                    @Override
                    public String load(String filePath) {
                        // 核心：单个文件摘要计算（带超时/异常处理）
                        return calculateFileDigestWithTimeout(filePath, 5000); // 5秒超时
                    }
                });

        // 步骤3：可控线程池并行计算（避免并行流的线程调度开销）
        ExecutorService executor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), // 核心线程数=CPU核心数
                Runtime.getRuntime().availableProcessors() * 2, // 最大线程数=2*CPU核心数
                60, TimeUnit.SECONDS, // 空闲线程超时
                new ArrayBlockingQueue<>(1000), // 有界队列，防止内存溢出
                new ThreadFactory() {
                    private final AtomicInteger count = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, "digest-calc-" + count.incrementAndGet());
                        thread.setDaemon(true); // 守护线程，不阻塞程序退出
                        thread.setPriority(Thread.NORM_PRIORITY - 1); // 降低优先级，不抢占主线程
                        return thread;
                    }
                }, new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时，由调用线程执行，避免任务丢失
        );

        try {
            // 提交所有文件路径的计算任务
            List<CompletableFuture<Void>> futures = filePaths.stream().map(filePath -> CompletableFuture.runAsync(() -> {
                try {
                    // 从缓存获取（已计算则直接返回，未计算则调用load方法）
                    digestCache.get(filePath);
                } catch (Exception e) {
                    LOG.error("计算文件摘要失败：filePath=" + filePath, e);
                }
            }, executor)).toList();

            // 等待所有任务完成（带总超时）
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);

            // 步骤4：将缓存结果转换为普通Map（线程安全）
            Map<String, String> resultMap = new HashMap<>();
            for (String filePath : filePaths) {
                try {
                    String digest = digestCache.getIfPresent(filePath);
                    if (digest != null && !digest.trim().isEmpty()) {
                        resultMap.put(filePath, digest);
                    }
                } catch (Exception e) {
                    LOG.error("获取缓存的文件摘要失败：filePath=" + filePath, e);
                }
            }
            return resultMap;

        } catch (TimeoutException e) {
            LOG.error("文件摘要计算超时（总超时60秒）", e);
            return Collections.emptyMap();
        } catch (Exception e) {
            LOG.error("文件摘要并行计算异常", e);
            return Collections.emptyMap();
        } finally {
            // 优雅关闭线程池
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }
    }

    /**
     * 带超时的文件摘要计算（防止单个文件卡住）
     *
     * @param filePath  文件路径
     * @param timeoutMs 超时时间（毫秒）
     * @return 文件摘要
     */
    private static String calculateFileDigestWithTimeout(String filePath, long timeoutMs) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> calculateFileDigest(filePath));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            LOG.error("计算文件摘要超时（" + timeoutMs + "ms）：filePath=" + filePath, e);
            return "";
        } catch (Exception e) {
            LOG.error("计算文件摘要失败：filePath=" + filePath, e);
            return "";
        }
    }

}