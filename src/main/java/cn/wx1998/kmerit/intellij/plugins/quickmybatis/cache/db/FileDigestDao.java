package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.db;

import com.intellij.openapi.project.Project;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * file_digest 表的操作
 */
public class FileDigestDao extends BaseDao {

    public FileDigestDao(@NotNull Project project) {
        super(project);
    }

    /**
     * 按需查询：根据文件路径获取对应的摘要码
     */
    public String getSqlIdsByFile(String filePath) {
        String sql = "SELECT digest FROM file_digest WHERE file_path = ? ";
        try (Connection conn = getConnection()) {
            List<String> digestList = queryRunner.query(conn, sql, new ColumnListHandler<>("digest"), filePath);
            return digestList != null ? digestList.get(0) : "";
        } catch (SQLException e) {
            throw new RuntimeException("查询文件摘要码失败", e);
        }
    }

    /**
     * 插入：添加文件摘要（file_path 作为唯一条件，已存在则忽略）
     */
    public void insert(String filePath, String digest) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("文件路径不能为空，无法插入摘要码");
        }
        if (digest == null || digest.trim().isEmpty()) {
            throw new IllegalArgumentException("摘要码不能为空，无法插入");
        }
        String sql = "INSERT INTO file_digest (file_path, digest) VALUES (?, ?) ON DUPLICATE KEY UPDATE digest = VALUES(digest) ";

        try (Connection conn = getConnection()) {
            queryRunner.update(conn, sql, filePath, digest);
        } catch (SQLException e) {
            throw new RuntimeException("插入文件摘要码失败（文件路径：" + filePath + "）", e);
        }
    }

    /**
     * 批量插入：批量添加文件摘要（已存在则忽略）
     */
    public int batchInsert(Map<String, String> digestMap) {
        if (digestMap.isEmpty()) {
            return 0;
        }
        String sql = "INSERT IGNORE INTO file_digest (file_path, digest) VALUES (?, ?) ON DUPLICATE KEY UPDATE digest = VALUES(digest) ";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            Object[][] params = digestMap.entrySet().stream().peek(entry -> {
                String filePath = entry.getKey();
                String digest = entry.getValue();
                if (filePath == null || filePath.trim().isEmpty() || digest == null || digest.trim().isEmpty()) {
                    throw new IllegalArgumentException("存在空的文件路径或摘要码，无法批量插入");
                }
            }).map(entry -> new Object[]{entry.getKey(), entry.getValue()}).toArray(Object[][]::new);
            int[] batch = queryRunner.batch(conn, sql, params);
            conn.commit();
            conn.setAutoCommit(true);
            return batch == null ? 0 : batch.length;
        } catch (SQLException e) {
            throw new RuntimeException("批量插入文件摘要码失败", e);
        }
    }

    /**
     * 更新：根据文件路径更新摘要码（file_path 作为唯一条件）
     */
    public void updateByFilePath(String filePath, String digest) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("文件路径不能为空，无法更新摘要码");
        }
        if (digest == null || digest.trim().isEmpty()) {
            throw new IllegalArgumentException("摘要码不能为空，无法更新");
        }
        String sql = "UPDATE file_digest SET digest = ? WHERE file_path = ?";

        try (Connection conn = getConnection()) {
            queryRunner.update(conn, sql, digest, filePath);
        } catch (SQLException e) {
            throw new RuntimeException("更新文件摘要码失败（文件路径：" + filePath + "）", e);
        }
    }

    /**
     * 批量更新：根据文件路径批量更新摘要码
     */
    public void batchUpdateByFilePath(Map<String, String> digestMap) {
        if (digestMap.isEmpty()) {
            return;
        }
        String sql = "UPDATE file_digest SET digest = ? WHERE file_path = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            Object[][] params = digestMap.entrySet().stream().peek(entry -> {
                String filePath = entry.getKey();
                String digest = entry.getValue();
                if (filePath == null || filePath.trim().isEmpty() || digest == null || digest.trim().isEmpty()) {
                    throw new IllegalArgumentException("存在空的文件路径或摘要码，无法批量更新");
                }
            }).map(entry -> new Object[]{entry.getValue(), entry.getKey()}).toArray(Object[][]::new);
            queryRunner.batch(conn, sql, params);
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            throw new RuntimeException("批量更新文件摘要码失败", e);
        }
    }

    /**
     * 新增：UPSERT 操作（存在则更新，不存在则插入）
     */
    public void upsert(String filePath, String digest) {
        if (filePath == null || filePath.trim().isEmpty() || digest == null || digest.trim().isEmpty()) {
            throw new IllegalArgumentException("文件路径和摘要码不能为空");
        }
        String sql = "MERGE INTO file_digest (file_path, digest) VALUES (?, ?)  WHEN MATCHED THEN UPDATE SET digest = ? WHEN NOT MATCHED THEN INSERT (file_path, digest) VALUES (?, ?)";

        try (Connection conn = getConnection()) {
            queryRunner.update(conn, sql, filePath, digest, digest, filePath, digest);
        } catch (SQLException e) {
            throw new RuntimeException("UPSERT 文件摘要码失败（文件路径：" + filePath + "）", e);
        }
    }

    /**
     * 查询所有文件的摘要信息（Key=文件路径，Value=摘要码）
     */
    @NotNull
    public Map<String, String> getAllFileDigest() {
        String sql = "SELECT file_path, digest FROM file_digest";
        try (Connection conn = getConnection()) {
            List<Map<String, Object>> rawResult = queryRunner.query(conn, sql, new MapListHandler());
            Map<String, String> result = new HashMap<>();
            for (Map<String, Object> row : rawResult) {
                String filePath = (String) row.get("file_path");
                String digest = (String) row.get("digest");
                if (filePath != null && digest != null) {
                    result.put(filePath, digest);
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("查询所有文件摘要信息失败", e);
        }
    }

    /**
     * 清空 file_digest 表所有数据（全局缓存清理）
     */
    public int clearAll() {
        String sql = "TRUNCATE TABLE file_digest";
        try (Connection conn = getConnection()) {
            return queryRunner.update(conn, sql);
        } catch (SQLException e) {
            throw new RuntimeException("清空file_digest表所有数据失败", e);
        }
    }
}