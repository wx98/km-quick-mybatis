package cn.wx1998.kmerit.intellij.plugins.quickmybatis.util;


import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * IDEA 内置 H2 的连接管理器
 */
public class DataBaseManager {

    private static final Logger LOG = Logger.getInstance(DataBaseManager.class);
    /**
     * 单项目锁
     */
    private static final ReentrantLock DB_LOCK = new ReentrantLock();
    /**
     * H2 连接URL
     */
    private static final String DB_URL_TEMPLATE = "jdbc:h2:file:%s/.idea/km_mybatis_cache;MODE=MYSQL;AUTO_SERVER=TRUE";
    /**
     * 数据库账号
     */
    private static final String DB_USER = "sa";
    /**
     * 数据库密码
     */
    private static final String DB_PWD = "sa";
    /**
     * 表结构SQL文件映射
     */
    private static final Map<String, String> TABLE_SQL_FILE_MAP = new HashMap<>();
    /**
     * H2表存在性查询SQL
     */
    private static final String CHECK_TABLE_EXIST_SQL = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ? AND TABLE_SCHEMA = SCHEMA()";

    static {
        // 表结构SQL文件映射初始化
        TABLE_SQL_FILE_MAP.put("element_java", "sql/element_java.sql");
        TABLE_SQL_FILE_MAP.put("element_xml", "sql/element_xml.sql");
        TABLE_SQL_FILE_MAP.put("file_digest", "sql/file_digest.sql");
    }

    /**
     * 项目实例
     */
    private final Project project;
    /**
     * HikariCP 数据源
     */
    private HikariDataSource dataSource;

    public DataBaseManager(Project project) {
        this.project = project;
        initH2DataSource();
    }

    public static DataBaseManager getInstance(@NotNull Project project) {
        return project.getService(DataBaseManager.class);
    }

    /**
     * 初始化 HikariCP 连接池 + H2 数据库
     */
    private void initH2DataSource() {
        DB_LOCK.lock();
        try {
            // 1. 加载 H2 驱动（兼容 IDEA 内置 H2）
            Class.forName("org.h2.Driver");

            // 2. 构建 Hikari 配置（轻量级参数，适配插件场景）
            HikariConfig config = new HikariConfig();
            String projectPath = FileUtil.toSystemDependentName(Objects.requireNonNull(project.getBasePath()));
            String dbUrl = String.format(DB_URL_TEMPLATE, projectPath);

            // 核心连接配置
            config.setJdbcUrl(dbUrl);
            config.setUsername(DB_USER);
            config.setPassword(DB_PWD);
            config.setDriverClassName("org.h2.Driver");

            // 连接池优化配置（插件场景轻量配置）
            config.setMaximumPoolSize(10); // 最大连接数（插件足够用）
            config.setMinimumIdle(2);      // 最小空闲连接（避免频繁创建）
            config.setIdleTimeout(60000);  // 空闲连接超时时间（1分钟）
            config.setConnectionTimeout(30000); // 连接获取超时（3秒）
            config.setMaxLifetime(1800000); // 连接最大生命周期（30分钟）
            config.setAutoCommit(true);    // 自动提交

            // 3. 初始化数据源
            this.dataSource = new HikariDataSource(config);

            // 4. 初始化表结构（复用原有逻辑）
            initTables();

        } catch (ClassNotFoundException e) {
            throw new RuntimeException("IDEA 未内置 H2 驱动", e);
        } catch (SQLException e) {
            throw new RuntimeException("H2 数据库初始化失败", e);
        } catch (IOException e) {
            throw new RuntimeException("SQL 初始化文件读取失败", e);
        } finally {
            DB_LOCK.unlock();
        }
    }

    /**
     * 初始化表结构
     */
    private void initTables() throws SQLException, IOException {
        // 从连接池获取连接执行建表逻辑
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {

            for (Map.Entry<String, String> entry : TABLE_SQL_FILE_MAP.entrySet()) {
                String tableName = entry.getKey();
                String sqlFilePath = entry.getValue();
                // 检查表是否存在
                if (checkTableExists(tableName)) continue;
                // 执行建表SQL文件
                executeSqlFile(stmt, sqlFilePath);
            }
        }
    }

    /**
     * 检查指定表是否存在
     */
    private boolean checkTableExists(String tableName) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(CHECK_TABLE_EXIST_SQL)) {
            stmt.setString(1, tableName.toUpperCase());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * 读取并执行单个SQL文件
     */
    private void executeSqlFile(Statement stmt, String sqlFilePath) throws SQLException, IOException {
        try (InputStream is = getClass().getResourceAsStream("/" + sqlFilePath)) {
            if (is == null) {
                throw new SQLException("SQL初始化文件不存在: " + sqlFilePath);
            }
            String sqlContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            // 拆分多语句（兼容SQL文件中多个语句的情况）
            String[] sqlStatements = sqlContent.split(";");
            for (String sql : sqlStatements) {
                String trimmedSql = sql.trim();
                if (!trimmedSql.isEmpty()) {
                    stmt.execute(trimmedSql);
                }
            }
        }
    }

    /**
     * 获取数据库连接
     */
    public Connection getConnection() throws SQLException {
        // 校验数据源状态，异常时重建
        if (dataSource == null || dataSource.isClosed()) {
            initH2DataSource();
        }
        // 从连接池获取连接
        return dataSource.getConnection();
    }

    /**
     * 关闭数据源（IDEA 项目关闭时调用，释放连接池资源）
     */
    public void close() {
        DB_LOCK.lock();
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close(); // 关闭连接池（而非单连接）
                dataSource = null;
            }
        } catch (Exception e) {
            // 插件关闭阶段静默处理，仅打印日志
            LOG.error("数据源关闭时异常", e);
        } finally {
            DB_LOCK.unlock();
        }
    }

    /**
     * 获取当前数据源状态
     */
    public boolean isDataSourceActive() {
        return dataSource != null && !dataSource.isClosed();
    }
}