package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.db;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.JavaElementInfo;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.XmlElementInfo;
import com.intellij.openapi.project.Project;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 可以在此处做一些对缓存的操作
 */
public class CacheDao extends BaseDao {

    private final JavaElementDao javaElementDao;
    private final XmlElementDao xmlElementDao;
    private final FileDigestDao fileDigestDao;

    public CacheDao(@NotNull Project project) {
        super(project);
        javaElementDao = new JavaElementDao(project);
        xmlElementDao = new XmlElementDao(project);
        fileDigestDao = new FileDigestDao(project);
    }

    public JavaElementDao getJavaElementDao() {
        return javaElementDao;
    }

    public XmlElementDao getXmlElementDao() {
        return xmlElementDao;
    }

    public FileDigestDao getFileDigestDao() {
        return fileDigestDao;
    }

    public int batchInsertJavaElementInfo(List<JavaElementInfo> elements) {
        return javaElementDao.batchInsert(elements);
    }

    public int batchInsertXmlElementInfo(List<XmlElementInfo> elements) {
        return xmlElementDao.batchInsert(elements);
    }

    public int batchInsertFileDigest(Map<String, String> elements) {
        return fileDigestDao.batchInsert(elements);
    }

    public List<XmlElementInfo> getXmlElementsBySqlId(String sqlId) {
        return xmlElementDao.getBySqlId(sqlId);
    }

    public List<JavaElementInfo> getJavaElementsBySqlId(String sqlId) {
        return javaElementDao.getBySqlId(sqlId);
    }


    public Set<String> getSqlIdsByJavaFile(String javaFilePath) {
        return javaElementDao.getSqlIdsByFile(javaFilePath);
    }

    public Set<String> getSqlIdsByXmlFile(String xmlFilePath) {
        return javaElementDao.getSqlIdsByFile(xmlFilePath);
    }

    public int saveFileDigest(String path, String digest) {
        return fileDigestDao.batchInsert(Collections.singletonMap(path, digest));
    }

    public String getDigestByFilePath(@NonNls @NotNull String path) {
        return fileDigestDao.getSqlIdsByFile(path);
    }

    public Map<String, String> getAllFileDigest() {
        return fileDigestDao.getAllFileDigest();
    }

    public int clearJavaFileCache(String javaFilePath) {
        return javaElementDao.deleteByFilePath(javaFilePath);
    }

    public int clearXmlFileCache(String xmlFilePath) {
        return xmlElementDao.deleteByFilePath(xmlFilePath);
    }

    public int clearAll() {
        // 清空Java元素缓存
        int i1 = javaElementDao.clearAll();
        // 清空XML元素缓存
        int i2 = xmlElementDao.clearAll();
        // 清空文件摘要缓存
        int i3 = fileDigestDao.clearAll();
        return i1 + i2 + i3;
    }


    public Set<JavaElementInfo> getAllJavaElementInfo() {
        return javaElementDao.getAll();
    }

    public Set<XmlElementInfo> getAllXmlElementInfo() {
        return xmlElementDao.getAll();
    }

    /**
     * 获取文件涉及的所有 SqlId（联合element_java + element_xml）
     *
     * @param filePath 文件路径
     * @return 该文件关联的所有SQL ID集合
     */
    public Set<String> getAllSqlIdByFilePath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("文件路径不能为空");
        }
        String sql = "select sql_id from (select sql_id from element_java where file_path = ? union select sql_id from element_xml where file_path = ?) as t";
        try (Connection conn = getConnection()) {
            List<String> sqlIdList = queryRunner.query(conn, sql, new ColumnListHandler<>("sql_id"), filePath, filePath);
            return new HashSet<>(sqlIdList);
        } catch (SQLException e) {
            throw new RuntimeException("查询文件[" + filePath + "]关联的所有SQL ID失败", e);
        }
    }


    /**
     * 获取 获取 sqlId列表涉及的所有文件路径
     *
     * @param sqlIdList sqlId列表
     * @return sqlId列表涉及的所有文件路径
     */
    public Set<String> getAllFilePathsBySqlIdList(Set<String> sqlIdList) {
        if (sqlIdList == null || sqlIdList.isEmpty()) {
            return Collections.emptySet();
        }
        String placeholders = String.join(",", Collections.nCopies(sqlIdList.size(), "?"));
        // UNION去重查询：合并Java+XML表中这些SQL ID的所有文件路径
        String sql = "select file_path from (select file_path from public.element_java where sql_id in (%s) union select file_path from public.element_xml where sql_id in (%s))".formatted(placeholders, placeholders);

        try (Connection conn = getConnection()) {
            // 构造参数：IN条件需要传两次（对应两个IN）
            Object[] params = new Object[sqlIdList.size() * 2];
            System.arraycopy(sqlIdList.toArray(), 0, params, 0, sqlIdList.size());
            System.arraycopy(sqlIdList.toArray(), 0, params, sqlIdList.size(), sqlIdList.size());
            List<String> filePathList = queryRunner.query(conn, sql, new ColumnListHandler<>("file_path"), params);
            return new HashSet<>(filePathList);
        } catch (SQLException e) {
            throw new RuntimeException("查询SQL ID列表关联的所有文件路径失败", e);
        }
    }

    /**
     * 删除所有涉及的sqlId
     *
     * @param sqlIdList sqlId列表
     * @return 删除的条目数
     */
    public int removeBySqlIdList(Set<String> sqlIdList) {
        if (sqlIdList == null || sqlIdList.isEmpty()) {
            return 0;
        }

        // 构建IN条件的占位符
        String placeholders = String.join(",", Collections.nCopies(sqlIdList.size(), "?"));
        String javaDeleteSql = "DELETE FROM element_java WHERE sql_id IN (%s)".formatted(placeholders);
        String xmlDeleteSql = "DELETE FROM element_xml WHERE sql_id IN (%s)".formatted(placeholders);

        try (Connection conn = getConnection()) {
            // 批量删除：先删Java表，再删XML表
            Object[] params = sqlIdList.toArray();
            int javaDeleteCount = queryRunner.update(conn, javaDeleteSql, params);
            int xmlDeleteCount = queryRunner.update(conn, xmlDeleteSql, params);

            // 返回累计删除数
            return javaDeleteCount + xmlDeleteCount;
        } catch (SQLException e) {
            throw new RuntimeException("删除SQL ID列表[" + String.join(",", sqlIdList) + "]关联的记录失败", e);
        }
    }
}