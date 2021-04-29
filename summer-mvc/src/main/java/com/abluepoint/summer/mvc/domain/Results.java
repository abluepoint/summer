package com.abluepoint.summer.mvc.domain;

import org.slf4j.Logger;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;

public class Results {

    public static final int SUCCESS_CODE = 0;
    public static final String SUCCESS_MESSAGE = "OK";
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(Results.class);


    public static Result<?> success() {
        return success(null);
    }

    public static Result<?> success(Object data) {
        return success(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    private static Result<?> success(int code, String msg, Object data) {
        return new Result<>(code, msg, data);
    }

    public static Result<?> fail(int code, String msg, Object data) {
        log.debug("build fail result,code: {}, msg: {}", code, msg);
        return new Result<>(code, msg, data);
    }

    public static Result<?> fail(int code, String msg) {
        return fail(code, msg, null);
    }

    public static ModelAndView failView(int code, String msg, Object data) {
        ModelAndView mav = new ModelAndView(new MappingJackson2JsonView());
        log.debug("build fail ModelAndView,code: {}, msg: {}", code, msg);
        mav.addObject("code", code);
        mav.addObject("msg", msg);
        mav.addObject("data", data);
        return mav;
    }

    public static ModelAndView failView(int code, String msg) {
        ModelAndView mav = new ModelAndView(new MappingJackson2JsonView());
        log.debug("build fail ModelAndView,code: {}, msg: {}", code, msg);
        mav.addObject("code", code);
        mav.addObject("msg", msg);
        return mav;
    }


}
