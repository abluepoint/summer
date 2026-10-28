package com.abluepoint.summer.sample.configuration.domain;

import com.abluepoint.summer.common.error.api.CommonErrorCode;
import com.abluepoint.summer.common.error.api.SummerException;
import java.util.List;
import java.util.Set;

public final class ConfigurationOverrideRules {
    private ConfigurationOverrideRules() {}
    public static List<String> precedence(String organization) { return organization == null ? List.of("_tenant") : List.of(organization,"_tenant"); }
    public static boolean readable(boolean publicReadable, boolean enabled) { return publicReadable && enabled; }
    public static void validateSave(Object value,String organization) { if(value==null||organization==null||!organization.matches("[a-zA-Z0-9_-]{1,64}")) throw invalid(); }
    public static void expected(long current,Long expected) { if(expected==null||expected!=current) throw conflict(); }
    public static String saveState(boolean draft,boolean enabled) { return draft?"DRAFT":enabled?"PUBLISHED":"DISABLED"; }
    public static void action(String action) { if(!Set.of("publish","disable","rollback").contains(action)) throw invalid(); }
    public static void requirePublished(Long revision) { if(revision==null) throw conflict(); }
    public static void target(String action,String state) { if("publish".equals(action)?!"DRAFT".equals(state):"DRAFT".equals(state)) throw conflict(); }
    public static void publishEnabled(String action,boolean enabled) { if("publish".equals(action)&&!enabled) throw invalid(); }
    public static String transitionState(String action) { return switch(action){case "publish"->"PUBLISHED";case "disable"->"DISABLED";default->"ROLLED_BACK";}; }
    private static SummerException invalid() { return new SummerException(CommonErrorCode.INVALID_ARGUMENT); }
    private static SummerException conflict() { return new SummerException(CommonErrorCode.CONFLICT); }
}
