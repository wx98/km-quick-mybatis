package cn.wx1998.kmerit.intellij.plugins.quickmybatis.parser;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认MyBatis XML解析器实现类
 * 提供比默认解析器更全面的解析功能，使用MyBatisXmlStructure进行标签验证和结构分析
 */
public class DefaultMyBatisXmlParser implements MyBatisXmlParser {

    private static final Logger LOG = Logger.getInstance(DefaultMyBatisXmlParser.class);
    private final Project project;

    public static final Map<String, DefaultBatisParseResult> cache = new ConcurrentHashMap<>();

    /**
     * 构造函数
     *
     * @param project 当前项目实例，用于初始化解析器
     */
    public DefaultMyBatisXmlParser(Project project) {
        this.project = project;
        LOG.debug("为项目初始化默认MyBatis XML解析器: " + project.getName());
    }

    /**
     * 创建默认MyBatisXmlParser实例的工厂方法
     *
     * @param project 当前项目实例
     * @return EnhancedMyBatisXmlParser实例
     */
    public static MyBatisXmlParser create(@NotNull Project project) {
        LOG.debug("创建EnhancedMyBatisXmlParser实例");
        return new DefaultMyBatisXmlParser(project);
    }

    /**
     * 解析MyBatis XML文件
     *
     * @param file 需要解析的XML文件
     * @return 解析结果对象
     * @throws IllegalArgumentException 如果文件不是有效的MyBatis XML文件
     */
    @Override
    public MyBatisParseResult parse(XmlFile file) {
        String path = file.getVirtualFile().getPath();
        // 原子操作：若不存在则计算并放入缓存，避免重复解析
        return cache.computeIfAbsent(path, k -> {
            LOG.debug("开始解析MyBatis XML文件: " + path);
            if (!isValidMyBatisFile(file)) {
                LOG.warn("无效的MyBatis XML文件: " + file.getName());
                throw new IllegalArgumentException("不是有效的MyBatis XML文件: " + file.getName());
            }
            validateMapperStructure(file);
            LOG.debug("MyBatis XML文件解析完成: " + file.getVirtualFile().getPath());
            return new DefaultBatisParseResult(file);
        });
    }

    /**
     * 验证文件是否为有效的MyBatis XML文件
     *
     * @param file 需要验证的XML文件
     * @return 如果文件是有效的MyBatis XML文件则返回true，否则返回false
     */
    @Override
    public boolean isValidMyBatisFile(XmlFile file) {
        LOG.debug("验证文件是否为有效的MyBatis文件: " + file.getName());

        // 首先检查根标签是否为 mapper
        XmlDocument document = file.getDocument();
        if (document == null) {
            LOG.debug("文件没有XML文档结构: " + file.getName());
            return false;
        }

        // 检查根标签是否为mapper
        XmlTag rootTag = document.getRootTag();
        if (rootTag == null || !MyBatisXmlStructure.MAPPER_TAG.equals(rootTag.getName())) {
            LOG.debug("文件根标签不是mapper: " + (rootTag != null ? rootTag.getName() : "null") + "，文件名: " + file.getName());
            return false;
        }

        // 检查是否有名为namespace的有效属性
        XmlAttribute namespaceAttr = rootTag.getAttribute("namespace");
        if (namespaceAttr == null || namespaceAttr.getValue() == null || namespaceAttr.getValue().trim().isEmpty()) {
            LOG.debug("Mapper标签缺少有效的namespace属性: " + file.getName());
            return false;
        }

        LOG.debug("文件验证为有效的MyBatis文件: " + file.getName() + "，命名空间: " + namespaceAttr.getValue());
        return true;
    }

    /**
     * 验证Mapper文件结构的有效性
     *
     * @param file 需要验证的Mapper XML文件
     */
    private void validateMapperStructure(XmlFile file) {
        LOG.debug("验证Mapper文件结构: " + file.getName());

        XmlTag rootTag = file.getDocument().getRootTag();
        if (rootTag == null) {
            LOG.debug("文件没有根标签: " + file.getName());
            return;
        }

        // 获取所有子标签
        XmlTag[] subTags = rootTag.getSubTags();
        LOG.debug("文件包含" + subTags.length + "个子标签: " + file.getName());

        // 检查每个子标签是否为 MyBatis 支持的标签
        int statementCount = 0;
        int sqlFragmentCount = 0;
        int unsupportedCount = 0;

        for (XmlTag tag : subTags) {
            String tagName = tag.getName();

            if (!MyBatisXmlStructure.isSupportedTag(tagName)) {
                LOG.warn("不支持的MyBatis标签: " + tagName + "，文件名: " + file.getName());
                unsupportedCount++;
                continue;
            }

            // 验证 SQL 语句标签的属性
            if (MyBatisXmlStructure.isStatementTag(tagName)) {
                validateStatementTag(tag, tagName);
                statementCount++;
            } else if (MyBatisXmlStructure.SQL_TAG.equals(tagName)) {
                sqlFragmentCount++;
            }
        }

        LOG.debug("文件结构验证完成: " + file.getName() + "，语句数量: " + statementCount + "，SQL片段数量: " + sqlFragmentCount + "，不支持标签数量: " + unsupportedCount);
    }

    /**
     * 验证SQL语句标签的属性有效性
     *
     * @param tag     XML标签对象
     * @param tagName 标签名称
     */
    private void validateStatementTag(XmlTag tag, String tagName) {
        // 检查是否有id属性
        XmlAttribute idAttr = tag.getAttribute("id");
        if (idAttr == null || idAttr.getValue() == null || idAttr.getValue().trim().isEmpty()) {
            LOG.warn("SQL语句标签缺少id属性: " + tagName + "，文件名: " + tag.getContainingFile().getName());
        } else {
            LOG.debug("验证SQL语句标签: " + tagName + "，id: " + idAttr.getValue() + "，文件名: " + tag.getContainingFile().getName());
        }

        // 获取该标签支持的所有属性
        Set<String> supportedAttributes = MyBatisXmlStructure.getStatementAttributes(tagName);

        // 检查所有属性是否为支持的属性
        for (XmlAttribute attr : tag.getAttributes()) {
            String attrName = attr.getName();
            if (!supportedAttributes.contains(attrName)) {
                LOG.warn("不支持的属性: " + attrName + " 用于 " + tagName + "标签，文件名: " + tag.getContainingFile().getName());
            }
        }
    }

    /**
     * 默认MyBatis解析结果实现类
     * 提供对解析结果的全面访问和管理功能
     */
    private static class DefaultBatisParseResult implements MyBatisParseResult {

        private final XmlFile file;
        private final Map<String, List<XmlTag>> statementMap = new HashMap<>();
        private final Map<String, XmlTag> sqlFragmentMap = new HashMap<>();
        private final Map<String, XmlTag> resultMap = new HashMap<>();

        public DefaultBatisParseResult(XmlFile file) {
            this.file = file;
            // 初始化映射
            initializeMaps();
        }

        /**
         * 初始化内部映射集合
         * 解析XML文件中的所有SQL语句标签
         */
        private void initializeMaps() {
            LOG.debug("在EnhancedMyBatisXmlParser中初始化映射，文件: " + (file != null ? file.getName() : "空文件"));

            // 直接从 XML 文件解析以避免空指针异常
            if (file != null) {
                parseXmlFileDirectly();
            }
        }

        /**
         * 直接从XML文件解析内容，避免空指针异常
         */
        private void parseXmlFileDirectly() {
            LOG.debug("直接从XML文件解析内容: " + file.getName());

            XmlTag rootTag = file.getDocument().getRootTag();
            if (rootTag != null) {
                // 查找所有 SQL 语句标签
                for (String statementTag : MyBatisXmlStructure.STATEMENT_TAGS) {
                    XmlTag[] statementTags = rootTag.findSubTags(statementTag);
                    for (XmlTag tag : statementTags) {
                        XmlAttribute idAttr = tag.getAttribute("id");
                        if (idAttr != null && idAttr.getValue() != null) {
                            String id = idAttr.getValue().trim();
                            if (!id.isEmpty()) {
                                statementMap.computeIfAbsent(id, k -> new ArrayList<>()).add(tag);
                                LOG.debug("存储SQL语句: " + id + " (" + statementTag + ")，来自文件: " + file.getName());
                            }
                        }
                    }
                }

                // 查找所有 SQL 片段
                XmlTag[] sqlTags = rootTag.findSubTags(MyBatisXmlStructure.SQL_TAG);
                for (XmlTag tag : sqlTags) {
                    XmlAttribute idAttr = tag.getAttribute("id");
                    if (idAttr != null && idAttr.getValue() != null) {
                        String id = idAttr.getValue().trim();
                        if (!id.isEmpty()) {
                            // 直接存储 XML 标签
                            sqlFragmentMap.put(id, tag);
                            LOG.debug("存储SQL片段: " + id + "，来自文件: " + file.getName());
                        }
                    }
                }

                // 查找所有 resultMap 片段
                XmlTag[] resultTags = rootTag.findSubTags(MyBatisXmlStructure.RESULT_MAP_TAG);
                for (XmlTag tag : resultTags) {
                    XmlAttribute idAttr = tag.getAttribute("id");
                    if (idAttr != null && idAttr.getValue() != null) {
                        resultMap.put(idAttr.getValue().trim(), tag);
                    }
                }
            }
        }

        /**
         * 获取XML文件的命名空间
         *
         * @return 命名空间字符串，如果无法获取则返回null
         */
        @Override
        public String getNamespace() {
            LOG.debug("从DefaultMyBatisParseResult获取命名空间");

            if (file != null) {
                XmlTag rootTag = file.getDocument().getRootTag();
                if (rootTag != null) {
                    XmlAttribute namespaceAttr = rootTag.getAttribute("namespace");
                    if (namespaceAttr != null && namespaceAttr.getValue() != null) {
                        String namespace = namespaceAttr.getValue().trim();
                        LOG.debug("成功获取命名空间: " + namespace);
                        return namespace;
                    }
                }
            }
            LOG.debug("获取命名空间失败");
            return null;
        }

        public XmlTag getRootMapper() {
            if (file != null) {
                XmlTag rootTag = file.getDocument().getRootTag();
                if (rootTag != null) {
                    return rootTag;
                }
            }
            return null;
        }

        /**
         * 获取所有SQL语句元素
         *
         * @return SQL语句元素列表（当前版本返回空列表）
         */
        @Override
        public Map<String, List<XmlTag>> getStatements() {
            return this.statementMap;
        }

        /**
         * 获取所有SQL片段元素
         *
         * @return SQL片段元素列表（
         */
        @Override
        public Map<String, XmlTag> getSqlFragments() {
            return this.sqlFragmentMap;
        }

        /**
         * 获取所有结果映射元素
         *
         * @return 结果映射元素列表（当前版本返回空列表）
         */
        @Override
        public Map<String, XmlTag> getResultMaps() {
            return this.resultMap;
        }

        /**
         * 根据ID获取SQL语句元素
         *
         * @param id SQL语句ID
         * @return SQL语句元素（当前版本返回null）
         */
        @Override
        public List<XmlTag> getStatementById(String id) {
            Map<String, List<XmlTag>> sqls = this.statementMap;
            if (sqls.containsKey(id)) {
                return sqls.get(id);
            }
            // todo : 有点怪
            return null;
        }
    }
}