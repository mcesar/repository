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

import javax.annotation.Resource;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.Transient;

import org.apache.commons.beanutils.PropertyUtils;
import org.jboss.annotation.ejb.TransactionTimeout;

@Stateless
public class JpaRepository implements Repository, NativeRepository, Repository.TransactionManagement {

	private static String[] ignorableOperations = { "fetch", "distinct", "orderBy", "orderByDesc", "groupBy", "sum",
			"count", "max", "leftJoin", "limit" };

	static {
		Arrays.sort(ignorableOperations);
	}

	@PersistenceContext(unitName = "RepositoryJPA")
	protected EntityManager em;
	
	@Resource
	private SessionContext context;
	
	public JpaRepository() {
	}
	
	public JpaRepository(EntityManager em) {
		this.em = em;
	}

	@Override
	public <T> Collection<T> matching(T obj) {
		try {
			return tryMatching(obj);
		} catch (EntityNotFoundException e) {
			return null;
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public <T> Collection<T> matching(Object[] exp) {
		try {
			return internalMatching(exp);
		} catch (EntityNotFoundException e) {
			return null;
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public <T> Collection<T> tryMatching(T obj) throws Exception {
		return internalMatching(new Object[] { obj, null });
	}

	protected <T> Collection<T> internalMatching(Object[] exp) throws Exception {
		String w = where(exp);
		if (exp[0] == null)
			throw new IllegalArgumentException();
		String[] qs = { "" };
		Query q = createQuery(exp, w, qs);
		setParameterValues(q, qs[0], exp);
		long s = System.currentTimeMillis();
		Collection<T> resultado = runQuery(q);
		long e = System.currentTimeMillis();
		long elapsed = (e - s) / 1000;
		if (elapsed > 10) {
			System.out.println(String.format("[Query runned for %ds]: %s", elapsed, qs[0]));
		}
		return resultado;
	}

	@Override
	public <T> T add(T o) throws Exception {
		return em.merge(o);
	}

	@Override
	public void remove(Object o) {
		em.remove(o);
	}

	@Override
	public void removeAll(Object o) throws Exception {
		removeAll(new Object[] { o, new LinkedHashMap<String, Collection<Object[]>>() });
	}

	@Override
	public int removeAll(Object[] exp) throws Exception {
		if (exp == null || exp[0] == null)
			throw new IllegalArgumentException();
		String qs = String.format("delete from %s t %s",
				exp[0].getClass().getSimpleName().replaceAll("_\\$\\$_javassist_.*", ""), where(exp));
		Query q = em.createQuery(qs);
		setParameterValues(q, qs, exp);
		return q.executeUpdate();
	}

	@Override
	public <T> T replace(T o) {
		return em.merge(o);
	}

	@Override
	public void replace(Object o, Object where) throws Exception {
		Object[] expWhere;
		if (where instanceof Object[]) {
			expWhere = (Object[]) where;
		} else {
			expWhere = new Object[] { where, new LinkedHashMap<String, Collection<Object[]>>() };
		}
		Object[] expObj = new Object[] { o, new LinkedHashMap<String, Collection<Object[]>>() };
		String qs = updateString(expObj, expWhere);
		Query q = createUpdateQuery(qs, expWhere);
		setParameterValues(q, qs, expObj, "", "_set_");
		setParameterValues(q, qs, expWhere);
		q.executeUpdate();
	}

	protected Query createUpdateQuery(String qs, Object[] exp) throws Exception {
		return em.createQuery(qs);
	}

	private String updateString(Object[] obj, Object[] where) throws Exception {
		String setExpression = where(obj, "", "_set_", null).toString();
		return String
				.format("update %s t set %s %s",
						where[0].getClass().getSimpleName().replaceAll("_\\$\\$_javassist_.*", ""), setExpression,
						where(where));
	}

	@SuppressWarnings("unchecked")
	@Override
	@TransactionTimeout(600)
	public <T> T execute(RunnableWithResult r, Object... args) throws Exception {
		try {
			return (T) r.run(this, args);
		} catch (Exception e) {
			context.setRollbackOnly();
			throw e;
		}
	}

	protected <T> String where(Object[] exp) throws Exception {
		StringBuilder w = where(exp, "", "", null);
		if (w.length() > 0)
			w.insert(0, "where ");
		return w.toString();
	}

	private StringBuilder where(Object[] exp, String prefix, String bp, String tablePrefix) throws Exception {
		Object obj = exp[0];
		Map<String, Collection<Object[]>> nonDefaultOperations = nonDefaultOperations(exp);
		StringBuilder w = new StringBuilder();
		if (obj == null)
			return w;
		PropertyDescriptor[] descriptors = PropertyUtils.getPropertyDescriptors(obj.getClass());
		String p1 = prefix == null || prefix.length() == 0 ? "" : prefix + ".";
		String p2 = prefix == null || prefix.length() == 0 ? "" : prefix.replace(".", "_") + "_";
		int orIndex = 0;
		for (PropertyDescriptor pd : descriptors) {
			if (pd.getReadMethod() == null || pd.getWriteMethod() == null)
				continue;
			if (pd.getReadMethod().isAnnotationPresent(Transient.class))
				continue;
			String key = p1 + pd.getName();
			String tp = tablePrefix;
			Object value = pd.getReadMethod().invoke(obj);
			if (value instanceof FilterCollection<?>) {
				value = ((FilterCollection<?>) value).iterator().next();
				if (tp == null)
					tp = "t_";
			}
			if (tp == null)
				tp = "t.";
			Collection<Object[]> operations = operationsWithoutFetchsAndDistinctsAndOrderBysAndAggregates(key,
					nonDefaultOperations);
			if (value == null && (!nonDefaultOperations.containsKey(key) || operations.isEmpty()))
				continue;
			if (nonDefaultOperations.containsKey(key) && !operations.isEmpty()) {
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
			} else if (value.getClass().isAnnotationPresent(Entity.class)) {
				appendClause(w, where(new Object[] { value, nonDefaultOperations }, key, bp, tp).toString());
			} else {
				appendClause(w, String.format("%s%s%s = :%s%s", tp, p1, pd.getName(), p2, bp + pd.getName()));
			}
		}
		return w;
	}

	private List<?> listWithoutNull(Collection<?> inList) {
		List<?> result = new ArrayList<Object>(inList);
		result.remove(null);
		return result;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Collection<Object[]>> nonDefaultOperations(Object[] exp) {
		Map<String, Collection<Object[]>> result = (Map<String, Collection<Object[]>>) exp[1];
		if (result == null)
			result = new LinkedHashMap<String, Collection<Object[]>>();
		return result;
	}

	protected Query createQuery(Object[] exp, String w, String[] queryString) throws Exception {
		Integer limit = limit(exp);
		String qs = queryString(exp, w);
		Query query = em.createQuery(qs);
		queryString[0] = qs;
		if (limit != null) {
			query.setMaxResults(limit);
		}
		return query;
	}

	protected String queryString(Object[] exp, String w) throws Exception {
		String collections = collections(exp, "t.");
		String distinct = collections.isEmpty() ? "" : "distinct ";
		return String.format(
				"select %s%s from %s t%s%s%s%s%s",
				distinct,
				properties(exp),
				exp[0].getClass().getSimpleName().replaceAll("_\\$\\$_javassist_.*", ""),
				beginWithSpace(collections),
				beginWithSpace(fetch(exp, "t.")),
				beginWithSpace(w),
				beginWithSpace(suffixExpression(exp, "t.", "", "groupBy", "group by ")),
				beginWithSpace(suffixExpression(exp, "t.", new String[] { "", " desc" }, new String[] { "orderBy",
						"orderByDesc" }, "order by "))).trim();
	}

	protected <T> void setParameterValues(Query q, String queryString, Object[] exp) throws Exception {
		setParameterValues(q, queryString, exp, "", "");
	}

	protected <T> void setParameterValues(Query q, String queryString, Object[] exp, String prefix, String bp)
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
			if (value == null && (!nonDefaultOperations.containsKey(key) || operations.isEmpty()))
				continue;
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
								q.setParameter(pn, trio[0]);
							} else if ("rlike".equals(operator)) {
								q.setParameter(pn, ((Object[]) operation[1])[0]);
							} else if ("tlike".equals(operator)) {
								q.setParameter(pn, ((Object[]) operation[1])[0]);
							} else {
								q.setParameter(pn, operation[1]);
							}
						}
					}
				}
			} else if (value.getClass().isAnnotationPresent(Entity.class)) {
				setParameterValues(q, queryString, new Object[] { value, nonDefaultOperations }, key, bp);
			} else {
				String parameterName = p2 + bp + pd.getName();
				q.setParameter(parameterName, value);
			}
		}
	}

	private Object[] orExpFrom(Object[] pair, String prefix) {
		Object[] orExp;
		if (pair[1] instanceof Object[])
			orExp = (Object[]) pair[1];
		else
			orExp = new Object[] { pair[1], new LinkedHashMap<String, Collection<Object[]>>() };
		@SuppressWarnings("unchecked")
		Map<String, Collection<Object[]>> nonDefaultOperationsOfOrExp = (Map<String, Collection<Object[]>>) orExp[1];
		Map<String, Collection<Object[]>> newNonDefaultOperationsOfOrExp = new LinkedHashMap<String, Collection<Object[]>>();
		for (Entry<String, Collection<Object[]>> e : nonDefaultOperationsOfOrExp.entrySet()) {
			newNonDefaultOperationsOfOrExp.put((prefix == null || prefix.isEmpty() ? "" : prefix + ".") + e.getKey(),
					e.getValue());
		}
		orExp[1] = newNonDefaultOperationsOfOrExp;
		return orExp;
	}

	@SuppressWarnings("unchecked")
	protected <T> Collection<T> runQuery(Query q) {
		return q.getResultList();
	}

	private void appendClause(StringBuilder sb, String clause) {
		if (sb.length() > 0 && clause.length() > 0)
			sb.append(" and ");
		sb.append(clause);
	}

	private String properties(Object[] exp) {
		Map<String, Collection<Object[]>> nonDefaultOperations = nonDefaultOperations(exp);
		StringBuilder distinctSb = new StringBuilder();
		StringBuilder aggregatesKeySb = new StringBuilder();
		StringBuilder aggregatesSb = new StringBuilder();
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
		String result = distinctSb.toString() + aggregatesSb;
		return result.length() == 0 ? "t" : result;
	}

	private String collections(Object[] exp, String prefix) throws Exception {
		StringBuilder sb = new StringBuilder();
		PropertyDescriptor[] descriptors = PropertyUtils.getPropertyDescriptors(exp[0].getClass());
		for (PropertyDescriptor pd : descriptors) {
			if (pd.getReadMethod() == null || pd.getWriteMethod() == null)
				continue;
			if (pd.getReadMethod().isAnnotationPresent(Transient.class))
				continue;
			Object value = pd.getReadMethod().invoke(exp[0]);
			if (value instanceof FilterCollection<?>) {
				if (sb.length() > 0)
					sb.append(" ");
				sb.append("join ");
				sb.append(prefix);
				sb.append(pd.getName());
				sb.append(" ");
				sb.append(prefix.replace(".", "_"));
				sb.append(pd.getName());
			}
		}
		return sb.toString();
	}

	private String fetch(Object[] exp, String prefix) throws Exception {
		StringBuilder sb = new StringBuilder();
		Map<String, Collection<Object[]>> nonDefaultOperations = nonDefaultOperations(exp);
		for (Entry<String, Collection<Object[]>> e : nonDefaultOperations.entrySet()) {
			for (Object[] operation : e.getValue()) {
				if ("fetch".equals(operation[0]) || "leftJoin".equals(operation[0])) {
					if (sb.length() > 0)
						sb.append(" ");
					sb.append("left join ");
					if ("fetch".equals(operation[0]))
						sb.append("fetch ");
					Object value = PropertyUtils.getProperty(exp[0], e.getKey().split("\\.")[0]);
					String p = prefix;
					if (value instanceof FilterCollection<?>) {
						p = p.replace(".", "_");
					}
					sb.append(p);
					sb.append(e.getKey());
				}
			}
		}
		return sb.toString();
	}

	protected Integer limit(Object[] exp) throws Exception {
		Map<String, Collection<Object[]>> nonDefaultOperations = nonDefaultOperations(exp);
		for (Entry<String, Collection<Object[]>> e : nonDefaultOperations.entrySet()) {
			for (Object[] operation : e.getValue()) {
				if ("limit".equals(operation[0])) {
					return (Integer) operation[1];
				}
			}
		}
		return null;
	}

	private Collection<Object[]> operationsWithoutFetchsAndDistinctsAndOrderBysAndAggregates(String key,
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

	private String suffixExpression(Object[] exp, String prefix, String[] suffix, String[] operation, String keyword) {
		StringBuilder sb = new StringBuilder();
		Map<String, Collection<Object[]>> nonDefaultOperations = nonDefaultOperations(exp);
		for (Entry<String, Collection<Object[]>> e : nonDefaultOperations.entrySet()) {
			for (Object[] eachOperation : e.getValue()) {
				for (int i = 0; i < operation.length; i++) {
					if (operation[i].equals(eachOperation[0])) {
						if (sb.length() > 0)
							sb.append(", ");
						sb.append(prefix);
						sb.append(e.getKey());
						sb.append(suffix[i]);
						break;
					}
				}
			}
		}
		if (sb.length() > 0)
			sb.insert(0, keyword);
		return sb.toString();
	}

	private String suffixExpression(Object[] exp, String prefix, String suffix, String operation, String keyword) {
		return suffixExpression(exp, prefix, new String[] { suffix }, new String[] { operation }, keyword);
	}

	private String beginWithSpace(String s) {
		if (s == null)
			return null;
		if (s.isEmpty())
			return s;
		return " " + s;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getAdapter(Class<T> c) {
		if (c.equals(NativeRepository.class))
			return (T) this;
		if (c.equals(Repository.TransactionManagement.class))
			return (T) this;
		return null;
	}

	@Override
	public int executaFuncaoNativa(String nomeFuncao, Object... parametros) {
		Collection<Object[]> retorno = executaFuncaoNativaComRetornoDeRegistro(nomeFuncao, parametros);
		return (Integer) (retorno.iterator().next() instanceof Object[] ? retorno.iterator().next()[0] : retorno
				.iterator().next());
	}

	@SuppressWarnings("unchecked")
	@Override
	public Collection<Object[]> executaFuncaoNativaComRetornoDeRegistro(String nomeFuncao, Object... parametros) {
		String qs = "select * from " + nomeFuncao + "(";
		String sep = "";
		for (int i = 0; i < parametros.length; i++) {
			qs += sep + "?";
			sep = ",";
		}
		qs += ")";
		em.flush();
		Query q = em.createNativeQuery(qs);
		int iParam = 1;
		for (Object parametro : parametros) {
			q.setParameter(iParam, parametro);
			iParam++;
		}
		return q.getResultList();
	}

	@Override
	public void flushAndClear() {
		em.flush();
		em.clear();
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> Collection<T> executarConsultaNativa(String consulta, Class<T> clazz) {
		if (clazz == null)
			return em.createNativeQuery(consulta).getResultList();
		else
			return em.createNativeQuery(consulta, clazz).getResultList();
	}

}