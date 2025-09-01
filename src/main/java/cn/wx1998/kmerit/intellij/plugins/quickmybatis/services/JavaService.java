package cn.wx1998.kmerit.intellij.plugins.quickmybatis.services;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.parser.MyBatisXmlParser;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.parser.MyBatisXmlParser.MyBatisParseResult;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.parser.MyBatisXmlParserFactory;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.setting.MyPluginSettings;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.DomUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

/**
 * The type Java service.
 *
 * @author yanglin
 */
public class JavaService implements Serializable {

    // 获取日志记录器实例
    private static final Logger LOG = Logger.getInstance(JavaService.class);

    @Serial
    private static final long serialVersionUID = 1L;

    private final Project project;

    private final JavaPsiFacade javaPsiFacade;

    /**
     * Instantiates a new Java service.
     *
     * @param project the project
     */
    public JavaService(Project project) {
        this.project = project;
        this.javaPsiFacade = JavaPsiFacade.getInstance(project);
    }

    /**
     * Gets instance.
     *
     * @param project the project
     * @return the instance
     */
    public static JavaService getInstance(@NotNull Project project) {
        return project.getService(JavaService.class);
    }

    /**
     * 计算表达式的值
     *
     * @param expression
     * @return
     */
    public static String parseExpression(PsiExpression expression) {
        if (expression instanceof PsiLiteralExpression) {
            // 直接返回字面量值
            return String.valueOf(((PsiLiteralExpression) expression).getValue());
        } else if (expression instanceof PsiBinaryExpression) {
            // 处理二元表达式（如字符串拼接）
            Deque<String> parts = new ArrayDeque<>();
            flattenBinaryExpression((PsiBinaryExpression) expression, parts);
            return String.join("", parts);
        } else if (expression instanceof PsiMethodCallExpression methodCall) {
            // 处理方法调用表达式
            PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if ("getName".equals(methodName)) {
                // 模拟 getName() 方法返回类名
                PsiExpression qualifier = methodExpression.getQualifierExpression();
                if (qualifier instanceof PsiClassObjectAccessExpression) {
                    PsiType type = ((PsiClassObjectAccessExpression) qualifier).getOperand().getType();
                    if (type instanceof PsiClassReferenceType) {
                        PsiClass psiClass = ((PsiClassReferenceType) type).resolve();
                        if (psiClass != null) {
                            return psiClass.getQualifiedName();
                        }
                    }
                }
            }
            return "";
        } else if (expression instanceof PsiReferenceExpression) {
            // 处理变量引用
            PsiElement resolved = ((PsiReferenceExpression) expression).resolve();
            if (resolved instanceof PsiField field) {
                PsiExpression initializer = field.getInitializer();
                if (initializer != null) {
                    return parseExpression(initializer);
                }
            } else if (resolved instanceof PsiLocalVariable localVar) {
                PsiExpression initializer = localVar.getInitializer();
                if (initializer != null) {
                    return parseExpression(initializer);
                }
                // 处理直接使用变量名作为字符串值的场景
                if (localVar.getType() instanceof PsiClassReferenceType && String.class.getName().equals(localVar.getType().getCanonicalText())) {
                    return localVar.getName();
                }
            }
        } else if (expression instanceof PsiPolyadicExpression polyadicExpression) {
            StringBuilder result = new StringBuilder();
            for (PsiExpression operand : polyadicExpression.getOperands()) {
                result.append(parseExpression(operand));
            }
            return result.toString();
        }
        return "";
    }

    /**
     * 处理二元表达式
     *
     * @param exp
     * @param parts
     */
    private static void flattenBinaryExpression(PsiBinaryExpression exp, Deque<String> parts) {
        PsiExpression lOperand = exp.getLOperand();
        PsiExpression rOperand = exp.getROperand();

        if (lOperand instanceof PsiBinaryExpression) {
            flattenBinaryExpression((PsiBinaryExpression) lOperand, parts);
        } else {
            parts.addFirst(parseExpression(lOperand));
        }

        if (rOperand instanceof PsiBinaryExpression) {
            flattenBinaryExpression((PsiBinaryExpression) rOperand, parts);
        } else {
            parts.addLast(parseExpression(rOperand));
        }
    }

    public JavaPsiFacade getJavaPsiFacade() {
        return javaPsiFacade;
    }

    public Project getProject() {
        return project;
    }

    /**
     * 获取项目中所有的MyBatis XML文件 //todo 这里要走缓存
     */
    private List<XmlFile> getMyBatisXmlFiles() {
        LOG.debug("Finding all MyBatis XML files in project");
        List<XmlFile> mybatisFiles = new ArrayList<>();

        // 获取项目的所有内容根目录
        for (VirtualFile contentRoot : ProjectRootManager.getInstance(project).getContentSourceRoots()) {
            if (contentRoot != null && contentRoot.isDirectory() && contentRoot.isValid()) {
                findXmlFilesRecursively(contentRoot, mybatisFiles);
            }
        }

        LOG.debug("Total MyBatis XML files found: " + mybatisFiles.size());
        return mybatisFiles;
    }

    /**
     * 递归查找 XML 文件
     */
    private void findXmlFilesRecursively(VirtualFile directory, List<XmlFile> result) {
        if (directory == null || !directory.isDirectory() || !directory.isValid()) {
            return;
        }

        for (VirtualFile file : directory.getChildren()) {
            if (file.isDirectory()) {
                findXmlFilesRecursively(file, result);
            } else if (file.getName().toLowerCase().endsWith(".xml")) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                if (psiFile instanceof XmlFile && DomUtils.isMybatisFile(psiFile)) {
                    result.add((XmlFile) psiFile);
                    LOG.debug("Found MyBatis XML file: " + file.getPath());
                }
            }
        }

        return;
    }

    /**
     * 处理类跳转逻辑
     *
     * @param psiClass
     * @param processor
     */
    public void processClass(PsiClass psiClass, CommonProcessors.CollectProcessor<XmlTag> processor) {
        LOG.debug("Processing class for MyBatis mapping: " + psiClass.getName());
        String ns = psiClass.getQualifiedName();

        MyBatisXmlParser parser = MyBatisXmlParserFactory.getRecommendedParser(project);
        List<XmlFile> myBatisXmlFiles = getMyBatisXmlFiles();

        for (XmlFile xmlFile : myBatisXmlFiles) {
            MyBatisParseResult result = parser.parse(xmlFile);
            String namespace = result.getNamespace();

            if (namespace != null && (namespace.equals(ns) || namespace.equals(ns + "Mapper"))) {
                LOG.debug("Found matching namespace: " + namespace + " for class: " + ns);

                XmlTag rootMapper = result.getRootMapper();
                processor.process(rootMapper);
            }
        }
    }

    /**
     * 处理方法跳转逻辑
     *
     * @param psiMethod
     * @param processor
     */
    public void processMethod(PsiMethod psiMethod, CommonProcessors.CollectProcessor<XmlTag> processor) {
        LOG.debug("Processing method for MyBatis mapping: " + psiMethod.getName());

        PsiClass psiClass = psiMethod.getContainingClass();
        if (null == psiClass) {
            LOG.debug("Method has no containing class");
            return;
        }
        if (!psiClass.isInterface()){
            return;
        }
        String qualifiedName = psiClass.getQualifiedName();
        String methodName = psiMethod.getName();
        String id = qualifiedName + "." + methodName;

        // 现在使用新的XML解析器来查找匹配的SQL语句
        MyBatisXmlParser parser = MyBatisXmlParserFactory.getRecommendedParser(project);
        List<XmlFile> myBatisXmlFiles = getMyBatisXmlFiles();

        for (XmlFile xmlFile : myBatisXmlFiles) {
            MyBatisParseResult result = parser.parse(xmlFile);
            String namespace = result.getNamespace();

            if (namespace != null && (namespace.equals(qualifiedName + "Mapper") || namespace.equals(qualifiedName))) {
                List<XmlTag> statementById = result.getStatementById(methodName);
                if (statementById != null) {
                    for (XmlTag statement : statementById) {
                        processor.process(statement);
                    }
                }
            }
        }
    }

    /**
     * 处理字段跳转逻辑
     *
     * @param field
     * @param processor
     */
    public void processField(@NotNull PsiField field, @NotNull Processor processor) {
        LOG.debug("Processing field for MyBatis mapping: " + field.getName());

        // 这里不再需要类型检查，因为我们不再依赖特定的DOM类型
        PsiExpression initializer = field.getInitializer();
        if (initializer != null) {
            String fieldValue = parseExpression(initializer);
            LOG.debug("Field value: " + fieldValue);

            MyBatisXmlParser parser = MyBatisXmlParserFactory.getRecommendedParser(project);
            List<XmlFile> myBatisXmlFiles = getMyBatisXmlFiles();

            for (XmlFile xmlFile : myBatisXmlFiles) {
                MyBatisParseResult result = parser.parse(xmlFile);
                String namespace = result.getNamespace();

                if (namespace != null && (namespace.equals(fieldValue) || namespace.equals(fieldValue + "Mapper"))) {
                    LOG.debug("Found matching namespace: " + namespace + " for field value: " + fieldValue);
                    XmlTag rootMapper = result.getRootMapper();
                    processor.process(rootMapper);
                }
            }
        }
    }

    /**
     * 处理方法调用跳转逻辑
     *
     * @param methodCall
     * @param processor
     */
    public void processMethodCall(@NotNull PsiMethodCallExpression methodCall, @NotNull Processor processor) {
        LOG.debug("Processing method call for MyBatis mapping");
        PsiMethod method = methodCall.resolveMethod();
        if (method == null || !isSqlSessionMethod(method)) {
            LOG.debug("Not a valid SqlSession method call");
            return;
        }

        PsiExpression[] args = methodCall.getArgumentList().getExpressions();
        if (args.length != 0) {
            // 解析第一个参数的实际值
            String mappedStatementId = parseExpression(args[0]);
            LOG.debug("Mapped statement ID: " + mappedStatementId);

            // 使用新的XML解析器来查找匹配的SQL语句
            MyBatisXmlParser parser = MyBatisXmlParserFactory.getRecommendedParser(project);
            List<XmlFile> myBatisXmlFiles = getMyBatisXmlFiles();

            for (XmlFile xmlFile : myBatisXmlFiles) {
                MyBatisParseResult result = parser.parse(xmlFile);
                String namespace = result.getNamespace();

                if (namespace != null && mappedStatementId.startsWith(namespace)) {
                    // 获取所有语句并进行匹配
                    Map<String, List<XmlTag>> statements = result.getStatements();
                    for (String key : statements.keySet()) {
                        List<XmlTag> xmlTagList = statements.get(key);
                        LOG.debug("Processing statement in file: " + xmlFile.getVirtualFile().getPath());
                        for (XmlTag tag : xmlTagList) {
                            if (mappedStatementId.equals(namespace + "." + key)) {
                                processor.process(tag);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 判断字段是否是指定类型的字段l
     *
     * @param type
     * @return
     */
    private boolean isType(PsiField type) {
        if (!(type.getType() instanceof PsiClassReferenceType)) {
            return false;
        }
        // 获取泛型擦除后的原始类型
        PsiClass psiClass = ((PsiClassReferenceType) type.getType()).rawType().resolve();
        return psiClass != null && String.class.getName().equals(psiClass.getQualifiedName());
    }

    /**
     * 判断方法是否是 SqlSession 的方法
     *
     * @param method
     * @return
     */
    private boolean isSqlSessionMethod(PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        if (!method.getModifierList().hasExplicitModifier(PsiModifier.PUBLIC)) {
            return false;
        }
        final var classFilters = MyPluginSettings.getInstance().getClassFilters();
        if (classFilters != null) {
            final var qualifiedName = containingClass.getQualifiedName();
            for (ClassFilter classFilter : classFilters) {
                final var pattern = classFilter.getPattern();
                if (StringUtil.equals(pattern, qualifiedName)) {
                    return true;
                }
            }
        }
        return false;
    }
}
