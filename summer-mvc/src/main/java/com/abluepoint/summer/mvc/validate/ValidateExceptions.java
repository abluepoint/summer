package com.abluepoint.summer.mvc.validate;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:ValidateExceptions.java
 * Date:2020-03-13 16:25:13
 */

import com.abluepoint.summer.common.exception.MessageSupport;
import com.abluepoint.summer.common.exception.SummerException;

import java.util.ArrayList;
import java.util.List;

public class ValidateExceptions extends SummerException {

    private List<ValidateException> errors;

    public ValidateExceptions() {
        super("validate.errors");
        this.errors = new ArrayList<>();
    }

    public boolean isEmpty() {
        return errors.isEmpty();
    }

    public boolean add(ValidateException e) {
        return errors.add(e);
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(super.getMessage());
        sb.append("\r\n");
        for (ValidateException ve:errors){
            MessageSupport.exportMessage(ve, sb);
            sb.append("\r\n");
        }
        return sb.toString();
    }

    public List<ValidateException> getErrors() {
        return errors;
    }
}
