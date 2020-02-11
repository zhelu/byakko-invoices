package lu.zhe.kyudo;

import com.google.common.base.*;

/** Describes the type of member. */
public enum MemberType {
  REGULAR(40), STUDENT(20), ASSOCIATE(15), MEMBER(40);

  private final int value;

  MemberType(int value) {
    this.value = value;
  }

  public static MemberType fromString(String s) {
    return MemberType.valueOf(Ascii.toUpperCase(s));
  }

  public int value() {
    return value;
  }
}
