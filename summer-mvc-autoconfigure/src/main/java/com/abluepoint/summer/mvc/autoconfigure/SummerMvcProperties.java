package com.abluepoint.summer.mvc.autoconfigure;

import com.abluepoint.summer.mvc.filter.CachedBodyHttpServletRequest;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties("summer.mvc")
public class SummerMvcProperties {

    private boolean enabled = true;
    private boolean wrapResponses = true;
    private final RequestBody requestBody = new RequestBody();
    private final RequestLogging requestLogging = new RequestLogging();
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

    public RequestLogging getRequestLogging() {
        return requestLogging;
    }

    public RequestBody getRequestBody() {
        return requestBody;
    }

    public static class RequestBody {
        private int maxBytes = CachedBodyHttpServletRequest.DEFAULT_MAX_BODY_BYTES;

        public int getMaxBytes() {
            return maxBytes;
        }

        public void setMaxBytes(int maxBytes) {
            if (maxBytes < 1) {
                throw new IllegalArgumentException("summer.mvc.request-body.max-bytes must be positive");
            }
            this.maxBytes = maxBytes;
        }
    }

    public static class RequestLogging {

        private boolean enabled = true;
        private final Body body = new Body();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Body getBody() {
            return body;
        }

        public static class Body {

            private boolean enabled = false;
            private int maxPreviewLength = 2048;
            private Set<String> redactedFields = new LinkedHashSet<>(Set.of(
                    "password", "passwd", "secret", "token", "access_token", "refresh_token",
                    "client_secret", "authorization", "cookie"
            ));

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public int getMaxPreviewLength() {
                return maxPreviewLength;
            }

            public void setMaxPreviewLength(int maxPreviewLength) {
                if (maxPreviewLength < 1) {
                    throw new IllegalArgumentException(
                            "summer.mvc.request-logging.body.max-preview-length must be positive"
                    );
                }
                this.maxPreviewLength = maxPreviewLength;
            }

            public Set<String> getRedactedFields() {
                return redactedFields;
            }

            public void setRedactedFields(Set<String> redactedFields) {
                if (redactedFields == null || redactedFields.isEmpty()) {
                    throw new IllegalArgumentException(
                            "summer.mvc.request-logging.body.redacted-fields must not be empty"
                    );
                }
                this.redactedFields = new LinkedHashSet<>(redactedFields);
            }
        }
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
