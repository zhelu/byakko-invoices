package lu.zhe.kyudo;

import com.google.common.collect.*;
import com.squareup.square.models.*;
import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit tests for {@link MemberDatabase}. */
@RunWith(JUnit4.class)
public class MemberDatabaseTest {
  @Test
  public void test() {
    Customer johnDoe = mock(Customer.class);
    Customer janeSmith = mock(Customer.class);
    when(johnDoe.getId()).thenReturn("asdf");
    when(janeSmith.getId()).thenReturn("foobar");
    when(johnDoe.getGivenName()).thenReturn("John");
    when(johnDoe.getFamilyName()).thenReturn("Doe");
    when(janeSmith.getGivenName()).thenReturn("Jane");
    when(janeSmith.getFamilyName()).thenReturn("Smith");
    CustomerGroupInfo group = mock(CustomerGroupInfo.class);
    when(group.getName()).thenReturn("MEMBER");
    when(johnDoe.getGroups()).thenReturn(ImmutableList.of(group));
    when(janeSmith.getGroups()).thenReturn(ImmutableList.of(group));

    MemberDatabase.Builder builder = MemberDatabase.builder();

    Member johnDoeMember = Member.create(johnDoe);
    Member janeSmithMember = Member.create(janeSmith);
    builder.addMember(johnDoeMember);
    builder.addMember(janeSmithMember);
    MemberDatabase database = builder.build();

    assertThat(database.idToMember().get("asdf")).isSameInstanceAs(johnDoeMember);
    assertThat(database.idToMember().get("foobar")).isSameInstanceAs(janeSmithMember);
    assertThat(database.nameToMember().get("John Doe")).isSameInstanceAs(johnDoeMember);
    assertThat(database.nameToMember().get("Jane Smith")).isSameInstanceAs(janeSmithMember);
  }
}
