package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.db;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.DataBaseManager;
import com.intellij.openapi.project.Project;
import org.apache.commons.dbutils.BasicRowProcessor;
import org.apache.commons.dbutils.BeanProcessor;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.RowProcessor;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyDescriptor;
import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/**
 * DAO 基础类，封装公共资源和方法
 */
public abstract class BaseDao {
    // 自定义BeanProcessor，实现下划线转驼峰
    protected static BeanProcessor beanProcessor = new BeanProcessor() {
        @Override
        protected int[] mapColumnsToProperties(ResultSetMetaData rsmd, PropertyDescriptor[] props) throws SQLException {
            final int cols = rsmd.getColumnCount();
            final int[] columnToProperty = new int[cols + 1];
            java.util.Arrays.fill(columnToProperty, PROPERTY_NOT_FOUND);

            for (int col = 1; col <= cols; col++) {
                // 1. 获取数据库列名（优先用ColumnLabel，无则用ColumnName）
                String columnName = rsmd.getColumnLabel(col);
                if (null == columnName || columnName.isEmpty()) {
                    columnName = rsmd.getColumnName(col);
                }

                // 2. 核心：下划线列名转驼峰属性名（如file_path → filePath）
                String propertyName = underlineToCamel(columnName);

                // 3. 遍历实体类属性，匹配驼峰属性名（大小写不敏感）
                for (int i = 0; i < props.length; i++) {
                    final PropertyDescriptor prop = props[i];
                    final String propName = prop.getName();

                    // 匹配规则：驼峰属性名忽略大小写匹配
                    if (propertyName.equalsIgnoreCase(propName)) {
                        columnToProperty[col] = i;
                        break;
                    }
                }
            }
            return columnToProperty;
        }

        /**
         * 工具方法：下划线字符串转驼峰字符串
         * @param underlineStr 下划线命名的字符串（如file_path、sql_id）
         * @return 驼峰命名的字符串（如filePath、sqlId）
         */
        private String underlineToCamel(String underlineStr) {
            if (underlineStr == null || underlineStr.isEmpty()) {
                return underlineStr;
            }
            StringBuilder sb = new StringBuilder();
            boolean nextUpperCase = false;
            for (char c : underlineStr.toCharArray()) {
                if (c == '_') {
                    nextUpperCase = true;
                } else {
                    sb.append(nextUpperCase ? Character.toUpperCase(c) : c);
                    nextUpperCase = false;
                }
            }
            return sb.toString();
        }
    };
    /**
     * 构造自定义RowProcessor
     */
    protected static RowProcessor rowProcessor = new BasicRowProcessor(beanProcessor);
    /**
     * 数据库管理
     */
    protected final DataBaseManager h2Manager;
    /**
     * 用 commons-dbutils 执行sql
     */
    protected final QueryRunner queryRunner;

    /**
     * 构造类
     *
     * @param project project
     */
    public BaseDao(@NotNull Project project) {
        this.h2Manager = DataBaseManager.getInstance(project);
        this.queryRunner = new QueryRunner();
    }

    /**
     * 获取数据库连接
     */
    protected Connection getConnection() throws SQLException {
        return h2Manager.getConnection();
    }

}