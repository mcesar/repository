package repository;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.Stateless;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.JoinColumn;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.Table;

import org.apache.commons.beanutils.PropertyUtils;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.proxy.HibernateProxy;

@Stateless
public class SqlRepository extends AbstractSqlRepository implements Repository {

	@PersistenceContext(unitName = "RepositoryJPA")
	protected EntityManager em;

	private boolean usingQuestionMarks = false;

	public SqlRepository() {
	}

	public SqlRepository(EntityManager em) {
		this.em = em;
	}

	public boolean isUsingQuestionMarks() {
		return this.usingQuestionMarks;
	}

	public void setUsingQuestionMarks(boolean usingQuestionMarks) {
		this.usingQuestionMarks = usingQuestionMarks;
	}

	@Override
	public <T> T add(T o) throws Exception {
		return null;
	}

	@Override
	public void remove(Object o) throws Exception {
	}

	@Override
	public void removeAll(Object o) throws Exception {
	}

	@Override
	public int removeAll(Object[] o) throws Exception {
		return 0;
	}

	@Override
	public <T> T replace(T o) throws Exception {
		return null;
	}

	@Override
	public void replace(Object o, Object where) throws Exception {
	}

	@Override
	public <T> T execute(RunnableWithResult r, Object... args) throws Exception {
		return null;
	}

	@Override
	public <T> T getAdapter(Class<T> c) {
		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	protected <T> Collection<T> tryMatching(Object[] exp) throws Exception {
		String w = where(exp);
		if (exp[0] == null) throw new IllegalArgumentException();
		String qs = queryString(exp, w);
		List<PropertyDescriptor> fetches = fetches(exp);
		Query query = createQuery(exp, qs);
		setParameters(exp, qs, query);
		Collection<T> result = runQuery(query);
		if (result == null) return result;
		if (fetches.isEmpty()) {
			clearProxies(result);
		} else {
			Collection<Object> objects = new ArrayList<Object>();
			List<String> fields = primitiveFields(exp[0].getClass());
			List<List<String>> fieldsOfFetched = new ArrayList<List<String>>();
			for (PropertyDescriptor pd : fetches) {
				fieldsOfFetched.add(primitiveFields(pd.getPropertyType()));
			}
			Class<?> clazz = exp[0].getClass();
			for (Object o : result) {
				Object[] arr = (Object[]) o;
				Object obj = exp[0].getClass().newInstance();
				objects.add(obj);
				int j = 0;
				for (int i = 0; i < fields.size(); i++) {
					setValue(obj, clazz, fields.get(i), arr[j]);
					j++;
				}
				Iterator<List<String>> it = fieldsOfFetched.iterator();
				for (PropertyDescriptor pd : fetches) {
					Object fetchedObject = pd.getPropertyType().newInstance();
					pd.getWriteMethod().invoke(obj, fetchedObject);
					List<String> fetchedFields = it.next();
					for (int i = 0; i < fetchedFields.size(); i++) {
						setValue(fetchedObject, pd.getPropertyType(), fetchedFields.get(i), arr[j]);
						j++;
					}
				}
			}
			return (Collection<T>) objects;
		}
		return result;
	}

	private void setValue(Object obj, Class<? extends Object> clazz, String fieldName, Object fieldValue)
			throws NoSuchFieldException, IllegalAccessException {
		Field f = clazz.getDeclaredField(fieldName);
		f.setAccessible(true);
		if (Integer.class.equals(f.getType()) && fieldValue != null) {
			f.set(obj, (((Number) fieldValue)).intValue());	
		} else {
			f.set(obj, fieldValue);
		}
	}

	protected Query createQuery(Object[] exp, String qs) {
		if (qs.contains("t.*")) {
			return em.createNativeQuery(qs, exp[0].getClass());
		} else {
			return em.createNativeQuery(qs);
		}
	}

	@SuppressWarnings("unchecked")
	protected <T> Collection<T> runQuery(Query query) throws Exception {
		return query.getResultList();
	}

	protected void setParameters(Object[] exp, String qs, Query query) throws Exception {
		setParameterValues(query, qs, exp, "", "");
	}

	protected String queryString(Object[] exp) throws Exception {
		return queryString(exp, where(exp));
	}

	protected String queryString(Object[] exp, String w) throws Exception {
		String select = select(exp);
		String fields;
		if (select.isEmpty()) {
			fields = "t.*";
		} else {
			fields = fieldsString(exp[0].getClass(), "t");
		}
		return "select " + fields + select + " from " + from(exp, "t_") + beginWithSpace(w);
	}

	private String fieldsString(Class<?> clazz, String prefix) {
		StringBuilder sb = new StringBuilder();
		for (String fieldName : primitiveFields(clazz)) {
			if (sb.length() > 0) sb.append(", ");
			sb.append(prefix);
			sb.append(".");
			sb.append(fieldName);
		}
		return sb.toString();
	}

	private List<String> primitiveFields(Class<?> clazz) {
		List<String> result = new ArrayList<String>();
		PropertyDescriptor[] descriptors = PropertyUtils.getPropertyDescriptors(clazz);
		for (PropertyDescriptor pd : descriptors) {
			if (!isPersistedProperty(pd)) continue;
			if (pd.getPropertyType().isAnnotationPresent(Entity.class)) continue;
			if (Collection.class.isAssignableFrom(pd.getPropertyType())) continue;
			result.add(pd.getName());
		}
		return result;
	}

	protected StringBuilder where(Object[] exp, String prefix, String bp, String tablePrefix) throws Exception {
		Object obj = exp[0];
		Map<String, Collection<Object[]>> nonDefaultOperations = nonDefaultOperations(exp);
		StringBuilder w = new StringBuilder();
		if (obj == null) return w;
		PropertyDescriptor[] descriptors = PropertyUtils.getPropertyDescriptors(obj.getClass());
		String p1 = prefix == null || prefix.length() == 0 ? "" : prefix + ".";
		String p2 = prefix == null || prefix.length() == 0 ? "" : prefix.replace(".", "_") + "_";
		int orIndex = 0;
		for (PropertyDescriptor pd : descriptors) {
			if (!isPersistedProperty(pd)) continue;
			String key = p1 + pd.getName();
			String tp = tablePrefix;
			Object value = pd.getReadMethod().invoke(obj);
			if (tp == null) tp = "t.";
			Collection<Object[]> operations = operationsWithoutFetchsAndDistinctsAndOrderBysAndAggregates(key,
					nonDefaultOperations);
			if (value == null && (!nonDefaultOperations.containsKey(key) || operations.isEmpty())) {
				continue;
			}
			if (nonDefaultOperations.containsKey(key) && !operations.isEmpty()) {
				orIndex = appendNonDefaultOperations(prefix, bp, w, p1, p2, orIndex, pd, tp, operations);
			} else if (value.getClass().isAnnotationPresent(Entity.class)) {
				String ntp = "t.".equals(tp) ? "t_" : tp;
				appendClause(w, where(new Object[] { value, nonDefaultOperations },
						key.replace(".", "_"), bp, ntp).toString());
			} else {
				String parameterName = usingQuestionMarks ? "?" : String.format(":%s%s", p2, bp + pd.getName());
				appendClause(w, String.format("%s%s%s = %s", tp, p1, pd.getName(), parameterName));
			}
		}
		return w;
	}

	private String from(Object[] exp, String prefix) throws Exception {
		Class<?> c = exp[0].getClass();
		return qualifiedTableName(c) + " AS t" + joins(exp, prefix);
	}

	private String qualifiedTableName(Class<?> c) {
		Table table = c.getAnnotation(Table.class);
		String schemaName = "";
		if (table != null && table.schema() != null && !table.schema().isEmpty()) {
			schemaName = table.schema() + ".";
		}
		String qualifiedTableName = schemaName + c.getSimpleName();
		return qualifiedTableName;
	}

	private String select(Object[] exp) throws Exception {
		StringBuilder sb = new StringBuilder();
		for (PropertyDescriptor pd : fetches(exp)) {
			sb.append(", ");
			sb.append(fieldsString(pd.getPropertyType(), "t_" + pd.getName()));
		}
		return sb.toString();
	}

	private List<PropertyDescriptor> fetches(Object[] exp) throws Exception {
		Object obj = exp[0];
		Map<String, Collection<Object[]>> nonDefaultOperations = nonDefaultOperations(exp);
		List<PropertyDescriptor> result = new ArrayList<PropertyDescriptor>();
		PropertyDescriptor[] descriptors = PropertyUtils.getPropertyDescriptors(obj.getClass());
		for (PropertyDescriptor pd : descriptors) {
			if (!isPersistedProperty(pd)) continue;
			if (nonDefaultOperations.containsKey(pd.getName())) {
				for (Object[] arr : nonDefaultOperations.get(pd.getName())) {
					if ("fetch".equals(arr[0]) || "leftJoin".equals(arr[0])) {
						result.add(pd);
					}
				}
			}
		}
		return result;
	}

	private String joins(Object[] exp, String prefix) throws Exception {
		Object obj = exp[0];
		Map<String, Collection<Object[]>> nonDefaultOperations = nonDefaultOperations(exp);
		StringBuilder sb = new StringBuilder();
		PropertyDescriptor[] descriptors = PropertyUtils.getPropertyDescriptors(obj.getClass());
		for (PropertyDescriptor pd : descriptors) {
			if (!isPersistedProperty(pd)) continue;
			Object value = pd.getReadMethod().invoke(obj);
			String key = prefix.replace("_", ".") + pd.getName();
			if (key.startsWith("t.")) key = key.substring(2);
			boolean isFetch = false;
			if (nonDefaultOperations.containsKey(key)) {
				for (Object[] arr : nonDefaultOperations.get(key)) {
					if ("fetch".equals(arr[0]) || "leftJoin".equals(arr[0])) {
						isFetch = true;
						value = pd.getReadMethod().getReturnType().newInstance();
						break;
					}
				}
			}
			if (isFetch || (value != null && value.getClass().isAnnotationPresent(Entity.class))) {
				Field idField = RepositoryUtil.idField(value);
				String foreignKeyFieldName = foreignKeyFieldName(obj, pd);
				sb.append(String.format((isFetch ? " left " : " inner ") + "join %s AS %s%s on %s.%s = %s%s.%s",
						qualifiedTableName(value.getClass()), prefix, pd.getName(),
						prefix.substring(0, prefix.length() - 1), foreignKeyFieldName, prefix, pd.getName(),
						idField.getName()));
				if (!isFetch) {
					sb.append(joins(new Object[] { value, nonDefaultOperations }, prefix + pd.getName() + "_"));
				}
			}
		}
		return sb.toString();
	}

	private String foreignKeyFieldName(Object obj, PropertyDescriptor pd) {
		Field foreignKeyField = RepositoryUtil.fieldWithName(obj, pd.getName());
		if (foreignKeyField.isAnnotationPresent(JoinColumn.class)) {
			JoinColumn jc = foreignKeyField.getAnnotation(JoinColumn.class);
			return jc.name();
		}
		return pd.getName();
	}

	@Override
	protected void setParameter(Object q, String pn, Object object) {
		((Query) q).setParameter(pn, object);
	}

	private void clearProxies(Collection<?> c) throws Exception {
		if (c == null) return;
		Set<Object> processed = new HashSet<Object>();
		for (Object o : c) {
			clearProxies(o, processed);
		}
	}

	private void clearProxies(Object o, Set<Object> processed) throws Exception {
		processed.add(o);
		org.hibernate.Session session = (Session) em.getDelegate();
		session.evict(o);
		PropertyDescriptor[] descriptors = PropertyUtils.getPropertyDescriptors(o.getClass());
		for (PropertyDescriptor pd : descriptors) {
			if (!isPersistedProperty(pd)) continue;
			Object value = pd.getReadMethod().invoke(o);
			if (value instanceof HibernateProxy && !Hibernate.isInitialized(value)) {
				pd.getWriteMethod().invoke(o, new Object[] { null });
			} else if (value != null && value.getClass().isAnnotationPresent(Entity.class)
					&& !processed.contains(value)) {
				clearProxies(value, processed);
			}
		}
	}

	public String insertString(Object[] exp) throws Exception {
		Object obj = exp[0];
		PropertyDescriptor[] descriptors = PropertyUtils.getPropertyDescriptors(obj.getClass());
		StringBuilder sbc = new StringBuilder();
		StringBuilder sbv = new StringBuilder();
		for (PropertyDescriptor pd : descriptors) {
			if (!isPersistedProperty(pd)) continue;
			Object value = pd.getReadMethod().invoke(obj);
			if (value == null) continue;
			String fieldName = pd.getName();
			if (value.getClass().isAnnotationPresent(Entity.class)) {
				fieldName = foreignKeyFieldName(obj, pd);
			}
			if (sbc.length() > 0) {
				sbc.append(", ");
				sbv.append(", ");
			}
			sbc.append(fieldName);
			sbv.append("?");
		}
		return String.format("insert into %s(%s) values(%s)", qualifiedTableName(obj.getClass()), sbc, sbv);
	}

	private static final long serialVersionUID = 1L;

}