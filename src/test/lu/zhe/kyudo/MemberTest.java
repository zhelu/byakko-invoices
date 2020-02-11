package lu.zhe.kyudo;

import com.google.common.collect.*;
import com.squareup.square.models.*;
import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit test for {@link Member}. */
@RunWith(JUnit4.class)
public class MemberTest {
  @Test
  public void create() {
    for (MemberType type : MemberType.values()) {
      Customer customer = mock(Customer.class);
      when(customer.getGivenName()).thenReturn("John");
      when(customer.getFamilyName()).thenReturn("Doe");
      CustomerGroupInfo group = mock(CustomerGroupInfo.class);
      when(group.getName()).thenReturn(type.name());
      when(customer.getGroups()).thenReturn(ImmutableList.of(group));
      Member member = Member.create(customer);

      assertThat(member.name()).isEqualTo("John Doe");
      assertThat(member.type()).isEqualTo(type);
      assertThat(member.customer()).isSameInstanceAs(customer);
    }
  }
}
