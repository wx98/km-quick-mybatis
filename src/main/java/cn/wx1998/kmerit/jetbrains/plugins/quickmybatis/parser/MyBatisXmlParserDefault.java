package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.parser;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认MyBatis XML解析器实现类
 * 整合全局缓存，解析结果同步到MyBatisCacheConfig
 */
public class MyBatisXmlParserDefault implements MyBatisXmlParser {

    private static final Logger LOG = Logger.getInstance(MyBatisXmlParserDefault.class);
    private final Project project;

    /**
     * 构造函数
     *
     * @param project 当前项目实例，用于初始化解析器
     */
    public MyBatisXmlParserDefault(Project project) {
        this.project = project;
        LOG.debug("为项目初始化默认MyBatis XML解析器: " + project.getName());
    }

    /**
     * 创建默认MyBatisXmlParser实例的工厂方法
     *
     * @param project 当前项目实例
     * @return DefaultMyBatisXmlParser 实例
     */
    public static MyBatisXmlParser create(@NotNull Project project) {
        LOG.debug("创建 DefaultMyBatisXmlParser 实例");
        return new MyBatisXmlParserDefault(project);
    }

    /**
     * 解析MyBatis XML文件（核心方法）
     *
     * @param file 需要解析的XML文件
     * @return 解析结果对象
     * @throws IllegalArgumentException 如果文件不是有效的MyBatis XML文件
     */
    @Override
    public MyBatisParseResult parse(XmlFile file) {
        String path = file.getVirtualFile().getPath();
        LOG.debug("开始解析MyBatis XML文件: " + path);

        // 验证文件有效性
        boolean isValid = isValidMyBatisFile(file);
        if (!isValid) {
            return null;
        }
        // 创建解析结果
        return ReadAction.compute(() -> new DefaultBatisParseResult(file));
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
        // 所有Psi操作包裹在ReadAction.compute中，返回布尔结果
        return ReadAction.compute(() -> {
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
        });
    }

    /**
     * 验证Mapper文件结构的有效性，并同步标签信息到全局缓存
     *
     * @param file 需要验证的Mapper XML文件
     */
    private void validateMapperStructure(XmlFile file) {
        LOG.debug("验证Mapper文件结构: " + file.getName());
        ReadAction.run(() -> {
            XmlDocument document = file.getDocument();
            if (document == null) {
                LOG.debug("文件没有XML文档结构: " + file.getName());
                return;
            }
            XmlTag rootTag = document.getRootTag();
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

                // 验证并同步SQL语句标签（select/insert/update/delete）
                if (MyBatisXmlStructure.isStatementTag(tagName)) {
                    validateStatementTag(tag, tagName);
                    statementCount++;
                } else if (MyBatisXmlStructure.SQL_TAG.equals(tagName)) {
                    sqlFragmentCount++;
                }
            }

            LOG.debug("文件结构验证完成: " + file.getName() + "，语句数量: " + statementCount + "，SQL片段数量: " + sqlFragmentCount + "，不支持标签数量: " + unsupportedCount);
        });
    }

    /**
     * 验证SQL语句标签的属性有效性
     *
     * @param tag     XML标签对象
     * @param tagName 标签名称
     */
    private void validateStatementTag(XmlTag tag, String tagName) {
        // 检查是否有id属性
        ReadAction.run(() -> {
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
        });
    }

    /**
     * 默认MyBatis解析结果实现类
     * 解析XML标签并同步到全局缓存
     */
    private class DefaultBatisParseResult implements MyBatisParseResult {

        private final XmlFile file;
        private final Map<String, List<XmlTag>> statementMap = new ConcurrentHashMap<>();
        private final Map<String, XmlTag> sqlFragmentMap = new ConcurrentHashMap<>();
        private final Map<String, XmlTag> resultMap = new ConcurrentHashMap<>();

        public DefaultBatisParseResult(XmlFile file) {
            this.file = file;
            initializeMaps(); // 解析并同步缓存
        }

        /**
         * 初始化内部映射集合，同时同步到全局缓存
         */
        private void initializeMaps() {
            LOG.debug("初始化XML解析结果映射，文件: " + (file != null ? file.getName() : "空文件"));

            // 直接从 XML 文件解析以避免空指针异常
            if (file != null) {
                parseXmlFileDirectly();
            }
        }

        /**
         * 直接从XML文件解析内容，同步到本地集合和全局缓存
         */
        private void parseXmlFileDirectly() {
            LOG.debug("直接从XML文件解析内容: " + file.getName());
            // 1. 获取XML路径
            String xmlFilePath = file.getVirtualFile().getPath();
            XmlDocument xmlDocument = file.getDocument();
            if (xmlDocument == null) return;

            XmlTag rootTag = xmlDocument.getRootTag();
            if (rootTag == null) return;

            // 获取文档对象，用于计算行号
            PsiDocumentManager psiDocManager = PsiDocumentManager.getInstance(project);
            var document = psiDocManager.getDocument(file);
            if (document == null) {
                LOG.warn("无法获取XML文件的文档对象: " + file.getName());
                return;
            }

            // 1. 解析SQL语句标签（select/insert/update/delete）
            for (String statementTag : MyBatisXmlStructure.STATEMENT_TAGS) {
                XmlTag[] statementTags = rootTag.findSubTags(statementTag);
                for (XmlTag tag : statementTags) {
                    XmlAttribute idAttr = tag.getAttribute("id");
                    if (idAttr == null || idAttr.getValue() == null) continue;

                    String sqlId = idAttr.getValue().trim();
                    if (sqlId.isEmpty()) continue;

                    // 更新本地statementMap
                    statementMap.computeIfAbsent(sqlId, k -> new ArrayList<>()).add(tag);
                }
            }

            // 2. 解析SQL片段（sql标签）
            XmlTag[] sqlTags = rootTag.findSubTags(MyBatisXmlStructure.SQL_TAG);
            for (XmlTag tag : sqlTags) {
                XmlAttribute idAttr = tag.getAttribute("id");
                if (idAttr == null || idAttr.getValue() == null) continue;

                String sqlId = idAttr.getValue().trim();
                if (sqlId.isEmpty()) continue;

                // 更新本地sqlFragmentMap
                sqlFragmentMap.put(sqlId, tag);
            }

            // 3. 解析resultMap标签
            XmlTag[] resultTags = rootTag.findSubTags(MyBatisXmlStructure.RESULT_MAP_TAG);
            for (XmlTag tag : resultTags) {
                XmlAttribute idAttr = tag.getAttribute("id");
                if (idAttr == null || idAttr.getValue() == null) continue;

                String sqlId = idAttr.getValue().trim();
                if (sqlId.isEmpty()) continue;

                // 更新本地resultMap
                resultMap.put(sqlId, tag);
            }
        }

        /**
         * 获取XML文件的命名空间
         *
         * @return 命名空间字符串，如果无法获取则返回null
         */

        @Override
        public String getNamespaceName() {
            XmlTag rootTag = getNamespace();
            if (rootTag == null) return null;
            XmlAttribute namespaceAttr = rootTag.getAttribute("namespace");
            return namespaceAttr != null && namespaceAttr.getValue() != null ? namespaceAttr.getValue().trim() : null;
        }

        @Override
        public XmlTag getNamespace() {
            return ReadAction.compute(() -> {
                if (file == null) return null;
                XmlDocument document = file.getDocument();
                if (document == null) return null;
                return document.getRootTag();
            });
        }


        public XmlTag getRootMapper() {
            return ReadAction.compute(() -> {
                if (file == null) return null;
                XmlDocument document = file.getDocument();

                return document != null ? document.getRootTag() : null;
            });
        }

        /**
         * 获取所有SQL语句元素
         *
         * @return SQL语句元素映射（id -> 标签列表）
         */
        @Override
        public Map<String, List<XmlTag>> getStatements() {
            return this.statementMap;
        }

        /**
         * 获取所有SQL片段元素
         *
         * @return SQL片段元素映射（id -> 标签）
         */
        @Override
        public Map<String, XmlTag> getSqlFragments() {
            return this.sqlFragmentMap;
        }

        /**
         * 获取所有结果映射元素
         *
         * @return 结果映射元素映射（id -> 标签）
         */
        @Override
        public Map<String, XmlTag> getResultMaps() {
            return this.resultMap;
        }

        /**
         * 根据ID获取SQL语句元素
         *
         * @param id SQL语句ID
         * @return SQL语句元素列表（无结果时返回空列表）
         */
        @Override
        public List<XmlTag> getStatementById(String id) {
            return statementMap.getOrDefault(id, Collections.emptyList());
        }
    }
}