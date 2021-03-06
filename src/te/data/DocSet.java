package te.data;

import java.util.*;

public class DocSet {
//	public List<Document> docs;
	public Map<String,Document> docsById;
	public AbstractTermVector terms;

	public DocSet() {
		init();
	}
	void init() {
//		docs = new ArrayList<>();
		docsById = new HashMap<>();
		terms = new TroveTermVector();
	}
	public DocSet(Collection<Document> _docs) {
		init();
		for (Document d : _docs) {
			add(d);
		}
	}
	public Collection<Document> docs() {
		return docsById.values();
	}
	
	public void add(Document d) {
		if ( ! docsById.containsKey(d.docid)) {
			docsById.put(d.docid,d);
			terms.addInPlace(d.termVec);	
		}
	}
}
