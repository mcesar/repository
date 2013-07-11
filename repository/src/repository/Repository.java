package repository;

import java.util.Collection;

import javax.ejb.Local;

@Local
public interface Repository {

	public <T> Collection<T> matching(T obj);

	public <T> Collection<T> matching(Object[] exp);

	public <T> Collection<T> tryMatching(T obj) throws Exception;

	public <T> T add(T o) throws Exception;

	public void remove(Object o) throws Exception;
	
	public void removeAll(Object o) throws Exception;
	
	public int removeAll(Object[] o) throws Exception;

	public <T> T replace(T o) throws Exception;
	
	public void replace(Object o, Object where) throws Exception;

	public <T> T execute(RunnableWithResult r, Object...args) throws Exception;
	
	public <T> T getAdapter(Class<T> c);
	
	public interface TransactionManagement {
		public void flushAndClear();
	}

}