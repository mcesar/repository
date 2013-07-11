package repository;

public interface RunnableWithResult {

	public Object run(Repository repositorio, Object...args) throws Exception;
}