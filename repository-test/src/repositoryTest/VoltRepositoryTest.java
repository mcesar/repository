package repositoryTest;

import java.util.*;

import repository.*;

public class VoltRepositoryTest implements RunnableWithResult {
	public Object run(Repository repository, Object...args) throws Exception {
	    String name = (String) args[0];
	    Repository.BatchProcessor batch = repository.getAdapter(Repository.BatchProcessor.class);
	    Collection<Long> values = new ArrayList();

        for (long i = 0; i < 72; i++) {
	      values.add(i);
    	}

	    long count = (Long)
    	  ((Object[]) repository.matching(C.builder().id(null).count().exp())
      		.iterator().next())[0];

	    batch.add(C.builder()
	      .id(count + 1)
	      .build());

	    for (int i = 0; i < 6000; i++) {
	      batch.add(C.builder()
	        .id((count + 1) * 10000 + i)
	        .build());
	    }
	    batch.submit();

		return null;
	}
}