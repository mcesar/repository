package repository;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Transient;

import org.apache.commons.beanutils.PropertyUtils;

public class RepositoryUtil {

	private static Field idFieldFromClass(Class<?> clazz) {
		if (clazz == null) return null;
		for (Field f : clazz.getDeclaredFields()) {
			if (f.isAnnotationPresent(Id.class)) {
				f.setAccessible(true);
				return f;
			}
		}
		return idFieldFromClass(clazz.getSuperclass());
	}

	public static <T> Field idField(T o) {
		for (Field f : o.getClass().getDeclaredFields()) {
			if (f.isAnnotationPresent(Id.class)) {
				f.setAccessible(true);
				return f;
			}
		}
		return idFieldFromClass(o.getClass());
	}

	public static <T> Field fieldWithName(T o, String name) {
		for (Field f : o.getClass().getDeclaredFields()) {
			if (f.getName().equals(name)) {
				f.setAccessible(true);
				return f;
			}
		}
		return null;
	}

	public static <T> Integer idValue(T o) {
		try {
			Field idField = idField(o);
			if (idField == null) throw new IllegalArgumentException("Id property not found");
			return (Integer) idField.get(o);
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public static <T> void idValue(T o, Integer id) {
		try {
			idField(o).set(o, id);
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public static void nulifyProperties(Object obj) {
		PropertyDescriptor[] descriptors = PropertyUtils.getPropertyDescriptors(obj.getClass());
		for (PropertyDescriptor pd : descriptors) {
			if (pd.getReadMethod() == null || pd.getWriteMethod() == null) continue;
			if (pd.getReadMethod().isAnnotationPresent(Transient.class)) continue;
			try {
				Object value = pd.getReadMethod().invoke(obj);
				if (value != null) {
					if (value.getClass().isAnnotationPresent(Entity.class)) {
						nulifyProperties(value);
					} else if (!pd.getPropertyType().isPrimitive()) {
						pd.getWriteMethod().invoke(obj, (Object) null);
					}
				}
			} catch (Exception e) {
				throw new IllegalArgumentException(e);
			}
		}
	}
}