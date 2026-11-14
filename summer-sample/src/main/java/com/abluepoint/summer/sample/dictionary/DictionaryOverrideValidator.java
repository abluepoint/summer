package com.abluepoint.summer.sample.dictionary;
import com.abluepoint.summer.sample.configuration.ConfigurationValueValidator;
import com.abluepoint.summer.common.error.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
@Component
class DictionaryOverrideValidator implements ConfigurationValueValidator {
    private final ObjectMapper mapper;
    private final EffectiveDictionaryReader effective;
    DictionaryOverrideValidator(ObjectMapper mapper,EffectiveDictionaryReader effective) {
        this.effective=effective;
        this.mapper=mapper;
    }
    public String resource() { return "dictionaries"; }
    public void requireBase(String tenant,String key,Authentication auth, com.abluepoint.summer.sample.authorization.BusinessDataScopes.Filter scope) {
        effective.read(tenant,key,true,scope);
    }
    public void validate(String tenant,String key,Object value,Authentication auth, com.abluepoint.summer.sample.authorization.BusinessDataScopes.Filter scope) {
        requireBase(tenant,key,auth,scope);
        if(!(value instanceof Collection<?> values)||values.size()>200) throw invalid();
        List<DictionaryItemView> options;
        try { options=mapper.convertValue(value,mapper.getTypeFactory().constructCollectionType(List.class,DictionaryItemView.class)); }
        catch(RuntimeException e) { throw invalid(); }
        Set<String> allowed=effective.read(tenant,key,true,scope).selectableValues();
        if(options.stream().anyMatch(i->i==null||i.value()==null||!allowed.contains(i.value())||i.label()==null||i.label().isBlank()||i.label().length()>200||i.sortOrder()<0)
            ||options.stream().map(DictionaryItemView::value).distinct().count()!=options.size()) throw invalid();
    }
    private static SummerException invalid(){return new SummerException(CommonErrorCode.INVALID_ARGUMENT);}
}
