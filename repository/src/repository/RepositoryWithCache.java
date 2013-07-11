package repository;

import java.util.ArrayList;
import java.util.Collection;

public class RepositoryWithCache implements Repository {

	private Repository repositorio;

	private Repository cache = new InMemoryRepository();

	private Collection<String> classesEmCache = new ArrayList<String>();

	public RepositoryWithCache(Repository repositorio) {
		this.repositorio = repositorio;
	}

	@Override
	public <T> Collection<T> matching(T obj) {
		ensureIsInCache(obj);
		return cache.matching(obj);
	}

	@Override
	public <T> Collection<T> matching(Object[] exp) {
		ensureIsInCache(exp[0]);
		return cache.matching(exp);
	}

	@Override
	public <T> Collection<T> tryMatching(T obj) throws Exception {
		ensureIsInCache(obj);
		return cache.tryMatching(obj);
	}

	@Override
	public <T> T add(T o) throws Exception {
		ensureIsInCache(o);
		return cache.add(o);
	}

	@Override
	public void remove(Object o) throws Exception {
		ensureIsInCache(o);
		cache.remove(o);
	}

	@Override
	public void removeAll(Object o) throws Exception {
		ensureIsInCache(o);
		cache.removeAll(o);
	}

	@Override
	public int removeAll(Object[] o) throws Exception {
		ensureIsInCache(o[0]);
		return cache.removeAll(o);
	}

	@Override
	public <T> T replace(T o) throws Exception {
		ensureIsInCache(o);
		return cache.replace(o);
	}

	@Override
	public void replace(Object o, Object where) throws Exception {
		ensureIsInCache(o);
		cache.replace(o, where);
	}

	@Override
	public <T> T execute(RunnableWithResult r, Object... args) throws Exception {
		return cache.execute(r, args);
	}

	@Override
	public <T> T getAdapter(Class<T> c) {
		return cache.getAdapter(c);
	}

	private <T> void ensureIsInCache(T obj) {
		String className = obj.getClass().getName();
		if (!classesEmCache.contains(className)) {
			fillCache(obj);
			classesEmCache.add(className);
		}
	}

	private <T> void fillCache(T obj) {
		long s, e;
		s = System.currentTimeMillis();
		try {
			@SuppressWarnings("unchecked")
			T t = (T) newInstance(obj.getClass());
			Collection<T> r = todosOsObjetosDoTipo(t);
			if (r == null || r.isEmpty()) {
				cache.add(t);
				cache.removeAll(t);
			} else {
				for (T eachT : r) {
					cache.add(eachT);
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			throw new RuntimeException(ex);
		} finally {
			e = System.currentTimeMillis();
			System.out.print(String.format("Preenchendo cache para a classe %s. " + (e-s) + "ms.", obj.getClass()));
		}
	}

	protected <T> Collection<T> todosOsObjetosDoTipo(T t) {
		return repositorio.matching(t);
	}

	private <T> T newInstance(Class<T> clazz) {
		try {
			return clazz.getConstructor().newInstance();
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException();
		}
	}

	public Repository getRepositorio() {
		return repositorio;
	}

	public void removeDaCache(Object o) throws Exception {
		cache.removeAll(o);
		classesEmCache.remove(o.getClass().getName());
	}
}
