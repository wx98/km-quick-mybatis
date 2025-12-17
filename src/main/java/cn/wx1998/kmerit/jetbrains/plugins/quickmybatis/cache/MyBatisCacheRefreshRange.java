package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache;

public enum MyBatisCacheRefreshRange {

    JAVA("Java"), XML("Xml"), JAVA_METHOD_CALL("Java 方法调用"), ALL("所有");

    final String msg;

    MyBatisCacheRefreshRange(String msg) {
        this.msg = msg;
    }

    @Override
    public String toString() {
        return msg;
    }
}
