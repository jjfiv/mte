package te.data;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.string.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.document.DocVar;
import edu.umass.cs.jfoley.coop.index.IndexBuilder;
import edu.umass.cs.jfoley.coop.index.IndexReader;
import edu.umass.cs.jfoley.coop.schema.CategoricalVarSchema;
import edu.umass.cs.jfoley.coop.schema.DocVarSchema;
import edu.umass.cs.jfoley.coop.schema.IndexConfiguration;
import edu.umass.cs.jfoley.coop.schema.IntegerVarSchema;
import te.data.Schema.ColumnInfo;
import te.data.Schema.DataType;
import te.ui.Configuration;
import utility.util.BasicFileIO;
import utility.util.JsonUtil;
import utility.util.U;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

public class Corpus implements DataLayer {
	private Map<String,Document> docsById;
	public List<Document> docsInOriginalOrder;
	private AbstractTermVector globalTerms;
	private InvertedIndex index;
//	SpatialIndex hierIndex;
//	DoubleSummaryStatistics xSummary, ySummary;
	private Schema schema;
	public Map<String,SummaryStats> covariateSummaries;
	double doclenSumSq = 0;
	public boolean needsCovariateTypeConversion = false;
	Directory indexDir;
	IndexReader indexReader = null;

	SummaryStats selectStats = new SummaryStats();

	public Corpus() {
		// for now, just make a hidden directory to store index files.
		indexDir = new Directory(".index");
		docsById = new HashMap<>();
		index = new InvertedIndex();
		docsInOriginalOrder = new ArrayList<>();
		setSchema(new Schema());
	}
	
	@Override
	public Collection<Document> allDocs() {
		return docsInOriginalOrder;
	}

	@Override
	public DocSet getDocSet(Collection<String> docids) {
		DocSet ds = new DocSet();
		for (String docid : docids) {
			ds.add( docsById.get(docid) );
		}
		return ds;
	}
	
	public DocSet naiveSelect(String xAttr, String yAttr, double minX, double maxX, double minY, double maxY) {
		DocSet ds = new DocSet();
		for (Document document : docsById.values()) {
			double x = getSchema().getDouble(document, xAttr);
			double y = getSchema().getDouble(document, yAttr);
			if(x >= minX && x <= maxX && y <= minY && y >= maxY) {
				ds.add(document);
			}
		}
		return ds;
	}
	
	@Override
	public DocSet select(String xAttr, String yAttr, double minX, double maxX, double minY, double maxY) {
		long t0 = System.nanoTime();
		DocSet docs = naiveSelect(xAttr, yAttr, minX, maxX, minY, maxY);
		selectStats.add(1e-6*(System.nanoTime()-t0));
		if((selectStats.count() % 10) == 0) {
			System.err.println("SELECT: "+selectStats);
		}
		return docs;
	}
	
	@Override
	public void runTokenizer(Function<String, List<Token>> tokenizer) {
		long t0 = System.currentTimeMillis(); U.p("Running tokenizer");
		for (Document d : docsById.values()) {
			d.tokens = tokenizer.apply(d.text);
		}
		U.pf("Tokenizer completed (%d ms)\n", (System.currentTimeMillis()-t0) );
	}
	public void loadNLP(String filename) throws IOException {
		for (String line : BasicFileIO.openFileLines(filename)) {
			String parts[] = line.split("\t");
			String docid = parts[0];
			if ( ! docsById.containsKey(docid)) continue;
			JsonNode jdoc = JsonUtil.readJson(parts[1]);
			docsById.get(docid).loadFromNLP(jdoc);
		}
	}

	/** Makes term occurrences boolean */
	public void indicatorize() {
		for (Document d : docsById.values()) {
			AbstractTermVector newvec = new TroveTermVector();
			for (String w : d.termVec.support()) {
				newvec.increment(w);
			}
			d.termVec = newvec;
		}
	}

	/** disjunction query */
	@Override
	public DocSet select(List<String> terms) {
		DocSet ret = new DocSet();
		for (String term : terms) {
			for (Document d : index.getMatchingDocs(term)) {
				if (d.termVec.value(term) > 0) {
					ret.add(d);
				}
			}
		}
		return ret;
	}

	public void calculateCovariateSummaries() {
		covariateSummaries = new HashMap<>();
		for (String k : getSchema().varnames()) covariateSummaries.put(k, new SummaryStats());
		for (Document d : allDocs()) {
			for (String varname : getSchema().varnames()) {
				if (!d.covariates.containsKey(varname)) continue;
				covariateSummaries.get(varname).add(getSchema().getDouble(d, varname));
			}
		}
		U.p("Covariate summary stats: " + covariateSummaries);
	}

	/**
	 * This function at least collects categorical variable information and makes it canonical, picking an order and a numericalization.
	 */
	public void convertCovariateTypes() {
		U.p("Covariate types, before conversion pass: " + getSchema().columnTypes);
		for (Document d : allDocs()) {
			for (String varname : getSchema().columnTypes.keySet()) {
				if (!d.covariates.containsKey(varname)) continue;
				ColumnInfo ci = getSchema().columnTypes.get(varname);
				Object converted = getSchema().columnTypes.get(varname).convertFromJson( (JsonNode) d.covariates.get(varname) );
				d.covariates.put(varname, converted);
				if (ci.dataType==DataType.CATEG && !ci.levels.name2level.containsKey(converted)) {
					ci.levels.addLevel((String) converted);
				}
			}
		}
		U.p("Covariate types, after conversion pass: " + getSchema().columnTypes);
	}

	public void setDataFromDataLoader(DataLoader dataloader) {
		U.pf("%d docs loaded total\n", dataloader.docsInOriginalOrder.size());
		this.docsById = dataloader.docsById;
		this.docsInOriginalOrder = dataloader.docsInOriginalOrder;
	}

	@Override
	public Schema getSchema() {
		return schema;
	}

	@Override
	public Document pullDocument(String id) {
		return docsById.get(id);
	}

	public void setSchema(Schema schema) {
		this.schema = schema;
	}


	/**
	 * This is essentially the corpus statistics:
	 */
	public AbstractTermVector getGlobalTerms() {
		return globalTerms;
	}

	public boolean attributeExists(String name) {
		return schema.varnames().contains(name);
	}

	public void finalizeCorpusAnalysis(Configuration config) {
		long t0=System.nanoTime();
		U.p("Analyzing covariates");

		if (needsCovariateTypeConversion) {
			convertCovariateTypes();
		}
		calculateCovariateSummaries();

		U.pf("done analyzing covariates (%.0f ms)\n", 1e-6*(System.nanoTime()-t0));
		t0=System.nanoTime(); U.p("Building index backend");
		try {
			buildDiskIndexBackend();
		} catch (IOException ioe) {
			throw new RuntimeException(ioe);
		}
		U.pf("done building index backend (%.0f ms)\n", 1e-6*(System.nanoTime()-t0));

		t0=System.nanoTime(); U.p("Analyzing document texts");

		for (Document doc : allDocs()) {
			if (Thread.interrupted()) return;
			NLP.analyzeDocument(da, doc);
		}
		config.afteranalysisCallback.get();

		U.pf("done analyzing doc texts (%.0f ms)\n", 1e-6*(System.nanoTime()-t0));

		finalizeIndexing();
	}

	private void buildDiskIndexBackend() throws IOException {
		IndexConfiguration cfg = IndexConfiguration.create();
		for (Map.Entry<String, ColumnInfo> kv : schema.columnTypes.entrySet()) {
			String varName = kv.getKey();
			ColumnInfo varInfo = kv.getValue();
			switch(varInfo.dataType) {
				case NUMBER:
					cfg.documentVariables.put(varName, IntegerVarSchema.create(varName));
					break;
				case CATEG:
					cfg.documentVariables.put(varName, new CategoricalVarSchema(varName, new ArrayList<>(varInfo.levels.name2level.keySet()), true));
					break;
				case BOOLEAN:
					System.err.println("Skipping boolean variable "+varName+ " for now.");
					break;
			}
		}

		// HACK: clear out anything in the indexDir from before, while formats are changing this is a good idea to rebuild every time:
		indexDir.removeRecursively();

		try (IndexBuilder builder = new IndexBuilder(cfg, indexDir)) {
			for (Document document : allDocs()) {
				CoopDoc cdoc = new CoopDoc();

				List<String> text = new ArrayList<>();
				List<String> pos = new ArrayList<>();
				List<String> ner = new ArrayList<>();
				for (Token token : document.tokens) {
					if(token.text != null) text.add(token.text);
					if(token.pos != null) pos.add(token.pos);
					if(token.ner != null) ner.add(token.ner);
				}
				if(!text.isEmpty()) cdoc.setTerms("tokens", text);
				if(!pos.isEmpty()) cdoc.setTerms("pos", pos);
				if(!ner.isEmpty()) cdoc.setTerms("ner", ner);
				cdoc.setRawText(StrUtil.join(text, " "));

				cdoc.setName(document.docid);
				Map<String,DocVar> variables = new HashMap<>();
				for (Map.Entry<String, Object> kv : document.covariates.entrySet()) {
					DocVarSchema schemaByName = cfg.documentVariables.get(kv.getKey());
					if(schemaByName == null) {
						continue;
					}
					variables.put(kv.getKey(), schemaByName.createValue(kv.getValue()));
				}
				cdoc.setVariables(variables);

				builder.addDocument(cdoc);
			}
		}

		indexReader = new IndexReader(indexDir);
	}

	NLP.DocAnalyzer da = new NLP.UnigramAnalyzer();

	private void finalizeIndexing() {
		long t0=System.nanoTime();
//		hierIndex = new HierIndex(16, xSummary.getMin(), xSummary.getMax(), ySummary.getMin(), ySummary.getMax());
//		hierSums.doSpatialSums(docsById.values());
//		hierSums.dump();

		U.p("finalizing");
		for (Document d : docsById.values()) {
			index.add(d);
			double n = d.termVec.getTotalCount();
			doclenSumSq += n*n;
		}
		DocSet allds = new DocSet( docsById.values() );
		globalTerms = allds.terms;
		U.pf("done finalizing (%.2f ms)\n", 1e-6*(System.nanoTime()-t0));
	}
}
