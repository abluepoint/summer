package com.abluepoint.summer.sample.dictionary.infrastructure;

import com.abluepoint.summer.sample.dictionary.domain.DictionaryRules;

import com.abluepoint.summer.sample.dictionary.application.view.DictionaryItemView;
import com.abluepoint.summer.sample.dictionary.infrastructure.persistence.DictionaryItemRepository;
import com.abluepoint.summer.sample.dictionary.infrastructure.persistence.DictionaryTypeRepository;
import com.abluepoint.summer.sample.configuration.application.port.ConfigurationValueValidator;
import com.abluepoint.summer.sample.authorization.application.DataScopeService;
import com.abluepoint.summer.common.error.api.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
@Component
public class DictionaryOverrideValidator implements ConfigurationValueValidator {
    private final DictionaryTypeRepository types; private final DictionaryItemRepository items;
    private final DataScopeService scopes; private final ObjectMapper mapper;
    public DictionaryOverrideValidator(DictionaryTypeRepository types,DictionaryItemRepository items,DataScopeService scopes,ObjectMapper mapper) {
        this.types=types;this.items=items;this.scopes=scopes;this.mapper=mapper;
    }
    public String resource() { return "dictionaries"; }
    public void requireBase(String tenant,String key,Authentication auth) {
        types.findScoped(key,scopes.filter(tenant,resource(),auth)).orElseThrow(()->new SummerException(CommonErrorCode.NOT_FOUND));
    }
    public void validate(String tenant,String key,Object value,Authentication auth) {
        requireBase(tenant,key,auth);
        if(!(value instanceof Collection<?> values)||values.size()>200) throw invalid();
        List<DictionaryItemView> options;
        try { options=mapper.convertValue(value,mapper.getTypeFactory().constructCollectionType(List.class,DictionaryItemView.class)); }
        catch(RuntimeException e) { throw invalid(); }
        Set<String> allowed=new HashSet<>();items.findScoped(key,scopes.filter(tenant,resource(),auth)).forEach(i->allowed.add(i.value()));
        if(options.stream().anyMatch(i->i==null||!DictionaryRules.validOverrideItem(i.value(),i.label(),i.sortOrder(),allowed.contains(i.value())))
            ||options.stream().map(DictionaryItemView::value).distinct().count()!=options.size()) throw invalid();
    }
    private static SummerException invalid(){return new SummerException(CommonErrorCode.INVALID_ARGUMENT);}
}
