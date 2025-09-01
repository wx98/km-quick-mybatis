package cn.wx1998.kmerit.intellij.plugins.quickmybatis.setting.ui;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.Icons;
import com.intellij.CommonBundle;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.ui.classFilter.ClassFilterEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class MybatisClassFilterEditor extends ClassFilterEditor {

    private static final Logger LOG = Logger.getInstance(MybatisClassFilterEditor.class);

    public MybatisClassFilterEditor(Project project) {
        super(project);
        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(this.myTable);
        decorator.addExtraAction(new AddClassFilterAction());
        this.add(decorator.setRemoveAction(button -> TableUtil.removeSelectedItems(myTable)).setButtonComparator(
                this.getAddButtonText(),
                this.getAddPatternButtonText(),
                CommonBundle.message("button.remove", new Object[0])).disableUpDownActions().createPanel(), "Center");
    }

    @Override
    protected boolean addPatternButtonVisible() {
        return false;
    }

    @Override
    protected String getAddButtonText() {
        return JavaDebuggerBundle.message("button.add");
    }

    @Override
    protected Icon getAddButtonIcon() {
        return IconManager.getInstance().getIcon(Icons.IMAGES_ADD_LIST_SVG, this.getClass().getClassLoader());
    }

    private class AddClassFilterAction extends DumbAwareAction {

        // 在类级别缓存Throwable类
        private volatile PsiClass cachedThrowableClass;

        private AddClassFilterAction() {
            super(getAddButtonText(), null, getAddButtonIcon());
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(!myProject.isDefault());
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }

        private PsiClass getThrowableClass(Project project) {
            if (cachedThrowableClass == null || !cachedThrowableClass.isValid()) {
                cachedThrowableClass = ReadAction.compute(
                        () -> JavaPsiFacade.getInstance(project)
                                .findClass(
                                        CommonClassNames.JAVA_LANG_THROWABLE,
                                        GlobalSearchScope.allScope(project)
                                )
                );
            }
            return cachedThrowableClass;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {

            Project project = myProject;

            GlobalSearchScope searchScope = GlobalSearchScope.allScope(project);
            PsiClass throwableClass = getThrowableClass(project);

            TreeClassChooser classChooser = TreeClassChooserFactory.getInstance(project)
                    .createInheritanceClassChooser(
                            JavaBundle.message("class.filter.editor.choose.class.title"),
                            searchScope,
                            throwableClass,
                            true,   // 启用多选
                            true,   // 显示库类
                            null
                    );

            // 显示对话框
            classChooser.showDialog();

            // 处理选择结果
            PsiClass selectedClass = classChooser.getSelected();
            if (selectedClass != null) {
                ClassFilter filter = createFilter(getJvmClassName(selectedClass));
                myTableModel.addRow(filter);
                int row = myTableModel.getRowCount() - 1;
                myTable.getSelectionModel().setSelectionInterval(row, row);
                myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));
                IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(
                        () -> IdeFocusManager.getGlobalInstance().requestFocus(myTable, true)
                );
            }
        }


        private @Nullable String getJvmClassName(PsiClass aClass) {
            PsiClass parentClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true);
            if (parentClass != null) {
                final String parentName = getJvmClassName(parentClass);
                if (parentName == null) {
                    return null;
                }
                return parentName + "$" + aClass.getName();
            }
            return aClass.getQualifiedName();
        }
    }


}
