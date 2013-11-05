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
public class JpaRepository extends AbstractSqlRepository implements Repository, NativeRepository, Repository.TransactionManagement {

	@PersistenceContext(unitName = "RepositoryJPA")
	protected EntityManager em;
	
	@Resource
	private SessionContext context;
	
	public JpaRepository() {
	}
	
	public JpaRepository(EntityManager em) {
		this.em = em;
	}

	protected <T> Collection<T> tryMatching(Object[] exp) throws Exception {
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
			System.out.println(String.format("[Consulta levou %ds]: %s", elapsed, qs[0]));
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
			if (value instanceof FilterCollection<?>) {
				value = ((FilterCollection<?>) value).iterator().next();
				if (tp == null) tp = "t_";
			}
			if (tp == null) tp = "t.";
			Collection<Object[]> operations = operationsWithoutFetchsAndDistinctsAndOrderBysAndAggregates(key,
					nonDefaultOperations);
			if (value == null && (!nonDefaultOperations.containsKey(key) || operations.isEmpty()))
				continue;
			if (nonDefaultOperations.containsKey(key) && !operations.isEmpty()) {
				orIndex = appendNonDefaultOperations(prefix, bp, w, p1, p2, orIndex, pd, tp, operations);
			} else if (value.getClass().isAnnotationPresent(Entity.class)) {
				appendClause(w, where(new Object[] { value, nonDefaultOperations }, key, bp, tp).toString());
			} else {
				appendClause(w, String.format("%s%s%s = :%s%s", tp, p1, pd.getName(), p2, bp + pd.getName()));
			}
		}
		return w;
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

	public String queryString(Object[] exp) throws Exception {
		return queryString(exp, where(exp));
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

	protected void setParameter(Object q, String name, Object value) {
		((Query) q).setParameter(name, value);
	}

	@SuppressWarnings("unchecked")
	protected <T> Collection<T> runQuery(Query q) {
		return q.getResultList();
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

	private String properties(Object[] exp) {
		StringBuilder distinctSb = new StringBuilder();
		StringBuilder aggregatesSb = new StringBuilder();
		distinctsAndAggregates(exp, distinctSb, aggregatesSb);
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
		em.flush(); // operação necessária para que a função/procedure tenha
					// acesso aos registros recém modificados
		Query q = em.createNativeQuery(qs);
		int iParam = 1;
		for (Object parametro : parametros) {
			q.setParameter(iParam, parametro);
			iParam++;
		}
		return q.getResultList();
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> Collection<T> executarConsultaNativa(String consulta, Class<T> clazz) {
		if (clazz == null)
			return em.createNativeQuery(consulta).getResultList();
		else
			return em.createNativeQuery(consulta, clazz).getResultList();
	}

	@Override
	public void flushAndClear() {
		em.flush();
		em.clear();
	}

	private static final long serialVersionUID = 1L;

}