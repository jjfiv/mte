package te.data;

import gnu.trove.TObjectDoubleHashMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author jfoley
 */
public class TroveTermVector implements AbstractTermVector {
  TObjectDoubleHashMap<String> values;
  private double total;

  public TroveTermVector() {
    this(new TObjectDoubleHashMap<>(), 0.0);
  }
  public TroveTermVector(TObjectDoubleHashMap<String> values, double total) {
    this.values = values;
    this.total = total;
  }

  @Override
  public double valueSum(Collection<String> terms) {
    double sum = 0.0;
    for (String term : terms) {
      sum += values.get(term);
    }
    return sum;
  }

  @Override
  public void increment(String term, double value) {
    values.adjustOrPutValue(term, value, value);
    total += value;
  }

  @Override
  public void increment(String term) {
    increment(term, 1.0);
  }

  @Override
  public void addInPlace(AbstractTermVector other) {
    for (String s : keySet()) {
      double val = other.value(s);
      values.adjustOrPutValue(s, val, val);
      total += val;
    }
  }

  @Override
  public double value(String term) {
    return values.get(term);
  }

  @Override
  public Set<String> support() {
    return new HashSet<>(keySet());
  }

  @Override
  public AbstractTermVector copy() {
    return new TroveTermVector(values.clone(), total);
  }

  @Override
  public double getTotalCount() {
    return total;
  }

  @Override
  public Collection<String> keySet() {
    return Arrays.asList((String[]) values.keys(new String[0]));
  }
}
