package cn.wx1998.kmerit.intellij.plugins.quickmybatis.services;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.MyBatisCacheConfig;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.JavaElementInfo;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.setting.MyBatisSetting;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.setting.MyPluginSettings;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.DomUtils;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.classFilter.ClassFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

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
    // 元素类型常量
    @NonNls
    private static final String TYPE_CLASS = "class";
    @NonNls
    private static final String TYPE_METHOD = "method";
    @NonNls
    private static final String TYPE_FIELD = "field";
    @NonNls
    private static final String TYPE_METHOD_CALL = "methodCall";
    private final Project project;
    private final MyBatisCacheConfig cacheConfig;
    private final MyBatisSetting setting; // 配置类，管理命名规则

    /**
     * Instantiates a new Java service.
     *
     * @param project the project
     */
    public JavaService(Project project) {
        this.project = project;
        this.cacheConfig = MyBatisCacheConfig.getInstance(project);
        this.setting = MyBatisSetting.getInstance(project);
    }

    /**
     * Gets instance.
     *
     * @param project the project
     * @return the instance
     */
    public static JavaService getInstance(@NotNull Project project) {
        JavaService service = project.getService(JavaService.class);
        return service;
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
            PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
            if (arguments.length != 0) {
                // 通常 SQL ID 是第一个字符串参数
                PsiExpression firstArg = arguments[0];
                if (firstArg instanceof PsiLiteralExpression) {
                    Object value = ((PsiLiteralExpression) firstArg).getValue();
                    return value instanceof String ? (String) value : null;
                } else if (firstArg instanceof PsiBinaryExpression) {
                    return parseExpression(firstArg);
                }

            } else {
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

            }
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

    public Project getProject() {
        return project;
    }

    /**
     * 获取项目中所有的MyBatis XML文件
     */
    public List<XmlFile> getMyBatisXmlFiles() {
        LOG.debug("Finding all MyBatis XML files in project");
        // 关键：将所有 Psi 相关操作（遍历+判断+PsiFile创建）完全包裹在 ReadAction 中
        return ReadAction.compute(() -> {
            List<XmlFile> mybatisFiles = new ArrayList<>();
            // 获取项目的所有内容根目录（非 Psi 操作，但在 ReadAction 中执行不影响）
            for (VirtualFile contentRoot : ProjectRootManager.getInstance(project).getContentSourceRoots()) {
                if (contentRoot != null && contentRoot.isDirectory() && contentRoot.isValid()) {
                    findXmlFilesRecursively(contentRoot, mybatisFiles);
                }
            }
            LOG.debug("Total MyBatis XML files found: " + mybatisFiles.size());
            return mybatisFiles;
        });
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
                // 1. PsiManager.findFile 是 Psi 读取操作（安全）
                PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                if (psiFile instanceof XmlFile) {
                    // 2. DomUtils.isMybatisFile 可能访问 Psi 结构（安全）
                    if (DomUtils.isMybatisFile(psiFile)) {
                        result.add((XmlFile) psiFile);
                        LOG.debug("Found MyBatis XML file: " + file.getPath());
                    }
                }
            }
        }
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

    /**
     * 处理类元素，计算 SQL ID 并同步到缓存
     */
    public void processClass(@NotNull PsiClass psiClass) {
        if (!isValidPsiElement(psiClass)) return;

        // 1. 计算 SQL ID（基于类名）
        String sqlId = calculateClassSqlId(psiClass);
        if (sqlId == null) return;

        // 2. 封装 Java 元素信息
        JavaElementInfo info = createJavaElementInfo(psiClass, sqlId, TYPE_CLASS);
        if (info == null) return;

        // 3. 同步到缓存
        cacheConfig.addJavaElementMapping(sqlId, info);
    }

    /**
     * 处理方法元素，计算 SQL ID 并同步到缓存
     */
    public void processMethod(@NotNull PsiMethod psiMethod) {
        if (!isValidPsiElement(psiMethod)) return;

        // 1. 计算 SQL ID（基于类名 + 方法名）
        String sqlId = calculateMethodSqlId(psiMethod);
        if (sqlId == null) return;

        // 2. 封装 Java 元素信息
        JavaElementInfo info = createJavaElementInfo(psiMethod, sqlId, TYPE_METHOD);
        if (info == null) return;

        // 3. 同步到缓存
        cacheConfig.addJavaElementMapping(sqlId, info);
    }

    /**
     * 处理字段元素，计算 SQL ID 并同步到缓存
     */
    public void processField(@NotNull PsiField psiField) {
        if (!isValidPsiElement(psiField)) return;

        // 1. 计算 SQL ID（基于类名 + 字段名）
        String sqlId = calculateFieldSqlId(psiField);
        if (sqlId == null) return;

        // 2. 封装 Java 元素信息
        JavaElementInfo info = createJavaElementInfo(psiField, sqlId, TYPE_FIELD);
        if (info == null) return;

        // 3. 同步到缓存
        cacheConfig.addJavaElementMapping(sqlId, info);
    }

    /**
     * 处理方法调用，计算 SQL ID 并同步到缓存
     */
    public void processMethodCall(@NotNull PsiMethodCallExpression methodCall) {
        if (!isValidPsiElement(methodCall)) return;

        // 1. 过滤非 MyBatis 相关的方法调用（如 sqlSession.selectOne(...)）
        if (!isSqlSessionMethod(methodCall)) return;

        // 2. 解析方法参数中的 SQL ID（如 parseExpression 提取字符串参数）
        String sqlId = parseExpression(methodCall);
        if (sqlId == null || sqlId.trim().isEmpty()) return;

        // 3. 封装 Java 元素信息
        JavaElementInfo info = createJavaElementInfo(methodCall, sqlId, TYPE_METHOD_CALL);
        if (info == null) return;

        // 4. 同步到缓存
        cacheConfig.addJavaElementMapping(sqlId, info);
    }

    /**
     * 基于类名计算 SQL ID（结合配置的命名规则）
     */
    @Nullable
    private String calculateClassSqlId(@NotNull PsiClass psiClass) {
        String className = psiClass.getQualifiedName();
        if (className == null) return null;

        // 从配置获取类级别的命名规则模板（默认："${className}"）
        String template = setting.getClassNamingRule();
        return template.replace("${className}", className);
    }

    /**
     * 基于类名 + 方法名计算 SQL ID
     */
    @Nullable
    private String calculateMethodSqlId(@NotNull PsiMethod psiMethod) {
        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass == null) return null;

        String className = containingClass.getQualifiedName();
        String methodName = psiMethod.getName();
        if (className == null || methodName == null) return null;

        // 从配置获取方法级别的命名规则模板（默认："${className}.${methodName}"）
        String template = setting.getMethodNamingRule();
        return template.replace("${className}", className).replace("${methodName}", methodName);
    }

    /**
     * 基于类名 + 字段名计算 SQL ID
     */
    @Nullable
    private String calculateFieldSqlId(@NotNull PsiField psiField) {
        PsiClass containingClass = psiField.getContainingClass();
        if (containingClass == null) return null;

        String className = containingClass.getQualifiedName();
        String fieldName = psiField.getName();
        if (className == null || fieldName == null) return null;

        // 从配置获取字段级别的命名规则模板（默认："${className}.${fieldName}"）
        String template = setting.getFieldNamingRule();
        return template.replace("${className}", className).replace("${fieldName}", fieldName);
    }

    private boolean isSqlSessionMethod(@NotNull PsiMethodCallExpression methodCall) {
        PsiMethod method = methodCall.resolveMethod();
        if (method == null) return false;

        // 获取方法名（如 "selectOne", "insert" 等）
        String methodName = method.getName();

        // 从配置获取需要匹配的方法名列表（默认：select|insert|update|delete）
        return setting.getSqlSessionMethodPatterns().stream().anyMatch(pattern -> methodName.matches(pattern));
    }

    /**
     * 创建 Java 元素信息（封装文件路径、行号等）
     */
    @Nullable
    private JavaElementInfo createJavaElementInfo(@NotNull PsiElement element, @NotNull String sqlId, @NotNull String elementType) {
        PsiFile containingFile = element.getContainingFile();
        if (containingFile == null || containingFile.getVirtualFile() == null) return null;

        // 获取文件路径
        String filePath = containingFile.getVirtualFile().getPath();

        // 获取行号（文档行号从 0 开始，显示时通常 +1）
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
        PsiDocumentManagerBase docManager = (PsiDocumentManagerBase) documentManager;
        int startOffset = element.getTextRange().getStartOffset();
        int lineNumber = docManager.getDocument(containingFile).getLineNumber(startOffset) + 1;

        return new JavaElementInfo(filePath, lineNumber, elementType, sqlId);
    }

    /**
     * 校验 Psi 元素是否有效（非空、存在于文件中）
     */
    private boolean isValidPsiElement(@NotNull PsiElement element) {
        return element.isValid() && element.getContainingFile() != null && element.getContainingFile().getVirtualFile() != null;
    }
}
