package lu.zhe.kyudo;

import com.google.auto.value.*;
import com.google.common.base.*;
import com.squareup.square.models.*;

import java.util.*;

/**
 * Representation of a club member.
 *
 * <p>Backed by the Square API definition of a Customer.
 **/
@AutoValue
public abstract class Member {
  public static Member create(Customer customer, Map<String, String> groups) {
    MemberType type = null;
    boolean autoInvoice = false;
    for (String groupId : customer.getGroupIds()) {
      try {
        type = MemberType.valueOf(Ascii.toUpperCase(groups.get(groupId)));
      } catch (Exception e) {
        if (groups.get(groupId).equals("AutoInvoice)")) {
          autoInvoice = true;
        }
      }
    }
    if (type == null) {
      throw new IllegalStateException(
          "Member \"" + customer.getGivenName() + " " + customer.getFamilyName() +
              "\" has no type.");
    }
    return new lu.zhe.kyudo.AutoValue_Member(type,
        customer,
        customer.getGivenName() + " " + customer.getFamilyName(),
        autoInvoice);
  }

  public abstract MemberType type();

  public abstract Customer customer();

  public abstract String name();

  public abstract boolean autoInvoice();
}
