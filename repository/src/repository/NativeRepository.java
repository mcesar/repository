package repository;

import java.util.Collection;

public interface NativeRepository {

	int executaFuncaoNativa(String nomeFuncao, Object... parametros);

	Collection<Object[]> executaFuncaoNativaComRetornoDeRegistro(String nomeFuncao, Object... parametros);

	<T> Collection<T> executarConsultaNativa(String consulta, Class<T> clazz);

}
