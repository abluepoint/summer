package com.abluepoint.summer.mvc.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("summer.mvc")
public class SummerMvcProperties {

    private boolean enabled = true;
    private boolean wrapResponses = true;
    private final FieldValidation fieldValidation = new FieldValidation();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isWrapResponses() {
        return wrapResponses;
    }

    public void setWrapResponses(boolean wrapResponses) {
        this.wrapResponses = wrapResponses;
    }

    public FieldValidation getFieldValidation() {
        return fieldValidation;
    }

    public static class FieldValidation {

        private boolean enabled = true;
        private int maxListSize = 100;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxListSize() {
            return maxListSize;
        }

        public void setMaxListSize(int maxListSize) {
            if (maxListSize < 1) {
                throw new IllegalArgumentException("summer.mvc.field-validation.max-list-size must be positive");
            }
            this.maxListSize = maxListSize;
        }
    }
}
