package io.substrait.relation;

import io.substrait.type.NamedStruct;
import io.substrait.type.Type;
import java.util.List;

/** Contains tag interfaces for handling {@link com.google.protobuf.Any} types within Substrait. */
public class Extension {

  private interface ToProto {
    com.google.protobuf.Any toProto();
  }

  /**
   * Optimization associated with an {@link io.substrait.proto.AdvancedExtension}
   *
   * <p>An optimization is helpful information that don't influence semantics. May be ignored by a
   * consumer.
   */
  public interface Optimization extends ToProto {}

  /**
   * Enhancement associated with an {@link io.substrait.proto.AdvancedExtension}
   *
   * <p>An enhancement alter semantics. Cannot be ignored by a consumer.
   */
  public interface Enhancement extends ToProto {}

  public interface LeafRelDetail extends ToProto {
    /**
     * @return the record layout for the associated {@link ExtensionLeaf} relation
     */
    Type.Struct deriveRecordType();
  }

  public interface SingleRelDetail extends ToProto {
    /**
     * @param input to the associated {@link ExtensionSingle} relation
     * @return the record layout for the associated {@link ExtensionSingle} relation
     */
    Type.Struct deriveRecordType(Rel input);
  }

  public interface MultiRelDetail extends ToProto {
    /**
     * @param inputs to the associated {@link ExtensionMulti} relation
     * @return the record layout for the associated {@link ExtensionMulti} relation
     */
    Type.Struct deriveRecordType(List<Rel> inputs);
  }

  public interface ExtensionTableDetail extends ToProto {
    /**
     * @return the table schema for the associated {@link ExtensionTable} relation
     */
    NamedStruct deriveSchema();
  }
}