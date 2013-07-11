package repositoryTest;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import repository.JpaRepository;
import repository.Repository;

public class Main {

	public static void main(String[] args) throws Exception {
		EntityManagerFactory emf = Persistence.createEntityManagerFactory("repository");
		EntityManager em = emf.createEntityManager();
		Repository repository = new JpaRepository(em);
		em.getTransaction().begin();
		repository.add(Person.builder().name("a").build());
		em.getTransaction().commit();
		em.close();
	}
}
