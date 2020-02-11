package lu.zhe.kyudo;

import com.google.auto.value.*;
import com.google.common.base.*;
import com.squareup.square.models.*;

/**
 * Representation of a club member.
 *
 * <p>Backed by the Square API definition of a Customer.
 **/
@AutoValue
public abstract class Member {
  public static Member create(Customer customer) {
    MemberType type = null;
    for (CustomerGroupInfo group : customer.getGroups()) {
      try {
        type = MemberType.valueOf(Ascii.toUpperCase(group.getName()));
      } catch (Exception e) {
        // Ignore
      }
    }
    if (type == null) {
      throw new IllegalStateException(
          "Member \"" + customer.getGivenName() + " " + customer.getFamilyName() +
              "\" has no type.");
    }
    return new lu.zhe.kyudo.AutoValue_Member(type,
        customer,
        customer.getGivenName() + " " + customer.getFamilyName());
  }

  public abstract MemberType type();

  public abstract Customer customer();

  public abstract String name();
}
