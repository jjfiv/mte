package te.data;

import java.util.Collection;
import java.util.Set;

/**
 * This interface represents a language model or some other weighted collection of terms.
 * @author jfoley
 */
public interface AbstractTermVector {
  double valueSum(Collection<String> terms);

  void increment(String term, double value);

  void increment(String term);

  void addInPlace(TermVector other);

  double value(String term);

  Set<String> support();

  AbstractTermVector copy();

  double getTotalCount();
}
