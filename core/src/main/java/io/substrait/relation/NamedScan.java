package io.substrait.relation;

import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
public abstract class NamedScan extends AbstractReadRel {

  public abstract List<String> getNames();

  @Override
  public <O, E extends Exception> O accept(RelVisitor<O, E> visitor) throws E {
    return visitor.visit(this);
  }

  public static ImmutableNamedScan.Builder builder() {
    return ImmutableNamedScan.builder();
  }
}
