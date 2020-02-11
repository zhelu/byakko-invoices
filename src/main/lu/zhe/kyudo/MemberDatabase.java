package lu.zhe.kyudo;

import com.google.auto.value.*;
import com.google.common.collect.*;

/** Database for looking up members by id or full name. */
@AutoValue
abstract class MemberDatabase {
  public static Builder builder() {
    return new lu.zhe.kyudo.AutoValue_MemberDatabase.Builder();
  }

  public abstract ImmutableMap<String, Member> idToMember();

  public abstract ImmutableMap<String, Member> nameToMember();

  @AutoValue.Builder
  protected static abstract class Builder {
    public abstract MemberDatabase build();

    protected abstract ImmutableMap.Builder<String, Member> idToMemberBuilder();

    protected abstract ImmutableMap.Builder<String, Member> nameToMemberBuilder();

    public Builder addMember(Member member) {
      idToMemberBuilder().put(member.customer().getId(), member);
      nameToMemberBuilder().put(
          member.customer().getGivenName() + " " + member.customer().getFamilyName(), member);
      return this;
    }
  }
}
