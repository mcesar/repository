package repository;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import javax.persistence.Entity;
import javax.persistence.Transient;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.beanutils.converters.DoubleConverter;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;

public class InMemoryRepository implements Repository, RepositoryWithIndexes {

	private Map<String, List<Object>> entitiesMap = new LinkedHashMap<String, List<Object>>();

	private Map<String, String> idMap = new LinkedHashMap<String, String>();

	private Map<String, Collection<Indice<?>>> indices = new LinkedHashMap<String, Collection<Indice<?>>>();

	private Map<String, Collection<Object>> entidadesIndexadas = new LinkedHashMap<String, Collection<Object>>();

	private static Map<Class<?>, PropertyDescriptor[]> propertyDescriptors =
		new LinkedHashMap<Class<?>, PropertyDescriptor[]>();

	private static Map<Class<?>, Collection<PropertyDescriptor>> propertyDescriptorsWithNonTransient =
		new LinkedHashMap<Class<?>, Collection<PropertyDescriptor>>();

	@Override
	public <T> Collection<T> matching(T obj) {
		try {
			return tryMatching(obj);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public <T> Collection<T> matching(Object[] exp) {
		try {
			return consultaInterna(exp);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public <T> Collection<T> tryMatching(T obj) throws Exception {
		return consultaInterna(new Object[] { obj, new LinkedHashMap<String, Object>() });
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T add(T o) throws Exception {
		String className = o.getClass().getName();
		className = className.replaceAll("_\\$\\$_javassist_.*", "");
		if (!entitiesMap.containsKey(className)) {
			entitiesMap.put(className, new ArrayList<Object>());
		}
		if (o instanceof HibernateProxy) {
			Hibernate.initialize(o);
			o = (T) ((HibernateProxy) o).getHibernateLazyInitializer().getImplementation();
		}
		Field f = RepositoryUtil.idField(o);
		if (f.get(o) == null) {
			List<Object> list = entitiesMap.get(className);
			int last = 0;
			if (list != null && !list.isEmpty()) {
				for (Object obj : list) {
					if (last < ((Integer) f.get(obj)))
						last = (Integer) f.get(obj);
				}
			}
			// last = (Integer) f.get(list.get(list.size() - 1));
			f.set(o, last + 1);
		}
		if (!idMap.containsKey(className + "|" + f.get(o))) {
			entitiesMap.get(className).add(o);
			indexaObjeto(o);
			idMap.put(className + "|" + f.get(o), "");
		}
		return o;
	}

	@Override
	public void remove(Object o) throws Exception {
		String className = o.getClass().getName();
		if (entitiesMap.containsKey(className)) {
			entitiesMap.get(className).remove(o);
			idMap.remove(className + "|" + RepositoryUtil.idField(o).get(o));
			desindexaObjeto(o);
		}
	}

	@Override
	public void removeAll(Object o) throws Exception {
		removeAll(new Object[] { o, new LinkedHashMap<String, Collection<Object[]>>() });
	}

	@Override
	public int removeAll(Object[] o) throws Exception {
		Collection<Object> oo = matching(o);
		if (oo == null) return 0;
		for (Object eachObj : new ArrayList<Object>(oo)) {
			remove(eachObj);
		}
		return oo.size();
	}

	@Override
	public <T> T replace(T o) throws Exception {
		Object o_ = o.getClass().getConstructor().newInstance();
		Field f = RepositoryUtil.idField(o);
		f.set(o_, f.get(o));
		removeAll(o_);
		return add(o);
	}

	@Override
	public void replace(Object o, Object where) throws Exception {
		throw new Exception("N?o implementado");
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T execute(RunnableWithResult r, Object... args) throws Exception {
		return (T) r.run(this, args);
	}

	@SuppressWarnings("unchecked")
	private <T> Collection<T> consultaInterna(Object[] exp) throws Exception {
		T obj = exp == null ? null : (T) exp[0];
		if (obj == null) return null;
		Map<String, Collection<Object[]>> nonDefaultOperations =
			operationsWithoutFetchs((Map<String, Collection<Object[]>>) exp[1]);
		String className = obj.getClass().getName();
		if (!entitiesMap.containsKey(className)) return null;
		Collection<Object[]> filters = filters(obj, nonDefaultOperations, "");
		if (filters == null || filters.isEmpty()) return (Collection<T>) entitiesMap.get(className);
		Collection<Object> result = new ArrayList<Object>();
		Collection<T> universoDeObjetos = universoDeObjetos(exp, nonDefaultOperations, className);
		for (Object object : universoDeObjetos) {
			if (matches(object, filters, nonDefaultOperations, "")) result.add(object);
		}
		result = processOrderBy(nonDefaultOperations, result);
		result = processDistincts(nonDefaultOperations, result);
		result = processAggregates(nonDefaultOperations, result);
		return (Collection<T>) result;
	}

	private Map<String, Collection<Object[]>> operationsWithoutFetchs(
			Map<String, Collection<Object[]>> nonDefaultOperations) {
		Map<String, Collection<Object[]>> resultado = new LinkedHashMap<String, Collection<Object[]>>();
		for (Entry<String, Collection<Object[]>> e : nonDefaultOperations.entrySet()) {
			Collection<Object[]> value = new ArrayList<Object[]>();
			for (Object[] arr : e.getValue()) {
				if (!"fetch".equals(arr[0])) value.add(arr);
			}
			if (!value.isEmpty()) resultado.put(e.getKey(), value);
		}
		return resultado;
	}

	@SuppressWarnings("unchecked")
	private <T> Collection<Object> processOrderBy(Map<String, Collection<Object[]>> nonDefaultOperations,
			Collection<Object> result) throws Exception {
		final Collection<String> orderBys = propertiesWithModifier(nonDefaultOperations, "orderBy", "orderByDesc");
		if (result == null || orderBys == null || orderBys.isEmpty()) return result;
		result = new ArrayList<Object>(result);
		final Map<Object, List<Comparable<Object>>> orderByValuesMap =
			new LinkedHashMap<Object, List<Comparable<Object>>>();
		for (Object o : result) {
			orderByValuesMap.put(o, orderByValues(o, orderBys, "t."));
		}
		final List<Integer> orderByFactors = orderByFactors(nonDefaultOperations);
		Collections.sort((List<T>) result, new Comparator<T>() {

			@Override
			public int compare(T o1, T o2) {
				List<Comparable<Object>> v1 = orderByValuesMap.get(o1);
				List<Comparable<Object>> v2 = orderByValuesMap.get(o2);
				for (int i = 0; i < v1.size(); i++) {
					if (!v1.get(i).equals(v2.get(i))) return v1.get(i).compareTo(v2.get(i)) * orderByFactors.get(i);
				}
				return 0;
			}
		});
		return result;
	}

	private List<Integer> orderByFactors(Map<String, Collection<Object[]>> nonDefaultOperations) {
		List<Integer> result = new ArrayList<Integer>();
		for (Entry<String, Collection<Object[]>> e : nonDefaultOperations.entrySet()) {
			for (Object[] operation : e.getValue()) {
				if ("orderBy".equals(operation[0])) result.add(1);
				if ("orderByDesc".equals(operation[0])) result.add(-1);
			}
		}
		return result;
	}

	private Collection<Object> processDistincts(Map<String, Collection<Object[]>> nonDefaultOperations,
			Collection<Object> result)
			throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		Collection<String> distincts = propertiesWithModifier(nonDefaultOperations, "distinct");
		if (result == null || distincts == null || distincts.isEmpty()) return result;
		List<Object> newResult = new ArrayList<Object>();
		for (Object o : result) {
			Object[] r = new Object[distincts.size()];
			int i = 0;
			for (String d : distincts) {
				r[i++] = PropertyUtils.getProperty(o, d.substring(2));
			}
			newResult.add(r);
		}
		return newResult;
	}

	private Collection<Object> processAggregates(Map<String, Collection<Object[]>> nonDefaultOperations,
			Collection<Object> result)
			throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		Collection<String> groupBys = propertiesWithModifier(nonDefaultOperations, "groupBy");
		Collection<String> sums = propertiesWithModifier(nonDefaultOperations, "sum");
		Collection<String> counts = propertiesWithModifier(nonDefaultOperations, "count");
		if (result == null || (groupBys == null && sums == null || counts == null)
				|| (groupBys.isEmpty() && sums.isEmpty() && counts.isEmpty())) return result;
		Map<String, Object[]> newResult = new LinkedHashMap<String, Object[]>();
		for (Object o : result) {
			StringBuilder sb = new StringBuilder();
			for (String g : groupBys) {
				if (sb.length() > 0) sb.append("|");
				sb.append(PropertyUtils.getProperty(o, g.substring(2)));
			}
			if (!newResult.containsKey(sb.toString())) {
				newResult.put(sb.toString(), new Object[groupBys.size() + sums.size() + counts.size()]);
			}
			Object[] r = newResult.get(sb.toString());
			int i = 0;
			for (Entry<String, Collection<Object[]>> e : nonDefaultOperations.entrySet()) {
				if (groupBys.contains("t." + e.getKey())) {
					r[i++] = PropertyUtils.getProperty(o, e.getKey());
				} else if (counts.contains("t." + e.getKey())) {
					r[i] = r[i] == null ? 1 : (Integer) r[i] + 1;
					i++;
				} else if (sums.contains("t." + e.getKey())) {
					double v = ((Number) PropertyUtils.getProperty(o, e.getKey())).doubleValue();
					r[i] = r[i] == null ? v : ((Number) r[i]).doubleValue() + v;
					i++;
				}
			}
		}
		return new ArrayList<Object>(newResult.values());
	}

	@SuppressWarnings("unchecked")
	private <T> List<Comparable<Object>> orderByValues(T t, Collection<String> orderBys, String prefix)
			throws Exception {
		List<Comparable<Object>> result = new ArrayList<Comparable<Object>>();
		int levels = StringUtils.countMatches(prefix, ".");
		boolean deveAdentrarNivelInterno = false;
		for (String orderBy : orderBys) {
			if (StringUtils.countMatches(orderBy, ".") > levels) {
				deveAdentrarNivelInterno = true;
				break;
			}
		}
		for (PropertyDescriptor pd : propertyDescriptors(t)) {
			for (String orderBy : orderBys) {
				if (orderBy.equals(prefix + pd.getName())) {
					Object value = pd.getReadMethod().invoke(t);
					result.add((Comparable<Object>) value);
				} else if (isEntidade(pd.getPropertyType()) && deveAdentrarNivelInterno) {
					Object value = pd.getReadMethod().invoke(t);
					if (value != null && isEntidade(value.getClass()))
						result.addAll(orderByValues(value, orderBys, prefix + pd.getName() + "."));
				}
			}
		}
		return result;
	}

	private <T> PropertyDescriptor[] propertyDescriptors(T t) {
		if (!propertyDescriptors.containsKey(t.getClass()))
			propertyDescriptors.put(t.getClass(), PropertyUtils.getPropertyDescriptors(t.getClass()));
		return propertyDescriptors.get(t.getClass());
	}

	private Collection<String> propertiesWithModifier(Map<String, Collection<Object[]>> nonDefaultOperations,
			String... modifiers) {
		Collection<String> result = new ArrayList<String>();
		for (Entry<String, Collection<Object[]>> e : nonDefaultOperations.entrySet()) {
			for (Object[] operation : e.getValue()) {
				for (String modifier : modifiers) {
					if (modifier.equals(operation[0])) result.add("t." + e.getKey());
				}
			}
		}
		return result;
	}

	private <T> Collection<Object[]> filters(T obj, Map<String, Collection<Object[]>> nonDefaultOperations,
			String prefix) throws Exception {
		Collection<Object[]> filterValues = new ArrayList<Object[]>();
		Class<?> c = obj.getClass();
		for (PropertyDescriptor pd : metodos(c)) {
			Object value = pd.getReadMethod().invoke(obj);
			if (value != null || nonDefaultOperations.containsKey(prefix + pd.getName())) {
				filterValues.add(new Object[] { pd, value });
			}
		}
		return filterValues;
	}

	private Collection<PropertyDescriptor> metodos(Class<?> c) {
		if (!propertyDescriptorsWithNonTransient.containsKey(c)) {
			propertyDescriptorsWithNonTransient.put(c, new ArrayList<PropertyDescriptor>());
			for (PropertyDescriptor pd : PropertyUtils.getPropertyDescriptors(c)) {
				Method readMethod = pd.getReadMethod();
				if (readMethod == null || pd.getWriteMethod() == null) continue;
				if (readMethod.isAnnotationPresent(Transient.class)) continue;
				propertyDescriptorsWithNonTransient.get(c).add(pd);
			}
		}
		return propertyDescriptorsWithNonTransient.get(c);
	}

	@SuppressWarnings("unchecked")
	private boolean matches(Object object, Collection<Object[]> filters,
			Map<String, Collection<Object[]>> nonDefaultOperations,
			String prefix) throws Exception {
		for (Object[] filter : filters) {
			PropertyDescriptor pd = (PropertyDescriptor) filter[0];
			Object value = filter[1];
			Object eachValue = pd.getReadMethod().invoke(object);
			if (nonDefaultOperations.containsKey(prefix + pd.getName())) {
				for (Object[] operation : nonDefaultOperations.get(prefix + pd.getName())) {
					if ("like".equals(operation[0]) || "ilike".equals(operation[0])) {
						String operando = operation[1].toString();
						if ("ilike".equals(operation[0])) operando = operando.toLowerCase();
						String regex = operando.replace("_", ".").replace("%", ".*");
						if (eachValue != null && !Pattern.matches(regex, eachValue.toString()))
							return false;
					} else if ("in".equals(operation[0])) {
						List<Object> col = new ArrayList<Object>((Collection<Object>) operation[1]);
						if (!col.contains(eachValue)) return false;
					} else if ("insq".equals(operation[0])) {
						boolean isFound = false;
						Object[] subExp;
						if (operation[1] instanceof Object[]) {
							subExp = (Object[]) operation[1];
						} else {
							subExp = new Object[] { operation[1], new LinkedHashMap<String, Collection<Object[]>>() };
						}
						Collection<Object> vv = matching(subExp);
						Field f = RepositoryUtil.idField(eachValue);
						for (Object o : vv) {
							if (f.get(eachValue).equals(f.get(o))) {
								isFound = true;
								break;
							}
						}
						if (!isFound) return isFound;
					} else if (">=".equals(operation[0])) {
						Double[] o = toDoubles(eachValue, operation[1]);
						if (eachValue == null || o[0] < o[1]) return false;
					} else if ("<=".equals(operation[0])) {
						Double[] o = toDoubles(eachValue, operation[1]);
						if (eachValue == null || o[0] > o[1]) return false;
					} else if ("=".equals(operation[0])) {
						if (!ObjectUtils.equals(eachValue, operation[1])) return false;
					} else if ("<>".equals(operation[0])) {
						if (ObjectUtils.equals(eachValue, operation[1])) return false;
					} else if (!"fetch".equals(operation[0]) && !"distinct".equals(operation[0])
							&& !"orderBy".equals(operation[0]) && !"orderByDesc".equals(operation[0])
							&& !"sum".equals(operation[0]) && !"count".equals(operation[0])
							&& !"groupBy".equals(operation[0])) {
						return false;
					}
				}
			} else if (isEntidade(value.getClass())) {
				String p = prefix + pd.getName() + ".";
				if (!matches(eachValue, filters(value, nonDefaultOperations, p), nonDefaultOperations, p))
					return false;
			} else if (!value.equals(eachValue)) {
				return false;
			}
		}
		return true;
	}

	private Double[] toDoubles(Object v1, Object v2) {
		if (v1 instanceof Date) v1 = ((Date) v1).getTime();
		if (v2 instanceof Date) v2 = ((Date) v2).getTime();
		return new Double[] {
				(Double) new DoubleConverter().convert(Double.class, v1),
				(Double) new DoubleConverter().convert(Double.class, v2) };
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getAdapter(Class<T> c) {
		if (c.equals(RepositoryWithIndexes.class)) return (T) this;
		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <C> void adicionarIndice(Indice<C> indice) {
		Class<C> c =
			(Class<C>) ((ParameterizedType) indice.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
		if (!indices.containsKey(c.getName())) {
			indices.put(c.getName(), new ArrayList<RepositoryWithIndexes.Indice<?>>());
		}
		indices.get(c.getName()).add(indice);
		if (entitiesMap.containsKey(c.getName())) {
			for (Object o : entitiesMap.get(c.getName())) {
				indexaObjeto(o, (Indice<Object>) indice);
			}
		}
	}

	private Map<String, Boolean> mapaDeAnotacoes = new LinkedHashMap<String, Boolean>();

	private boolean isEntidade(Class<?> c) {
		if (!mapaDeAnotacoes.containsKey(c.getName())) {
			mapaDeAnotacoes.put(c.getName(), c.isAnnotationPresent(Entity.class));
		}
		return mapaDeAnotacoes.get(c.getName());
	}

	// Collection<Indice<?>> indice = indices.get(o.getClass().getName());

	@SuppressWarnings("unchecked")
	private void indexaObjeto(Object o) {
		if (o == null || !indices.containsKey(o.getClass().getName())) return;
		for (Indice<?> ix : indices.get(o.getClass().getName())) {
			indexaObjeto(o, (Indice<Object>) ix);
		}
	}

	private void indexaObjeto(Object o, Indice<Object> indice) {
		String key = chaveDe(o, indice);
		if (key == null) return;
		if (!entidadesIndexadas.containsKey(key)) entidadesIndexadas.put(key, new ArrayList<Object>());
		entidadesIndexadas.get(key).add(o);
	}

	@SuppressWarnings("unchecked")
	private void desindexaObjeto(Object o) {
		if (o == null || !indices.containsKey(o.getClass().getName())) return;
		for (Indice<?> ix : indices.get(o.getClass().getName())) {
			desindexaObjeto(o, (Indice<Object>) ix);
		}
	}

	private <C> void desindexaObjeto(Object o, Indice<Object> indice) {
		String key = chaveDe(o, indice);
		if (key == null) return;
		if (entidadesIndexadas.containsKey(key)) entidadesIndexadas.get(key).remove(o);
	}

	private String chaveDe(Object o, Indice<Object> indice) {
		if (indice == null) return null;
		return o.getClass().getName() + "|" + indice.chave(o);
	}

	@SuppressWarnings("unchecked")
	protected <T> Collection<T> universoDeObjetos(Object[] exp, Map<String, Collection<Object[]>> nonDefaultOperations,
			String className) {
		Collection<T> universoDeObjetos = null;
		if (nonDefaultOperations == null || nonDefaultOperations.isEmpty()) {
			Collection<Indice<?>> indicesDaClasse = indices.get(exp[0].getClass().getName());
			if (indicesDaClasse != null) {
				for (Indice<?> ix : indicesDaClasse) {
					String key = chaveDe(exp[0], (Indice<Object>) ix);
					if (key == null) continue;
					Collection<T> candidato = (Collection<T>) entidadesIndexadas.get(key);
					if (universoDeObjetos == null || candidato.size() < universoDeObjetos.size()) {
						universoDeObjetos = candidato;
					}
				}
			}
		}
		if (universoDeObjetos == null) {
			universoDeObjetos = (Collection<T>) entitiesMap.get(className);
		}
		return universoDeObjetos;
	}
}
