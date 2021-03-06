

package vn.icommerce.common.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Custom serializer to serialize a {@link BigDecimal} object to its string representation.
 *
 *
 *
 *
 */
public class BigDecimalSerializer extends StdSerializer<BigDecimal> {

  public BigDecimalSerializer() {
    this(BigDecimal.class);
  }

  public BigDecimalSerializer(Class<BigDecimal> t) {
    super(t);
  }

  @Override
  public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    gen.writeString(value.setScale(0, RoundingMode.DOWN).toString());
  }
}
