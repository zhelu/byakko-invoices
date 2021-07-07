package lu.zhe.kyudo;

import com.google.common.collect.*;
import com.squareup.square.models.*;
import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;

import java.util.*;

import static com.google.common.truth.Truth.*;
import static org.mockito.Mockito.*;

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
    when(johnDoe.getGroupIds()).thenReturn(ImmutableList.of("regular"));
    when(janeSmith.getGroupIds()).thenReturn(ImmutableList.of("regular"));
    Map<String, String> groups = ImmutableMap.of("regular", "REGULAR");
    MemberDatabase.Builder builder = MemberDatabase.builder();

    Member johnDoeMember = Member.create(johnDoe, groups);
    Member janeSmithMember = Member.create(janeSmith, groups);
    builder.addMember(johnDoeMember);
    builder.addMember(janeSmithMember);
    MemberDatabase database = builder.build();

    assertThat(database.idToMember().get("asdf")).isSameInstanceAs(johnDoeMember);
    assertThat(database.idToMember().get("foobar")).isSameInstanceAs(janeSmithMember);
    assertThat(database.nameToMember().get("John Doe")).isSameInstanceAs(johnDoeMember);
    assertThat(database.nameToMember().get("Jane Smith")).isSameInstanceAs(janeSmithMember);
  }
}
