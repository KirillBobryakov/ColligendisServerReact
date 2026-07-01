package com.colligendis.server.database;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.util.StringUtils;

import com.colligendis.server.database.numista.model.NType.DemonetizedStatus;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

		Class<?> currentClass = this.getClass();
		while (currentClass != null && currentClass != Object.class) {
			appendDeclaredFieldProperties(currentClass.getDeclaredFields(), properties);
			currentClass = currentClass.getSuperclass();
		}

		return properties;
	}

	private void appendDeclaredFieldProperties(Field[] fields, Map<String, Object> properties) {
		for (Field field : fields) {
			if (shouldSkipPropertyField(field)) {
				continue;
			}

			try {
				field.setAccessible(true);
				Object value = field.get(this);

				if (value != null) {
					if (value instanceof String) {
						if (StringUtils.hasText((String) value)) {
							properties.put(field.getName(), value);
						}
					} else if (value instanceof Enum<?>) {
						properties.put(field.getName(), ((Enum<?>) value).name());
					} else {
						properties.put(field.getName(), value);
					}
				}
			} catch (IllegalAccessException e) {
				continue;
			}
		}
	}

	public String getPropertiesQuery() {
		StringBuilder query = new StringBuilder();

		Class<?> currentClass = this.getClass();
		while (currentClass != null && currentClass != Object.class) {
			appendDeclaredFieldQuery(currentClass.getDeclaredFields(), query);
			currentClass = currentClass.getSuperclass();
		}

		return query.toString();
	}

	private void appendDeclaredFieldQuery(Field[] fields, StringBuilder query) {
		for (Field field : fields) {
			if (shouldSkipPropertyField(field)) {
				continue;
			}

			try {
				field.setAccessible(true);
				Object value = field.get(this);

				if (value != null) {
					if (value instanceof String) {
						if (StringUtils.hasText((String) value)) {
							query.append(", ").append(field.getName()).append(": $").append(field.getName());
						}
					} else {
						query.append(", ").append(field.getName()).append(": $").append(field.getName());
					}
				}
			} catch (IllegalAccessException e) {
				continue;
			}
		}
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
			if (value instanceof Boolean b) {
				return b;
			}
			if (value instanceof Number n) {
				return n.intValue() != 0;
			}
			return Boolean.parseBoolean(value.toString());
		} else if (targetType == ZonedDateTime.class) {
			return convertToZonedDateTime(value);
		} else if (targetType == DemonetizedStatus.class) {
			return DemonetizedStatus.fromCode(value.toString());
		} else if (targetType.isEnum() && value != null) {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			Object enumValue = Enum.valueOf((Class<Enum>) targetType, value.toString());
			return enumValue;
		} else if (Collection.class.isAssignableFrom(targetType) && value instanceof Iterable<?>) {
			return copyIntoCollection(targetType, (Iterable<?>) value);
		} else if (value != null && !targetType.isAssignableFrom(value.getClass())) {
			// Fallback: try to convert simple numeric types if possible
			if (isNumericType(targetType) && value instanceof Number) {
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

	private static boolean isNumericType(Class<?> targetType) {
		return targetType == Integer.class || targetType == int.class
				|| targetType == Long.class || targetType == long.class
				|| targetType == Double.class || targetType == double.class
				|| targetType == Float.class || targetType == float.class
				|| targetType == Short.class || targetType == short.class
				|| targetType == Byte.class || targetType == byte.class;
	}

	private static ZonedDateTime convertToZonedDateTime(Object value) {
		if (value instanceof ZonedDateTime zdt) {
			return zdt;
		}
		if (value instanceof OffsetDateTime odt) {
			return odt.toZonedDateTime();
		}
		if (value instanceof LocalDateTime ldt) {
			return ldt.atZone(ZoneId.systemDefault());
		}
		if (value instanceof Instant instant) {
			return instant.atZone(ZoneId.systemDefault());
		}
		if (value instanceof LocalDate ld) {
			return ld.atStartOfDay(ZoneId.systemDefault());
		}
		return (ZonedDateTime) value;
	}

	private static Collection<?> copyIntoCollection(Class<?> targetType, Iterable<?> source) {
		Collection<Object> collection = instantiateCollection(targetType);
		for (Object item : source) {
			collection.add(item);
		}
		return collection;
	}

	/**
	 * Relationship targets and nested nodes are linked via Cypher, not stored as
	 * node properties (Neo4j cannot serialize {@link AbstractNode} values).
	 */
	private static boolean shouldSkipPropertyField(Field field) {
		if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
			return true;
		}
		if (java.lang.reflect.Modifier.isTransient(field.getModifiers())) {
			return true;
		}
		Class<?> type = field.getType();
		if (type.equals(Relationship.class)) {
			return true;
		}
		if (AbstractNode.class.isAssignableFrom(type)) {
			return true;
		}
		if (Mono.class.isAssignableFrom(type) || Flux.class.isAssignableFrom(type)) {
			return true;
		}
		if (Collection.class.isAssignableFrom(type) && isCollectionOfNonPropertyElements(field)) {
			return true;
		}
		return false;
	}

	private static boolean isCollectionOfNonPropertyElements(Field field) {
		Type generic = field.getGenericType();
		if (!(generic instanceof ParameterizedType parameterized)) {
			return false;
		}
		Type[] args = parameterized.getActualTypeArguments();
		if (args.length != 1 || !(args[0] instanceof Class<?> elementType)) {
			return false;
		}
		return AbstractNode.class.isAssignableFrom(elementType)
				|| Mono.class.isAssignableFrom(elementType)
				|| Flux.class.isAssignableFrom(elementType);
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
