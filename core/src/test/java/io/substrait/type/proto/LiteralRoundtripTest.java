package io.substrait.type.proto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.substrait.expression.ExpressionCreator;
import io.substrait.expression.proto.ExpressionProtoConverter;
import io.substrait.expression.proto.ProtoExpressionConverter;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

public class LiteralRoundtripTest {
  static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(LiteralRoundtripTest.class);

  @Test
  void decimal() {
    var val = ExpressionCreator.decimal(false, BigDecimal.TEN, 10, 2);
    var to = new ExpressionProtoConverter(null);
    var from = new ProtoExpressionConverter(null, null, null);
    assertEquals(val, from.from(val.accept(to)));
  }
}
