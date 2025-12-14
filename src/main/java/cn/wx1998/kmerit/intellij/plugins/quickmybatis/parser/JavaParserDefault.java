package cn.wx1998.kmerit.intellij.plugins.quickmybatis.parser;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.MyBatisCache;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.MyBatisCacheFactory;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.services.JavaService;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java文件解析器默认实现：提取类名、方法名及方法参数等信息
 */
public class JavaParserDefault implements JavaParser {

    private static final Logger LOG = Logger.getInstance(JavaParserDefault.class);
    private final Project project;
    private final MyBatisCache cacheConfig; // 全局缓存管理器


    public JavaParserDefault(Project project) {
        this.project = project;
        this.cacheConfig = MyBatisCacheFactory.getRecommendedParser(project);
        LOG.debug("为项目初始化默认Java解析器: " + project.getName());
    }


    public static JavaParser create(Project project) {
        LOG.debug("创建 JavaParserDefault 实例");
        return new JavaParserDefault(project);
    }


    @Override
    public JavaParseResult parse(PsiJavaFile file) {
        String path = file.getVirtualFile().getPath();
        LOG.debug("开始解析Java文件: " + path);

        // 验证文件有效性
        boolean isValid = isValidJavaFile(file);
        if (!isValid) {
            return null;
        }

        // 创建解析结果,为了节省时间 解析结果不包含 方法调用
        return ReadAction.compute(() -> new DefaultJavaParseResult(file, false));
    }

    @Override
    public JavaParseResult parseEverything(PsiJavaFile file) {
        String path = file.getVirtualFile().getPath();
        LOG.debug("开始解析Java文件: " + path);

        // 验证文件有效性
        boolean isValid = isValidJavaFile(file);
        if (!isValid) {
            return null;
        }

        // 创建解析结果,结果包含 方法调用
        return ReadAction.compute(() -> new DefaultJavaParseResult(file, true));
    }

    @Override
    public boolean isValidJavaFile(PsiJavaFile file) {
        return ReadAction.compute(() -> {
            String fileName = file.getName();
            LOG.debug("验证Java文件有效性: " + fileName);
            VirtualFile virtualFile = file.getVirtualFile();
            if (virtualFile == null) {
                LOG.debug("文件不存在，验证失败: " + fileName);
                return false;
            }
            // 3. 检查是否包含至少一个有效类/接口（非匿名类、非局部类）
            PsiClass[] classes = file.getClasses();
            if (classes.length == 0) {
                LOG.debug("文件不包含任何类或接口，验证失败: " + fileName);
                return false;
            }
            // 4. 检查公共类名是否与文件名一致（Java规范要求）
            for (PsiClass cls : classes) {
                if (cls.hasModifierProperty("public")) { // 公共类必须与文件名一致
                    String className = cls.getName();
                    String expectedFileName = className + ".java";
                    if (!fileName.equals(expectedFileName)) {
                        LOG.debug("公共类名与文件名不一致，验证失败: " + fileName + "，类名: " + className);
                        return false;
                    }
                }
            }

            // 5. 可选：排除测试类（如包含@Test注解的类）
            if (isTestClass(file, virtualFile)) {
                LOG.debug("文件是测试类，验证失败: " + fileName);
                return false;
            }
            LOG.debug("文件验证通过（可正常编译）: " + fileName);
            return true;
        });
    }

    /**
     * 判断是否为测试类（
     */
    private boolean isTestClass(PsiJavaFile file, VirtualFile virtualFile) {
        String filePath = virtualFile.getPath();
        PsiClass[] classes = file.getClasses();

        // 情况1：文件位于 src/test/java 目录下（标准测试目录）
        if (filePath.contains("/src/test/java/") || filePath.contains("\\src\\test\\java\\")) {
            return true;
        }

        // 情况2：类上带有常见测试注解（如JUnit、SpringTest等）
        for (PsiClass cls : classes) {
            // 检查类级别是否有测试注解
            if (hasTestAnnotation(cls)) {
                return true;
            }
            // 检查方法级别是否有测试注解（如类中存在@Test方法，可能是测试类）
            for (PsiMethod method : cls.getMethods()) {
                if (hasTestAnnotation(method)) {
                    return true;
                }
            }
        }

        // 其他情况均视为非测试类
        return false;
    }

    /**
     * 检查元素（类/方法）是否带有测试注解
     */
    private boolean hasTestAnnotation(PsiModifierListOwner element) {
        // 常见测试注解列表（可根据实际需求扩展）
        Set<String> testAnnotations = new HashSet<>(Arrays.asList("org.junit.Test",// JUnit4测试方法
                "org.junit.jupiter.api.Test",                                      // JUnit5测试方法
                "org.springframework.boot.test.context.SpringBootTest",            // SpringBoot测试类
                "org.testng.annotations.Test"                                      // TestNG测试方法

        ));

        for (PsiAnnotation annotation : element.getAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if (qualifiedName != null && testAnnotations.contains(qualifiedName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 验证Java文件结构并同步到缓存
     */
    private void validateJavaStructure(PsiJavaFile file) {
        LOG.debug("验证Java文件结构: " + file.getName());
        PsiClass[] classes = file.getClasses();
        for (PsiClass cls : classes) {
            // 同步接口信息到缓存
            if (cls.isInterface()) {
//                cacheConfig.addMapperInterface(cls.getQualifiedName(), file);
                LOG.debug("已缓存Mapper接口: " + cls.getQualifiedName());
            }
        }
    }

    /**
     * 默认Java解析结果实现类
     */
    private static class DefaultJavaParseResult implements JavaParseResult {

        private final PsiJavaFile file;
        /**
         * 类列表
         */
        private final Map<String, PsiClass> classes = new ConcurrentHashMap<>();
        /**
         * 接口列表
         */
        private final Map<String, PsiClass> interfaces = new ConcurrentHashMap<>();
        /**
         * 类接口方法名
         */
        private final Map<String, List<PsiMethod>> classMethodsByName = new ConcurrentHashMap<>();
        /**
         * 接口方法名
         */
        private final Map<String, List<PsiMethod>> interfaceMethodsByName = new ConcurrentHashMap<>();
        /**
         * 类方法调用
         */
        private final Map<String, List<PsiMethodCallExpression>> classMethodCall = new ConcurrentHashMap<>();
        /**
         * 静态字符串类成员
         */
        private final Map<String, List<PsiField>> staticStringField = new ConcurrentHashMap<>();
        /**
         * 解析是否包含方法调用的标记
         */
        private final boolean includeMethodCalls;

        public DefaultJavaParseResult(PsiJavaFile file, boolean includeMethodCalls) {
            this.file = file;
            this.includeMethodCalls = includeMethodCalls;
            initializeMaps();
        }

        private void initializeMaps() {
            LOG.debug("初始化Java解析结果映射: " + file.getName());
            PsiClass[] psiClasses = file.getClasses();
            for (PsiClass cls : psiClasses) {
                String className = cls.getQualifiedName();
                // 分类存储类和接口
                if (cls.isInterface()) {
                    interfaces.put(className, cls);
                    PsiMethod[] methods = cls.getMethods();
                    for (PsiMethod method : methods) {
                        String methodKey = className + "." + method.getName();
                        interfaceMethodsByName.computeIfAbsent(methodKey, k -> new ArrayList<>()).add(method);
                    }
                } else if (!cls.isEnum() && !cls.isAnnotationType()) {
                    classes.put(className, cls);
                    PsiMethod[] methods = cls.getMethods();
                    for (PsiMethod method : methods) {
                        String methodKey = className + "." + method.getName();
                        // 按方法名分组（处理重载）
                        classMethodsByName.computeIfAbsent(methodKey, k -> new ArrayList<>()).add(method);
                        // 包含方法调用的解析
                        if (includeMethodCalls) {
                            PsiCodeBlock body = method.getBody();
                            if (body == null) continue;
                            Collection<PsiMethodCallExpression> childrenOfType = PsiTreeUtil.findChildrenOfType(body, PsiMethodCallExpression.class);
                            for (PsiMethodCallExpression callExpr : childrenOfType) {
                                JavaService javaService = JavaService.getInstance(body.getProject());
                                if (!javaService.isSqlSessionMethod(callExpr)) {
                                    continue;
                                }
                                PsiExpressionList argumentList = callExpr.getArgumentList();
                                PsiExpression[] expressions = argumentList.getExpressions();
                                if (expressions == null || expressions.length < 1) {
                                    continue;
                                }
                                PsiExpression expression = expressions[0];
                                String key = JavaService.parseExpression(expression);
                                if (key != null && !key.isEmpty()) {
                                    classMethodCall.computeIfAbsent(key, k -> new ArrayList<>()).add(callExpr);
                                }
                            }
                        }
                    }
                }
                PsiField[] fields = cls.getFields();
                for (PsiField field : fields) {
                    PsiType type = field.getType();
                    String canonicalText = type.getCanonicalText();
                    if ("java.lang.String".equals(canonicalText)) {
                        PsiExpression initializer = field.getInitializer();
                        String key = JavaService.parseExpression(initializer);

                        if (key != null && !key.isEmpty()) {
                            if (className != null && key.startsWith(className)) {
                                staticStringField.computeIfAbsent(key, k -> new ArrayList<>()).add(field);
                            }
                        }
                    }
                }
            }
        }

        @Override
        public String getQualifiedName() {
            return ReadAction.compute(() -> {
                return file.getPackageName() + "." + file.getName().replace(".java", "");
            });
        }

        @Override
        public Map<String, PsiClass> getClasses() {
            return Collections.unmodifiableMap(classes);
        }

        @Override
        public Map<String, PsiClass> getInterfaces() {
            return Collections.unmodifiableMap(interfaces);
        }

        @Override
        public Map<String, List<PsiMethod>> getAllClassMethods() {
            return Collections.unmodifiableMap(classMethodsByName);
        }

        @Override
        public Map<String, List<PsiMethod>> getAllInterfaceMethods() {
            return Collections.unmodifiableMap(interfaceMethodsByName);
        }

        @Override
        public List<PsiMethod> getInterfaceMethodsByName(String interfaceMethodName) {
            return Collections.unmodifiableList(interfaceMethodsByName.getOrDefault(interfaceMethodName, new ArrayList<>()));
        }

        @Override
        public List<PsiMethod> getClassMethodsByName(String classMethodName) {
            return Collections.unmodifiableList(classMethodsByName.getOrDefault(classMethodName, new ArrayList<>()));
        }

        @Override
        public Map<String, List<PsiMethodCallExpression>> getClassMethodCall() {
            return Collections.unmodifiableMap(classMethodCall);
        }

        @Override
        public Map<String, List<PsiField>> getStaticStringField() {
            return Collections.unmodifiableMap(staticStringField);
        }

        @Override
        public List<PsiField> getStaticStringFieldByName(String fieldByName) {
            return Collections.unmodifiableList(staticStringField.getOrDefault(fieldByName, new ArrayList<>()));
        }
    }
}