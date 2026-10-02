package com.zeshanaslam.actionhealth.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Small reflection helpers for APIs that only exist on some server versions or plugins.
 * Lookups return null instead of throwing, so callers can try the next option.
 */
public final class Reflect {

    private Reflect() {
    }

    public static Class<?> findClass(String... names) {
        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException | LinkageError ignored) {
            }
        }

        return null;
    }

    public static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        if (type == null) return null;

        Method method;
        try {
            method = type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException | RuntimeException | LinkageError e) {
            return null;
        }

        try {
            // Implementation classes (for example CraftBukkit item metas) are often not public.
            method.setAccessible(true);
        } catch (RuntimeException ignored) {
        }

        return method;
    }

    public static Object getStaticField(Class<?> type, String name) {
        if (type == null) return null;

        try {
            Field field = type.getField(name);
            return field.get(null);
        } catch (ReflectiveOperationException | SecurityException | LinkageError e) {
            return null;
        }
    }

    /**
     * Calls a public no-argument method on the runtime class of the target.
     * Returns null if the target is null, the method does not exist or the call fails.
     */
    public static Object call(Object target, String name) {
        if (target == null) return null;

        Method method = findMethod(target.getClass(), name);
        if (method == null) return null;

        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }
}
