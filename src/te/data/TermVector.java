package te.data;
import java.util.*;

public class TermVector implements AbstractTermVector {
	public Map<String,Double> map;
	private double totalCount = 0;
	
	TermVector() {
		map = new HashMap<>();
	}
	
	@Override
	public double valueSum(Collection<String> terms) {
		double x = 0;
		for (String t : terms) {
			x += value(t);
		}
		return x;
	}
	
	@Override
	public void increment(String term, double value) {
		ensure0(term);
		map.put(term, map.get(term) + value);
		totalCount = getTotalCount() + value;
	}
	@Override
	public void increment(String term) {
		increment(term, 1.0);
	}

	@Override
	public void addInPlace(TermVector other) {
		for (String k : other.map.keySet()) {
			ensure0(k);
			increment(k, other.value(k));
		}
	}
	@Override
	public double value(String term) {
		if (!map.containsKey(term)) return 0;
		return map.get(term);
	}
	@Override
	public Set<String> support() {
		// tricky. it would be safer to check for zero-ness. this could be wrong if negative values are ever used in sums.
		return map.keySet();
	}
	
	@Override
	public AbstractTermVector copy() {
		TermVector ret = new TermVector();
		ret.map = new HashMap<>(this.map);
		ret.totalCount = this.getTotalCount();
		return ret;
	}
	
//	static public TermVector sum(TermVector u, TermVector v) {
//		TermVector ret = u.copy();
//		ret.addInPlace(v);
//		return ret;
//	}
	
	/** helper: ensure that 'term' exists in the map */
	void ensure0(String term) {
		if (!map.containsKey(term)) {
			map.put(term, 0.0);
		}
	}

	@Override
	public double getTotalCount() {
		return totalCount;
	}
}
