package repository;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class FilterCollection<E> extends AbstractCollection<E> {

	private Collection<E> col = new ArrayList<E>();

	public FilterCollection(E e) {
		col.add(e);
	}

	@Override
	public Iterator<E> iterator() {
		return col.iterator();
	}

	@Override
	public int size() {
		return col.size();
	}

}