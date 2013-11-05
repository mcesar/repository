package repository.voltdb;

import java.util.*;
import org.voltdb.*;

public class CollectionFactory {

	public static class VoltCollection extends AbstractCollection<Object[]> {

		private VoltTable table;

		private VoltIterator iterator;

		public VoltCollection(VoltTable table) {
			this.table = table;
			this.iterator = new VoltIterator(table);
		}

		public Iterator<Object[]> iterator() {
			return iterator;
		}

		public int size() {
			return table.getRowCount();
		}
	}

	public static class VoltIterator implements Iterator<Object[]> {

		private VoltTable table;

		public VoltIterator(VoltTable table) {
			this.table = table;
		}

		public void remove() {
		}

		public Object[] next() {
			if (!hasNext()) throw new RuntimeException("Não há mais elementos na coleção");
			table.advanceRow();
			Object[] result = new Object[table.getColumnCount()];
			for (int i = 0; i < table.getColumnCount(); i++) {
				result[i] = table.get(i, table.getColumnType(i));
			}
			return result;
		}

		public boolean hasNext() {
			return table.getActiveRowIndex() < table.getRowCount() - 1;
		}
	}

	public static Collection<Object[]> newVoltCollection(VoltTable[] tables) {
		if (tables == null || tables.length == 0) throw new IllegalArgumentException();
		return new VoltCollection(tables[0]);
	}

}