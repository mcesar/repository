package repositoryTest;

import javax.persistence.*;

import repository.*;

public class C {

	@Id
	private Long id;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public static class Builder<T extends Builder<?>> extends
			AbstractBuilder<T> {
		
		private C obj = new C();

		public Builder() {
			RepositoryUtil.nulifyProperties(obj);
		}

		@SuppressWarnings("unchecked")
		public T id(Long id) {
			obj.id = id;
			lastProperty = "id";
			lastValue = id;
			return (T) this;
		}

		public C build() {
			return obj;
		}
	}

	public static Builder<Builder<?>> builder() {
		return new Builder<Builder<?>>();
	}

}