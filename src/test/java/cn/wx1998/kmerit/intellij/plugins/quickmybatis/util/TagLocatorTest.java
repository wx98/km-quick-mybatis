package cn.wx1998.kmerit.intellij.plugins.quickmybatis.util;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.XmlElementInfo;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.parser.MyBatisXmlParser;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.parser.MyBatisXmlParserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@TestDataPath("$CONTENT_ROOT/src/test/resources/cn/wx1998/kmerit/intellij/plugins/quickmybatis/util/")
public class TagLocatorTest extends BasePlatformTestCase {

    private static final Logger log = LoggerFactory.getLogger(TagLocatorTest.class);

    @Override
    protected String getTestDataPath() {
        return "src/test/resources/cn/wx1998/kmerit/intellij/plugins/quickmybatis/util/";
    }

    @Test
    public void testAaaaa_isValidMyBatisFile() {

        XmlFile xmlFile = (XmlFile) myFixture.configureByFile("Test1Mapper.xml");
        Project project = xmlFile.getProject();

        MyBatisXmlParser parser = MyBatisXmlParserFactory.createDefaultParser(project);

        MyBatisXmlParser.MyBatisParseResult parse = parser.parse(xmlFile);

        Map<String, List<XmlTag>> statements = parse.getStatements();

        statements.forEach((key, statement) -> {
            statement.forEach(element -> {
                XmlElementInfo xmlElementInfo = TagLocator.createXmlElementInfo(element, key, "mysql", element.getName());

                XmlTag xmlTagByInfo = TagLocator.findXmlTagByInfo(xmlElementInfo, project);

                if (element.equals(xmlTagByInfo)) {
                    log.info("success:{}", xmlElementInfo);
                } else {
                    log.info("failure:{}", xmlElementInfo);

                }
            });
        });
    }

}