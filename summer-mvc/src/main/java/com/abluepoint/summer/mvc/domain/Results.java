package com.abluepoint.summer.mvc.domain;

import com.abluepoint.summer.common.exception.CodeMessageEnum;
import org.slf4j.Logger;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;

public class Results {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(Results.class);

    public static <T> Result<T> success() {
        return success(CodeMessageEnum.OK.getMessageKey(),null);
    }

    public static <T> Result<T> success(T data) {
        return success(CodeMessageEnum.OK.getMessageKey(), data);
    }

    public static <T> Result<T> success(String msg, T data) {
        return success(CodeMessageEnum.OK.getCode(),msg,data);
    }

    public static <T> Result<T> success(int code ,String msg, T data) {
        return new Result<>(code, msg, data);
    }

    public static <T> Result<T> fail(int code, String msg, T data) {
        log.debug("response fail result,code: {}, msg: {}", code, msg);
        return new Result<>(code, msg, data);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return fail(code, msg, null);
    }

    public static ModelAndView failView(int code, String msg, Object data) {
        ModelAndView mav = new ModelAndView(new MappingJackson2JsonView());
        log.debug("response fail result,code: {}, msg: {}", code, msg);
        mav.addObject("code", code);
        mav.addObject("message", msg);
        mav.addObject("data", data);
        return mav;
    }

    public static ModelAndView failView(int code, String msg) {
        ModelAndView mav = new ModelAndView(new MappingJackson2JsonView());
        log.debug("response fail result,code: {}, msg: {}", code, msg);
        mav.addObject("code", code);
        mav.addObject("message", msg);
        return mav;
    }

}
