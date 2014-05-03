package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JFormattedTextField.AbstractFormatter;
import javax.swing.JFormattedTextField.AbstractFormatterFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import org.codehaus.jackson.JsonProcessingException;
import org.jdesktop.swingx.MultiSplitLayout;
import org.jdesktop.swingx.MultiSplitPane;

import bibliothek.gui.DockController;
import bibliothek.gui.dock.DefaultDockable;
import bibliothek.gui.dock.SplitDockStation;
import bibliothek.gui.dock.station.split.SplitDockGrid;
import util.BasicFileIO;
import util.JsonUtil;
import util.U;
import d.Analysis;
import d.Analysis.TermvecComparison;
import d.Corpus;
import d.DocSet;
import d.Document;
import d.Levels;
import d.NLP;
import d.TermQuery;
import d.TermVector;
import edu.stanford.nlp.util.StringUtils;

interface QueryReceiver {
	public void receiveQuery(Collection<String> docids);
}

@SuppressWarnings("serial")
public class Main implements QueryReceiver {
	public Corpus corpus;
	public DocSet curDS = new DocSet();

	public List<String> docdrivenTerms = new ArrayList<>();
	public List<String> pinnedTerms = new ArrayList<>();
	public List<String> termdrivenTerms = new ArrayList<>();
	
	TermvecComparison docvarCompare;
	TermvecComparison termtermBoolqueryCompare;
	
	JFrame mainFrame;
	TermTable  docdrivenTermTable;
	TermTable pinnedTermTable;
	TermTable  termdrivenTermTable;
	BrushPanel brushPanel;
	TextPanel textPanel;
	JLabel mainqueryInfo;
	JLabel subqueryInfo;
	JLabel termlistInfo;
	JSpinner tpSpinner;
	JSpinner tcSpinner;
	JLabel tcInfo;
	JLabel termtermDescription;
//	private JButton killDocvarQuery;
	
	Supplier<Void> afteranalysisCallback = () -> null;
	Supplier<Void> uiOverridesCallback = () -> null;

	public void initData() throws JsonProcessingException, IOException {
		
		corpus = Corpus.loadXY("/d/sotu/sotu.xy");
		corpus.loadNLP("/d/sotu/sotu.ner");
		corpus.loadLevels("/d/sotu/schema.json");
		NLP.DocAnalyzer da = new NLP.UnigramAnalyzer();
//		NLP.NgramAnalyzer da = new NLP.NgramAnalyzer() {{ order=1; stopwordFilter=true; posnerFilter=true; }};
//		NLP.NgramAnalyzer da = new NLP.NgramAnalyzer() {{ order=5; stopwordFilter=true; posnerFilter=true; }};
		uiOverridesCallback = () -> {
	      brushPanel.minUserY = -2;
	      brushPanel.maxUserY = -brushPanel.minUserY;
	      return null;
		};
		
//		corpus = Corpus.loadXY("/d/reviews_bryan/ALLnyc.Menu.jsonxy");
//		corpus.runTokenizer(NLP::simpleTokenize);
////		corpus.runTokenizer(NLP::stanfordTokenize);
//		NLP.NgramAnalyzer da = new NLP.NgramAnalyzer() {{ order=2; posnerFilter=false; }};
		
//		corpus = Corpus.loadXY("/d/acl/just_meta.xy");
////		corpus.runTokenizer(NLP::simpleTokenize);
////		corpus.runTokenizer(NLP::stanfordTokenize);
//		corpus.loadNLP("/d/acl/just_meta.ner");
//		corpus.loadLevels("/d/acl/schema.json");
//		NLP.NgramAnalyzer da = new NLP.NgramAnalyzer() {{
//			order=3; posnerFilter=true; stopwordFilter=true;
//		}};
////		afteranalysisCallback = () -> { corpus.indicatorize(); return null; };


//		corpus = Corpus.loadXY("/d/twi/geo2/data/v8/smalltweets2.sample.sort_by_user.useragg.xy.samp2k");
////		corpus = Corpus.loadXY("/d/twi/geo2/data/v8/smalltweets2.sample.sort_by_user.useragg.xy.samp100");
//		corpus.runTokenizer(NLP::simpleTokenize);
//		NLP.DocAnalyzer da = new NLP.UnigramAnalyzer();
////		NLP.NgramAnalyzer da = new NLP.NgramAnalyzer();
////		da.order = 2;
////		da.posnerFilter = false;
//		afteranalysisCallback = () -> { corpus.indicatorize(); return null; };


//		corpus = Corpus.loadXY("/d/bible/by_bookchapter.json.xy.filtered");
//		corpus.loadLevels("/d/bible/schema.json");
//		corpus.runTokenizer(NLP::stanfordTokenize);
//		NLP.DocAnalyzer da = new NLP.UnigramAnalyzer();
//		uiOverridesCallback = () -> { 
//		      brushPanel.minUserY = -1;
//		      brushPanel.maxUserY = 66;
//		      return null;
//		};

		for (Document doc : corpus.docsById.values()) {
			NLP.analyzeDocument(da, doc);	
		}
		afteranalysisCallback.get();
		corpus.finalizeIndexing();
	}

	void uiOverrides() {
		uiOverridesCallback.get();
	}
	
	double getTermProbThresh() {
		return (double) tpSpinner.getValue();
	}
	int getTermCountThresh() {
		return (int) tcSpinner.getValue();
	}
	
	@Override
	public void receiveQuery(Collection<String> docids) {
		curDS = corpus.getDocSet(docids);
		refreshQueryInfo();
		refreshDocdrivenTermList();
		refreshTextPanel();
	}

	void refreshTextPanel() {
		textPanel.show(getCurrentTQ().terms, curDS);
	}
	
	void refreshDocdrivenTermList() {
		docvarCompare = new TermvecComparison(curDS.terms, corpus.globalTerms);
		docdrivenTerms.clear();
		docdrivenTerms.addAll( docvarCompare.topEpmi(getTermProbThresh(), getTermCountThresh()) );
		docdrivenTermTable.model.fireTableDataChanged();
		termlistInfo.setText(U.sf("%d/%d terms", docdrivenTerms.size(), curDS.terms.support().size()));
		pinnedTermTable.updateCalculations();
//		int effectiveTermcountThresh = (int) Math.floor(getTermProbThresh() * curDS.terms.totalCount);
//		termcountInfo.setText(effectiveTermcountThresh==0 ? "all terms" : U.sf("count >= %d", effectiveTermcountThresh));
	}
	
	void runTermTermQuery(TermQuery tq) {
		// bool-occur
		TermVector focus = corpus.select(tq.terms).terms;
		termtermBoolqueryCompare = new TermvecComparison(focus, corpus.globalTerms);
		List<String> termResults = termtermBoolqueryCompare.topEpmi(getTermProbThresh(), getTermCountThresh());
		termdrivenTerms = termResults;
		termdrivenTermTable.model.fireTableDataChanged();
		String queryterms = tq.terms.stream().collect(Collectors.joining(", "));
		String queryinfo = U.sf("%d %s: %s", tq.terms.size(), tq.terms.size()==1 ? "term" : "terms", queryterms);
		termtermDescription.setText(U.sf("Terms most associated with %s", queryinfo));
		termtermDescription.setToolTipText(queryinfo);

		// joint- or cond-occur
//		Analysis.TermTermAssociations tta = new Analysis.TermTermAssociations();
//		tta.queryTerms = tq.terms;
//		tta.corpus = corpus;
//		List<String> termResults = tta.topEpmi(1);
	}
	

	void refreshQueryInfo() {
		String s = U.sf("Docvar selection: %s docs, %s wordtoks\n", 
				GUtil.commaize(curDS.docs().size()), 
				GUtil.commaize((int)curDS.terms.totalCount));
		mainqueryInfo.setText(s);
	}
	
	TermQuery getCurrentTQ() {
		TermQuery curTQ = new TermQuery(corpus);
    	Set<String> selterms = new LinkedHashSet<>();
    	selterms.addAll(docdrivenTermTable.getSelectedTerms());
    	selterms.addAll(pinnedTermTable.getSelectedTerms());
    	curTQ.terms.addAll(selterms);
    	return curTQ;
	}
	
	/** give this a termlist. it consults the global fcView for the terms' stats. */
	public class TermTableModel extends AbstractTableModel {
		// these are lazy so can be swapped out or changed without this class needing to know
		Supplier<List<String>> terms;
		Supplier<TermvecComparison> comparison;  
		
		@Override
		public String getColumnName(int j) {
			return (new String[]{ "term", "local","","global", "lift" })[ j ];
		}
		@Override
		public int getRowCount() {
			return terms.get().size();
		}
		@Override
		public int getColumnCount() {
			return 5;
		}
		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			String t = terms.get().get(rowIndex);
			double epmi = comparison == null || comparison.get()==null ? 0 
									: comparison.get().epmi(t);
			switch (columnIndex) {
			case 0: return t;
			case 1: return (int) comparison.get().focus.value(t);
			case 2: return ":";
			case 3: return (int) comparison.get().background.value(t);
			case 4: return epmi;
			}
			assert false; return null;
		}
		@Override
		public Class<?> getColumnClass(int c) {
			switch (c) {
			case 0: return String.class;
			case 1: return Integer.class;
			case 2: return String.class;
			case 3: return Integer.class;
			case 4: return Double.class;
			}
			assert false; return null;
		}
	}
	void addTermdriverAction(final TermTable tt) {
        tt.table.getSelectionModel().addListSelectionListener(e -> {
        	if (!e.getValueIsAdjusting()) {
        		runTermdrivenQuery(); 
    		}});
	}
	
	void runTermdrivenQuery() {
		TermQuery curTQ = getCurrentTQ();
		String msg = curTQ.terms.size()==0 ? "No selected terms" 
				: curTQ.terms.size()+" selected terms: " + StringUtils.join(curTQ.terms, ", ");
		subqueryInfo.setText(msg);
		refreshTextPanel();
		brushPanel.showTerms(curTQ);
		runTermTermQuery(curTQ);
	}
	

	
	void pinTerm(String term) { 
		pinnedTerms.add(term);
//		refreshTermList();
//		refreshQueryInfo();
//		pinnedTermTable.model.fireTableRowsInserted(pinnedTerms.size()-2, pinnedTerms.size()-1);
		pinnedTermTable.model.fireTableRowsInserted(0, pinnedTerms.size()-1);
//		pinnedTermTable.table.setRowSelectionInterval(pinnedTerms.size()-1, pinnedTerms.size()-1);
	}
	void unpinTerm(String term) {
//		refreshTermList();
//		refreshQueryInfo();
		int[] rowsToDel = IntStream.range(0,pinnedTermTable.model.getRowCount())
			.filter(row -> pinnedTermTable.getTermAt(row).equals(term))
			.toArray();
		for (int row : rowsToDel) {
			pinnedTermTable.model.fireTableRowsDeleted(row,row);	
		}
		pinnedTerms.remove(term);
	}
	
	static JPanel titledPanel(String title, JComponent internal) {
		JPanel top = new JPanel();
		top.add(new JLabel(title));
		top.add(internal);
		return top;
	}
	
	void setupUI() {
//        int leftwidth = 365-5, rightwidth=430-5, height=550;
        
        /////////////////  termpanel  ///////////////////
        
        setupTermfilterSpinners();

        JPanel termfilterPanel = new JPanel();
        termfilterPanel.setLayout(new BoxLayout(termfilterPanel, BoxLayout.X_AXIS));
        termfilterPanel.add(new JLabel("Term Prob >="));
        termfilterPanel.add(tpSpinner);
        
        termfilterPanel.add(new JLabel("Count >="));
        termfilterPanel.add(tcSpinner);
        
        termlistInfo = new JLabel();
        
        //////  termtable: below the frequency spinners  /////
        
        docdrivenTermTable = new TermTable(new TermTableModel());
        docdrivenTermTable.model.terms = () -> docdrivenTerms;
        docdrivenTermTable.model.comparison = () -> docvarCompare;
        docdrivenTermTable.setupTermTable();
		addTermdriverAction(docdrivenTermTable);
        docdrivenTermTable.doubleClickListener = this::pinTerm;
        
        termdrivenTermTable = new TermTable(new TermTableModel());
        termdrivenTermTable.model.terms = () -> termdrivenTerms;
        termdrivenTermTable.model.comparison = () -> termtermBoolqueryCompare;
        termdrivenTermTable.setupTermTable();
        termdrivenTermTable.doubleClickListener = this::pinTerm;
        
        pinnedTermTable = new TermTable(new TermTableModel());
        pinnedTermTable.model.terms = () -> pinnedTerms;
        pinnedTermTable.model.comparison = () -> docvarCompare;
        pinnedTermTable.setupTermTable();
        addTermdriverAction(pinnedTermTable);
        pinnedTermTable.doubleClickListener = this::unpinTerm;

        JPanel pinnedWrapper = new JPanel(new BorderLayout());
        pinnedWrapper.add(pinnedTermTable.top(), BorderLayout.CENTER);
        
        JPanel docdrivenWrapper = new JPanel(new BorderLayout());
        JPanel topstuff = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topstuff.add(termlistInfo);
        docdrivenWrapper.add(topstuff, BorderLayout.NORTH);
        docdrivenWrapper.add(docdrivenTermTable.top(), BorderLayout.CENTER);
                
        JPanel termdrivenWrapper = new JPanel(new BorderLayout());
        termtermDescription = new JLabel("");
        termdrivenWrapper.add(termtermDescription, BorderLayout.NORTH);
        termdrivenWrapper.add(termdrivenTermTable.top(), BorderLayout.CENTER);
        
        //////////////////////////  right-side panel  /////////////////////////
        
        mainqueryInfo = new JLabel();
//        queryInfo.setPreferredSize(new Dimension(rightwidth-killqueryW,20));
        subqueryInfo = new JLabel();
//        subqueryInfo.setPreferredSize(new Dimension(rightwidth-killqueryW,20));
        JPanel queryInfo = new JPanel() {{ add(mainqueryInfo); add(subqueryInfo); }};

        brushPanel = new BrushPanel(this, corpus.allDocs());
        brushPanel.yLevels = corpus.yLevels;
        brushPanel.setOpaque(true);
        brushPanel.setBackground(Color.white);
//        brushPanel.setPreferredSize(new Dimension(rightwidth, 250));
        brushPanel.setBorder(BorderFactory.createLineBorder(Color.black));
        brushPanel.setDefaultXYLim(corpus);
        
        textPanel = new TextPanel();
//        textPanel.scrollpane.setMinimumSize(new Dimension(rightwidth-10,300));
        
		DockController controller = new DockController();
		SplitDockStation station = new SplitDockStation();
		controller.add(station);

		SplitDockGrid grid = new SplitDockGrid();

//		grid.addDockable(0,0, 1,1, new DefaultDockable("BLABLA") {{ }});

		grid.addDockable(0,0,   1,10, new DefaultDockable("Pinned terms") {{ add(pinnedWrapper); }});
		grid.addDockable(0,10, 1,20, new DefaultDockable("Covariate-associated terms") {{ add(docdrivenWrapper); }});
		grid.addDockable(0,30, 1,20, new DefaultDockable("Term-associated terms") {{ add(termdrivenWrapper); }});
		
		grid.addDockable(1,0, 1,5, new DefaultDockable("Query info") {{ add(queryInfo); }});
//		grid.addDockable(1,5, 1,5, new DefaultDockable("qi2") {{ add(subqueryInfo); }});
		grid.addDockable(1,10, 1,15, new DefaultDockable("Covariate view") {{ add(brushPanel); }});
		grid.addDockable(1,25, 1,20, new DefaultDockable("Text view") {{ add(textPanel.top()); }});
		
		station.dropTree(grid.toTree());

        mainFrame = new JFrame("Text Explorer Tool");
		mainFrame.add(station.getComponent());
		mainFrame.setBounds(20,20, 800,600);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        ToolTipManager.sharedInstance().setDismissDelay((int) 1e6);
	}
	
	static JPanel wrapWithPadding(JComponent comp, int mar) {
//		JPanel tmp = new JPanel(new FlowLayout(FlowLayout.CENTER,mar,mar));
		JPanel tmp = new JPanel();
		tmp.setBorder(BorderFactory.createEmptyBorder(mar,mar,mar,mar));
		tmp.setLayout(new BorderLayout());
        tmp.add(comp, BorderLayout.CENTER);
        return tmp;
	}
	
	private static JButton createSimpleButton(String text) {
		  JButton button = new JButton(text);
		  button.setForeground(Color.BLACK);
		  button.setBackground(Color.WHITE);
		  Border line = new LineBorder(Color.BLACK);
		  Border margin = new EmptyBorder(1,1,1,1);
		  Border compound = new CompoundBorder(line, margin);
		  button.setBorder(compound);
		  return button;
		}


	void setupTermfilterSpinners() {
		tpSpinner = new JSpinner(new SpinnerStuff.MySM());
        JFormattedTextField tpText = ((JSpinner.DefaultEditor) tpSpinner.getEditor()).getTextField();
        tpText.setFormatterFactory(new AbstractFormatterFactory() {
			@Override public AbstractFormatter getFormatter(JFormattedTextField tf) {
				return new SpinnerStuff.NiceFractionFormatter();
			}
        });
        tpSpinner.setValue(.0005);
        tpSpinner.setPreferredSize(new Dimension(150,30));
        tpSpinner.addChangeListener(e -> refreshDocdrivenTermList());

        tcSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 1000, 1));
        tcSpinner.setPreferredSize(new Dimension(60,30));
        tcSpinner.setValue(1);
        tcSpinner.addChangeListener(e -> refreshDocdrivenTermList());
	}
	
	public static void main(String[] args) throws IOException {
		final Main main = new Main();
		main.initData();
		SwingUtilities.invokeLater(() -> {
			main.setupUI();
			main.uiOverrides();
			main.mainFrame.setVisible(true);
		});
	}
}
