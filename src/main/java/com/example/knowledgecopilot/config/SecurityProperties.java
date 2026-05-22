package com.example.knowledgecopilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {
    private boolean enabled = true;
    private final RateLimit rateLimit = new RateLimit();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public static class RateLimit {
        private boolean enabled = true;
        private int windowSeconds = 60;
        private int askLimitPerWindow = 120;
        private int feedbackLimitPerWindow = 240;
        private int adminLimitPerWindow = 90;
        private int defaultApiLimitPerWindow = 300;
        private int maxTrackedKeys = 50_000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }

        public int getAskLimitPerWindow() {
            return askLimitPerWindow;
        }

        public void setAskLimitPerWindow(int askLimitPerWindow) {
            this.askLimitPerWindow = askLimitPerWindow;
        }

        public int getFeedbackLimitPerWindow() {
            return feedbackLimitPerWindow;
        }

        public void setFeedbackLimitPerWindow(int feedbackLimitPerWindow) {
            this.feedbackLimitPerWindow = feedbackLimitPerWindow;
        }

        public int getAdminLimitPerWindow() {
            return adminLimitPerWindow;
        }

        public void setAdminLimitPerWindow(int adminLimitPerWindow) {
            this.adminLimitPerWindow = adminLimitPerWindow;
        }

        public int getDefaultApiLimitPerWindow() {
            return defaultApiLimitPerWindow;
        }

        public void setDefaultApiLimitPerWindow(int defaultApiLimitPerWindow) {
            this.defaultApiLimitPerWindow = defaultApiLimitPerWindow;
        }

        public int getMaxTrackedKeys() {
            return maxTrackedKeys;
        }

        public void setMaxTrackedKeys(int maxTrackedKeys) {
            this.maxTrackedKeys = maxTrackedKeys;
        }
    }
}
