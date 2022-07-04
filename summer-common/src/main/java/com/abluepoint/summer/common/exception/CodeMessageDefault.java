package com.abluepoint.summer.common.exception;

public final class CodeMessageDefault implements CodeMessage{

    private final Integer code;
    private final String messageKey;

    public CodeMessageDefault(Integer code, String messageKey) {
        this.code = code;
        this.messageKey = messageKey;
    }

    public static CodeMessageDefault newInstance(Integer code, String messageKey) {
        return new CodeMessageDefault(code, messageKey);
    }

    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getMessageKey() {
        return messageKey;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof CodeMessageDefault)) return false;
        final CodeMessageDefault other = (CodeMessageDefault) o;
        final Object this$code = this.getCode();
        final Object other$code = other.getCode();
        if (this$code == null ? other$code != null : !this$code.equals(other$code)) return false;
        final Object this$messageKey = this.getMessageKey();
        final Object other$messageKey = other.getMessageKey();
        if (this$messageKey == null ? other$messageKey != null : !this$messageKey.equals(other$messageKey)) return false;
        return true;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $code = this.getCode();
        result = result * PRIME + ($code == null ? 43 : $code.hashCode());
        final Object $messageKey = this.getMessageKey();
        result = result * PRIME + ($messageKey == null ? 43 : $messageKey.hashCode());
        return result;
    }

    public String toString() {
        return "CodeMessageDefault(code=" + this.getCode() + ", messageKey=" + this.getMessageKey() + ")";
    }
}
