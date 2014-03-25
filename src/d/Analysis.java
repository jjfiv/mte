package d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.DiscreteDomains;
import com.google.common.collect.Ordering;
import com.google.common.collect.Range;
import com.google.common.collect.Ranges;

import util.Arr;
import util.U;
import util.misc.Pair;

public class Analysis {
	public static class FocusContrastView {
		TermVector focus, background;
		
		public FocusContrastView(TermVector focus, TermVector bg) {
			this.focus=focus;
			this.background = bg;
		}
		
		public double epmi(String term) {
			double myprob = focus.value(term) / focus.totalCount;
			double globalprob = background.value(term) / background.totalCount;
			return myprob / globalprob;
		}
		
		public List<String> topEpmi(double minprob, int mincount) {
			final List<String> terms = new ArrayList<>();
			final List<Double> epmis = new ArrayList<>();
			for (String term : focus.support()) {
				double myprob = focus.value(term) / focus.totalCount;
				double globalprob = background.value(term) / background.totalCount;
				if (myprob < minprob) continue;
				if (focus.value(term) < mincount) continue;

				double ratio = myprob / globalprob;
				terms.add(term);
				epmis.add(ratio);
			}
			List<Integer> inds = Arr.asList( Arr.rangeInts(terms.size()) );
			Collections.sort(inds, Ordering.natural().reverse().onResultOf(ind ->
				U.pair(epmis.get(ind), focus.value(terms.get(ind)))
			));
			List<String> ret = new ArrayList<>();
			for (int i : inds) ret.add(terms.get(i));
			return ret;
		}
	}
	
	/**
	 * exponentiated PMI = p(x|y)/p(x)  (= p(x,y)/[p(x)p(y)])
	 * Provost and Fawcett call this "lift" (ch9, p244)
	 */
	public static List<WeightedTerm> topEPMI(
			double minprob, int mincount, final TermVector focus, final TermVector background) {
		final List<WeightedTerm> arr = new ArrayList<>();
		for (String term : focus.support()) {
			double myprob = focus.value(term) / focus.totalCount;
			double globalprob = background.value(term) / background.totalCount;
			if (myprob < minprob) continue;
			if (focus.value(term) < mincount) continue;

			double ratio = myprob / globalprob;
			arr.add(new WeightedTerm(term, ratio));
		}
		List<Integer> inds = Arr.asList( Arr.rangeInts(arr.size()) );
		Collections.sort(inds, Ordering.natural().reverse().onResultOf(ind ->
				U.pair(arr.get(ind).weight,  focus.value(arr.get(ind).term))
		));
		List<WeightedTerm> ret = new ArrayList<>();
		for (int i : inds) ret.add(arr.get(i));
		return ret;
	}
	
}
