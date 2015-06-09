package te.ui;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import te.data.Corpus;
import te.data.DataLoader;
import te.data.NLP;
import te.exceptions.BadConfig;
import te.exceptions.BadData;
import te.exceptions.BadSchema;
import utility.util.U;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public class Configuration {
	Config conf;
	String dirOfConfFile;
	String tokenizerName = null;
			

	public static Configuration defaultConfiguration() {
		Configuration c = new Configuration();
		//c.conf = new EmptyConfig();
		c.conf = ConfigFactory.parseString("{}");
		c.tokenizerName = "StanfordTokenizer";
		return c;
	}
	/** 
	 * this is how shell "dirname" works:
	 * 
	 *    asdf/qwer.txt => asdf
	 * 
	 *    abcd => .
	 */
	public static String dirname(String f) {
		String par = (new File(f)).getParent();
		return par==null ? "." : par;
	}
	public static String basename(String f) {
		return (new File(f)).getName();
	}
	public static boolean isAbsolute(String f) {
		return new File(f).isAbsolute();
	}
	
	static String resolvePath(String dirOfConfFile, String pathInConfFile) {
		if (isAbsolute(pathInConfFile)) {
			return pathInConfFile;
		}
		else {
			return new File(dirOfConfFile, pathInConfFile).toString();
		}
	}
	static String resolvePathExists(String dirOfConfFile, String pathInConfFile) throws BadSchema {
		String f = resolvePath(dirOfConfFile,pathInConfFile);
		assertFileExists(f);
		return f;
	}
	
	static void assertFileExists(String filename) throws BadSchema {
		if ( ! (new File(filename)).exists()) {
			throw new BadSchema("File does not exist: " + filename);
		}
	}
	/** run this only once all the document texts are loaded */
	public void doNLPBasedOnConfig(Corpus corpus) throws BadConfig, BadSchema, IOException {
		if (conf.hasPath("nlp_file") && conf.hasPath("tokenizer"))
			throw new BadConfig("Don't specify both tokenizer and nlp_file");
		if (conf.hasPath("nlp_file")) {
			String f = resolvePathExists(dirOfConfFile, conf.getString("nlp_file"));
			corpus.loadNLP(f);
		}
		else {
			String tname;
			if (conf.hasPath("tokenizer")) {
				tname = conf.getString("tokenizer");
			} else {
				U.p("Defaulting to tokenizer=StanfordTokenizer");
				tname = "StanfordTokenizer";
			}
			switch (tname) {
				case "WhitespaceTokenizer":
					corpus.runTokenizer(NLP::whitespaceTokenize);
					break;
				case "StanfordTokenizer":
					corpus.runTokenizer(NLP::stanfordTokenize);
					break;
				default:
					throw new BadConfig("Unknown tokenizer: " + tname);
			}
		}
	}

	public Supplier<Void> afteranalysisCallback = () -> null;
	public String xattr, yattr;

	public void initWithConfig(Corpus corpus, String filename, DataLoader dataloader) throws IOException, BadConfig, BadSchema {

		// TODO in the future, this function shouldn't be responsible for actually running potentially-expensive analysis routines.
		// it should queue them up somehow.
		
		dirOfConfFile = dirname(filename);
		File _file = new File(filename);
		conf = ConfigFactory.parseFile(_file);
		conf = conf.resolve();
		U.p(conf);
		
		if (conf.hasPath("indicatorize") && conf.getBoolean("indicatorize")) {
			afteranalysisCallback = () -> { corpus.indicatorize(); return null; };
		}
		if (conf.hasPath("data")) {
			String path = resolvePathExists(dirOfConfFile, conf.getString("data"));
			try {
				dataloader.loadJsonLines(path);
			} catch (BadData | IOException e) {
				e.printStackTrace();
			}
			corpus.needsCovariateTypeConversion = true;
		}
		if (conf.hasPath("schema")) {
			Object schema = conf.getAnyRef("schema");
			if (schema instanceof String) {
				String sfilename = resolvePathExists(dirOfConfFile, (String) schema);
				corpus.getSchema().loadSchemaFromFile(sfilename);
			}
			else {
				corpus.getSchema().loadSchemaFromConfigObject(conf.getObject("schema"));
			}
		}

		// data view config should be set only after all data is loaded.
		if (conf.hasPath("x")) {
			String x = conf.getString("x");
			if(!corpus.attributeExists(x)) {
				assert false : "bad x variable " + x;
			}
			xattr = x;
		}
		if (conf.hasPath("y")) {
			String y = conf.getString("y");
			if (!corpus.attributeExists(y)) {
				assert false : "bad y variable" + y;
			}
			yattr = y;
		}
	}


	/**
	 * Construct a configuration object from an empty corpus and any command line arguments.
	 * @param corpus document storage object.
	 * @param args command line arguments.
	 * @return a newn configuration that is almost ready.
	 * @throws IOException
	 * @throws BadConfig
	 * @throws BadSchema
	 * @throws BadData
	 */
	public static Configuration initializeFromCommandlineArgs(Corpus corpus, String args[]) throws IOException, BadConfig, BadSchema, BadData {
		Configuration c = null;
		boolean gotConfFile = false;
		DataLoader dataloader = new DataLoader();

		for (String arg : args) {
			Path p = Main.FS.getPath(arg);
//			U.pf("%s  isfile %s  isdir %s\n", arg, Files.isRegularFile(p), Files.isDirectory(p));
			if (Files.isDirectory(p)) {
				dataloader.loadTextFilesFromDirectory(arg);
			} else if (Files.isRegularFile(p)) {
				if (arg.matches(".*\\.(conf|config)$")) {
					if (gotConfFile) {
						assert false : "more than one configuration file specified";
					}
					U.pf("Processing as config file: %s\n", arg);
					gotConfFile = true;
					c = new Configuration();
					c.initWithConfig(corpus, arg, dataloader);
				} else if (arg.endsWith(".txt")) {
					dataloader.loadTextFileAsDocumentText(arg);
				}
			} else {
				U.p("WARNING: can't handle argument: " + arg);
			}
		}
		corpus.setDataFromDataLoader(dataloader);
		if (c==null) {
			c = defaultConfiguration();
		}
		c.doNLPBasedOnConfig(corpus);
		return c;
	}


}
