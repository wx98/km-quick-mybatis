package cn.wx1998.kmerit.intellij.plugins.quickmybatis.services;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.setting.MyBatisSetting;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.setting.MyPluginSettings;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.ProjectFileUtils;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.ui.classFilter.ClassFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class JavaService implements Serializable {

    // 元素类型常量
    @NonNls
    public static final String TYPE_CLASS = "class";
    @NonNls
    public static final String TYPE_INTERFACE_CLASS = "interfaceClass";
    @NonNls
    public static final String TYPE_METHOD = "method";
    @NonNls
    public static final String TYPE_INTERFACE_METHOD = "interfaceMethod";
    @NonNls
    public static final String TYPE_FIELD = "field";
    @NonNls
    public static final String TYPE_METHOD_CALL = "methodCall";
    // 获取日志记录器实例
    private static final Logger LOG = Logger.getInstance(JavaService.class);
    @Serial
    private static final long serialVersionUID = 1L;
    private final Project project;
    private final MyBatisSetting setting; // 配置类，管理命名规则

    /**
     * Instantiates a new Java service.
     *
     * @param project the project
     */
    public JavaService(Project project) {
        this.project = project;
        this.setting = MyBatisSetting.getInstance(project);
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
        return ReadAction.compute(() -> {
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
                    } else if (firstArg instanceof PsiReferenceExpression) {
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
        });
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

    /**
     * 替换方法调用文本中的第一个参数为计算后的值
     * 示例：sqlSession.selectOne(NAMESPACE + ".queryById", params) → sqlSession.selectOne("com.xxx.UserMapper.queryById", params)
     *
     * @param originalText  原始方法调用文本
     * @param methodName    方法名（如 selectOne）
     * @param newFirstParam 计算后的第一个参数值
     * @return 替换后的文本
     */
    public static String replaceFirstParam(String originalText, String methodName, String newFirstParam) {
        if (methodName == null || newFirstParam == null) {
            return originalText;
        }

        // 找到第一个 '(' 的位置（方法名后紧跟的括号）
        int openBraceIndex = originalText.indexOf('(', originalText.indexOf(methodName));
        if (openBraceIndex == -1) {
            return originalText;
        }

        // 找到第一个 ',' 或 ')' 的位置（第一个参数的结束位置）
        int firstParamEndIndex = originalText.indexOf(',', openBraceIndex);
        if (firstParamEndIndex == -1) {
            firstParamEndIndex = originalText.indexOf(')', openBraceIndex);
        }
        if (firstParamEndIndex == -1) {
            return originalText;
        }

        // 替换第一个参数（用双引号包裹计算后的值，保持语法一致）
        String prefix = originalText.substring(0, openBraceIndex + 1).trim();
        String suffix = originalText.substring(firstParamEndIndex).trim();
        return String.format("%s\"%s\"%s", prefix, newFirstParam, suffix);
    }

    public Project getProject() {
        return project;
    }

    /**
     * 获取项目中所有的MyBatis XML文件
     */
    public List<PsiJavaFile> getAllJavaFiles() {
        LOG.debug("Finding all Java files in project");
        return ReadAction.compute(() -> {
            List<PsiFile> filesByTypeInSourceRoots = ProjectFileUtils.getVirtualFileListByTypeInSourceRoots(project, "java");
            List<PsiJavaFile> psiJavaFiles = new ArrayList<>();
            for (PsiFile file : filesByTypeInSourceRoots) {
                if (file instanceof PsiJavaFile psiJavaFile) {
                    psiJavaFiles.add(psiJavaFile);
                    LOG.debug("Found Java file: " + file.getVirtualFile().getPath());
                }
            }
            LOG.debug("Total Java files found: " + psiJavaFiles.size());
            return psiJavaFiles;
        });
    }

    /**
     * 判断方法是否是 SqlSession 的方法
     *
     * @param callExpression
     * @return
     */
    public boolean isSqlSessionMethod(PsiMethodCallExpression callExpression) {
        PsiMethod psiMethod = callExpression.resolveMethod();
        PsiClass containingClass = psiMethod.getContainingClass();
        String qualifiedName = containingClass.getQualifiedName();
        final var classFilters = MyPluginSettings.getInstance().getClassFilters();
        if (classFilters != null) {
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
     * 判断方法是否是 SqlSession 的方法
     *
     * @param method
     * @return
     */
    public boolean isSqlSessionMethod(PsiMethod method) {
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
