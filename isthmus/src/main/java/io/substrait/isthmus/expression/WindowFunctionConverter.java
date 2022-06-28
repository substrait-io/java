package io.substrait.isthmus.expression;

import com.google.common.collect.ImmutableList;
import io.substrait.expression.Expression;
import io.substrait.expression.ExpressionCreator;
import io.substrait.expression.WindowFunctionInvocation;
import io.substrait.function.SimpleExtension;
import io.substrait.isthmus.SubstraitRelVisitor;
import io.substrait.proto.AggregateFunction;
import io.substrait.type.Type;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexNode;

public class WindowFunctionConverter
    extends FunctionConverter<
        SimpleExtension.WindowFunctionVariant,
        WindowFunctionInvocation,
        WindowFunctionConverter.WrappedAggregateCall>
    implements NonScalarFuncConverter<WindowFunctionInvocation, RexNode, Expression> {

  @Override
  protected ImmutableList<FunctionMappings.Sig> getSigs() {
    return FunctionMappings.WINDOW_SIGS;
  }

  public WindowFunctionConverter(
      List<SimpleExtension.WindowFunctionVariant> functions, RelDataTypeFactory typeFactory) {
    super(functions, typeFactory);
  }

  public WindowFunctionConverter(
      List<SimpleExtension.WindowFunctionVariant> functions,
      List<FunctionMappings.Sig> additionalSignatures,
      RelDataTypeFactory typeFactory) {
    super(functions, additionalSignatures, typeFactory);
  }

  @Override
  protected WindowFunctionInvocation generateBinding(
      WrappedAggregateCall call,
      SimpleExtension.WindowFunctionVariant function,
      List<Expression> arguments,
      Type outputType) {
    AggregateCall agg = call.getUnderlying();

    List<Expression.SortField> sorts =
        agg.getCollation() != null
            ? agg.getCollation().getFieldCollations().stream()
                .map(r -> SubstraitRelVisitor.toSortField(r, call.inputType))
                .toList()
            : Collections.emptyList();
    AggregateFunction.AggregationInvocation invocation =
        agg.isDistinct()
            ? AggregateFunction.AggregationInvocation.AGGREGATION_INVOCATION_DISTINCT
            : AggregateFunction.AggregationInvocation.AGGREGATION_INVOCATION_ALL;
    return ExpressionCreator.windowFunction(
        function,
        outputType,
        Expression.AggregationPhase.INITIAL_TO_RESULT,
        sorts,
        invocation,
        arguments);
  }

  public Optional<WindowFunctionInvocation> convert(
      RelNode input,
      Type.Struct inputType,
      AggregateCall call,
      Function<RexNode, Expression> topLevelConverter) {

    FunctionFinder m = signatures.get(call.getAggregation());
    if (m == null) {
      return Optional.empty();
    }
    if (!m.allowedArgCount(call.getArgList().size())) {
      return Optional.empty();
    }

    var wrapped = new WrappedAggregateCall(call, input, rexBuilder, inputType);
    return m.attemptMatch(wrapped, topLevelConverter);
  }

  static class WrappedAggregateCall implements FunctionConverter.GenericCall {
    private final AggregateCall call;
    private final RelNode input;
    private final RexBuilder rexBuilder;
    private final Type.Struct inputType;

    private WrappedAggregateCall(
        AggregateCall call, RelNode input, RexBuilder rexBuilder, Type.Struct inputType) {
      this.call = call;
      this.input = input;
      this.rexBuilder = rexBuilder;
      this.inputType = inputType;
    }

    @Override
    public Stream<RexNode> getOperands() {
      return call.getArgList().stream().map(r -> rexBuilder.makeInputRef(input, r));
    }

    public AggregateCall getUnderlying() {
      return call;
    }

    @Override
    public RelDataType getType() {
      return call.getType();
    }
  }
}
