package repository;

public interface RepositoryWithIndexes {

	public abstract class Indice<C> {
		public abstract Object chave(C c);
	}

	<C> void adicionarIndice(Indice<C> indice);
}
