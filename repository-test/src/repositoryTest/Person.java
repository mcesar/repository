package repositoryTest;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;

import repository.AbstractBuilder;
import repository.RepositoryUtil;

@Entity
public class Person {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "person_id_seq")
	@SequenceGenerator(name = "person_id_seq", sequenceName = "person_id_seq", allocationSize = 1)
	private Integer id;

	private String name;

	@ManyToOne
	@JoinColumn(name="address")
	private Address address;

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

		@SuppressWarnings("unchecked")
		public T address(Address address) {
			obj.address = address;
			lastProperty = "address";
			lastValue = address;
			return (T) this;
		}

		public Address.Builder<AddressBuilder> address() {
			Address.Builder<AddressBuilder> result = new AddressBuilder(this);
			obj.address = result.build();
			return result;
		}

		@SuppressWarnings("unchecked")
		public T address(Object[] exp) {
			obj.address = (Address) exp[0];
			add("address", exp);
			return (T) this;
		}

		public Person build() {
			return obj;
		}
	}

	public static Builder<Builder<?>> builder() {
		return new Builder<Builder<?>>();
	}

	public static class AddressBuilder extends Address.Builder<AddressBuilder> {
		private Builder<?> b;

		public AddressBuilder(Builder<?> b) {
			this.b = b;
		}

		public Builder<?> end() {
			b.addInnerNonDefaultOperations("address", exp()[1]);
			return b;
		}

		protected AddressBuilder add(String prefix, Object[] exp) {
			b.addInnerNonDefaultOperations("address." + prefix, exp[1]);
			return this;
		}
	}

}