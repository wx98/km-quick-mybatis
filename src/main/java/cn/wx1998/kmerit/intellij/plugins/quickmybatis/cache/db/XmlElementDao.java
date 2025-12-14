package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.db;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.XmlElementInfo;
import com.intellij.openapi.project.Project;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * element_xml 表的操作
 */
public class XmlElementDao extends BaseDao {

    public XmlElementDao(@NotNull Project project) {
        super(project);
    }

    /**
     * 按需查询：根据 SQL ID 获取 Xml 元素
     */
    public List<XmlElementInfo> getBySqlId(String sqlId) {
        String sql = "SELECT sql_id, file_path, tag_name, database_id, start_offset, end_offset FROM element_xml WHERE sql_id = ?";
        try (Connection conn = getConnection()) {
            return queryRunner.query(conn, sql, new BeanListHandler<>(XmlElementInfo.class, rowProcessor), sqlId);
        } catch (SQLException e) {
            throw new RuntimeException("查询 XML 元素失败", e);
        }
    }

    /**
     * 增量插入：批量添加 Xml 元素（避免全量重写）
     */
    public int batchInsert(List<XmlElementInfo> elements) {
        if (elements.isEmpty()) {
            return 0;
        }
        String sql = "INSERT INTO element_xml (sql_id, file_path, tag_name, database_id, start_offset, end_offset) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE start_offset = VALUES(start_offset), end_offset = VALUES(end_offset) ";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            Object[][] params = elements.stream().map(info -> new Object[]{info.getSqlId(), info.getFilePath(), info.getTagName(), info.getDatabaseId(), info.getStartOffset(), info.getEndOffset()}).toArray(Object[][]::new);
            int[] batch = queryRunner.batch(conn, sql, params);
            conn.commit();
            conn.setAutoCommit(true);
            return batch == null ? 0 : batch.length;
        } catch (SQLException e) {
            throw new RuntimeException("批量插入 XML 元素失败", e);
        }
    }

    /**
     * 更新：根据 SQL ID 更新 XML 元素（SQL ID 作为唯一条件）
     */
    public void updateBySqlId(XmlElementInfo info) {
        if (info.getSqlId() == null || info.getSqlId().trim().isEmpty()) {
            throw new IllegalArgumentException("SQL ID 不能为空，无法更新 XML 元素");
        }
        String sql = "UPDATE element_xml SET file_path = ?, tag_name = ?, database_id = ?, start_offset = ?, end_offset = ? WHERE sql_id = ?";

        try (Connection conn = getConnection()) {
            queryRunner.update(conn, sql, info.getFilePath(), info.getTagName(), info.getDatabaseId(), info.getStartOffset(), info.getEndOffset(), info.getSqlId());
        } catch (SQLException e) {
            throw new RuntimeException("更新 XML 元素失败（SQL ID：" + info.getSqlId() + "）", e);
        }
    }

    /**
     * 批量更新：根据 SQL ID 批量更新 XML 元素
     */
    public void batchUpdateBySqlId(List<XmlElementInfo> elements) {
        if (elements.isEmpty()) {
            return;
        }
        String sql = "UPDATE element_xml SET file_path = ?, tag_name = ?, database_id = ?, start_offset = ?, end_offset = ? WHERE sql_id = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            Object[][] params = elements.stream().peek(info -> {
                if (info.getSqlId() == null || info.getSqlId().trim().isEmpty()) {
                    throw new IllegalArgumentException("存在 SQL ID 为空的元素，无法批量更新");
                }
            }).map(info -> new Object[]{info.getFilePath(), info.getTagName(), info.getDatabaseId(), info.getStartOffset(), info.getEndOffset(), info.getSqlId()}).toArray(Object[][]::new);
            queryRunner.batch(conn, sql, params);
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            throw new RuntimeException("批量更新 XML 元素失败", e);
        }
    }

    /**
     * 删除：根据 SQL ID 删除 XML 元素
     *
     * @return
     */
    public int deleteBySqlId(String sqlId) {
        if (sqlId == null || sqlId.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL ID 不能为空，无法删除 XML 元素");
        }
        String sql = "DELETE FROM element_xml WHERE sql_id = ?";

        try (Connection conn = getConnection()) {
            return queryRunner.update(conn, sql, sqlId);
        } catch (SQLException e) {
            throw new RuntimeException("删除 XML 元素失败（SQL ID：" + sqlId + "）", e);
        }
    }

    /**
     * 批量删除：根据 SQL ID 列表批量删除 XML 元素
     */
    public void batchDeleteBySqlIds(List<String> sqlIds) {
        if (sqlIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", sqlIds.stream().map(s -> "?").toArray(String[]::new));
        String sql = "DELETE FROM element_xml WHERE sql_id IN (" + placeholders + ")";

        try (Connection conn = getConnection()) {
            Object[] params = sqlIds.toArray();
            queryRunner.update(conn, sql, params);
        } catch (SQLException e) {
            throw new RuntimeException("批量删除 XML 元素失败", e);
        }
    }

    /**
     * 根据文件路径获取对应的SQL ID集合
     */
    @NotNull
    public Set<String> getSqlIdsByFile(@NotNull String xmlFilePath) {
        if (xmlFilePath.trim().isEmpty()) {
            throw new IllegalArgumentException("XML文件路径不能为空");
        }
        String sql = "SELECT sql_id FROM element_xml WHERE file_path = ?";
        try (Connection conn = getConnection()) {
            List<String> sqlIdList = queryRunner.query(conn, sql, new ColumnListHandler<>("sql_id"), xmlFilePath);
            return new HashSet<>(sqlIdList);
        } catch (SQLException e) {
            throw new RuntimeException("查询XML文件[" + xmlFilePath + "]关联的SQL ID失败", e);
        }
    }

    /**
     * 根据文件路径删除所有关联的XML元素记录
     */
    public int deleteByFilePath(@NotNull String xmlFilePath) {
        if (xmlFilePath.trim().isEmpty()) {
            throw new IllegalArgumentException("XML文件路径不能为空");
        }
        String sql = "DELETE FROM element_xml WHERE file_path = ?";
        try (Connection conn = getConnection()) {
            return queryRunner.update(conn, sql, xmlFilePath);
        } catch (SQLException e) {
            throw new RuntimeException("删除XML文件[" + xmlFilePath + "]关联的缓存记录失败", e);
        }
    }

    /**
     * 查询所有XML元素记录
     */
    @NotNull
    public Set<XmlElementInfo> getAll() {
        String sql = "SELECT sql_id, file_path, tag_name, database_id, start_offset, end_offset FROM element_xml";
        try (Connection conn = getConnection()) {
            List<XmlElementInfo> allList = queryRunner.query(conn, sql, new BeanListHandler<>(XmlElementInfo.class, rowProcessor));
            return new HashSet<>(allList);
        } catch (SQLException e) {
            throw new RuntimeException("查询所有XML元素记录失败", e);
        }
    }


    /**
     * 清空 element_xml 表所有数据（全局缓存清理）
     */
    public int clearAll() {
        String sql = "TRUNCATE TABLE element_xml";
        try (Connection conn = getConnection()) {
            return queryRunner.update(conn, sql);
        } catch (SQLException e) {
            throw new RuntimeException("清空element_xml表所有数据失败", e);
        }
    }
}