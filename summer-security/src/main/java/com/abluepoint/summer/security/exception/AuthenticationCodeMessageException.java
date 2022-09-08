package com.abluepoint.summer.security.exception;

import com.abluepoint.summer.common.exception.CodeMessage;
import com.abluepoint.summer.common.exception.MessageSupport;
import com.abluepoint.summer.common.exception.Messageable;
import org.springframework.security.core.AuthenticationException;

public class AuthenticationCodeMessageException extends AuthenticationException implements Messageable {

    private MessageSupport messageSupport = new MessageSupport();

    public AuthenticationCodeMessageException(CodeMessage codeMessage, Throwable cause) {
        super(codeMessage.getMessageKey(), cause);
        messageSupport.setFromCodeMessage(codeMessage);
    }

    public AuthenticationCodeMessageException(CodeMessage codeMessage) {
        super(codeMessage.getMessageKey());
        messageSupport.setFromCodeMessage(codeMessage);
    }

    public AuthenticationCodeMessageException(CodeMessage codeMessage, Throwable cause, Object... args) {
        super(codeMessage.getMessageKey(), cause);
        messageSupport.setFromCodeMessage(codeMessage);
    }

    public AuthenticationCodeMessageException(CodeMessage codeMessage, Object... args) {
        super(codeMessage.getMessageKey());
        messageSupport.setFromCodeMessage(codeMessage);
    }

    @Override
    public Object[] getArgs() {
        return messageSupport.getArgs();
    }

    @Override
    public Integer getCode() {
        return messageSupport.getCode();
    }

    @Override
    public String getMessageKey() {
        return messageSupport.getMessageKey();
    }
}
