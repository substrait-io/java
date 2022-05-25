package io.substrait.isthmus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.substrait.plan.Plan;
import io.substrait.plan.PlanProtoConverter;
import io.substrait.plan.ProtoPlanConverter;
import io.substrait.proto.AggregateFunction;
import java.io.IOException;
import java.util.Arrays;
import org.apache.calcite.sql.parser.SqlParseException;
import org.junit.jupiter.api.Test;

public class ProtoPlanConverterTest extends PlanTestBase {
  private void assertProtoRelRoundrip(String query) throws IOException, SqlParseException {
    SqlToSubstrait s = new SqlToSubstrait();
    String[] values = asString("tpch/schema.sql").split(";");
    var creates = Arrays.stream(values).filter(t -> !t.trim().isBlank()).toList();
    io.substrait.proto.Plan protoPlan1 = s.execute(query, creates);
    String planStr1 = protoPlan1.toString();
    Plan plan = new ProtoPlanConverter().from(protoPlan1);
    io.substrait.proto.Plan protoPlan2 = new PlanProtoConverter().toProto(plan);
    assertEquals(protoPlan1, protoPlan2);
  }

  private io.substrait.proto.Plan getProtoPlan(String query1)
      throws IOException, SqlParseException {
    SqlToSubstrait s = new SqlToSubstrait();
    String[] values = asString("tpch/schema.sql").split(";");
    var creates = Arrays.stream(values).filter(t -> !t.trim().isBlank()).toList();
    return s.execute(query1, creates);
  }

  @Test
  public void aggregate() throws IOException, SqlParseException {
    assertProtoRelRoundrip("select count(L_ORDERKEY),sum(L_ORDERKEY) from lineitem");
  }

  private static void assertAggregateInvocationDistinct(io.substrait.proto.Plan plan) {
    assertEquals(
        AggregateFunction.AggregationInvocation.AGGREGATION_INVOCATION_DISTINCT,
        plan.getRelations(0)
            .getRoot()
            .getInput()
            .getAggregate()
            .getMeasuresList()
            .get(0)
            .getMeasure()
            .getInvocation());
  }

  @Test
  public void distinctCount() throws IOException, SqlParseException {
    String distinctQuery = "select count(DISTINCT L_ORDERKEY) from lineitem";
    io.substrait.proto.Plan protoPlan = getProtoPlan(distinctQuery);
    assertAggregateInvocationDistinct(protoPlan);
    assertAggregateInvocationDistinct(
        new PlanProtoConverter().toProto(new ProtoPlanConverter().from(protoPlan)));
  }

  @Test
  public void filter() throws IOException, SqlParseException {
    assertProtoRelRoundrip("select L_ORDERKEY from lineitem WHERE L_ORDERKEY + 1 > 10");
  }

  @Test
  public void joinAggSortLimit() throws IOException, SqlParseException {
    assertProtoRelRoundrip(
        "select\n"
            + "  l.l_orderkey,\n"
            + "  sum(l.l_extendedprice * (1 - l.l_discount)) as revenue,\n"
            + "  o.o_orderdate,\n"
            + "  o.o_shippriority\n"
            + "\n"
            + "from\n"
            + "  \"customer\" c,\n"
            + "  \"orders\" o,\n"
            + "  \"lineitem\" l\n"
            + "\n"
            + "where\n"
            + "  c.c_mktsegment = 'HOUSEHOLD'\n"
            + "  and c.c_custkey = o.o_custkey\n"
            + "  and l.l_orderkey = o.o_orderkey\n"
            + "  and o.o_orderdate < date '1995-03-25'\n"
            + "  and l.l_shipdate > date '1995-03-25'\n"
            + "\n"
            + "group by\n"
            + "  l.l_orderkey,\n"
            + "  o.o_orderdate,\n"
            + "  o.o_shippriority\n"
            + "order by\n"
            + "  revenue desc,\n"
            + "  o.o_orderdate\n"
            + "limit 10");
  }

  @Test
  public void existsCorrelatedSubquery() throws IOException, SqlParseException {
    assertProtoRelRoundrip(
        "select l_partkey from lineitem where exists (select o_orderdate from orders where o_orderkey = l_orderkey)");
  }

  @Test
  public void uniqueCorrelatedSubquery() throws IOException, SqlParseException {
    assertProtoRelRoundrip(
        "select l_partkey from lineitem where unique (select o_orderdate from orders where o_orderkey = l_orderkey)");
  }

  @Test
  public void inPredicateCorrelatedSubQuery() throws IOException, SqlParseException {
    assertProtoRelRoundrip(
        "select l_orderkey from lineitem where l_partkey in (select p_partkey from part where p_partkey = l_partkey)");
  }

  @Test
  public void notInPredicateCorrelatedSubquery() throws IOException, SqlParseException {
    assertProtoRelRoundrip(
        "select l_orderkey from lineitem where l_partkey not in (select p_partkey from part where p_partkey = l_partkey)");
  }

  @Test
  public void existsNestedCorrelatedSubquery() throws IOException, SqlParseException {
    String sql =
        "SELECT p_partkey\n"
            + "FROM part p\n"
            + "WHERE EXISTS\n"
            + "    (SELECT *\n"
            + "     FROM lineitem l\n"
            + "     WHERE l.l_partkey = p.p_partkey\n"
            + "       AND UNIQUE\n"
            + "         (SELECT *\n"
            + "          FROM partsupp ps\n"
            + "          WHERE ps.ps_partkey = p.p_partkey\n"
            + "          AND   PS.ps_suppkey = l.l_suppkey))";
    assertProtoRelRoundrip(sql);
  }

  @Test
  public void nestedScalarCorrelatedSubquery() throws IOException, SqlParseException {
    assertProtoRelRoundrip(asString("subquery/nested_scalar_subquery_in_filter.sql"));
  }
}
