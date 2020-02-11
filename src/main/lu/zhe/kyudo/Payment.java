package lu.zhe.kyudo;

import com.google.auto.value.*;

/** Represents a payment and an amount. */
@AutoValue
abstract class Payment {
  public static Payment create(Member member, PaymentType type) {
    return new lu.zhe.kyudo.AutoValue_Payment(member.type().value() * type.scale, type);
  }

  public abstract int amount();

  public abstract PaymentType type();

  /** Describes the type of payment. */
  public enum PaymentType {
    FIRST_SHOT(2), DUES(1);

    private final int scale;

    PaymentType(int scale) {
      this.scale = scale;
    }
  }
}
