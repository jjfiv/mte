package te.ui;

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
import java.util.Collections;
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
import javax.swing.JTextArea;
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

import bibliothek.gui.DockController;
import bibliothek.gui.dock.DefaultDockable;
import bibliothek.gui.dock.SplitDockStation;
import bibliothek.gui.dock.station.split.SplitDividerStrategy;
import bibliothek.gui.dock.station.split.SplitDockGrid;
import te.data.Analysis;
import te.data.Corpus;
import te.data.DocSet;
import te.data.Document;
import te.data.NLP;
import te.data.TermInstance;
import te.data.TermQuery;
import te.data.TermVector;
import te.data.Analysis.TermvecComparison;
import te.data.Schema.Levels;
import te.exceptions.BadConfig;
import te.exceptions.BadSchema;
import te.ui.textview.FullDocViewer;
import te.ui.textview.Highlighter;
import te.ui.textview.KWICViewer;
import util.BasicFileIO;
import util.JsonUtil;
import util.U;
import edu.stanford.nlp.util.StringUtils;

/** someone who listens to updates from a brush panel */
interface BrushPanelListener {
	public void receiveCovariateQuery(Collection<String> docids);
}

public class Main implements BrushPanelListener {
	public Corpus corpus = new Corpus();
	public DocSet curDS = new DocSet();
	Document currentFulldoc;
	
	private String xattr, yattr;

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
	KWICViewer kwicPanel;
	FullDocViewer fulldocPanel;
	DefaultDockable fulldocDock;
	InfoArea mainqueryInfo;
	InfoArea subqueryInfo;
	JLabel termlistInfo;
	JSpinner tpSpinner;
	JSpinner tcSpinner;
	JLabel tcInfo;
	InfoArea termtermDescription;
//	private JButton killDocvarQuery;
	
	NLP.DocAnalyzer da = new NLP.UnigramAnalyzer();
	Supplier<Void> afteranalysisCallback = () -> null;
	Supplier<Void> uiOverridesCallback = () -> null;
	
	/** only call this after schema is set. return false if fails. */
	public boolean setXAttr(String xattrName) {
		if ( ! corpus.schema.varnames().contains(xattrName)) {
			return false;
		}
		xattr = xattrName;
		return true;
	}
	/** only call this after schema is set. return false if fails. */
	public boolean setYAttr(String yattrName) {
		if ( ! corpus.schema.varnames().contains(yattrName)) {
			return false;
		}
		yattr = yattrName;
		return true;
	}

	void uiOverrides() {
		uiOverridesCallback.get();
	}
	
	void finalizeCorpusAnalysisAfterConfiguration() {
		if (corpus.needsCovariateTypeConversion) {
			corpus.convertCovariateTypes();	
		}
		corpus.calculateCovariateSummaries();
		U.p("Analyzing document texts");
		for (Document doc : corpus.docsById.values()) {
			NLP.analyzeDocument(da, doc);	
		}
		afteranalysisCallback.get();
		U.p("done analyzing");
		corpus.finalizeIndexing();
	}
	double getTermProbThresh() {
		return (double) tpSpinner.getValue();
	}
	int getTermCountThresh() {
		return (int) tcSpinner.getValue();
	}
	@Override
	public void receiveCovariateQuery(Collection<String> docids) {
		curDS = corpus.getDocSet(docids);
		refreshQueryInfo();
		refreshDocdrivenTermList();
		refreshKWICPanel();
	}
	void refreshKWICPanel() {
		kwicPanel.show(getCurrentTQ().terms, curDS);
	}
	void selectTerminstForFullview(Document d, TermInstance ti) {
		selectSingleDocumentForFullview(d);
		fulldocPanel.textarea.requestScrollToTerminst(ti);
	}
	void selectSingleDocumentForFullview(Document doc) {
		fulldocDock.setTitleText("Document: " + doc.docid);
		fulldocPanel.show(getCurrentTQ().terms, doc);
		currentFulldoc = doc;
		brushPanel.setFulldoc(doc);
		kwicPanel.top().repaint();
	}
	void refreshSingleDocumentInFullview() {
		fulldocPanel.showForCurrentDoc(getCurrentTQ().terms, false);
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
		String s = U.sf("Docvar selection: %s docs, %s wordtoks", 
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
		refreshKWICPanel();
		brushPanel.showTerms(curTQ);
		runTermTermQuery(curTQ);
		refreshSingleDocumentInFullview();
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
	
	static class InfoArea extends JLabel {
		public InfoArea(String s) {
			super(s);
			// WTF
//			setMaximumSize(new Dimension(100,50));
			setMinimumSize(new Dimension(200,16));
			setBackground(Color.WHITE);
		}
//		public Dimension getMaximumSize() {
////			return new Dimension(300,50);
//		}
	}
	
	static String sizes(JComponent x) {
		return String.format("size=%s prefsize=%s min=%s max=%s", x.getSize(), x.getPreferredSize(), x.getMinimumSize(), x.getMaximumSize());
	}
	
	void setupUI() {
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
                
        termtermDescription = new InfoArea("");
        JPanel termdrivenWrapper = new JPanel(new BorderLayout()) {{
        	addComponentListener(new ComponentAdapter() {
        		@Override
        		public void componentResized(ComponentEvent e) {
//        			U.p("termterm " + sizes(termtermDescription));
        		}
        	});
        }};
        termdrivenWrapper.add(termtermDescription, BorderLayout.NORTH);
        termdrivenWrapper.add(termdrivenTermTable.top(), BorderLayout.CENTER);
        
        //////////////////////////  right-side panel  /////////////////////////
        
        mainqueryInfo = new InfoArea("");
        subqueryInfo = new InfoArea("");
        
        JPanel queryInfo = new JPanel() {{
	        	setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
	        	add(mainqueryInfo); add(subqueryInfo);
	        	addComponentListener(new ComponentAdapter() {
	        		@Override
	        		public void componentResized(ComponentEvent e) {
//	        			U.p("mainqueryInfo " + sizes(mainqueryInfo));
//	        			U.p("subqueryInfo " + sizes(subqueryInfo));
	        		}
	        	});
    		}
        };

        brushPanel = new BrushPanel(this, corpus.allDocs());
        brushPanel.schema = corpus.schema;
        brushPanel.setOpaque(true);
        brushPanel.setBackground(Color.white);
//        brushPanel.setPreferredSize(new Dimension(rightwidth, 250));
        brushPanel.setBorder(BorderFactory.createLineBorder(Color.black));
        // todo this is bad organization that the app owns the xattr/yattr selections and copies them to the brushpanel, right?
        // i guess eventually we'll need a current-user-config object as the source of truth for this and brushpanel should be hooked up to pull from it?
        if (xattr != null) brushPanel.xattr = xattr;
        if (yattr != null) brushPanel.yattr = yattr;
        brushPanel.setDefaultXYLim(corpus);
        
        kwicPanel = new KWICViewer();
        kwicPanel.fulldocClickReceiver = this::selectSingleDocumentForFullview;
        kwicPanel.fulldocTerminstClickReceiver = this::selectTerminstForFullview;
        kwicPanel.getCurrentFulldocDoc = () -> this.currentFulldoc;
        
        fulldocPanel = new FullDocViewer();
        
		DockController controller = new DockController();
		SplitDockStation station = new SplitDockStation();
		controller.add(station);
		
		SplitDockGrid grid = new SplitDockGrid();

		fulldocDock = new DefaultDockable("Document view") {{ add(fulldocPanel.top()); }};

//		double x=0.5, rx=1-0.5;
		double w1=3, w2=6;
		double y,h;
		y=0;
		grid.addDockable(0,0,   w1,h=5, new DefaultDockable("Pinned terms") {{ add(pinnedWrapper); }});
		grid.addDockable(0,y+=h, w1,h=2, new DefaultDockable("Frequency control") {{ add(termfilterPanel); }});
		grid.addDockable(0,y+=h, w1,h=10, new DefaultDockable("Covariate-associated terms") {{ add(docdrivenWrapper); }});
		grid.addDockable(0,y+=h, w1,h=5, new DefaultDockable("Term-associated terms") {{ add(termdrivenWrapper); }});
//		grid.addDockable(0,y+=h, x,8, fulldocDock);
		
		y=0;
		grid.addDockable(w1,y, w2,h=3, new DefaultDockable("Query info") {{ add(queryInfo); }});
		grid.addDockable(w1,y+=h, w2,h=7, new DefaultDockable("Covariate view") {{ add(brushPanel); }});
		h=15;
		grid.addDockable(w1, y,           w2/2, h, new DefaultDockable("KWIC view") {{ add(kwicPanel.top()); }});
		grid.addDockable(w1+w2/2, y, w2/2, h, fulldocDock);
	
		station.dropTree(grid.toTree());

        mainFrame = new JFrame("Text Explorer Tool");
		mainFrame.add(station.getComponent());
		mainFrame.pack();
		mainFrame.setBounds(15,0, 1000, 768-25);  //  mac osx toolbar is 22,23ish tall
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
        tcSpinner.setValue(2);
        tcSpinner.addChangeListener(e -> refreshDocdrivenTermList());
	}
	
	static void usage() {
		System.out.println("Usage:  Launch ConfigFilename");
		System.exit(1);
	}
	
	public static void myMain(String[] args) throws IOException, BadSchema, BadConfig {
		final Main main = new Main();
		
		if (args.length < 1) usage();
		
		if (args[0].equals("--debug")) {
			new ExtraInit(main).initWithCode();	
		}
		else {
			Configuration.initWithConfig(main, args[0]);
			main.finalizeCorpusAnalysisAfterConfiguration();
		}

		SwingUtilities.invokeLater(() -> {
			main.setupUI();
			main.uiOverrides();
			main.mainFrame.setVisible(true);
		});
	}
}
