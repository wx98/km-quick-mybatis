package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache;

import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.db.CacheDao;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.info.JavaElementInfo;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.info.XmlElementInfo;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util.ProjectFileUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MyBatis缓存类默认实现
 */
public class MyBatisCacheDefault implements MyBatisCache {

    private static final Logger LOG = Logger.getInstance(MyBatisCacheDefault.class);
    /**
     * 单例模式（按项目隔离缓存）
     */
    private static final Map<Project, MyBatisCacheDefault> INSTANCES = new ConcurrentHashMap<>();

    /**
     * 缓存操作
     */
    private static CacheDao cacheDao;

    /**
     * 私有构造器
     */
    private MyBatisCacheDefault() {
    }

    public static MyBatisCacheDefault getInstance(@NotNull Project project) {
        cacheDao = new CacheDao(project);
        return INSTANCES.computeIfAbsent(project, k -> new MyBatisCacheDefault());
    }

    /**
     * 添加SQL ID与Java元素的映射
     */
    @Override
    public void addJavaElementMapping(@NotNull List<JavaElementInfo> javaElementInfoList) {
        long time1 = System.currentTimeMillis();
        int count1 = cacheDao.batchInsertJavaElementInfo(javaElementInfoList);
        long time2 = System.currentTimeMillis();
        LOG.debug((time2 - time1) + "ms,addJavaElementMapping.javaElementDao.batchInsert:javaElementInfoList.size=" + javaElementInfoList.size() + ",count1=" + count1);
        Set<String> uniqueFilePaths = javaElementInfoList.stream().map(JavaElementInfo::getFilePath).filter(Objects::nonNull).filter(path -> !path.trim().isEmpty()).collect(Collectors.toSet());
        Map<String, String> fileMap = ProjectFileUtils.calculateFileDigestsParallel(uniqueFilePaths);
        long time3 = System.currentTimeMillis();
        LOG.debug((time3 - time2) + "ms,addJavaElementMapping.digest.calculateFileDigest:fileMap.size=" + fileMap.size());
        int count2 = cacheDao.batchInsertFileDigest(fileMap);
        long time4 = System.currentTimeMillis();
        LOG.debug((time4 - time3) + "ms,addJavaElementMapping.fileDigestDao.batchInsert:fileMap.size=" + fileMap.size() + ",count2=" + count2);
    }

    /**
     * 根据SQL ID获取关联的Java元素
     */
    @NotNull
    @Override
    public Set<JavaElementInfo> getJavaElementsBySqlId(@NotNull String sqlId) {
        List<JavaElementInfo> javaElementInfos = cacheDao.getJavaElementsBySqlId(sqlId);
        return javaElementInfos.isEmpty() ? Collections.emptySet() : new HashSet<>(javaElementInfos);
    }

    // ========================= SQL ID与XML元素映射操作 =========================

    /**
     * 添加SQL ID与XML元素的映射
     */
    @Override
    public void addXmlElementMapping(@NotNull List<XmlElementInfo> xmlElementInfoList) {
        long time1 = System.currentTimeMillis();
        int count1 = cacheDao.batchInsertXmlElementInfo(xmlElementInfoList);
        long time2 = System.currentTimeMillis();
        LOG.debug((time2 - time1) + "ms,addXmlElementMapping.xmlElementDao.batchInsert:xmlElementInfoList.size=" + xmlElementInfoList.size() + ",count1=" + count1);
        Set<String> uniqueFilePaths = xmlElementInfoList.stream().map(XmlElementInfo::getFilePath).filter(Objects::nonNull).filter(path -> !path.trim().isEmpty()).collect(Collectors.toSet());
        Map<String, String> fileMap = ProjectFileUtils.calculateFileDigestsParallel(uniqueFilePaths);
        long time3 = System.currentTimeMillis();
        LOG.debug((time3 - time2) + "ms,addXmlElementMapping.digest.calculateFileDigest:fileMap.size=" + fileMap.size());
        int count2 = cacheDao.batchInsertFileDigest(fileMap);
        long time4 = System.currentTimeMillis();
        LOG.debug((time4 - time3) + "ms,addXmlElementMapping.fileDigestDao.batchInsert:fileMap.size=" + fileMap.size() + ",count2=" + count2);
    }

    /**
     * 根据SQL ID获取关联的XML元素
     */
    @NotNull
    @Override
    public Set<XmlElementInfo> getXmlElementsBySqlId(@NotNull String sqlId) {
        List<XmlElementInfo> xmlElementsBySqlId = cacheDao.getXmlElementsBySqlId(sqlId);
        return xmlElementsBySqlId.isEmpty() ? Collections.emptySet() : new HashSet<>(xmlElementsBySqlId);
    }

    // ========================= 文件与SQL ID的映射操作 =========================

    /**
     * 获取Java文件涉及的所有SQL ID
     */
    @NotNull
    @Override
    public Set<String> getSqlIdsByJavaFile(@NotNull String javaFilePath) {
        return cacheDao.getSqlIdsByJavaFile(javaFilePath);
    }

    /**
     * 获取XML文件包含的所有SQL ID
     */
    @NotNull
    @Override
    public Set<String> getSqlIdsByXmlFile(@NotNull String xmlFilePath) {
        return cacheDao.getSqlIdsByXmlFile(xmlFilePath);
    }

    // ========================= 文件摘要操作（为后续缓存驱逐做准备） =========================

    /**
     * 保存文件摘要
     */
    @Override
    public int saveFileDigest(@NotNull VirtualFile file, @NotNull String digest) {
        String path = file.getPath();
        return cacheDao.saveFileDigest(path, digest);
    }

    /**
     * 获取文件缓存的摘要
     */
    @Nullable
    @Override
    public String getFileDigest(@NotNull VirtualFile file) {
        return cacheDao.getDigestByFilePath(file.getPath());
    }


    /**
     * 获取所有文件的缓存摘要
     */
    @Nullable
    @Override
    public Map<String, String> getAllFileDigest() {
        return cacheDao.getAllFileDigest();
    }

    /**
     * 清除指定Java文件的所有缓存映射
     */
    @Override
    public int clearJavaFileCache(@NotNull String javaFilePath) {
        return cacheDao.clearJavaFileCache(javaFilePath);
    }

    /**
     * 清除指定XML文件的所有缓存映射
     */
    @Override
    public int clearXmlFileCache(@NotNull String xmlFilePath) {
        return cacheDao.clearXmlFileCache(xmlFilePath);
    }

    /**
     * 清除所有缓存（用于全局刷新）
     */
    @Override
    public int clearCache(MyBatisCacheRefreshRange cacheRefreshRange) {
        return switch (cacheRefreshRange) {
            case XML -> cacheDao.clearAllXmlElement();
            case JAVA -> cacheDao.clearAllJavaElement();
            case JAVA_METHOD_CALL -> cacheDao.clearAllJavaMethodCallElement();
            case ALL -> cacheDao.clearAll();
        };
    }

    /**
     * 获取所有SQL ID到Java元素的映射
     */
    @Override
    public Map<String, Set<JavaElementInfo>> getSqlIdToJavaElements() {
        Map<String, Set<JavaElementInfo>> sqlIdToJavaElements = new HashMap<>();
        Set<JavaElementInfo> javaElementInfoSet = cacheDao.getAllJavaElementInfo();
        if (CollectionUtils.isEmpty(javaElementInfoSet)) {
            return Collections.unmodifiableMap(sqlIdToJavaElements);
        }
        for (JavaElementInfo javaElementInfo : javaElementInfoSet) {
            String sqlId = javaElementInfo.getSqlId();
            if (sqlId == null) {
                continue;
            }
            sqlIdToJavaElements.computeIfAbsent(sqlId, k -> new HashSet<>()).add(javaElementInfo);
        }
        return Collections.unmodifiableMap(sqlIdToJavaElements);
    }

    /**
     * 获取所有SQL ID到XML元素的映射（修正笔误）
     */
    @Override
    public Map<String, Set<XmlElementInfo>> getSqlIdToXmlElements() {
        Map<String, Set<XmlElementInfo>> sqlIdToXmlElements = new HashMap<>();
        Set<XmlElementInfo> xmlElementInfoSet = cacheDao.getAllXmlElementInfo();
        if (CollectionUtils.isEmpty(xmlElementInfoSet)) {
            return Collections.unmodifiableMap(sqlIdToXmlElements);
        }
        for (XmlElementInfo xmlElementInfo : xmlElementInfoSet) {
            String sqlId = xmlElementInfo.getSqlId();
            if (sqlId == null) {
                continue;
            }
            sqlIdToXmlElements.computeIfAbsent(sqlId, k -> new HashSet<>()).add(xmlElementInfo);
        }
        return Collections.unmodifiableMap(sqlIdToXmlElements);
    }

    @Override
    public Set<String> getAllSqlIdByFilePath(String filePath) {
        return cacheDao.getAllSqlIdByFilePath(filePath);
    }

    @Override
    public Set<String> getAllFilePathsBySqlIdList(Set<String> stringSet) {
        return cacheDao.getAllFilePathsBySqlIdList(stringSet);
    }

    /**
     * 删除所有涉及的sqlId
     *
     * @param sqlIdList sqlId列表
     * @return 删除的条目
     */
    @Override
    public int removeBySqlIdList(Set<String> sqlIdList) {
        return cacheDao.removeBySqlIdList(sqlIdList);
    }

    @Override
    public int countFileDigestTable() {
        return cacheDao.countFileDigestTable();
    }

    @Override
    public int countElementJavaTable() {
        return cacheDao.countElementJavaTable();
    }

    @Override
    public int countElementXmlTable() {
        return cacheDao.countElementXmlTable();
    }

    @Override
    public int countElementJavaTableByMethodCall() {
        return cacheDao.countElementJavaTableByMethodCall();
    }
}