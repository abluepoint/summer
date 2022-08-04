package com.abluepoint.summer.mvc.exception;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:GlobalHandlerExceptionResolver.java
 * Date:2020-03-16 15:48:16
 */

import com.abluepoint.summer.mvc.interceptor.RequestLoggingSupport;
import com.abluepoint.summer.mvc.validate.ValidateExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Set;

public class GlobalHandlerExceptionResolver extends SummerHandlerExceptionResolver {

    private RequestLoggingSupport requestLoggingSupport;
    private static final Logger logger = LoggerFactory.getLogger(GlobalHandlerExceptionResolver.class);

    public GlobalHandlerExceptionResolver(RequestLoggingSupport requestLoggingSupport) {
        this.requestLoggingSupport = requestLoggingSupport;
    }

    // @ExceptionHandler(MissingServletRequestParameterException.class)
    // @ResponseStatus(HttpStatus.BAD_REQUEST)
    // public R handleError(MissingServletRequestParameterException e) {
    //     log.warn("缺少请求参数", e.getMessage());
    //     String message = String.format("缺少必要的请求参数: %s", e.getParameterName());
    //     return R.fail(ResultCode.PARAM_MISS, message);
    // }
    //
    // @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    // @ResponseStatus(HttpStatus.BAD_REQUEST)
    // public R handleError(MethodArgumentTypeMismatchException e) {
    //     log.warn("请求参数格式错误", e.getMessage());
    //     String message = String.format("请求参数格式错误: %s", e.getName());
    //     return R.fail(ResultCode.PARAM_TYPE_ERROR, message);
    // }
    //
    // @ExceptionHandler(MethodArgumentNotValidException.class)
    // @ResponseStatus(HttpStatus.BAD_REQUEST)
    // public R handleError(MethodArgumentNotValidException e) {
    //     log.warn("参数验证失败", e.getMessage());
    //     return handleError(e.getBindingResult());
    // }
    //
    // @ExceptionHandler(BindException.class)
    // @ResponseStatus(HttpStatus.BAD_REQUEST)
    // public R handleError(BindException e) {
    //     log.warn("参数绑定失败", e.getMessage());
    //     return handleError(e.getBindingResult());
    // }
    //
    // private R handleError(BindingResult result) {
    //     FieldError error = result.getFieldError();
    //     String message = String.format("%s:%s", error.getField(), error.getDefaultMessage());
    //     return R.fail(ResultCode.PARAM_BIND_ERROR, message);
    // }
    //
    // @ExceptionHandler(ConstraintViolationException.class)
    // @ResponseStatus(HttpStatus.BAD_REQUEST)
    // public R handleError(ConstraintViolationException e) {
    //     log.warn("参数验证失败", e.getMessage());
    //     Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
    //     ConstraintViolation<?> violation = violations.iterator().next();
    //     String path = ((PathImpl) violation.getPropertyPath()).getLeafNode().getName();
    //     String message = String.format("%s:%s", path, violation.getMessage());
    //     return R.fail(ResultCode.PARAM_VALID_ERROR, message);
    // }
    //
    // @ExceptionHandler(NoHandlerFoundException.class)
    // @ResponseStatus(HttpStatus.NOT_FOUND)
    // public R handleError(NoHandlerFoundException e) {
    //     log.error("404没找到请求:{}", e.getMessage());
    //     return R.fail(ResultCode.NOT_FOUND, e.getMessage());
    // }
    //
    // @ExceptionHandler(HttpMessageNotReadableException.class)
    // @ResponseStatus(HttpStatus.BAD_REQUEST)
    // public R handleError(HttpMessageNotReadableException e) {
    //     log.error("消息不能读取:{}", e.getMessage());
    //     return R.fail(ResultCode.MSG_NOT_READABLE, e.getMessage());
    // }
    //
    // @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    // @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    // public R handleError(HttpRequestMethodNotSupportedException e) {
    //     log.error("不支持当前请求方法:{}", e.getMessage());
    //     return R.fail(ResultCode.METHOD_NOT_SUPPORTED, e.getMessage());
    // }
    //
    // @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    // @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    // public R handleError(HttpMediaTypeNotSupportedException e) {
    //     log.error("不支持当前媒体类型:{}", e.getMessage());
    //     return R.fail(ResultCode.MEDIA_TYPE_NOT_SUPPORTED, e.getMessage());
    // }
    //
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    // @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    // public R handleError(HttpMediaTypeNotAcceptableException e) {
    //     String message = e.getMessage() + " " + StringUtil.join(e.getSupportedMediaTypes());
    //     log.error("不接受的媒体类型:{}", message);
    //     return R.fail(ResultCode.MEDIA_TYPE_NOT_SUPPORTED, message);
    // }

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String afterMsg = requestLoggingSupport.getAfterMessage(request);

        if (ex instanceof MissingServletRequestParameterException) {

        }else if(ex instanceof MethodArgumentTypeMismatchException){

        }

        if (logger.isDebugEnabled()) {
            logger.debug(afterMsg + " failed", ex);
        }
        return super.resolveException(request, response, handler, ex);
    }
}