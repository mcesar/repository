package repositoryTest;

import java.util.Collection;

import repository.InMemoryRepository;
import repository.Repository;

public class Main {

	public static void main(String[] args) throws Exception {
		/*
		EntityManagerFactory emf = Persistence.createEntityManagerFactory("repository");
		EntityManager em = emf.createEntityManager();
		Repository repository = new JpaRepository(em);
		em.getTransaction().begin();
		testRepository(repository);
		em.getTransaction().commit();
		em.close();
		*/
		testRepository(new InMemoryRepository());
		System.out.println("OK");
	}

	private static void testRepository(Repository repository) throws Exception {
		
		repository.removeAll(Person.builder().build());
		repository.removeAll(Address.builder().build());
		
		Address address = repository.add(Address.builder().line1("w3").build());
		System.out.println("Address " + address.getId() + " added\n");
		
		Collection<Address> addresses = repository.matching(Address.builder().line1("w3").build());
		System.out.println("Address " + address.getId() + " recovered:");
		for (Address a : addresses) {
			System.out.println("Line1: " + a.getLine1());
		}
		System.out.println("");
		
		Person person = repository.add(Person.builder().name("abc").address(address).build());
		System.out.println("Person " + person.getId() + " added\n");
		
		Collection<Person> persons = repository.matching(Person.builder().name("a").startsWith().exp());
		System.out.println("Address " + address.getId() + " recovered:");
		for (Person p : persons) {
			System.out.println("Name: " + p.getName());
		}
		System.out.println("");
		persons = repository.matching(Person.builder().address().line1("w").startsWith().end().exp());
		for (Person p : persons) {
			System.out.println("Name: " + p.getName());
		}
		System.out.println("");
	}
}
