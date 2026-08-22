package com.example.payments.platform.service.infrastructure.persistence;

import com.baomidou.mybatisplus.extension.toolkit.SqlRunner;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** MyBatis-Plus SQL adapter used by legacy admin application services. */
@Component
public final class MybatisPlusClient {
  private static final Pattern NAMED_PARAMETER = Pattern.compile(":([A-Za-z][A-Za-z0-9_]*)");
  private final SqlRunner runner = SqlRunner.db();

  public StatementSpec sql(String sql) {
    return new StatementSpec(sql);
  }

  public class StatementSpec {
    private final String sql;
    private final Map<String, Object> parameters = new LinkedHashMap<>();

    private StatementSpec(String sql) {
      this.sql = sql;
    }

    public StatementSpec param(String name, Object value) {
      parameters.put(name, value);
      return this;
    }

    public int update() {
      return runner.update(renderSql(), parameters.values().toArray()) ? 1 : 0;
    }

    public Query<Map<String, Object>> query() {
      return new Query<>(runner.selectList(renderSql(), parameters.values().toArray()), Map.class);
    }

    public <T> Query<T> query(Class<T> type) {
      var rows = runner.selectList(renderSql(), parameters.values().toArray());
      return new Query<>(rows.stream().map(row -> convert(row, type)).toList(), type);
    }

    public <T> Query<T> query(RowMapper<T> mapper) {
      var rows = runner.selectList(renderSql(), parameters.values().toArray());
      var mapped = new ArrayList<T>(rows.size());
      for (int i = 0; i < rows.size(); i++) mapped.add(mapper.map(new Row(rows.get(i)), i));
      return new Query<>(mapped, null);
    }

    private String renderSql() {
      var matcher = NAMED_PARAMETER.matcher(sql);
      var rendered = new StringBuffer();
      var values = new ArrayList<Object>();
      while (matcher.find()) {
        var name = matcher.group(1);
        if (!parameters.containsKey(name)) throw new IllegalArgumentException("Missing SQL parameter: " + name);
        values.add(sqlValue(parameters.get(name)));
        matcher.appendReplacement(rendered, Matcher.quoteReplacement("{" + (values.size() - 1) + "}"));
      }
      matcher.appendTail(rendered);
      parameters.clear();
      for (int i = 0; i < values.size(); i++) parameters.put(String.valueOf(i), values.get(i));
      return rendered.toString();
    }
  }

  private static Object sqlValue(Object value) {
    if (value instanceof java.util.Date || value instanceof java.time.temporal.TemporalAccessor) {
      return value.toString().replace('T', ' ').replace("Z", "");
    }
    return value;
  }

  @FunctionalInterface
  public interface RowMapper<T> {
    T map(Row row, int rowNumber);
  }

  public static final class Row {
    private final Map<String, Object> values;

    private Row(Map<String, Object> values) {
      this.values = values;
    }

    public String getString(String name) { return value(name, String.class); }
    public int getInt(String name) { return ((Number) value(name, Number.class)).intValue(); }
    public long getLong(String name) { return ((Number) value(name, Number.class)).longValue(); }
    public boolean getBoolean(String name) { return Boolean.TRUE.equals(value(name, Object.class)); }
    public BigDecimal getBigDecimal(String name) { return value(name, BigDecimal.class); }
    public Date getDate(String name) { return value(name, Date.class); }

    @SuppressWarnings("unchecked")
    private <T> T value(String name, Class<T> type) {
      Object value = values.get(name);
      if (value == null) value = values.get(name.toUpperCase(Locale.ROOT));
      return (T) value;
    }
  }

  public static final class Query<T> {
    private final List<T> rows;
    private Query(List<T> rows, Class<?> ignored) { this.rows = rows; }
    public List<T> list() { return rows; }
    public List<T> listOfRows() { return rows; }
    public T single() {
      if (rows.size() != 1) throw new IllegalStateException("Expected one row, got " + rows.size());
      return rows.get(0);
    }
    public Optional<T> optional() { return rows.stream().findFirst(); }
  }

  @SuppressWarnings("unchecked")
  private static <T> T convert(Map<String, Object> row, Class<T> type) {
    if (Map.class.isAssignableFrom(type)) return (T) row;
    Object first = row.values().stream().findFirst().orElse(null);
    if (type == String.class) return (T) String.valueOf(first);
    if (type == Long.class || type == long.class) return (T) Long.valueOf(((Number) first).longValue());
    if (type == Integer.class || type == int.class) return (T) Integer.valueOf(((Number) first).intValue());
    if (type == BigDecimal.class) return (T) first;
    try {
      if (type.isRecord()) {
        RecordComponent[] components = type.getRecordComponents();
        Object[] args = new Object[components.length];
        Class<?>[] types = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
          types[i] = components[i].getType();
          args[i] = rowValue(row, components[i].getName(), types[i]);
        }
        Constructor<T> constructor = type.getDeclaredConstructor(types);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
      }
      return type.getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot map SQL row to " + type.getName(), e);
    }
  }

  private static Object rowValue(Map<String, Object> row, String name, Class<?> type) {
    Object value = row.get(name);
    if (value == null) value = row.get(toSnakeCase(name));
    if (value == null) value = row.get(name.toUpperCase(Locale.ROOT));
    if (value == null || type.isInstance(value)) return value;
    if ((type == Long.class || type == long.class) && value instanceof Number n) return n.longValue();
    if ((type == Integer.class || type == int.class) && value instanceof Number n) return n.intValue();
    if ((type == Boolean.class || type == boolean.class) && value instanceof Number n) return n.intValue() != 0;
    if (type == java.time.Instant.class && value instanceof java.util.Date date) return date.toInstant();
    if (type == java.time.LocalDateTime.class && value instanceof java.util.Date date)
      return date.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDateTime();
    if (type == java.time.LocalDate.class && value instanceof java.sql.Date date) return date.toLocalDate();
    if (type == String.class) return String.valueOf(value);
    return value;
  }

  private static String toSnakeCase(String value) {
    return value.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
  }
}
