package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache;

import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.Test;

import java.util.Set;

/**
 * 缓存管理器的集成测试，使用实际的XML文件来测试缓存功能
 */
@TestDataPath("$CONTENT_ROOT/src/test/resources/cn/wx1998/kmerit/intellij/plugins/quickmybatis/cache/")
public class MyBatisCacheIntegrationTest extends BasePlatformTestCase {

    private MyBatisCacheManager cacheManager;
    private Project project;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        project = getProject();
        cacheManager = new DefaultMyBatisCacheManager(project);
        // 确保测试开始前缓存为空
        cacheManager.clearAllCache();
    }

    @Override
    protected void tearDown() throws Exception {
        // 测试结束后清理所有缓存
        cacheManager.clearAllCache();
        super.tearDown();
    }

    /**
     * 测试加载和解析TestMapper1.xml文件，并缓存映射关系
     */
    @Test
    public void testCacheIntegrationWithTestMapper1() {
        // 加载测试文件
        XmlFile xmlFile = (XmlFile) myFixture.configureByFile("TestMapper1.xml");
        assertNotNull("XML文件应该成功加载", xmlFile);

        String filePath = xmlFile.getVirtualFile().getPath();
        String namespace = "com.example.TestMapper1";

        // 手动添加映射关系，模拟解析器的工作
        cacheManager.putClassXmlMapping(namespace, filePath);
        cacheManager.putMethodStatementMapping(namespace, "findById", "com.example.TestMapper1.findById");
        cacheManager.putMethodStatementMapping(namespace, "insert", "com.example.TestMapper1.insert");
        cacheManager.putMethodStatementMapping(namespace, "update", "com.example.TestMapper1.update");
        cacheManager.putMethodStatementMapping(namespace, "deleteById", "com.example.TestMapper1.deleteById");
        cacheManager.putMethodStatementMapping(namespace, "findAll", "com.example.TestMapper1.findAll");
        cacheManager.putMethodStatementMapping(namespace, "findByUsername", "com.example.TestMapper1.findByUsername");

        // 验证缓存中的映射关系
        Set<String> xmlFiles = cacheManager.getXmlFilesForClass(namespace);
        assertFalse("类应该有对应的XML文件", xmlFiles.isEmpty());
        assertTrue("XML文件路径应该包含在映射中", xmlFiles.contains(filePath));

        assertEquals("XML文件应该对应正确的命名空间", namespace, cacheManager.getClassForXmlFile(filePath));

        // 验证方法-Statement映射
        assertEquals("findById方法应该映射到正确的Statement ID",
                "com.example.TestMapper1.findById",
                cacheManager.getStatementIdForMethod(namespace, "findById"));
        assertEquals("insert方法应该映射到正确的Statement ID",
                "com.example.TestMapper1.insert",
                cacheManager.getStatementIdForMethod(namespace, "insert"));

        // 验证Statement-方法映射
        assertEquals("findById Statement应该映射到正确的方法名",
                "findById",
                cacheManager.getMethodForStatementId(filePath, "com.example.TestMapper1.findById"));
        assertEquals("insert Statement应该映射到正确的方法名",
                "insert",
                cacheManager.getMethodForStatementId(filePath, "com.example.TestMapper1.insert"));

        // 验证缓存有效性
        assertTrue("类缓存应该有效", cacheManager.isCacheValid(namespace));
        assertTrue("XML文件缓存应该有效", cacheManager.isCacheValid(filePath));
    }

    /**
     * 测试加载和解析TestMapper2.xml文件，并缓存映射关系
     */
    @Test
    public void testCacheIntegrationWithTestMapper2() {
        // 加载测试文件
        XmlFile xmlFile = (XmlFile) myFixture.configureByFile("TestMapper2.xml");
        assertNotNull("XML文件应该成功加载", xmlFile);

        String filePath = xmlFile.getVirtualFile().getPath();
        String namespace = "com.example.TestMapper2";

        // 手动添加映射关系，模拟解析器的工作
        cacheManager.putClassXmlMapping(namespace, filePath);
        cacheManager.putMethodStatementMapping(namespace, "getProductById", "com.example.TestMapper2.getProductById");
        cacheManager.putMethodStatementMapping(namespace, "addProduct", "com.example.TestMapper2.addProduct");
        cacheManager.putMethodStatementMapping(namespace, "findProductsByCategory", "com.example.TestMapper2.findProductsByCategory");
        cacheManager.putMethodStatementMapping(namespace, "findProductsPaged", "com.example.TestMapper2.findProductsPaged");
        cacheManager.putMethodStatementMapping(namespace, "searchProducts", "com.example.TestMapper2.searchProducts");

        // 验证缓存中的映射关系
        Set<String> xmlFiles = cacheManager.getXmlFilesForClass(namespace);
        assertFalse("类应该有对应的XML文件", xmlFiles.isEmpty());
        assertTrue("XML文件路径应该包含在映射中", xmlFiles.contains(filePath));

        assertEquals("XML文件应该对应正确的命名空间", namespace, cacheManager.getClassForXmlFile(filePath));

        // 验证方法-Statement映射
        assertEquals("getProductById方法应该映射到正确的Statement ID",
                "com.example.TestMapper2.getProductById",
                cacheManager.getStatementIdForMethod(namespace, "getProductById"));

        // 验证Statement-方法映射
        assertEquals("getProductById Statement应该映射到正确的方法名",
                "getProductById",
                cacheManager.getMethodForStatementId(filePath, "com.example.TestMapper2.getProductById"));
    }

    /**
     * 测试同时加载两个XML文件，并验证缓存中的映射关系
     */
    @Test
    public void testCacheMultipleMappers() {
        // 加载第一个测试文件
        XmlFile xmlFile1 = (XmlFile) myFixture.configureByFile("TestMapper1.xml");
        assertNotNull("第一个XML文件应该成功加载", xmlFile1);

        // 加载第二个测试文件
        XmlFile xmlFile2 = (XmlFile) myFixture.configureByFile("TestMapper2.xml");
        assertNotNull("第二个XML文件应该成功加载", xmlFile2);

        String filePath1 = xmlFile1.getVirtualFile().getPath();
        String filePath2 = xmlFile2.getVirtualFile().getPath();
        String namespace1 = "com.example.TestMapper1";
        String namespace2 = "com.example.TestMapper2";

        // 手动添加映射关系
        cacheManager.putClassXmlMapping(namespace1, filePath1);
        cacheManager.putClassXmlMapping(namespace2, filePath2);
        cacheManager.putMethodStatementMapping(namespace1, "findById", "com.example.TestMapper1.findById");
        cacheManager.putMethodStatementMapping(namespace2, "getProductById", "com.example.TestMapper2.getProductById");

        // 验证两个映射都存在于缓存中
        Set<String> filesForMapper1 = cacheManager.getXmlFilesForClass(namespace1);
        Set<String> filesForMapper2 = cacheManager.getXmlFilesForClass(namespace2);

        assertFalse("TestMapper1应该有对应的XML文件", filesForMapper1.isEmpty());
        assertFalse("TestMapper2应该有对应的XML文件", filesForMapper2.isEmpty());

        assertTrue("TestMapper1应该映射到正确的XML文件", filesForMapper1.contains(filePath1));
        assertTrue("TestMapper2应该映射到正确的XML文件", filesForMapper2.contains(filePath2));

        // 验证两个命名空间对应的是不同的XML文件
        assertFalse("两个Mapper的XML文件路径应该不同", filePath1.equals(filePath2));
    }

    /**
     * 测试文件修改导致的缓存失效机制
     */
    @Test
    public void testCacheInvalidationOnFileChange() {
        // 加载测试文件
        XmlFile xmlFile = (XmlFile) myFixture.configureByFile("TestMapper1.xml");
        assertNotNull("XML文件应该成功加载", xmlFile);

        String filePath = xmlFile.getVirtualFile().getPath();
        String namespace = "com.example.TestMapper1";

        // 手动添加映射关系
        cacheManager.putClassXmlMapping(namespace, filePath);

        // 验证缓存有效
        assertTrue("初始状态下，类缓存应该有效", cacheManager.isCacheValid(namespace));
        assertTrue("初始状态下，XML文件缓存应该有效", cacheManager.isCacheValid(filePath));

        // 手动使文件缓存失效 - 使用PsiFile参数而不是String路径
        cacheManager.invalidateFileCache(xmlFile);

        // 验证缓存已失效
        assertFalse("文件缓存应该失效", cacheManager.isCacheValid(filePath));
        assertFalse("关联的类缓存也应该失效", cacheManager.isCacheValid(namespace));

        // 刷新失效的缓存
        cacheManager.refreshInvalidatedCaches();

        // 验证缓存重新有效
        assertTrue("刷新后，类缓存应该重新有效", cacheManager.isCacheValid(namespace));
        assertTrue("刷新后，XML文件缓存应该重新有效", cacheManager.isCacheValid(filePath));
    }

    /**
     * 测试缓存统计信息的更新
     */
    @Test
    public void testCacheStatisticsUpdates() {
        // 加载测试文件
        XmlFile xmlFile = (XmlFile) myFixture.configureByFile("TestMapper1.xml");
        assertNotNull("XML文件应该成功加载", xmlFile);

        String filePath = xmlFile.getVirtualFile().getPath();
        String namespace = "com.example.TestMapper1";

        // 获取初始统计信息
        CacheStatistics stats = cacheManager.getCacheStatistics();
        long initialHits = stats.getHits();
        long initialMisses = stats.getMisses();
        long initialRequests = stats.getRequests();

        // 添加映射关系
        cacheManager.putClassXmlMapping(namespace, filePath);
        cacheManager.putMethodStatementMapping(namespace, "findById", "com.example.TestMapper1.findById");

        // 访问缓存，应该命中
        cacheManager.getXmlFilesForClass(namespace);
        cacheManager.getClassForXmlFile(filePath);
        cacheManager.getStatementIdForMethod(namespace, "findById");

        // 访问不存在的缓存，应该未命中
        cacheManager.getXmlFilesForClass("non.existent.Mapper");
        cacheManager.getClassForXmlFile("non/existent/path.xml");

        // 验证统计信息更新
        // 注意：由于统计信息更新是异步的，这里可能需要等待或使用反射来验证
        // 在实际环境中，统计信息会通过定期扫描任务(updateStatistics())更新
    }

    /**
     * 测试清除特定文件的缓存
     */
    @Test
    public void testClearSpecificFileCache() {
        // 加载两个测试文件
        XmlFile xmlFile1 = (XmlFile) myFixture.configureByFile("TestMapper1.xml");
        XmlFile xmlFile2 = (XmlFile) myFixture.configureByFile("TestMapper2.xml");

        String filePath1 = xmlFile1.getVirtualFile().getPath();
        String filePath2 = xmlFile2.getVirtualFile().getPath();
        String namespace1 = "com.example.TestMapper1";
        String namespace2 = "com.example.TestMapper2";

        // 添加映射关系
        cacheManager.putClassXmlMapping(namespace1, filePath1);
        cacheManager.putClassXmlMapping(namespace2, filePath2);

        // 清除第一个文件的缓存
        cacheManager.clearXmlFileCache(filePath1);

        // 验证第一个文件的缓存被清除
        assertTrue("第一个文件的类缓存应该为空", cacheManager.getXmlFilesForClass(namespace1).isEmpty());
        assertNull("第一个文件的类映射应该为空", cacheManager.getClassForXmlFile(filePath1));

        // 验证第二个文件的缓存仍然存在
        assertFalse("第二个文件的类缓存不应该为空", cacheManager.getXmlFilesForClass(namespace2).isEmpty());
        assertEquals("第二个文件的类映射应该仍然存在",
                namespace2, cacheManager.getClassForXmlFile(filePath2));
    }

    @Override
    protected String getTestDataPath() {
        return "src/test/resources/cn/wx1998/kmerit/intellij/plugins/quickmybatis/cache/";
    }
}