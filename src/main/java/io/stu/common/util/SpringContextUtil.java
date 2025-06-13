package io.stu.common.util;

import jakarta.annotation.Nonnull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class SpringContextUtil implements ApplicationContextAware {

    private static ApplicationContext context;

    public static ApplicationContext getApplicationContext() {
        return context;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    @Nonnull
    public static <T> T getBean(Class<T> clazz) {
        ApplicationContext springAppCtx = getApplicationContext();
        if (springAppCtx == null) {
            throw new IllegalStateException();
        } else {
            return springAppCtx.getBean(clazz);
        }
    }

    public static void publishEvent(@Nonnull Object event) {
        getApplicationContext().publishEvent(event);
    }

    public static boolean isDevEnv() {
        return onProfile("dev");
    }

    public static boolean onProfile(String profile) {
        return Arrays.stream(context.getEnvironment().getActiveProfiles())
            .anyMatch(activeProfile -> activeProfile.equalsIgnoreCase(profile));

    }

    public static String getEnvProperty(String key) {
        return getApplicationContext().getEnvironment().getProperty(key);
    }
}
