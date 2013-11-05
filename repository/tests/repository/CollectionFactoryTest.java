package repository;

import java.util.*;

import org.junit.*;
import static org.junit.Assert.*;

import org.voltdb.*;

import repository.voltdb.*;

public class CollectionFactoryTest {

	private Collection<Object[]> col1;

	private Collection<Object[]> col2;

	@Before
	public void setup() {
		VoltTable[] t1 = { new VoltTable(new VoltTable.ColumnInfo("col1", VoltType.BIGINT)) };
		t1[0].addRow(1);
		col1 = CollectionFactory.newVoltCollection(t1);

		VoltTable[] t2 = { new VoltTable(new VoltTable.ColumnInfo("col1", VoltType.BIGINT), 
			new VoltTable.ColumnInfo("col2", VoltType.STRING)) };
		t2[0].addRow(1, "a");
		t2[0].addRow(2, "b");
		col2 = CollectionFactory.newVoltCollection(t2);
	}

	@Test
	public void newVoltCollection_NullTable() {
		try {
			Collection<Object[]> col = CollectionFactory.newVoltCollection(null);
			fail("Não é permitido o uso de coleções nulas");
		} catch (IllegalArgumentException e) {
		}
	}

	@Test
	public void newVoltCollection_TableWithOneRow_size() {
		assertEquals(1, col1.size());
	}

	@Test
	public void newVoltCollection_TableWithOneRow_hasNext() {
		Iterator<Object[]> it = col1.iterator();
		assertTrue(it.hasNext());
		it.next();
		assertFalse(it.hasNext());
	}

	@Test
	public void newVoltCollection_TableWithOneRow_next() {
		Iterator<Object[]> it = col1.iterator();
		Object[] arr = it.next();
		assertEquals(1l, arr[0]);
	}

	@Test
	public void newVoltCollection_TableWithTwoRows_size() {
		assertEquals(2, col2.size());
	}

	@Test
	public void newVoltCollection_TableWithTwoRows_hasNext() {
		Iterator<Object[]> it = col2.iterator();
		assertTrue(it.hasNext());
		it.next();
		assertTrue(it.hasNext());
		it.next();
		assertFalse(it.hasNext());
	}

	@Test
	public void newVoltCollection_TableWithTwoRows_next() {
		Iterator<Object[]> it = col2.iterator();
		Object[] arr = it.next();
		assertEquals(1l, arr[0]);
		assertEquals("a", arr[1]);
		arr = it.next();
		assertEquals(2l, arr[0]);
		assertEquals("b", arr[1]);
	}

}