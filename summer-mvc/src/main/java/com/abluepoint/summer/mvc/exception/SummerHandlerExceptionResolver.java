package com.abluepoint.summer.mvc.exception;

import com.abluepoint.summer.common.exception.Messageable;
import com.abluepoint.summer.mvc.domain.Results;
import com.abluepoint.summer.mvc.validate.ValidateException;
import com.abluepoint.summer.mvc.validate.ValidateExceptions;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:SummerHandlerExceptionResolver.java
 * Date:2020-03-23 17:32:23
 */

public class SummerHandlerExceptionResolver implements HandlerExceptionResolver, MessageSourceAware {

    private MessageSource messageSource;

    private CodeMapping codeMapping;

    public SummerHandlerExceptionResolver(CodeMapping codeMapping) {
        this.codeMapping = codeMapping;
    }

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {

        if (ex instanceof ValidateExceptions) {
            ValidateExceptions exs = ((ValidateExceptions) ex);
            List<ValidateException> list = exs.getErrors();
            ValidateException validateEx = null;
            List<Map<String, Object>> errorList = new ArrayList<>(list.size());
            Map<String, Object> temp = null;
            for (int i = 0; i < list.size(); i++) {
                validateEx = list.get(i);
                temp = new HashMap<>(2);
                temp.put("name", validateEx.getName());
                temp.put("errorMsg", getMessage(request,validateEx));
                errorList.add(temp);
            }
            Map<String, Object> data = new HashMap<>(1);
            data.put("errors", errorList);
            return Results.failView(codeMapping.getCode(exs), getMessage(request,exs), data);

        } else if (ex instanceof Messageable) {
            Messageable message = (Messageable) ex;
            return Results.failView(codeMapping.getCode(ex), getMessage(request,message));
        } else {
            return Results.failView(codeMapping.getCode(ex), messageSource.getMessage("system.exception.undefined",null, null));
        }


    }

    protected String getMessage(HttpServletRequest request,Messageable validateEx) {
        return messageSource.getMessage(validateEx.getMessageKey(),validateEx.getArgs(), null);
    }

    @Override
    public void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource;
    }
}
