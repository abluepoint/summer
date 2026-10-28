package com.abluepoint.summer.sample.plugin.domain;
public final class PluginLifecycleRules {
 private PluginLifecycleRules() {}
 public static PluginDescriptor trusted(PluginDescriptor descriptor, String digest) { if(descriptor == null || !descriptor.artifactDigest().equals(digest)) throw new PluginTrustException("Plugin artifact is not in the trusted catalog"); return descriptor; }
 public static void requireTrusted(boolean trusted) { if(!trusted) throw new PluginTrustException("Plugin artifact is not trusted"); }
 public static void requireEnabled(PluginStatus status) { if(status != PluginStatus.ENABLED) throw new PluginCompatibilityException("Plugin is not enabled"); }
 public static PluginStatus registrationStatus(PluginDescriptor descriptor,String version) { return compatible(descriptor,version)?PluginStatus.REGISTERED:PluginStatus.INCOMPATIBLE; }
 public static PluginStatus upgradeStatus(PluginDescriptor descriptor,String version) { return compatible(descriptor,version)?PluginStatus.DISABLED:PluginStatus.INCOMPATIBLE; }
 public static boolean compatible(PluginDescriptor descriptor, String version) { return descriptor != null && descriptor.version().equals(version) && descriptor.requiredFrameworkVersion().startsWith("4"); }
 public static String incompatibleMessage(PluginDescriptor descriptor, String version) { return descriptor == null ? "Plugin is not registered in the trusted catalog" : "Plugin version " + version + " is incompatible with the supported contract " + descriptor.version(); }
 public static void validate(String id, String version, String digest) { if(blank(id) || !id.matches("[a-z][a-z0-9-]{2,119}") || blank(version) || blank(digest)) throw new PluginValidationException("pluginId, version and artifactDigest are required"); }
 public static boolean tenantCapability(boolean allowed, String reason, boolean scope) { return allowed && (!"LEGACY_SCOPE".equals(reason) || scope); }
 private static boolean blank(String value) { return value == null || value.isBlank(); }
}
