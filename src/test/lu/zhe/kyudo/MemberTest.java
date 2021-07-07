package lu.zhe.kyudo;

import com.google.common.base.*;
import com.google.common.collect.*;
import com.squareup.square.models.*;
import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;

import java.util.*;

import static com.google.common.collect.ImmutableMap.*;
import static com.google.common.truth.Truth.*;
import static org.mockito.Mockito.*;

/** Unit test for {@link Member}. */
@RunWith(JUnit4.class)
public class MemberTest {
  @Test
  public void create() {
    Map<String, String> groups = Arrays
        .stream(MemberType.values())
        .collect(toImmutableMap(t -> Ascii.toLowerCase(t.toString()),
            t -> Ascii.toUpperCase(t.toString())));

    for (MemberType type : MemberType.values()) {
      Customer customer = mock(Customer.class);
      when(customer.getGivenName()).thenReturn("John");
      when(customer.getFamilyName()).thenReturn("Doe");
      when(customer.getGroupIds()).thenReturn(ImmutableList.of(Ascii.toLowerCase(type.toString())));

      Member member = Member.create(customer, groups);

      assertThat(member.name()).isEqualTo("John Doe");
      assertThat(member.type()).isEqualTo(type);
      assertThat(member.customer()).isSameInstanceAs(customer);
    }
  }
}
