package cn.wx1998.kmerit.intellij.plugins.quickmybatis.parser;

import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import java.util.List;
import java.util.Map;

/**
 * MyBatis XML解析器接口
 * 定义了MyBatis XML文件解析的核心方法
 */
public interface MyBatisXmlParser {

    /**
     * 解析MyBatis XML文件
     *
     * @param file 要解析的XML文件
     * @return 解析结果，包含了XML文件中的所有相关元素
     */
    MyBatisParseResult parse(XmlFile file);

    /**
     * 判断是否为有效的MyBatis XML文件
     *
     * @param file 要检查的XML文件
     * @return 如果是有效的MyBatis XML文件，返回true；否则返回false
     */
    boolean isValidMyBatisFile(XmlFile file);

    /**
     * 解析结果接口
     * 包含了解析MyBatis XML文件后得到的所有元素
     */
    interface MyBatisParseResult {

        /**
         * 获取命名空间
         *
         * @return 命名空间字符串
         */
        String getNamespaceName();

        /**
         * 获取命名空间
         *
         * @return 命名空间字符串
         */
        XmlTag getNamespace();

        /**
         * 获取根Mapper节点
         *
         * @return 根Mapper节点
         */
        XmlTag getRootMapper();

        /**
         * 获取所有的SQL语句元素
         *
         * @return SQL语句元素列表
         */
        Map<String, List<XmlTag>> getStatements();

        /**
         * 获取所有的SQL片段
         *
         * @return SQL片段列表
         */
        Map<String, XmlTag> getSqlFragments();

        /**
         * 获取所有的结果映射
         *
         * @return 结果映射列表
         */
        Map<String, XmlTag> getResultMaps();

        /**
         * 根据ID获取SQL语句元素
         *
         * @param id SQL语句的ID
         * @return 对应的SQL语句元素，如果不存在返回null
         */
        List<XmlTag> getStatementById(String id);
    }
}