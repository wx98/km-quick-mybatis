package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util;

import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.MyBatisCacheManagerDefault;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.info.XmlElementInfo;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.parser.MyBatisXmlParser;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.parser.MyBatisXmlParserFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.Test;

import java.util.List;
import java.util.Map;

@TestDataPath("$CONTENT_ROOT/src/test/resources/cn/wx1998/kmerit/jetbrains/plugins/quickmybatis/util/")
public class TagLocatorTest extends BasePlatformTestCase {

    private static final Logger log = Logger.getInstance(MyBatisCacheManagerDefault.class);

    @Override
    protected String getTestDataPath() {
        return "src/test/resources/cn/wx1998/kmerit/jetbrains/plugins/quickmybatis/util/";
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
                    log.info("success:" + xmlElementInfo);
                } else {
                    log.info("failure:" + xmlElementInfo);

                }
            });
        });
    }

}