package repository;

import static java.util.Arrays.asList;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.ObjectUtils;

public abstract class AbstractBuilder<T extends AbstractBuilder<?>> {

	protected String lastProperty;

	protected Object lastValue;

	private Map<String, List<Object[]>> nonDefaultOperations = new LinkedHashMap<String, List<Object[]>>();

	@SuppressWarnings("unchecked")
	public T like() {
		addNonDefaultOperation("like");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T ilike() {
		addNonDefaultOperation("ilike");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T notLike() {
		addNonDefaultOperation("notlike");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T likeWithTranslate(String search, String replace) {
		if (lastValue != null) {
			Object[] arr = { ((String) lastValue).toLowerCase(), search, replace };
			lastValue = arr;
			addNonDefaultOperation("tlike");
		}
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T contains() {
		if (lastValue instanceof String && lastValue.toString().length() == 0) {
			anulaUltimaPropriedade();
			return (T) this;
		}
		if (lastValue != null) {
			lastValue = ("%" + lastValue + "%").toLowerCase();
			addNonDefaultOperation("ilike");
		}
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T containsWithReplace(String... searchReplace) {
		if (lastValue instanceof String && lastValue.toString().length() == 0) {
			anulaUltimaPropriedade();
			return (T) this;
		}
		if (lastValue != null) {
			Object[] arr = { ("%" + lastValue + "%").toLowerCase(), searchReplace };
			lastValue = arr;
			addNonDefaultOperation("rlike");
		}
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T startsWith() {
		lastValue = (lastValue == null ? "" : lastValue) + "%";
		addNonDefaultOperation("like");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T orIn(Object... values) {
		if (values == null)
			return (T) this;
		List<Object> list = new ArrayList<Object>(asList(values));
		list.add(0, lastValue);
		lastValue = list;
		addNonDefaultOperation("in");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T orIn(Collection<?> values) {
		if (values == null)
			return (T) this;
		return orIn(values.toArray());
	}

	@SuppressWarnings("unchecked")
	public T norIn(Object... values) {
		List<Object> list = new ArrayList<Object>(asList(values));
		list.add(0, lastValue);
		lastValue = list;
		addNonDefaultOperation("nin");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T orInSubquery(Object exp) {
		lastValue = exp;
		addNonDefaultOperation("insq");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T ge() {
		addNonDefaultOperation(">=");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T gt() {
		addNonDefaultOperation(">");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T le() {
		addNonDefaultOperation("<=");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T lt() {
		addNonDefaultOperation("<");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T fetch() {
		addNonDefaultOperation("fetch");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T leftJoin() {
		addNonDefaultOperation("leftJoin");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T eq() {
		addNonDefaultOperation("=");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T eqIgnoringCase() {
		if (lastValue != null)
			lastValue = lastValue.toString().toLowerCase();
		addNonDefaultOperation("~=");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T neq() {
		addNonDefaultOperation("<>");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T distinct() {
		addNonDefaultOperation("distinct");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T orderBy() {
		addNonDefaultOperation("orderBy");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T orderByDesc() {
		addNonDefaultOperation("orderByDesc");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T sum() {
		addNonDefaultOperation("sum");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T max() {
		addNonDefaultOperation("max");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T count() {
		addNonDefaultOperation("count");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T groupBy() {
		addNonDefaultOperation("groupBy");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T limit() {
		addNonDefaultOperation("groupBy");
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T limit(int number) {
		addNonDefaultOperation(lastProperty, "limit", number);
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T or(Object exp) {
		String lastOperator = lastOperator();
		removeLastOperation();
		lastValue = new Object[] { lastValue, exp, lastOperator };
		addNonDefaultOperation("or");
		return (T) this;
	}

	public void addInnerNonDefaultOperations(String prefix, Object nonDefaultOperations) {
		@SuppressWarnings("unchecked")
		Map<String, Collection<Object[]>> nonDefaultOperations_ = (Map<String, Collection<Object[]>>) nonDefaultOperations;
		for (Entry<String, Collection<Object[]>> e : nonDefaultOperations_.entrySet()) {
			for (Object[] arr : e.getValue()) {
				addNonDefaultOperation(prefix + "." + e.getKey(), (String) arr[0], arr[1]);
			}
		}
	}

	public Object[] exp() {
		return new Object[] { build(), nonDefaultOperations };
	}

	public void addNonDefaultOperation(String property, String operator, Object value) {
		if (property == null)
			return;
		if (property.trim().equals("")) {
			nonDefaultOperations.put("", new ArrayList<Object[]>());
		}
		if (!nonDefaultOperations.containsKey(property))
			nonDefaultOperations.put(property, new ArrayList<Object[]>());
		nonDefaultOperations.get(property).add(new Object[] { operator, value });

	}

	public static String toString(Object[] exp) {
		@SuppressWarnings("unchecked")
		Map<String, Collection<Object[]>> nonDefaultOperations = (Map<String, Collection<Object[]>>) exp[1];
		StringBuilder sb = new StringBuilder();
		sb.append("\n[\n    ");
		sb.append(exp[0].toString());
		sb.append(",\n    [ ");
		for (Entry<String, Collection<Object[]>> e : nonDefaultOperations.entrySet()) {
			sb.append("\n      ");
			sb.append(e.getKey().toString());
			sb.append(": ");
			sb.append("[ ");
			boolean first = true;
			for (Object[] arr : e.getValue()) {
				if (!first)
					sb.append(", ");
				first = false;
				if ("or".equals(arr[0])) {
					sb.append("\nOR --> [\n");
					sb.append(arr[0].toString());
					sb.append(", ");
					Object[] arrayDoOr = (Object[]) arr[1];
					sb.append("\n[\n");
					sb.append(arrayDoOr[0].toString());
					sb.append(", ");
					sb.append(toString((Object[]) arrayDoOr[1]));
					sb.append(",\n");
					sb.append(arrayDoOr[2].toString());
					sb.append("\n]\n");
					sb.append("] <-- OR\n");
				} else {
					sb.append(Arrays.toString(arr));
				}
			}
			sb.append(" ]");
		}
		sb.append("\n    ]\n");
		sb.append("]");
		return sb.toString();
	}

	private void addNonDefaultOperation(String operator) {
		addNonDefaultOperation(lastProperty, operator, lastValue);
	}

	private Object[] lastOperation() {
		List<Object[]> propertyOperations = lastPropertyOperations();
		if (propertyOperations == null || propertyOperations.isEmpty())
			return null;
		Object[] result = propertyOperations.get(propertyOperations.size() - 1);
		if (!ObjectUtils.equals(result[1], lastValue))
			return null;
		return result;
	}

	private String lastOperator() {
		Object[] lastOperation = lastOperation();
		if (lastOperation == null)
			return null;
		return (String) lastOperation[0];
	}

	private void removeLastOperation() {
		List<Object[]> lastPropertyOperations = lastPropertyOperations();
		if (lastPropertyOperations == null)
			return;
		lastPropertyOperations.remove(lastOperation());
	}

	private List<Object[]> lastPropertyOperations() {
		if (lastProperty == null)
			return null;
		if (!nonDefaultOperations.containsKey(lastProperty))
			return null;
		List<Object[]> propertyOperations = nonDefaultOperations.get(lastProperty);
		return propertyOperations;
	}

	private void anulaUltimaPropriedade() {
		try {
			Class<?> class1 = this.getClass();
			if (class1.getSuperclass().getName().endsWith("Builder")
					&& !class1.getSuperclass().getName().endsWith("AbstractBuilder")) {
				class1 = class1.getSuperclass();
			}
			Field f = class1.getDeclaredField("obj");
			f.setAccessible(true);
			PropertyUtils.setProperty(f.get(this), lastProperty, null);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected AbstractBuilder<?> add(String prefix, Object[] exp) {
		addInnerNonDefaultOperations(prefix, exp[1]);
		return this;
	}

	protected abstract Object build();
}