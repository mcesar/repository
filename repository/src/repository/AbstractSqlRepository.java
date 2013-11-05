package repository;

import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.persistence.Entity;
import javax.persistence.EntityNotFoundException;
import javax.persistence.Transient;

import org.apache.commons.beanutils.PropertyUtils;

public abstract class AbstractSqlRepository {

	private static String[] ignorableOperations = { "fetch", "distinct", "orderBy", "orderByDesc", "groupBy", "sum",
			"count", "max", "leftJoin", "limit" };

	static {
		Arrays.sort(ignorableOperations);
	}

	public <T> Collection<T> matching(T obj) {
		try {
			return tryMatching(obj);
		} catch (EntityNotFoundException e) {
			return null;
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	public <T> Collection<T> matching(Object[] exp) {
		try {
			return tryMatching(exp);
		} catch (EntityNotFoundException e) {
			return null;
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	public <T> Collection<T> tryMatching(T obj) throws Exception {
		return tryMatching(new Object[] { obj, null });
	}

	protected abstract <T> Collection<T> tryMatching(Object[] objects) throws Exception;

	protected abstract String queryString(Object[] exp, String w) throws Exception;

	protected String where(Object[] exp) throws Exception {
		StringBuilder w = where(exp, "", "", null);
		if (w.length() > 0)
			w.insert(0, "where ");
		return w.toString();
	}

	protected abstract StringBuilder where(Object[] exp, String prefix, String bp, String tablePrefix) throws Exception;

	protected int appendNonDefaultOperations(String prefix, String bp, StringBuilder w, String p1, String p2,
			int orIndex, PropertyDescriptor pd, String tp, Collection<Object[]> operations) throws Exception {
		int i = 1;
		if (w.length() > 0)
			w.append(" and ");
		for (Object[] operation : operations) {
			String operator = (String) operation[0];
			if (i > 1 && w.length() > 0)
				w.append(" and ");
			String binding = bp + pd.getName() + (operations.size() > 1 ? i++ : "");
			if ("in".equals(operator) || "nin".equals(operator)) {
				String not = "in".equals(operator) ? "" : "not ";
				String logicalOperator = "in".equals(operator) ? "or" : "and";
				Collection<?> inList = (Collection<?>) operation[1];
				List<?> inListWithoutNull = listWithoutNull(inList);
				if (inList.isEmpty()) {
					w.append(String.format("%s%s%s is %snull", tp, p1, pd.getName(), not));
				} else if (inList.contains(null)) {
					if (inListWithoutNull.isEmpty()) {
						w.append(String.format("(%s%s%s is %snull)", tp, p1, pd.getName(), not));
					} else {
						w.append(String.format("(%s%s%s %s%s (:%s%s) %s %s%s%s is %snull)", tp, p1,
								pd.getName(), not, "in", p2, binding, logicalOperator, tp, p1, pd.getName(),
								not));
					}
					inList.remove(null);
				} else {
					w.append(String.format("%s%s%s %s%s (:%s%s)", tp, p1, pd.getName(), not, "in", p2, binding));
				}
			} else if ("insq".equals(operator)) {
				Object[] subExp;
				if (operation[1] instanceof Object[]) {
					subExp = (Object[]) operation[1];
				} else {
					subExp = new Object[] { operation[1], null };
				}
				String sw = where(subExp);
				w.append(String.format("%s%s%s in (%s)", tp, p1, pd.getName(), queryString(subExp, sw)));
			} else if ("=".equals(operator) && operation[1] == null) {
				w.append(String.format("%s%s%s is null", tp, p1, pd.getName()));
			} else if ("<>".equals(operator) && operation[1] == null) {
				w.append(String.format("%s%s%s is not null", tp, p1, pd.getName()));
			} else if ("~=".equals(operator)) {
				w.append(String.format("lower(%s%s%s) = :%s%s", tp, p1, pd.getName(), p2, binding));
			} else if ("notlike".equals(operator)) {
				w.append(String.format("%s%s%s not like :%s%s", tp, p1, pd.getName(), p2, binding));
			} else if ("ilike".equals(operator)) {
				w.append(String.format("lower(%s%s%s) like :%s%s", tp, p1, pd.getName(), p2, binding));
			} else if ("rlike".equals(operator)) {
				Object[] arr = ((Object[]) operation[1]);
				Object[] strings = (Object[]) arr[1];
				String replaceString = String.format("%s%s%s", tp, p1, pd.getName());
				for (int j = 0; j < strings.length; j += 2) {
					replaceString = String.format("replace(%s, '%s', '%s')", replaceString, strings[j],
							strings[j + 1]);
				}
				w.append(String.format("lower(%s) like :%s%s", replaceString, p2, binding));
			} else if ("tlike".equals(operator)) {
				Object[] arr = ((Object[]) operation[1]);
				w.append(String.format(
						"translate(lower(%s%s%s), '%s', '%s') like translate(:%s%s, '%s', '%s')", tp, p1,
						pd.getName(), arr[1], arr[2], p2, binding, arr[1], arr[2]));
			} else if ("or".equals(operator)) {
				Object[] trio = (Object[]) operation[1];
				String operand2 = null;
				String lastOperator = null;
				while (true) {
					Object[] orExp = orExpFrom(trio, prefix);
					String wOr = where(orExp, prefix, "_" + (orIndex > 0 ? orIndex : ""), tp).toString();
					if (operand2 == null) {
						operand2 = wOr;
					} else {
						operand2 = "(" + operand2 + ") or (" + wOr + ")";
					}
					orIndex++;
					lastOperator = (String) trio[2];
					if (!"or".equals(lastOperator))
						break;
					trio = (Object[]) trio[0];
				}
				if (lastOperator == null)
					lastOperator = "=";
				w.append(String.format("(%s%s%s %s :%s%s %s (%s))", tp, p1, pd.getName(), lastOperator, p2,
						binding, operator, operand2));
			} else {
				w.append(String.format("%s%s%s %s :%s%s", tp, p1, pd.getName(), operator, p2, binding));
			}
		}
		return orIndex;
	}

	protected <T> void setParameterValues(Object q, String queryString, Object[] exp, String prefix, String bp)
			throws Exception {
		Object obj = exp[0];
		Map<String, Collection<Object[]>> nonDefaultOperations = nonDefaultOperations(exp);
		PropertyDescriptor[] descriptors = PropertyUtils.getPropertyDescriptors(obj.getClass());
		String p1 = prefix == null || prefix.length() == 0 ? "" : prefix + ".";
		String p2 = prefix == null || prefix.length() == 0 ? "" : prefix.replace(".", "_") + "_";
		int orIndex = 0;
		for (PropertyDescriptor pd : descriptors) {
			if (pd.getReadMethod() == null || pd.getWriteMethod() == null)
				continue;
			if (pd.getReadMethod().isAnnotationPresent(Transient.class))
				continue;
			Object value = pd.getReadMethod().invoke(obj);
			String key = p1 + pd.getName();
			Collection<Object[]> operations = operationsWithoutFetchsAndDistinctsAndOrderBysAndAggregates(key,
					nonDefaultOperations);
			if (value == null && (!nonDefaultOperations.containsKey(key) || operations.isEmpty())) {
				continue;
			}
			if (value instanceof FilterCollection<?>) {
				value = ((FilterCollection<?>) value).iterator().next();
			}
			if (nonDefaultOperations.containsKey(key) && !operations.isEmpty()) {
				int i = 1;
				for (Object[] operation : operations) {
					String operator = (String) operation[0];
					if ("insq".equals(operator)) {
						int indexOfUnderscore = key.indexOf('_');
						String sp = indexOfUnderscore == -1 ? "" : key.substring(indexOfUnderscore + 1);
						Object[] subExp;
						if (operation[1] instanceof Object[]) {
							subExp = (Object[]) operation[1];
						} else {
							subExp = new Object[] { operation[1], null };
						}
						setParameterValues(q, queryString, subExp, sp, bp);
					} else {
						String pn = p2 + bp + pd.getName() + (operations.size() > 1 ? i++ : "");
						if (queryString.contains(":" + pn + " ") || queryString.endsWith(":" + pn)
								|| queryString.contains(":" + pn + ")") || queryString.contains(":" + pn + ",")) {
							if ("or".equals(operator)) {
								Object[] trio = (Object[]) operation[1];
								while (true) {
									Object[] orExp = orExpFrom(trio, prefix);
									setParameterValues(q, queryString, orExp, prefix, "_"
											+ (orIndex > 0 ? orIndex : ""));
									orIndex++;
									if (!"or".equals(trio[2]))
										break;
									trio = (Object[]) trio[0];
								}
								setParameter(q, pn, trio[0]);
							} else if ("rlike".equals(operator)) {
								setParameter(q, pn, ((Object[]) operation[1])[0]);
							} else if ("tlike".equals(operator)) {
								setParameter(q, pn, ((Object[]) operation[1])[0]);
							} else {
								setParameter(q, pn, operation[1]);
							}
						}
					}
				}
			} else if (value.getClass().isAnnotationPresent(Entity.class)) {
				setParameterValues(q, queryString, new Object[] { value, nonDefaultOperations }, key, bp);
			} else {
				String parameterName = p2 + bp + pd.getName();
				setParameter(q, parameterName, value);
			}
		}
	}

	protected abstract void setParameter(Object q, String pn, Object object);

	protected Object[] orExpFrom(Object[] pair, String prefix) {
		Object[] orExp;
		if (pair[1] instanceof Object[])
			orExp = (Object[]) pair[1];
		else
			orExp = new Object[] { pair[1], new LinkedHashMap<String, Collection<Object[]>>() };
		@SuppressWarnings("unchecked")
		Map<String, Collection<Object[]>> nonDefaultOperationsOfOrExp = (Map<String, Collection<Object[]>>) orExp[1];
		Map<String, Collection<Object[]>> newNonDefaultOperationsOfOrExp =
			new LinkedHashMap<String, Collection<Object[]>>();
		for (Entry<String, Collection<Object[]>> e : nonDefaultOperationsOfOrExp.entrySet()) {
			newNonDefaultOperationsOfOrExp.put((prefix == null || prefix.isEmpty() ? "" : prefix + ".") + e.getKey(),
					e.getValue());
		}
		orExp[1] = newNonDefaultOperationsOfOrExp;
		return orExp;
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Collection<Object[]>> nonDefaultOperations(Object[] exp) {
		Map<String, Collection<Object[]>> result = (Map<String, Collection<Object[]>>) exp[1];
		if (result == null)
			result = new LinkedHashMap<String, Collection<Object[]>>();
		return result;
	}

	protected Collection<Object[]> operationsWithoutFetchsAndDistinctsAndOrderBysAndAggregates(String key,
			Map<String, Collection<Object[]>> nonDefaultOperations) {
		Collection<Object[]> operations = null;
		if (nonDefaultOperations.containsKey(key)) {
			operations = new ArrayList<Object[]>(nonDefaultOperations.get(key));
			for (Iterator<Object[]> it = operations.iterator(); it.hasNext();) {
				Object[] operation = it.next();
				if (Arrays.binarySearch(ignorableOperations, operation[0]) > -1)
					it.remove();
			}
		}
		return operations;
	}

	private List<?> listWithoutNull(Collection<?> inList) {
		List<?> result = new ArrayList<Object>(inList);
		result.remove(null);
		return result;
	}

	protected String beginWithSpace(String s) {
		if (s == null)
			return null;
		if (s.isEmpty())
			return s;
		return " " + s;
	}

	protected void appendClause(StringBuilder sb, String clause) {
		if (sb.length() > 0 && clause.length() > 0)
			sb.append(" and ");
		sb.append(clause);
	}

	protected boolean isPersistedProperty(PropertyDescriptor pd) {
		if (pd.getReadMethod() == null || pd.getWriteMethod() == null) return false;
		if (pd.getReadMethod().isAnnotationPresent(Transient.class)) return false;
		return true;
	}

	protected void distinctsAndAggregates(Object[] exp, StringBuilder distinctSb, StringBuilder aggregatesSb) {
		StringBuilder aggregatesKeySb = new StringBuilder();
		Map<String, Collection<Object[]>> nonDefaultOperations = nonDefaultOperations(exp);
		for (Entry<String, Collection<Object[]>> e : nonDefaultOperations.entrySet()) {
			for (Object[] operation : e.getValue()) {
				if ("distinct".equals(operation[0])) {
					if (distinctSb.length() > 0)
						distinctSb.append(", ");
					distinctSb.append("t.");
					distinctSb.append(e.getKey());
				} else if ("groupBy".equals(operation[0])) {
					if (aggregatesKeySb.length() > 0)
						aggregatesKeySb.append(", ");
					aggregatesKeySb.append("t.");
					aggregatesKeySb.append(e.getKey());
				} else if ("sum".equals(operation[0]) || "count".equals(operation[0]) || "max".equals(operation[0])) {
					if (aggregatesSb.length() > 0)
						aggregatesSb.append(", ");
					aggregatesSb.append(operation[0]);
					aggregatesSb.append("(");
					aggregatesSb.append("t.");
					aggregatesSb.append(e.getKey());
					aggregatesSb.append(")");
				}
			}
		}
		if (aggregatesKeySb.length() > 0) {
			if (aggregatesSb.length() > 0)
				aggregatesSb.insert(0, ", ");
			aggregatesSb.insert(0, aggregatesKeySb);
		}
		if (distinctSb.length() > 0) {
			distinctSb.insert(0, "distinct ");
			if (aggregatesSb.length() > 0)
				aggregatesSb.insert(0, ", ");
		}
	}

	private static final long serialVersionUID = 1L;

}
