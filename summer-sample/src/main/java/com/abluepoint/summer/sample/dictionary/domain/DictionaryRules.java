package com.abluepoint.summer.sample.dictionary.domain;

import com.abluepoint.summer.common.error.api.SummerException;
import java.util.List;

public final class DictionaryRules {
    private DictionaryRules() {}
    public static void code(String code) { if(code==null||!code.matches("[a-z][a-z0-9-]{1,78}[a-z0-9]")) throw invalid("code"); }
    public static void item(String value,String label,Integer order) { if(value==null||!value.matches("[a-z0-9][a-z0-9._-]{0,78}[a-z0-9]")) throw invalid("value"); itemChange(label,order); }
    public static void itemChange(String label,Integer order) { if(label==null||label.isBlank()||order==null||order<0) throw invalid("item"); }
    public static boolean validOverrideItem(String value,String label,int order,boolean allowed) { return value!=null&&allowed&&label!=null&&!label.isBlank()&&label.length()<=200&&order>=0; }
    public static long currentVersion(Long version) { return version==null?0:version; }
    public static void expected(Long expected,long current) { if(expected==null||expected!=current) throw new SummerException(DictionaryErrorCode.VERSION_CONFLICT); }
    public static void disable(Long version,boolean published) { if(version==null||!published) throw invalidState("Dictionary is not published"); }
    public static void rollback(Long target,long current) { if(target==null||target<=0||target==current) throw invalidState("Rollback target is not a previous version"); }
    public static boolean readable(boolean enabled,boolean publicReadable,boolean published) { return enabled&&publicReadable&&published; }
    private static SummerException invalid(String field) { return new SummerException(DictionaryErrorCode.INVALID,List.of(new SummerException.Violation(field,"is invalid"))); }
    private static SummerException invalidState(String message) { return new SummerException(DictionaryErrorCode.INVALID_STATE,List.of(new SummerException.Violation("state",message))); }
}
