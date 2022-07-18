package io.substrait.isthmus.expression;

import com.google.common.collect.ImmutableList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;

public class FunctionMappings {
  // Static list of signature mapping between Calcite SQL operators and Substrait base function
  // names.
  public static final ImmutableList<Sig> SCALAR_SIGS;
  public static final ImmutableList<Sig> AGGREGATE_SIGS;
  public static final ImmutableList<Sig> WINDOW_SIGS;
  public static final Map<SqlOperator, TypeBasedResolver> OPERATOR_RESOLVER;

  static {
    SCALAR_SIGS =
        ImmutableList.<Sig>builder()
            .add(
                s(SqlStdOperatorTable.PLUS, "add"),
                s(SqlStdOperatorTable.MINUS, "subtract"),
                s(SqlStdOperatorTable.MULTIPLY, "multiply"),
                s(SqlStdOperatorTable.DIVIDE, "divide"),
                s(SqlStdOperatorTable.AND),
                s(SqlStdOperatorTable.OR),
                s(SqlStdOperatorTable.NOT),
                s(SqlStdOperatorTable.LESS_THAN, "lt"),
                s(SqlStdOperatorTable.LESS_THAN_OR_EQUAL, "lte"),
                s(SqlStdOperatorTable.GREATER_THAN, "gt"),
                s(SqlStdOperatorTable.GREATER_THAN_OR_EQUAL, "gte"),
                s(SqlStdOperatorTable.EQUALS, "equal"),
                s(SqlStdOperatorTable.BIT_XOR, "xor"),
                s(SqlStdOperatorTable.IS_NULL, "is_null"),
                s(SqlStdOperatorTable.IS_NOT_NULL, "is_not_null"),
                s(SqlStdOperatorTable.NOT_EQUALS, "not_equal"),
                s(SqlStdOperatorTable.MINUS_DATE, "subtract"),
                s(SqlStdOperatorTable.DATETIME_PLUS, "add"),
                s(SqlStdOperatorTable.EXTRACT, "extract"),
                s(SqlStdOperatorTable.LIKE),
                s(SqlStdOperatorTable.SUBSTRING, "substring"),
                s(SqlStdOperatorTable.CONCAT, "concat"))
            .build();

    AGGREGATE_SIGS =
        ImmutableList.<Sig>builder()
            .add(
                s(SqlStdOperatorTable.MIN, "min"),
                s(SqlStdOperatorTable.MAX, "max"),
                s(SqlStdOperatorTable.SUM, "sum"),
                s(SqlStdOperatorTable.COUNT, "count"),
                s(SqlStdOperatorTable.APPROX_COUNT_DISTINCT, "approx_count_distinct"),
                s(SqlStdOperatorTable.AVG, "avg"))
            .build();

    WINDOW_SIGS =
        ImmutableList.<Sig>builder()
            .add(
                s(SqlStdOperatorTable.ROW_NUMBER, "row_number"),
                s(SqlStdOperatorTable.LAG, "lag"),
                s(SqlStdOperatorTable.LEAD, "lead"),
                s(SqlStdOperatorTable.RANK, "rank"),
                s(SqlStdOperatorTable.DENSE_RANK, "dense_rank"),
                s(SqlStdOperatorTable.PERCENT_RANK, "percent_rank"),
                s(SqlStdOperatorTable.CUME_DIST, "cume_dist"),
                s(SqlStdOperatorTable.NTILE, "ntile"),
                s(SqlStdOperatorTable.FIRST_VALUE, "first_value"),
                s(SqlStdOperatorTable.LAST_VALUE, "last_value"),
                s(SqlStdOperatorTable.NTH_VALUE, "nth_value"))
            .build();

    // contains return-type based resolver for both scalar and aggregator operator
    OPERATOR_RESOLVER =
        Map.of(
            SqlStdOperatorTable.PLUS,
                resolver(
                    SqlStdOperatorTable.PLUS,
                    Set.of("i8", "i16", "i32", "i64", "f32", "f64", "decimal")),
            SqlStdOperatorTable.DATETIME_PLUS,
                resolver(SqlStdOperatorTable.PLUS, Set.of("date", "time", "timestamp")),
            SqlStdOperatorTable.MINUS,
                resolver(
                    SqlStdOperatorTable.MINUS,
                    Set.of("i8", "i16", "i32", "i64", "f32", "f64", "decimal")),
            SqlStdOperatorTable.MINUS_DATE,
                resolver(
                    SqlStdOperatorTable.MINUS_DATE, Set.of("date", "timestamp_tz", "timestamp")));
  }

  public static void main(String[] args) {
    SCALAR_SIGS.forEach(System.out::println);
  }

  public static Sig s(SqlOperator operator, String substraitName) {
    return new Sig(operator, substraitName.toLowerCase(Locale.ROOT));
  }

  public static Sig s(SqlOperator operator) {
    return s(operator, operator.getName().toLowerCase(Locale.ROOT));
  }

  record Sig(SqlOperator operator, String name) {}

  public static TypeBasedResolver resolver(SqlOperator operator, Set<String> outTypes) {
    return new TypeBasedResolver(operator, outTypes);
  }

  record TypeBasedResolver(SqlOperator operator, Set<String> types) {}
}
