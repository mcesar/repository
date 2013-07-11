package repositoryTest;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;

import repository.AbstractBuilder;
import repository.RepositoryUtil;

@Entity
public class Person {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator="person_id_seq")
	@SequenceGenerator(name = "person_id_seq", sequenceName = "person_id_seq", allocationSize = 1)
	private Integer id;

	private String name;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public static class Builder<T extends Builder<?>> extends
			AbstractBuilder<T> {
		private Person obj = new Person();

		public Builder() {
			RepositoryUtil.nulifyProperties(obj);
		}

		@SuppressWarnings("unchecked")
		public T id(Integer id) {
			obj.id = id;
			lastProperty = "id";
			lastValue = id;
			return (T) this;
		}

		@SuppressWarnings("unchecked")
		public T name(String name) {
			obj.name = name;
			lastProperty = "name";
			lastValue = name;
			return (T) this;
		}

		public Person build() {
			return obj;
		}
	}

	public static Builder<Builder<?>> builder() {
		return new Builder<Builder<?>>();
	}
}
