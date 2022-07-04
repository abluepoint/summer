package com.abluepoint.summer.mvc.exception;

import com.abluepoint.summer.common.exception.CodeMessageEnum;
import com.abluepoint.summer.common.exception.Messageable;
import com.abluepoint.summer.mvc.domain.Results;
import com.abluepoint.summer.mvc.validate.ValidateException;
import com.abluepoint.summer.mvc.validate.ValidateExceptions;
import org.slf4j.Logger;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:SummerHandlerExceptionResolver.java
 * Date:2020-03-23 17:32:23
 */

public class SummerHandlerExceptionResolver implements HandlerExceptionResolver, MessageSourceAware {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(SummerHandlerExceptionResolver.class);
    private MessageSource messageSource;

    public SummerHandlerExceptionResolver() {
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
                temp.put("errorMsg", getMessage(validateEx.getMessageKey(), validateEx.getArgs()));
                errorList.add(temp);
            }
            Map<String, Object> data = new HashMap<>(1);
            data.put("errors", errorList);

            return Results.failView(exs.getCode(), getMessage(exs.getMessageKey(), exs.getArgs()), data);

        } else if (ex instanceof Messageable) {

            Messageable message = (Messageable) ex;
            String messageKey = message.getMessageKey();

            return Results.failView(message.getCode(), getMessage(messageKey, message.getArgs()));

        } else {
            return Results.failView(CodeMessageEnum.SYSTEM_UNDEFINED_ERROR.getCode(), getMessage(CodeMessageEnum.SYSTEM_UNDEFINED_ERROR.getMessageKey(), null));
        }


    }

    protected String getMessage(String messageKey, Object[] args) {
        return messageSource.getMessage(messageKey, args, Locale.SIMPLIFIED_CHINESE);
    }

    @Override
    public void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource;
    }
}
