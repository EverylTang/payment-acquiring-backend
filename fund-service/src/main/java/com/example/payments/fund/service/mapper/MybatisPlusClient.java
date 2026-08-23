package com.example.payments.fund.service.mapper;

import com.baomidou.mybatisplus.extension.toolkit.SqlRunner;
import java.lang.reflect.Constructor;
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

/** MyBatis-Plus SQL adapter for reconciliation queries. */
@Component
public final class MybatisPlusClient {
  private static final Pattern PARAMETER = Pattern.compile(":([A-Za-z][A-Za-z0-9_]*)");
  private final SqlRunner runner = SqlRunner.db();

  public Statement sql(String sql) {
    return new Statement(sql);
  }

  public final class Statement {
    private final String sql;
    private final Map<String, Object> params = new LinkedHashMap<>();

    private Statement(String sql) {
      this.sql = sql;
    }

    public Statement param(String name, Object value) {
      params.put(name, value);
      return this;
    }

    public int update() {
      return runner.update(render(), params.values().toArray()) ? 1 : 0;
    }

    public Query<Map<String, Object>> query() {
      return new Query<>(runner.selectList(render(), params.values().toArray()));
    }

    public <T> Query<T> query(Class<T> type) {
      return new Query<>(
          runner.selectList(render(), params.values().toArray()).stream()
              .map(row -> convert(row, type))
              .toList());
    }

    public <T> Query<T> query(RowMapper<T> mapper) {
      var rows = runner.selectList(render(), params.values().toArray());
      var out = new ArrayList<T>();
      for (int i = 0; i < rows.size(); i++) out.add(mapper.map(new Row(rows.get(i)), i));
      return new Query<>(out);
    }

    private String render() {
      var m = PARAMETER.matcher(sql);
      var b = new StringBuffer();
      var vals = new ArrayList<Object>();
      while (m.find()) {
        var n = m.group(1);
        if (!params.containsKey(n))
          throw new IllegalArgumentException("Missing SQL parameter: " + n);
        vals.add(sqlValue(params.get(n)));
        m.appendReplacement(b, Matcher.quoteReplacement("{" + (vals.size() - 1) + "}"));
      }
      m.appendTail(b);
      params.clear();
      for (int i = 0; i < vals.size(); i++) params.put(String.valueOf(i), vals.get(i));
      return b.toString();
    }
  }

  private static Object sqlValue(Object value) {
    if (value instanceof java.util.Date || value instanceof java.time.temporal.TemporalAccessor)
      return value.toString().replace('T', ' ').replace("Z", "");
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

    public String getString(String n) {
      return (String) value(n);
    }

    public int getInt(String n) {
      return ((Number) value(n)).intValue();
    }

    public long getLong(String n) {
      return ((Number) value(n)).longValue();
    }

    public boolean getBoolean(String n) {
      return Boolean.TRUE.equals(value(n));
    }

    public BigDecimal getBigDecimal(String n) {
      return (BigDecimal) value(n);
    }

    public Date getDate(String n) {
      return (Date) value(n);
    }

    private Object value(String n) {
      Object v = values.get(n);
      return v == null ? values.get(n.toUpperCase(Locale.ROOT)) : v;
    }
  }

  public static final class Query<T> {
    private final List<T> rows;

    private Query(List<T> rows) {
      this.rows = rows;
    }

    public List<T> list() {
      return rows;
    }

    public List<T> listOfRows() {
      return rows;
    }

    public T single() {
      if (rows.size() != 1) throw new IllegalStateException("Expected one row, got " + rows.size());
      return rows.get(0);
    }

    public Optional<T> optional() {
      return rows.stream().findFirst();
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T convert(Map<String, Object> row, Class<T> type) {
    if (Map.class.isAssignableFrom(type)) return (T) row;
    Object v = row.values().stream().findFirst().orElse(null);
    if (type == String.class) return (T) String.valueOf(v);
    if (type == Long.class || type == long.class) return (T) Long.valueOf(((Number) v).longValue());
    if (type == Integer.class || type == int.class)
      return (T) Integer.valueOf(((Number) v).intValue());
    try {
      if (type.isRecord()) {
        var cs = type.getRecordComponents();
        var a = new Object[cs.length];
        var ts = new Class<?>[cs.length];
        for (int i = 0; i < cs.length; i++) {
          ts[i] = cs[i].getType();
          a[i] = row.getOrDefault(cs[i].getName(), row.get(toSnake(cs[i].getName())));
        }
        Constructor<T> c = type.getDeclaredConstructor(ts);
        c.setAccessible(true);
        return c.newInstance(a);
      }
      return type.getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot map SQL row to " + type.getName(), e);
    }
  }

  private static String toSnake(String v) {
    return v.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
  }
}
