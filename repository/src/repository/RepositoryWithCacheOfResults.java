package repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.persistence.Query;

import org.hibernate.ejb.QueryImpl;

public class RepositoryWithCacheOfResults implements Repository {

	private Repository repositorio;

	private Map<String, Object> cacheDeResultados = new LinkedHashMap<String, Object>();

	private MeuRepositorioServico repositorioServico = new MeuRepositorioServico();

	private Collection<Object> parameters;

	private class MeuRepositorioServico extends JpaRepository {

		public String queryString(Object[] exp) {
			try {
				return super.queryString(exp, where(exp));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		};

		@Override
		public <T> void setParameterValues(Query q, String queryString, Object[] exp) {
			try {
				super.setParameterValues(q, queryString, exp);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	private Query query = new QueryImpl(null, null) {

		@Override
		public Query setParameter(String name, Object value) {
			parameters.add(name);
			if (value == null)
				parameters.add("null");
			else if (value.getClass().isArray())
				parameters.add(Arrays.toString((Object[]) value));
			else
				parameters.add(value.toString());
			return query;
		};
	};

	public RepositoryWithCacheOfResults(Repository repositorio) {
		this.repositorio = repositorio;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> Collection<T> matching(T obj) {
		Object[] exp = new Object[] { obj, new LinkedHashMap<Object, Object>() };
		String chave = chave(exp);
		if (cacheDeResultados.containsKey(chave)) return (Collection<T>) cacheDeResultados.get(chave);
		Collection<T> resultado = repositorio.matching(obj);
		cacheDeResultados.put(chave, resultado);
		return resultado;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> Collection<T> matching(Object[] exp) {
		String chave = chave(exp);
		if (cacheDeResultados.containsKey(chave)) return (Collection<T>) cacheDeResultados.get(chave);
		Collection<T> resultado = repositorio.matching(exp);
		cacheDeResultados.put(chave, resultado);
		return resultado;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> Collection<T> tryMatching(T obj) throws Exception {
		Object[] exp = new Object[] { obj, new LinkedHashMap<Object, Object>() };
		String chave = chave(exp);
		if (cacheDeResultados.containsKey(chave)) return (Collection<T>) cacheDeResultados.get(chave);
		Collection<T> resultado = repositorio.tryMatching(obj);
		cacheDeResultados.put(chave, resultado);
		return resultado;
	}

	@Override
	public <T> T add(T o) throws Exception {
		return repositorio.add(o);
	}

	@Override
	public void remove(Object o) throws Exception {
		repositorio.remove(o);
	}

	@Override
	public void removeAll(Object o) throws Exception {
		repositorio.removeAll(o);
	}

	@Override
	public int removeAll(Object[] o) throws Exception {
		return repositorio.removeAll(o);
	}

	@Override
	public <T> T replace(T o) throws Exception {
		return repositorio.replace(o);
	}

	@Override
	public void replace(Object o, Object where) throws Exception {
		repositorio.replace(o, where);
	}

	@Override
	public <T> T execute(RunnableWithResult r, Object... args) throws Exception {
		return repositorio.execute(r, args);
	}

	@Override
	public <T> T getAdapter(Class<T> c) {
		return repositorio.getAdapter(c);
	}

	private String chave(Object[] exp) {
		String qs = repositorioServico.queryString(exp);
		parameters = new ArrayList<Object>();
		repositorioServico.setParameterValues(query, qs, exp);
		String chave = qs + "|" + parameters.toString();
		return chave;
	}

}