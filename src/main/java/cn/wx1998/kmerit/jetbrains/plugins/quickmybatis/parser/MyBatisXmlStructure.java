package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.parser;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MyBatis XML结构定义类
 * 根据MyBatis的DTD规范定义所有支持的标签和属性
 */
public final class MyBatisXmlStructure {

    // MyBatis Mapper根标签
    public static final String MAPPER_TAG = "mapper";
    // 主要SQL语句标签
    public static final String SELECT_TAG = "select";
    public static final String INSERT_TAG = "insert";
    public static final String UPDATE_TAG = "update";
    public static final String DELETE_TAG = "delete";
    // 所有SQL语句标签数组
    public static final String[] STATEMENT_TAGS = {SELECT_TAG, INSERT_TAG, UPDATE_TAG, DELETE_TAG};
    // 支持的SQL片段标签
    public static final String SQL_TAG = "sql";
    // 结果映射相关标签
    public static final String RESULT_MAP_TAG = "resultMap";
    public static final String RESULT_TAG = "result";
    public static final String ID_TAG = "id";
    public static final String COLLECTION_TAG = "collection";
    public static final String ASSOCIATION_TAG = "association";
    // 参数映射相关标签
    public static final String PARAMETER_MAP_TAG = "parameterMap";
    public static final String PARAMETER_TAG = "parameter";
    // 缓存相关标签
    public static final String CACHE_TAG = "cache";
    public static final String CACHE_REF_TAG = "cache-ref";
    // SQL语句的通用属性
    public static final Set<String> COMMON_STATEMENT_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("id", "parameterType", "parameterMap", "resultType", "resultMap", "timeout", "fetchSize", "statementType", "resultSetType", "flushCache", "useCache", "databaseId", "lang")));
    // INSERT语句特有的属性
    public static final Set<String> INSERT_SPECIFIC_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("useGeneratedKeys", "keyProperty", "keyColumn", "keyGenerator", "resultSets")));
    // UPDATE语句特有的属性
    public static final Set<String> UPDATE_SPECIFIC_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<>(List.of("flushCache")));
    // DELETE语句特有的属性
    public static final Set<String> DELETE_SPECIFIC_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<>(List.of("flushCache")));
    // SELECT语句特有的属性
    public static final Set<String> SELECT_SPECIFIC_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("useCache", "resultOrdered", "resultSets")));
    // RESULT MAP的属性
    public static final Set<String> RESULT_MAP_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("id", "type", "extend", "autoMapping")));
    // SQL片段的属性
    public static final Set<String> SQL_FRAGMENT_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<>(List.of("id")));
    // MAPPER标签的属性
    public static final Set<String> MAPPER_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<>(List.of("namespace")));
    // MyBatis DTD的URL
    public static final String[] MYBATIS_DTD_URLS = new String[]{"http://mybatis.org/dtd/mybatis-3-mapper.dtd", "https://mybatis.org/dtd/mybatis-3-mapper.dtd", "http://www.mybatis.org/dtd/mybatis-3-mapper.dtd", "https://www.mybatis.org/dtd/mybatis-3-mapper.dtd"};

    // 私有构造函数，防止实例化
    private MyBatisXmlStructure() {
        throw new UnsupportedOperationException();
    }

    /**
     * 判断一个标签是否为SQL语句标签
     *
     * @param tagName 标签名称
     * @return 如果是SQL语句标签，返回true；否则返回false
     */
    public static boolean isStatementTag(@NotNull String tagName) {
        return SELECT_TAG.equals(tagName) || INSERT_TAG.equals(tagName) || UPDATE_TAG.equals(tagName) || DELETE_TAG.equals(tagName);
    }

    /**
     * 判断一个标签是否为MyBatis支持的标签
     *
     * @param tagName 标签名称
     * @return 如果是MyBatis支持的标签，返回true；否则返回false
     */
    public static boolean isSupportedTag(@NotNull String tagName) {
        return MAPPER_TAG.equals(tagName) || isStatementTag(tagName) || SQL_TAG.equals(tagName) || RESULT_MAP_TAG.equals(tagName) || PARAMETER_MAP_TAG.equals(tagName) || CACHE_TAG.equals(tagName) || CACHE_REF_TAG.equals(tagName);
    }

    /**
     * 获取SQL语句标签的所有属性
     *
     * @param tagName 标签名称
     * @return 属性集合
     */
    public static Set<String> getStatementAttributes(@NotNull String tagName) {
        Set<String> attributes = new HashSet<>(COMMON_STATEMENT_ATTRIBUTES);

        switch (tagName) {
            case INSERT_TAG -> attributes.addAll(INSERT_SPECIFIC_ATTRIBUTES);
            case UPDATE_TAG -> attributes.addAll(UPDATE_SPECIFIC_ATTRIBUTES);
            case DELETE_TAG -> attributes.addAll(DELETE_SPECIFIC_ATTRIBUTES);
            case SELECT_TAG -> attributes.addAll(SELECT_SPECIFIC_ATTRIBUTES);
        }

        return Collections.unmodifiableSet(attributes);
    }
}