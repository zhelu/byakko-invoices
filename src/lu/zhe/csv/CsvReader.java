package lu.zhe.csv;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads CSV files. */
public class CsvReader {
  private CsvReader() {
    // disallow instantiation
  }

  // This regexp will still contain wrapping quotes and possibly double quotes inside.
  private static final String ELEMENT_NAMED_GROUP = "element";
  static final Pattern PATTERN = Pattern.compile("(?<=^|,)(?<" + ELEMENT_NAMED_GROUP +
      ">\"(?:[^\"]|\"\")*\"(?=,|$)|[^\",]*)(?=,|$)", Pattern.MULTILINE);

  public static ImmutableTable<Integer, String, String> parseString(String input,
                                                                    boolean headerRow) {
    return parseScanner(new Scanner(input), headerRow);
  }

  /** Parse CSV from file. Returns empty table on error. */
  public static ImmutableTable<Integer, String, String> parseFile(String path, boolean headerRow) {
    try {
      File f = new File(path);
      return parseScanner(new Scanner(f), headerRow);
    } catch (Exception e) {
      e.printStackTrace();
      return ImmutableTable.of();
    }
  }

  public static ImmutableTable<Integer, String, String> parseScanner(Scanner sc,
                                                                     boolean headerRow) {
    ImmutableTable.Builder<Integer, String, String> result = ImmutableTable.builder();
    int rowIndex = 0;
    List<String> header = new ArrayList<>();
    StringBuilder currentElement = new StringBuilder();
    while (sc.hasNextLine()) {
      currentElement.append(sc.nextLine());
      String row = currentElement.toString();
      if (row.chars().filter(c -> c == '"').count() % 2 == 0) {
        List<String> elements = new ArrayList<>();
        Matcher matcher = PATTERN.matcher(row);
        while (matcher.find()) {
          String element = matcher.group(ELEMENT_NAMED_GROUP);
          if (element.startsWith("\"") && element.endsWith("\"")) {
            element = element.substring(1, element.length() - 1);
          }
          element = element.replaceAll("\"\"", "\"");
          elements.add(element);
        }
        if (rowIndex == 0 && header.isEmpty()) {
          if (headerRow) {
            header = elements;
            currentElement = new StringBuilder();
            continue;
          } else {
            for (int i = 0; i < elements.size(); ++i) {
              header.add(String.valueOf(i));
            }
          }
        }
        if (elements.size() == 0) {
          // Skip blank rows.
          continue;
        }
        if (header.size() != elements.size()) {
          throw new IllegalArgumentException(
              "Row has unxpected size: " + elements.size() + ". Expected: " + header.size());
        }
        for (int i = 0; i < elements.size(); ++i) {
          result.put(rowIndex, header.get(i), elements.get(i));
        }
        ++rowIndex;
        currentElement = new StringBuilder();
      }
    }
    if (currentElement.length() > 0) {
      String row = currentElement.toString();
      List<String> elements = new ArrayList<>();
      Matcher matcher = PATTERN.matcher(row);
      while (matcher.find()) {
        String element = matcher.group(ELEMENT_NAMED_GROUP);
        if (element.startsWith("\"") && element.endsWith("\"")) {
          element = element.substring(1, element.length() - 1);
        }
        element = element.replaceAll("\"\"", "\"");
        elements.add(element);
      }
      if (rowIndex == 0) {
        if (headerRow) {
          header = elements;
        } else {
          for (int i = 0; i < elements.size(); ++i) {
            header.add(String.valueOf(i));
          }
        }
      }
      if (header.size() != elements.size()) {
        throw new IllegalArgumentException(
            "Row has unxpected size: " + elements.size() + ". Expected: " + header.size());
      }
      for (int i = 0; i < elements.size(); ++i) {
        result.put(rowIndex, header.get(i), elements.get(i));
      }
    }
    return result.build();
  }
}
