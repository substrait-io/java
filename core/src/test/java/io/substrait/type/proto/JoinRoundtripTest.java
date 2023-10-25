package io.substrait.type.proto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.substrait.TestBase;
import io.substrait.dsl.SubstraitBuilder;
import io.substrait.extension.ExtensionCollector;
import io.substrait.extension.SimpleExtension;
import io.substrait.relation.ProtoRelConverter;
import io.substrait.relation.Rel;
import io.substrait.relation.RelProtoConverter;
import io.substrait.relation.physical.HashJoin;
import io.substrait.relation.utils.StringHolderHandlingProtoRelConverter;
import io.substrait.type.TypeCreator;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class JoinRoundtripTest extends TestBase {

  final SimpleExtension.ExtensionCollection extensions = defaultExtensionCollection;

  TypeCreator R = TypeCreator.REQUIRED;

  final SubstraitBuilder b = new SubstraitBuilder(extensions);

  final ExtensionCollector functionCollector = new ExtensionCollector();
  final RelProtoConverter relProtoConverter = new RelProtoConverter(functionCollector);
  final ProtoRelConverter protoRelConverter =
      new StringHolderHandlingProtoRelConverter(functionCollector, extensions);

  final Rel leftTable =
      b.namedScan(
          Arrays.asList("T1"),
          Arrays.asList("a", "b", "c"),
          Arrays.asList(R.I64, R.FP64, R.STRING));

  final Rel rightTable =
      b.namedScan(
          Arrays.asList("T2"),
          Arrays.asList("d", "e", "f"),
          Arrays.asList(R.FP64, R.STRING, R.I64));

  void verifyRoundTrip(Rel rel) {
    io.substrait.proto.Rel protoRel = relProtoConverter.toProto(rel);
    Rel relReturned = protoRelConverter.from(protoRel);
    assertEquals(rel, relReturned);
  }

  @Test
  void hashJoin() {
    List<Integer> leftKeys = Arrays.asList(0, 1);
    List<Integer> rightKeys = Arrays.asList(2, 0);
    Rel relWithoutKeys =
        HashJoin.builder()
            .from(b.hashJoin(leftKeys, rightKeys, HashJoin.JoinType.INNER, leftTable, rightTable))
            .build();
    verifyRoundTrip(relWithoutKeys);
  }
}
