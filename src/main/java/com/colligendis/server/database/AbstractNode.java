package com.colligendis.server.database;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.util.StringUtils;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public abstract class AbstractNode {

    protected String uuid;
    protected ZonedDateTime createdAt;
    protected String createdBy;
    protected ZonedDateTime updatedAt;
    protected String updatedBy;
    protected ZonedDateTime deletedAt;
    protected String deletedBy;

    public String getLabel() {
        Field[] fields = this.getClass().getDeclaredFields();

        for (Field field : fields) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) && field.getName().equals("LABEL")) {
                try {
                    return (String) field.get(this);
                } catch (IllegalAccessException e) {
                    log.error("Failed to get label from field: {}", field.getName(), e);
                    throw new RuntimeException("Failed to get label from field: " + field.getName());
                }
            }
        }
        throw new RuntimeException("Failed to get label from class: " + this.getClass().getName());
    }

    public Map<String, Object> getPropertiesMap() {
        HashMap<String, Object> properties = new HashMap<>();

        // Get all declared fields from this class
        Field[] fields = this.getClass().getDeclaredFields();

        for (Field field : fields) {
            // Skip static and constant fields, and relationships
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            } else if (field.getType().equals(Relationship.class)) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object value = field.get(this);

                // Add to map only if value is not null
                if (value != null) {
                    // For Strings, also check if not empty
                    if (value instanceof String) {
                        if (StringUtils.hasText((String) value)) {
                            properties.put(field.getName(), value);
                        }
                    } else {
                        properties.put(field.getName(), value);
                    }
                }
            } catch (IllegalAccessException e) {
                // Skip fields that cannot be accessed
                continue;
            }
        }

        return properties;
    }

    public String getPropertiesQuery() {
        StringBuilder query = new StringBuilder();

        // Get all declared fields from this class
        Field[] fields = this.getClass().getDeclaredFields();

        for (Field field : fields) {
            // Skip static and constant fields, and relationships
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            } else if (field.getType().equals(Relationship.class)) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object value = field.get(this);

                // Add to query only if value is not null
                if (value != null) {
                    // For Strings, also check if not empty
                    if (value instanceof String) {
                        if (StringUtils.hasText((String) value)) {
                            query.append(", ").append(field.getName()).append(": $").append(field.getName());
                        }
                    } else {
                        query.append(", ").append(field.getName()).append(": $").append(field.getName());
                    }
                }
            } catch (IllegalAccessException e) {
                // Skip fields that cannot be accessed
                continue;
            }
        }

        return query.toString();
    }

    public static <T extends AbstractNode> T fromPropertiesMap(Class<T> clazz, Map<String, Object> props) {
        try {
            // Create a new instance of the specified class
            T instance = clazz.getDeclaredConstructor().newInstance();

            // Get all fields including inherited ones from parent classes
            Class<?> currentClass = clazz;
            while (currentClass != null && currentClass != Object.class) {
                Field[] fields = currentClass.getDeclaredFields();

                for (Field field : fields) {
                    // Skip static and constant fields
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        continue;
                    } else if (java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
                        continue;
                    }

                    try {
                        field.setAccessible(true);
                        Object value = props.get(field.getName());

                        if (value != null) {
                            Object convertedValue = convertValueForField(field, value);
                            if (convertedValue != null) {
                                field.set(instance, convertedValue);
                            }
                        }
                    } catch (IllegalAccessException e) {
                        // Skip fields that cannot be accessed
                        continue;
                    }
                }

                // Move to parent class
                currentClass = currentClass.getSuperclass();
            }

            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance from properties map", e);
        }
    }

    private static Object convertValueForField(Field field, Object value) {
        Class<?> targetType = field.getType();

        if (targetType == String.class) {
            return value.toString();
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            return (Boolean) value;
        } else if (targetType == ZonedDateTime.class) {
            return (ZonedDateTime) value;
        } else if (Collection.class.isAssignableFrom(targetType) && value instanceof Iterable<?>) {
            return copyIntoCollection(targetType, (Iterable<?>) value);
        } else if (!targetType.isAssignableFrom(value.getClass())) {
            // Fallback: try to convert simple numeric types if possible
            if (Number.class.isAssignableFrom(targetType) && value instanceof Number) {
                Number number = (Number) value;
                if (targetType == Integer.class || targetType == int.class) {
                    return number.intValue();
                } else if (targetType == Long.class || targetType == long.class) {
                    return number.longValue();
                } else if (targetType == Double.class || targetType == double.class) {
                    return number.doubleValue();
                } else if (targetType == Float.class || targetType == float.class) {
                    return number.floatValue();
                } else if (targetType == Short.class || targetType == short.class) {
                    return number.shortValue();
                } else if (targetType == Byte.class || targetType == byte.class) {
                    return number.byteValue();
                }
            }
        }

        return value;
    }

    private static Collection<?> copyIntoCollection(Class<?> targetType, Iterable<?> source) {
        Collection<Object> collection = instantiateCollection(targetType);
        for (Object item : source) {
            collection.add(item);
        }
        return collection;
    }

    private static Collection<Object> instantiateCollection(Class<?> targetType) {
        if (!targetType.isInterface() && !java.lang.reflect.Modifier.isAbstract(targetType.getModifiers())) {
            try {
                @SuppressWarnings("unchecked")
                Collection<Object> concreteCollection = (Collection<Object>) targetType.getDeclaredConstructor()
                        .newInstance();
                return concreteCollection;
            } catch (Exception ignored) {
                // fallback below
            }
        }
        return new ArrayList<>();
    }

}
