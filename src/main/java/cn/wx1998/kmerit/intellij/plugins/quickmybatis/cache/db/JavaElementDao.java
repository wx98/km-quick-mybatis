package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.db;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.JavaElementInfo;
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
 * element_java 表的操作
 */
public class JavaElementDao extends BaseDao {

    public JavaElementDao(@NotNull Project project) {
        super(project);
    }

    /**
     * 按需查询：根据 SQL ID 获取 Java 元素
     */
    public List<JavaElementInfo> getBySqlId(String sqlId) {
        String sql = "SELECT sql_id, file_path, element_type, start_offset, end_offset FROM element_java WHERE sql_id = ?";
        try (Connection conn = getConnection()) {
            return queryRunner.query(conn, sql, new BeanListHandler<>(JavaElementInfo.class, rowProcessor), sqlId);
        } catch (SQLException e) {
            throw new RuntimeException("查询 Java 元素失败", e);
        }
    }

    /**
     * 增量插入：批量添加 Java 元素（避免全量重写）
     */
    public int batchInsert(List<JavaElementInfo> elements) {
        if (elements.isEmpty()) {
            return 0;
        }
        String sql = "INSERT INTO element_java (sql_id, file_path, element_type, start_offset, end_offset) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE  end_offset = VALUES(end_offset) ";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            Object[][] params = elements.stream().map(info -> new Object[]{info.getSqlId(), info.getFilePath(), info.getElementType(), info.getStartOffset(), info.getEndOffset()}).toArray(Object[][]::new);
            int[] batch = queryRunner.batch(conn, sql, params);
            conn.commit();
            conn.setAutoCommit(true);
            return batch == null ? 0 : batch.length;
        } catch (SQLException e) {
            throw new RuntimeException("批量插入 Java 元素失败", e);
        }
    }

    /**
     * 更新：根据 SQL ID 更新 Java 元素（SQL ID 作为唯一条件）
     */
    public void updateBySqlId(JavaElementInfo info) {
        if (info.getSqlId() == null || info.getSqlId().trim().isEmpty()) {
            throw new IllegalArgumentException("SQL ID 不能为空，无法更新 Java 元素");
        }
        String sql = "UPDATE element_java SET file_path = ?, element_type = ?, start_offset = ?, end_offset = ? WHERE sql_id = ?";

        try (Connection conn = getConnection()) {
            queryRunner.update(conn, sql, info.getFilePath(), info.getElementType(), info.getStartOffset(), info.getEndOffset(), info.getSqlId());
        } catch (SQLException e) {
            throw new RuntimeException("更新 Java 元素失败（SQL ID：" + info.getSqlId() + "）", e);
        }
    }

    /**
     * 批量更新：根据 SQL ID 批量更新 Java 元素
     */
    public void batchUpdateBySqlId(List<JavaElementInfo> elements) {
        if (elements.isEmpty()) {
            return;
        }
        String sql = "UPDATE element_java SET file_path = ?, element_type = ?, start_offset = ?, end_offset = ? WHERE sql_id = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            Object[][] params = elements.stream().peek(info -> {
                if (info.getSqlId() == null || info.getSqlId().trim().isEmpty()) {
                    throw new IllegalArgumentException("存在 SQL ID 为空的元素，无法批量更新");
                }
            }).map(info -> new Object[]{info.getFilePath(), info.getElementType(), info.getStartOffset(), info.getEndOffset(), info.getSqlId()}).toArray(Object[][]::new);
            queryRunner.batch(conn, sql, params);
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            throw new RuntimeException("批量更新 Java 元素失败", e);
        }
    }

    /**
     * 删除：根据 SQL ID 删除 Java 元素
     */
    public int deleteBySqlId(String sqlId) {
        if (sqlId == null || sqlId.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL ID 不能为空，无法删除 Java 元素");
        }
        String sql = "DELETE FROM element_java WHERE sql_id = ?";

        try (Connection conn = getConnection()) {
            return queryRunner.update(conn, sql, sqlId);
        } catch (SQLException e) {
            throw new RuntimeException("删除 Java 元素失败（SQL ID：" + sqlId + "）", e);
        }
    }

    /**
     * 批量删除：根据 SQL ID 列表批量删除 Java 元素
     */
    public void batchDeleteBySqlIds(List<String> sqlIds) {
        if (sqlIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", sqlIds.stream().map(s -> "?").toArray(String[]::new));
        String sql = "DELETE FROM element_java WHERE sql_id IN (" + placeholders + ")";

        try (Connection conn = getConnection()) {
            Object[] params = sqlIds.toArray();
            queryRunner.update(conn, sql, params);
        } catch (SQLException e) {
            throw new RuntimeException("批量删除 Java 元素失败", e);
        }
    }

    /**
     * 根据文件路径获取对应的SQL ID集合
     */
    @NotNull
    public Set<String> getSqlIdsByFile(@NotNull String javaFilePath) {
        if (javaFilePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Java文件路径不能为空");
        }
        String sql = "SELECT sql_id FROM element_java WHERE file_path = ?";
        try (Connection conn = getConnection()) {
            List<String> sqlIdList = queryRunner.query(conn, sql, new ColumnListHandler<>("sql_id"), javaFilePath);
            return new HashSet<>(sqlIdList);
        } catch (SQLException e) {
            throw new RuntimeException("查询Java文件[" + javaFilePath + "]关联的SQL ID失败", e);
        }
    }

    /**
     * 根据文件路径删除所有关联的Java元素记录
     */
    public int deleteByFilePath(@NotNull String javaFilePath) {
        if (javaFilePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Java文件路径不能为空");
        }
        String sql = "DELETE FROM element_java WHERE file_path = ?";
        try (Connection conn = getConnection()) {
            return queryRunner.update(conn, sql, javaFilePath);
        } catch (SQLException e) {
            throw new RuntimeException("删除Java文件[" + javaFilePath + "]关联的缓存记录失败", e);
        }
    }

    /**
     * 查询所有Java元素记录
     */
    @NotNull
    public Set<JavaElementInfo> getAll() {
        String sql = "SELECT sql_id, file_path, element_type, start_offset, end_offset FROM element_java";
        try (Connection conn = getConnection()) {
            List<JavaElementInfo> allList = queryRunner.query(conn, sql, new BeanListHandler<>(JavaElementInfo.class, rowProcessor));
            return new HashSet<>(allList);
        } catch (SQLException e) {
            throw new RuntimeException("查询所有Java元素记录失败", e);
        }
    }


    /**
     * 清空 element_java 表所有数据（全局缓存清理）
     */
    public int clearAll() {
        String sql = "TRUNCATE TABLE element_java";
        try (Connection conn = getConnection()) {
            return queryRunner.update(conn, sql);
        } catch (SQLException e) {
            throw new RuntimeException("清空element_java表所有数据失败", e);
        }
    }
}