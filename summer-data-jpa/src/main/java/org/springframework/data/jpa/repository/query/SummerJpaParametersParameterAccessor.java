/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:SummerJpaParametersParameterAccessor.java
 * Date:2020-12-29 15:14:29
 */

package org.springframework.data.jpa.repository.query;

import org.springframework.data.repository.query.Parameter;
import org.springframework.data.repository.query.Parameters;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SummerJpaParametersParameterAccessor extends JpaParametersParameterAccessor{

    private Map<String,Object> namedParameterMap;
    /**
     * Creates a new {@link SummerJpaParametersParameterAccessor}.
     *
     * @param parameters must not be {@literal null}.
     * @param values     must not be {@literal null}.
     */
    SummerJpaParametersParameterAccessor(Parameters<?, ?> parameters, Object[] values) {
        super(parameters, values);
        initParameterMap(parameters);
    }

    /**
     * abluepoint: getNamedParameterMap
     * @return
     */
    public Map<String,Object> getNamedParameterMap(){
        if(namedParameterMap == null){
            initParameterMap(getParameters());
        }
        return namedParameterMap;
    }

    private void initParameterMap(Parameters<?, ?> parameters) {
        namedParameterMap = new HashMap<>(parameters.getNumberOfParameters());
        Iterator it = parameters.iterator();
        while (it.hasNext()) {
            Parameter p = (Parameter)it.next();
            if(p.isNamedParameter()){
                namedParameterMap.put(p.getName().get(),getValue(p));
            }
        }
    }
}
