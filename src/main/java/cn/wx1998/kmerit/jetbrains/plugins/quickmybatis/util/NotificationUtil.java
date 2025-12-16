package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 官方文档的通知工具类：<a href="https://plugins.jetbrains.com/docs/intellij/notifications.html">Notification</a>
 */
public class NotificationUtil {

    private static final String DEFAULT_NOTIFICATION_GROUP_ID = "cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util.NotificationUtil";

    /**
     * 通用通知显示方法（全自定义）
     *
     * @param project              当前项目（不可为null）
     * @param notificationKey      通知唯一标识（用于区分不同通知的"不再建议"状态，不可为null/空）
     * @param title                通知标题（不可为null/空）
     * @param content              通知内容（不可为null/空）
     * @param leftBtnText          左侧按钮文本（不可为null/空）
     * @param leftBtnAction        左侧按钮点击逻辑（不可为null）
     * @param rightBtnText         右侧按钮文本（不可为null/空）
     * @param rightBtnAction       右侧按钮点击逻辑（不可为null）
     * @param enableDoNotShowAgain 是否启用"不再建议"持久化（true=启用，false=不启用）
     */
    public static void showCustomNotification(@NotNull Project project, @NotNull String notificationKey, @NotNull String title, @NotNull String content, @NotNull String leftBtnText, @NotNull NotificationActionCallback leftBtnAction, @NotNull String rightBtnText, @NotNull NotificationActionCallback rightBtnAction, boolean enableDoNotShowAgain) {
        // 1. 参数校验（避免空指针）
        Objects.requireNonNull(project, "Project cannot be null");
        Objects.requireNonNull(notificationKey, "NotificationKey cannot be null or empty");
        Objects.requireNonNull(title, "Notification title cannot be null");
        Objects.requireNonNull(content, "Notification content cannot be null");
        Objects.requireNonNull(leftBtnText, "Left button text cannot be null");
        Objects.requireNonNull(leftBtnAction, "Left button action cannot be null");
        Objects.requireNonNull(rightBtnText, "Right button text cannot be null");
        Objects.requireNonNull(rightBtnAction, "Right button action cannot be null");


        // 2. 若启用"不再建议"，先检查状态
        if (enableDoNotShowAgain) {
            NotificationSettings settings = NotificationSettings.getInstance(project);
            if (settings.isDoNotShowAgain(notificationKey)) {
                return;
            }
        }

        // 3. 创建通知（使用通用通知组）
        Notification notification = NotificationGroupManager.getInstance().getNotificationGroup(DEFAULT_NOTIFICATION_GROUP_ID).createNotification(title, content, NotificationType.INFORMATION);

        // 4. 绑定左侧按钮（外部传入逻辑）
        notification.addAction(new NotificationAction(leftBtnText) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                // 执行外部传入的点击逻辑
                leftBtnAction.execute(e.getProject(), notification);
                // 执行后自动关闭通知（可根据需求改为由外部控制）
                notification.expire();
            }
        });

        // 5. 绑定右侧按钮（外部传入逻辑）
        notification.addAction(new NotificationAction(rightBtnText) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                // 若启用"不再建议"，点击右侧按钮时保存状态
                if (enableDoNotShowAgain) {
                    project.getService(NotificationSettings.class).setDoNotShowAgain(notificationKey, true);
                }
                // 执行外部传入的点击逻辑
                rightBtnAction.execute(e.getProject(), notification);
                // 执行后自动关闭通知
                notification.expire();
            }
        });

        // 6. 显示通知
        notification.notify(project);
    }

    /**
     * 函数式接口：通知按钮点击回调
     */
    @FunctionalInterface
    public interface NotificationActionCallback {
        void execute(@Nullable Project project, @NotNull Notification notification);
    }

    /**
     * 持久化配置服务（按 NotificationKey 存储不再建议状态）
     */
    @State(name = "kmQuickMybatisNotificationSettingsState", storages = @Storage("km-quick-mybatis-notification.xml"))
    public static class NotificationSettings implements PersistentStateComponent<NotificationSettings.State> {

        private State state = new State();

        /**
         * 无参构造函数（IDE 服务必须，用于自动实例化）
         */
        public NotificationSettings() {
        }

        public static NotificationSettings getInstance(@NotNull Project project) {
            Objects.requireNonNull(project, "Project cannot be null");
            return project.getService(NotificationSettings.class);
        }

        @Nullable
        @Override
        public State getState() {
            return state;
        }

        /**
         * 加载持久化状态（官方要求实现）
         */
        @Override
        public void loadState(@NotNull State state) {
            this.state = state;
            // 兼容旧数据：若 Map 为 null 则初始化
            if (this.state.doNotShowAgainMap == null) {
                this.state.doNotShowAgainMap = new HashMap<>();
            }
        }

        /**
         * 是否不再显示通知
         */
        public boolean isDoNotShowAgain(@NotNull String notificationKey) {
            // 若 key 不存在，默认返回 false（显示通知）
            return state.doNotShowAgainMap.getOrDefault(notificationKey, false);
        }

        /**
         * 按 NotificationKey 设置不再显示状态
         */
        public void setDoNotShowAgain(@NotNull String notificationKey, boolean doNotShowAgain) {
            state.doNotShowAgainMap.put(notificationKey, doNotShowAgain);
        }

        /**
         * 按 NotificationKey 重置不再显示状态（单个通知）
         */
        public void resetDoNotShowAgain(@NotNull String notificationKey) {
            state.doNotShowAgainMap.remove(notificationKey);
        }

        /**
         * 重置所有通知的不再显示状态
         */
        public void resetAllDoNotShowAgain() {
            state.doNotShowAgainMap.clear();
        }

        /**
         * 状态存储类：用 Map 按 NotificationKey 存储不再建议状态
         */
        public static class State {
            // key: NotificationKey, value: 是否不再显示
            public Map<String, Boolean> doNotShowAgainMap = new HashMap<>();
        }
    }
}