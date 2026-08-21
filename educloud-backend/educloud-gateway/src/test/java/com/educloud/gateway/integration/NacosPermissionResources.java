package com.educloud.gateway.integration;

final class NacosPermissionResources {

    private NacosPermissionResources() {
    }

    static String config(String namespace, String group, String dataId) {
        return format(namespace, group, "config", dataId);
    }

    static String naming(String namespace, String group, String serviceName) {
        return format(namespace, group, "naming", serviceName);
    }

    private static String format(String namespace, String group, String type, String name) {
        return namespace + ":" + group + ":" + type + "/" + name;
    }
}
