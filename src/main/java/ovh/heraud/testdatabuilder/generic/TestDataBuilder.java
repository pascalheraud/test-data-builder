package ovh.heraud.testdatabuilder.generic;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * Seeds a database with rows for repository or E2E tests, using raw SQL only
 * — it never touches entity classes or repositories. Subclasses (one per
 * project) add template methods (e.g. {@code newAuxiliaire()}) that build a
 * pre-filled {@link Data} and register it via {@link #register(Data)}.
 *
 * <p>
 * Works identically whether {@code dataSource} comes from a Spring-managed
 * Testcontainers instance (repository tests) or one built directly from a
 * {@code JdbcDatabaseContainer}'s connection info (E2E tests) — the builder
 * only ever depends on the {@link DataSource}.
 */
public abstract class TestDataBuilder {

	/**
	 * The kind of column a {@link TestColumn} maps to, for value conversion in
	 * {@link #setDataColumn}.
	 */
	public enum TargetTypeEnum {
		/** No conversion needed — the default, see {@link TestColumn#targetType()}. */
		TRANSPARENT,
		/** The column expects a {@code String}. */
		STRING,
		/** The column expects a {@code java.util.Date}/{@code java.sql.Date}. */
		DATE,
		/** The column expects a {@code java.sql.Timestamp}. */
		TIMESTAMP
	}

	private static final String DEFAULT_NAME = "root";

	private final JdbcTemplate jdbcTemplate;
	private final DatabaseVendor vendor;

	private final List<Data> orderedData = new ArrayList<>();
	private final LinkedHashSet<TestTable> toDeleteTables = new LinkedHashSet<>();
	private final Map<TestTable, Data> currentByTable = new LinkedHashMap<>();
	private final Map<String, Map<TestTable, List<Data>>> dataNaming = new LinkedHashMap<>();
	private final Map<TestTable, Integer> nextIndexByTable = new LinkedHashMap<>();

	private String name = DEFAULT_NAME;

	/**
	 * Creates a builder that seeds rows into {@code dataSource}.
	 *
	 * @param dataSource the database to seed rows into
	 * @param vendor     the database vendor {@code dataSource} connects to — drives
	 *                   how {@link #resolveGeneratedKey} reads a generated id back
	 *                   after an {@code INSERT}, since the JDBC driver's key map
	 *                   shape differs by vendor.
	 */
	protected TestDataBuilder(DataSource dataSource, DatabaseVendor vendor) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
		this.vendor = vendor;
	}

	/**
	 * Direct access to the underlying {@link JdbcTemplate}, for queries/deletes
	 * outside the {@link Data} template mechanism — a project-specific
	 * subclass uses it for read-back helper methods (e.g.
	 * {@code lastMockCallContent}), and calling test code can use it directly
	 * too instead of standing up a second {@code JdbcTemplate} on the same
	 * {@code DataSource} just to read back what {@link #create} inserted.
	 *
	 * @return the {@link JdbcTemplate} wrapping this builder's {@link DataSource}
	 */
	public JdbcTemplate getJdbcTemplate() {
		return jdbcTemplate;
	}

	/**
	 * Sets {@code column} on {@code data}, converting {@code value} first if needed.
	 * The base implementation is transparent — it stores {@code value} as-is. A
	 * project-specific subclass overrides this to convert project-specific value
	 * types (e.g. a date-builder type) based on {@code column.targetType()}.
	 *
	 * @param data   the row to set the column on
	 * @param column the column to set
	 * @param value  the value to convert and store
	 * @return {@code data}, for chaining
	 */
	public Data setDataColumn(Data data, TestColumn column, Object value) {
		return data.setColumn(column, value);
	}

	/**
	 * Switches the current naming scope — subsequent {@link #register}ed
	 * {@link Data} are filed under {@code name} until this is called again.
	 *
	 * @param name the naming scope subsequent registrations fall under
	 * @return this builder, for chaining
	 */
	public TestDataBuilder withName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * Registers a {@link Data} built by a template method, under the
	 * builder's current name. Any {@link Data} it references as a column
	 * value (for a foreign key) must already be registered — template
	 * methods create and register a parent before building a child that
	 * references it, so this never needs to insert one out of order.
	 *
	 * @param data the row to register
	 * @return {@code data}, for chaining
	 */
	protected Data register(Data data) {
		orderedData.add(data);
		currentByTable.put(data.getTable(), data);
		dataNaming.computeIfAbsent(name, n -> new LinkedHashMap<>())
				.computeIfAbsent(data.getTable(), t -> new ArrayList<>()).add(data);
		return data;
	}

	/**
	 * Marks {@code table} for cleanup on the next {@link #delete()}, without
	 * registering any {@link Data} for it. For tables written as a side effect
	 * of the code under test rather than seeded by a template method (e.g.
	 * an audit/log table written by the app itself) — {@link #delete()}
	 * otherwise only clears tables it has seen through {@link #register}.
	 *
	 * <p>
	 * Calling this again for a table already marked moves it back to the
	 * most-recently-touched position (see {@link #markTouched}) — needed for
	 * the same builder to be reused across several {@link #apply()} calls
	 * (e.g. one per scenario in the same test) while keeping this table
	 * deleted first every time, not just on the first call.
	 *
	 * @param table the table to mark for cleanup
	 */
	protected void deleteTable(TestTable table) {
		markTouched(table);
	}

	/**
	 * Moves {@code table} to the end of {@link #toDeleteTables} (the
	 * most-recently-touched position), even if it was already present.
	 * {@link LinkedHashSet#add} alone does not reorder an existing element,
	 * which would otherwise freeze the touch order after the first
	 * {@link #delete()}/{@link #apply()} cycle — breaking {@link #delete}'s
	 * reverse-of-touch-order guarantee for a builder reused across several
	 * cycles.
	 *
	 * @param table the table to move to the most-recently-touched position
	 */
	private void markTouched(TestTable table) {
		toDeleteTables.remove(table);
		toDeleteTables.add(table);
	}

	/**
	 * Returns a fresh, table-scoped index (0, 1, 2, ...) on every call.
	 * Template methods use it to derive distinct default values (e.g. unique
	 * emails, incrementing numeric bases) so calling a template several times
	 * never produces colliding rows.
	 *
	 * @param table the table to derive the next index for
	 * @return the next index for {@code table}, starting at {@code 0}
	 */
	protected int nextIndex(TestTable table) {
		int index = nextIndexByTable.getOrDefault(table, 0);
		nextIndexByTable.put(table, index + 1);
		return index;
	}

	/**
	 * A blank {@link Data} for {@code table}, tagged with a fresh table-scoped
	 * index (see {@link #nextIndex}) so the caller can derive distinct
	 * defaults from {@link Data#getIndex()} without computing the index
	 * separately. Base template methods (e.g. {@code newClient()}) start
	 * from this instead of {@code new Data(table)} + a separate
	 * {@code nextIndex(table)} call.
	 *
	 * @param table the table the new row belongs to
	 * @return a blank, not-yet-registered {@link Data} for {@code table}
	 */
	protected Data newData(TestTable table) {
		return new Data(table, nextIndex(table));
	}

	/**
	 * The most recently registered {@link Data} for the given table, across
	 * all names. Backs template methods like {@code newContractForCurrentClient()}.
	 *
	 * @param table the table to look up
	 * @return the most recently registered {@link Data} for {@code table}
	 * @throws IllegalStateException if nothing has been registered for {@code table} yet
	 */
	protected Data current(TestTable table) {
		Data data = currentByTable.get(table);
		if (data == null) {
			throw new IllegalStateException("No current data for table " + table);
		}
		return data;
	}

	/**
	 * The single {@link Data} registered for {@code table} under {@code name}.
	 *
	 * @param name  the naming scope to look up (see {@link #withName})
	 * @param table the table to look up
	 * @return the single {@link Data} registered for {@code table} under {@code name}
	 * @throws IllegalStateException unless exactly one {@link Data} matches
	 */
	public Data getData(String name, TestTable table) {
		List<Data> datas = getDataList(name, table);
		if (datas.size() != 1) {
			throw new IllegalStateException(
					"Expected exactly one " + table + " named '" + name + "', found " + datas.size());
		}
		return datas.get(0);
	}

	/**
	 * Same as {@link #getData(String, TestTable)}, under the builder's current name (see {@link #withName}).
	 *
	 * @param table the table to look up
	 * @return the single {@link Data} registered for {@code table} under the current name
	 */
	public Data getData(TestTable table) {
		return getData(name, table);
	}

	/**
	 * Every {@link Data} registered for {@code table} under {@code name}.
	 *
	 * @param name  the naming scope to look up (see {@link #withName})
	 * @param table the table to look up
	 * @return every {@link Data} registered for {@code table} under {@code name}, in registration order
	 */
	public List<Data> getDataList(String name, TestTable table) {
		return dataNaming.getOrDefault(name, Map.of()).getOrDefault(table, List.of());
	}

	/**
	 * Same as {@link #getDataList(String, TestTable)}, under the builder's current name (see {@link #withName}).
	 *
	 * @param table the table to look up
	 * @return every {@link Data} registered for {@code table} under the current name, in registration order
	 */
	public List<Data> getDataList(TestTable table) {
		return getDataList(name, table);
	}

	/**
	 * Number of rows currently in {@code table} — e.g. to assert
	 * {@link #delete()}/{@link #apply()} actually cleared it.
	 *
	 * @param table the table to count rows in
	 * @return the number of rows currently in {@code table}
	 */
	public int countRows(TestTable table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table.getSqlName(), Integer.class);
	}

	/**
	 * {@code delete()} then {@code create()} — call once all {@link Data} have
	 * been declared.
	 */
	public void apply() {
		delete();
		create();
	}

	/**
	 * Issues {@code DELETE FROM <table>;} for every table touched, in reverse
	 * touched order (children before parents) to respect foreign keys — see
	 * {@link #toDeleteTables}, populated by {@link #insert}, {@link #deleteTable},
	 * and {@link #withDeleteAll}.
	 */
	public void delete() {
		List<TestTable> reversed = new ArrayList<>(toDeleteTables);
		Collections.reverse(reversed);
		for (TestTable table : reversed) {
			jdbcTemplate.execute("DELETE FROM " + table.getSqlName());
		}
	}

	/**
	 * Replaces whatever was already marked for deletion with exactly
	 * {@code tables}, for tests that want a fully clean slate on the next
	 * {@link #delete()}/{@link #apply()} regardless of what this builder
	 * actually touched. Pass them in the order you want them deleted
	 * (children before parents, respecting every foreign key) — {@link #delete()}'s
	 * usual reverse-of-touched-order pass is accounted for internally, so
	 * the order given here is the order they're actually deleted in.
	 *
	 * @param tables the tables to delete, in the order they should be deleted
	 * @return this builder, for chaining
	 */
	public TestDataBuilder withDeleteAll(TestTable... tables) {
		toDeleteTables.clear();
		for (int i = tables.length - 1; i >= 0; i--) {
			toDeleteTables.add(tables[i]);
		}
		return this;
	}

	/**
	 * Inserts every registered {@link Data} not yet {@link Data#isAdded()} in
	 * registration order, resolving FK references to already-inserted
	 * {@link Data}'s generated id. Safe to call more than once: rows already
	 * inserted by an earlier {@code create()} call are skipped, so more
	 * {@link Data} can be registered and inserted afterward without
	 * re-inserting (or re-{@link #delete()}ing) what's already there.
	 */
	public void create() {
		for (Data data : orderedData) {
			if (!data.isAdded()) {
				insert(data);
			}
		}
	}

	/**
	 * Reads the generated id back from the key map returned by the JDBC driver
	 * after an {@code INSERT} (via {@link java.sql.Statement#RETURN_GENERATED_KEYS}).
	 * The entry the id comes back under differs by {@link DatabaseVendor} —
	 * this is the one vendor-specific extension point of the whole builder,
	 * driven entirely by the {@code vendor} given to the constructor rather
	 * than by subclassing.
	 *
	 * @param keys the generated-key map returned by the JDBC driver for one inserted row
	 * @return the generated id, read from the entry {@link #vendor} is known to use
	 */
	private Long resolveGeneratedKey(Map<String, Object> keys) {
		return switch (vendor) {
			case POSTGRESQL -> extractGeneratedKey(keys, "id");
			case ORACLE -> extractGeneratedKey(keys, "ID", "id");
			case MYSQL -> extractGeneratedKey(keys, "GENERATED_KEY");
			case MARIADB -> extractGeneratedKey(keys, "insert_id");
		};
	}

	/**
	 * @param keys              the generated-key map returned by the JDBC driver
	 * @param candidateColumns  the key entries to try, in order, until one is present
	 * @return the generated id, read from the first present entry in {@code candidateColumns}
	 * @throws IllegalStateException if none of {@code candidateColumns} is present in {@code keys}
	 */
	private Long extractGeneratedKey(Map<String, Object> keys, String... candidateColumns) {
		for (String candidate : candidateColumns) {
			Object value = keys.get(candidate);
			if (value != null) {
				return ((Number) value).longValue();
			}
		}
		throw new IllegalStateException(
				"No generated key found for " + vendor + " among " + Arrays.toString(candidateColumns)
						+ " in " + keys);
	}

	/** @param data the row to insert — must not already be {@link Data#isAdded()} */
	private void insert(Data data) {
		List<String> columns = new ArrayList<>();
		List<String> placeholders = new ArrayList<>();
		List<Object> values = new ArrayList<>();
		for (Map.Entry<String, Object> entry : data.getColumns().entrySet()) {
			columns.add(entry.getKey());
			Object value = entry.getValue();
			if (value instanceof SqlExpression expression) {
				placeholders.add(expression.sql());
				values.addAll(Arrays.asList(expression.params()));
				continue;
			}
			if (value instanceof Data referenced) {
				value = referenced.getGeneratedId();
			}
			placeholders.add("?");
			values.add(value);
		}
		String sql = "INSERT INTO " + data.getTable().getSqlName() + " ("
				+ String.join(", ", columns) + ") VALUES ("
				+ String.join(", ", placeholders) + ")";

		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement statement = connection.prepareStatement(sql, new String[] { "id" });
			for (int i = 0; i < values.size(); i++) {
				statement.setObject(i + 1, values.get(i));
			}
			return statement;
		}, keyHolder);

		Long generatedId = resolveGeneratedKey(keyHolder.getKeys());
		data.setGeneratedId(generatedId);
		data.markAdded();
		markTouched(data.getTable());
	}
}
