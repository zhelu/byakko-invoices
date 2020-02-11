package lu.zhe.csv;

import com.google.common.collect.*;
import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;

import static com.google.common.truth.Truth.*;

/** Unit tests for {@link CsvReader}. */
@RunWith(JUnit4.class)
public class CsvReaderTest {
  @Test
  public void testRead_withHeaderRow() {
    Table<Integer, String, String> result =
        CsvReader.parseString("Name,Age\n\"John, B\",11\nTara Q,12", true);
    assertThat(result).hasSize(4);
    assertThat(result).containsCell(0, "Name", "John, B");
    assertThat(result).containsCell(0, "Age", "11");
    assertThat(result).containsCell(1, "Name", "Tara Q");
    assertThat(result).containsCell(1, "Age", "12");
  }

  @Test
  public void testRead_withoutHeaderRow() {
    Table<Integer, String, String> result =
        CsvReader.parseString("\"John, B\",11\nTara Q,12", false);
    assertThat(result).hasSize(4);
    assertThat(result).containsCell(0, "0", "John, B");
    assertThat(result).containsCell(0, "1", "11");
    assertThat(result).containsCell(1, "0", "Tara Q");
    assertThat(result).containsCell(1, "1", "12");
  }
}
