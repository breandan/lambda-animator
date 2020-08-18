package lambda;

import lambda.Eval.Strategy;
import lambda.Heap.*;
import lambda.Primitives.BuiltInFunctions;
import org.json.JSONException;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URL;
import java.security.Permission;
import java.util.*;

public class Gui {
	public static void main(String[] args) {
		String resources = "http://thyer.name/lambda-animator/resources";

		if (args.length==2 && args[0].equals("-resources")) {
			resources = args[1];
		}
		else if (args.length!=0) {
			System.err.println("usage: lambda.Gui [-reseources <url>]");
			System.exit(1);
		}
		
		final String resourcesFinal = resources;
		
		SwingUtilities.invokeLater (new Runnable() {
			public void run() {
				JFrame frame = new JFrame("Lambda Animator");
				frame.getContentPane().add(new Panel (resourcesFinal));
				frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				frame.setMinimumSize(new Dimension (200,200));
				frame.setSize (640,480);
				frame.setVisible(true);
			}
		});
	}
	
	static class Panel extends JPanel 
		implements HtmlViewer.Callback, GraphViewer.Callback, InputPanel.Callback 
	{
		final att.grappa.Graph graph = new att.grappa.Graph ("");
		final JScrollBar stepScroll = new JScrollBar (JScrollBar.VERTICAL, 0, 1, 0, 1);
		final JTabbedPane tabbedPane = new JTabbedPane();
		final JLabel status = new JLabel (" ");
		final InputPanel inputPanel = new InputPanel(this);
		final GraphViewer graphPanel = new GraphViewer(this, graph);
		final JPanel graphTab = new JPanel (new GridBagLayout());
		final JFileChooser fileChooser = makeFileChooser();
		final PostponedRedraw postponedRedraw = new PostponedRedraw ();

		enum State {PAUSED, STEPPING, RUNNING};
		State state=State.PAUSED;
		int actionStartStep;
		int lastSnapshotStep;
		
		final BuiltInFunctions builtins = Primitives.makeBuiltIns();
		Env env = new Env ();
		Addr root;
		Stack stack = new Stack();
		ArrayList<Snapshot> snapshots = new ArrayList<Snapshot>();
		int currentSnapshot;
		
		int betaCount, deltaCount, substCount;
		int getCurrentStep () { return betaCount + deltaCount + substCount; } 
			
		InputPanel.Settings settings = new InputPanel.Settings ("", "", Eval.Strategy.NAME, Eval.Strategy.NAME, Eval.Form.WHNF, true);
		
		void placeComponent (JComponent parent, JComponent child, int x, int y, int w, int h, boolean sw, boolean sh) {
			int fill = sw ? sh ? GridBagConstraints.BOTH 
					           : GridBagConstraints.HORIZONTAL 
					      : sh ? GridBagConstraints.VERTICAL 
					    	   : GridBagConstraints.NONE;
			parent.add(child, new GridBagConstraints(x, y, w, h, sw?1.0:0.0, sh?1.0:0.0, 
					                GridBagConstraints.WEST, fill, new Insets(1,1,1,1), 0, 0));
		}
		
		Panel (final String resources) {
			super (new GridBagLayout());
			
			final JMenuBar appMenuBar = new JMenuBar ();
			final HtmlViewer introText = new HtmlViewer (this);
			final HtmlViewer examplesText = new HtmlViewer (this);
			
			tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
			tabbedPane.add("Introduction", new JScrollPane (introText));
			tabbedPane.add("Examples", new JScrollPane (examplesText));
			tabbedPane.add("Input", inputPanel);
			tabbedPane.add("Reductions", graphTab);
			tabbedPane.add("About", new JScrollPane (aboutPanel));
			
			tabbedPane.setSelectedComponent(inputPanel);
			tabbedPane.setSelectedIndex(0);
			tabbedPane.addChangeListener(tabChangeListener);
			tabbedPane.setFocusable(false);

			if (fileChooser!=null) {
				JMenu fileMenu = new JMenu ("File");
				fileMenu.add(openAction);
				fileMenu.add(saveAction);
				appMenuBar.add(fileMenu);
				appMenuBar.setMinimumSize(appMenuBar.getPreferredSize());
			}
			
			JPanel graphToolbar = new JPanel(new GridBagLayout());
			
			placeComponent (this,     appMenuBar,         1, 1, 1, 1, true,  false);
			placeComponent (this,     tabbedPane,         1, 2, 1, 1, true,  true);
			placeComponent (this,     status,             1, 3, 1, 1, true,  false);
			
			placeComponent (graphTab, graphPanel,         2, 2, 1, 1, true,  true);
			placeComponent (graphTab, graphToolbar,       1, 1, 2, 1, true,  false);
			placeComponent (graphTab, stepScroll,         3, 2, 1, 1, false, true);
			
			placeComponent (graphToolbar, new JLabel(" Step size: "), 1, 1, 1, 1, false, false);
			placeComponent (graphToolbar, stepSizeCombo,             2, 1, 1, 1, false, false);
			placeComponent (graphToolbar, new Button (stepAction),  3, 1, 1, 1, false, false);
			placeComponent (graphToolbar, new Button (runAction),   4, 1, 1, 1, false, false);
			placeComponent (graphToolbar, new Button (pauseAction), 5, 1, 1, 1, false, false);
			placeComponent (graphToolbar, scaleToFitCheckBox,        8, 1, 1, 1, false, false);
			placeComponent (graphToolbar, new JLabel(" Max nodes: "), 6, 1, 1, 1, false, false);
			placeComponent (graphToolbar, pruneGraphCombo,             7, 1, 1, 1, false, false);
			placeComponent (graphToolbar, new JPanel(),              9, 1, 2, 1, true, false);

			pruneGraphCombo.addActionListener(pruneGraphListener);
			pruneGraphCombo.setFocusable(false);
			pruneGraphCombo.setValue(100);
			
			stepScroll.addAdjustmentListener(stepAdjustmentListener);			
			scaleToFitCheckBox.setSelected(false);
			scaleToFitCheckBox.setFocusable(false);

			InputMap inputMap = graphTab.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
			inputMap.put(KeyStroke.getKeyStroke("UP"), "prevStep");
			inputMap.put(KeyStroke.getKeyStroke("DOWN"), "nextStep");
			ActionMap actionMap = graphTab.getActionMap();
			actionMap.put("prevStep", prevStepAction);
			actionMap.put("nextStep", nextStepAction);

			//use these keysrokes for changing tab
			KeyStroke prevTabKeyStroke = KeyStroke.getKeyStroke("alt LEFT"); 
			KeyStroke nextTabKeyStroke = KeyStroke.getKeyStroke("alt RIGHT"); 

			inputMap = this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
			inputMap.put(prevTabKeyStroke, "prevTab");
			inputMap.put(nextTabKeyStroke, "nextTab");
			inputMap.put(KeyStroke.getKeyStroke("control O"), "open");
			inputMap.put(KeyStroke.getKeyStroke("control S"), "save");
			actionMap = this.getActionMap();
			actionMap.put("prevTab", prevTab);
			actionMap.put("nextTab", nextTab);
			actionMap.put("open", openAction);
			actionMap.put("save", saveAction);

			inputPanel.setSettings(settings);
			
			setState (State.PAUSED);

			SwingUtilities.invokeLater(new Runnable () {
				public void run() {
					introText.openPage (resources+"/intro.html");
					examplesText.openPage (resources+"/examples.html");
				}
			});
		}

		public void reportStatus (String text) {
			status.setText(text==null ? " " : text);
		}
		public void setStatus (String text) {
			status.setText(text);
		}
		void clearStatus () {
			status.setText(" ");
		}

		ChangeListener tabChangeListener = new ChangeListener () {
			public void stateChanged(ChangeEvent e) {
				clearStatus();
			}
		};
		Action prevTab = new AbstractAction ("prevTab") {
			public void actionPerformed(ActionEvent e) {
				int numTabs = tabbedPane.getTabCount();
				tabbedPane.setSelectedIndex((tabbedPane.getSelectedIndex()+numTabs-1) % numTabs);
			}
		};
		Action nextTab = new AbstractAction ("nextTab") {
			public void actionPerformed(ActionEvent e) {
				int numTabs = tabbedPane.getTabCount();
				tabbedPane.setSelectedIndex((tabbedPane.getSelectedIndex()+1) % numTabs);
			}
		};

		
		JFileChooser makeFileChooser () {
			JFileChooser fileChooser;
			SecurityManager securityManager = System.getSecurityManager();
			Permission permission = new FilePermission ("<<ALL FILES>>", "read,write,delete");
			try {
				if (securityManager!=null) 
					securityManager.checkPermission(permission);
				fileChooser = new JFileChooser (".");
			}
			catch (Exception exc) {
				return null;
			}
			catch (Error exc) {
				return null;
			}
			//fileChooser.setCurrentDirectory(new File ("."));
			return fileChooser;
		}
		
		public static class Snapshot {
			final int step;
			//final ArrayList<Integer> stack;
			final String graphStr;
			final int beta, delta, subst;
			Snapshot (int step, /*ArrayList<Integer> stack,*/ String graphStr, int beta, int delta, int subst) {
				this.step = step;
				//this.stack = stack;
				this.graphStr = graphStr;
				this.beta = beta;
				this.delta = delta;
				this.subst = subst;
			}
		}
		
		Snapshot takeSnapshot () throws IOException {
			//ArrayList<Integer> stack2 = stack.getAddrIds();
			att.grappa.Graph graph = new att.grappa.Graph ("");
			Graph.buildGraph (graph, root, stack, pruneGraphCombo.getValue());
			StringWriter writer = new StringWriter ();
			graph.printGraph(writer);
			String graphStr = writer.toString();
			return new Snapshot (betaCount+deltaCount+substCount, /*stack2,*/ graphStr, betaCount, deltaCount, substCount);
		}
		
		void takeNewSnapshot () {
			try {
				Snapshot snapshot = takeSnapshot ();
				snapshots.add(snapshot);
				setCurrentSnapshot(snapshots.size()-1);
				postponedRedraw.redraw();
				lastSnapshotStep = getCurrentStep();
			}
			catch (IOException exc) {
				setStatus ("Error rendering graph: "+exc.getMessage());
			}
		}
		void retakeLastSnapshot () {
			try {
				Snapshot snapshot = takeSnapshot ();
				snapshots.set(snapshots.size()-1, snapshot);
				postponedRedraw.redraw();
			}
			catch (IOException exc) {
				setStatus ("Error rendering graph: "+exc.getMessage());
			}
		}
		
		public boolean hyperlinkClickFilter (URL url) {
			String urlStr = url.toString();
			if (urlStr.substring(urlStr.length()-3).equals(".la")) {
				// handle the link here
				try {
					setStatus ("opening example: "+urlStr);
					InputStream inputStream = url.openStream();
					openInputStream (inputStream);
				}
				catch (Exception exc) {
					setStatus("Error opening example: "+exc);
				}
				// and forbid the viewer following the link
				return false;
			}
			else {
				// otherwise permit the viewer to follow the link
				return true;
			}
		}
		
		Action scaleToFitAction = new AbstractAction ("Scale to fit") {
			public void actionPerformed(ActionEvent e) {
				if (scaleToFitCheckBox.isSelected()) {
					graphPanel.scaleToFit();
					graphPanel.repaint();
				}
			}
		};
		JCheckBox scaleToFitCheckBox = new JCheckBox (scaleToFitAction);
		
		static class Button extends JButton {
			Button (Action action) {
				super(action);
				setFocusable(false);
			}			
		}
		
		static class IntegerCombo extends JComboBox {
			int [] values;
			IntegerCombo (int ... values)
			{
				this.values = values;
				Arrays.sort(this.values);
				for (int value : values) {
					addItem (Integer.toString(value));
				}
				setFocusable(false);
			}
			int getValue () {
				return values[getSelectedIndex()];
			}
			void setValue (int value) {
				int pos = Arrays.binarySearch(values, value);
				if (pos>=0)
					setSelectedIndex(pos);
			}
		};
		IntegerCombo stepSizeCombo = 
			new IntegerCombo (1, 10, 100, 1000, 10000, 100000);
		
		IntegerCombo pruneGraphCombo = 
			new IntegerCombo ( 10,   15,   25,   40,   60, 
					           100,  150,  250,  400,  600, 
					           1000, 1500, 2500, 4000, 6000, 
					           10000 );
		
		ActionListener pruneGraphListener = new ActionListener () {
			public void actionPerformed(ActionEvent arg0) {
				if (root != null) {
					retakeLastSnapshot();
				}
			}
		};
		
		void step (int numSteps) {
			try {
				for (int i=0; i!=numSteps; i++) {
					showStep();
					if (stack.empty())
						break;
					Addr redexAddr = stack.peek().addr;
					if (stack.size() > 1)
						stack.pop();
					Node redexNode = redexAddr.deref();
					//if (redexNode.isInStack())
					//	stack.resetToRoot();
					Heap.Node.Tag nodeTag = redexNode.tag;
					Eval.reduce(redexAddr, settings.copyArgs);
					switch (nodeTag) {
					case APPLY:
						betaCount ++;
						break;
					case DELTA:
						deltaCount ++;
						break;
					case SUBST:
						substCount ++;
						break;
					default:
						throw new InternalError ("unexpected node type");
					}
					Eval.findNextRedex (stack);
				}
			}
			catch (Heap.BlackHole exc) {
				setStatus ("black hole in graph");
				root = null;
				stack.clear();
				setState (State.PAUSED);
			}
		}
		
		void setState (State newState) {
			state = newState;
			enableDisableActions();
			SwingUtilities.invokeLater(evalLoop);
		}
		
		Runnable evalLoop = new Runnable () {
			public void run() {
				//System.out.println(state);
				if (state==State.PAUSED) {	
					if (lastSnapshotStep != getCurrentStep()) {
						takeNewSnapshot();
					}
					return;
				}
				int stepSize = stepSizeCombo.getValue();
				int loopStartStep = getCurrentStep();
				int remainingSteps = actionStartStep+stepSize - loopStartStep;
				if (remainingSteps > 0) {
					try {
						step (Math.min(remainingSteps, 10));
						if (getCurrentStep() == loopStartStep)
							setState (State.PAUSED);
						else if (getCurrentStep() >= lastSnapshotStep + stepSize) {
							takeNewSnapshot();
							lastSnapshotStep = getCurrentStep();
						}
					}
					catch (Heap.BlackHole exc) {
						setStatus ("black hole in graph");
						root = null;
						stack.clear();
						setState(State.PAUSED);
						return;
					}
					SwingUtilities.invokeLater(evalLoop);
				}
				else {
					if (state==State.STEPPING || stack.empty())
						setState(State.PAUSED);
					else {
						actionStartStep = getCurrentStep();
						SwingUtilities.invokeLater(evalLoop);
					}
				}
			}
		};
		
		Action stepAction = new AbstractAction ("Step") {
			public void actionPerformed(ActionEvent e) {
				lastSnapshotStep = getCurrentStep ();
				actionStartStep = getCurrentStep ();
				setState (State.STEPPING);
			}
		};
		
		Action runAction = new AbstractAction ("Run") {
			public void actionPerformed(ActionEvent e) {
				lastSnapshotStep = getCurrentStep ();
				actionStartStep = getCurrentStep ();
				setState (State.RUNNING);
			}
		};
		
		Action pauseAction = new AbstractAction ("Pause") {
			public void actionPerformed(ActionEvent e) {
				setState (State.PAUSED);
			}
		};

		void setCurrentSnapshot (int step) {
			if (step >= 0 && step < snapshots.size()) {
				currentSnapshot = step;
				postponedRedraw.redraw();
			}
			stepScroll.setValues(currentSnapshot, 1, 0, snapshots.size());
			enableDisableActions();
		}
		
		AdjustmentListener stepAdjustmentListener = new AdjustmentListener () {
			public void adjustmentValueChanged(AdjustmentEvent e) {
				setCurrentSnapshot(stepScroll.getValue());
			}
		};
		
		Action nextStepAction = new AbstractAction () {
			public void actionPerformed(ActionEvent e) {
				if (currentSnapshot+1==snapshots.size() && stepAction.isEnabled())
					stepAction.actionPerformed(null);
				else
					setCurrentSnapshot (currentSnapshot+1);
			}
		};
		Action prevStepAction = new AbstractAction () {
			public void actionPerformed(ActionEvent e) {
				setCurrentSnapshot (currentSnapshot-1);
			}
		};
		
		void addContextMenu (final Component c, final JPopupMenu menu) {
			c.addMouseListener(new MouseAdapter () {
				public void mousePressed (MouseEvent e) {
					if ((e.getModifiers() & InputEvent.BUTTON3_MASK) != 0)
						menu.show(c, 0, c.getHeight());
				}
			});
		}
		
		void enableDisableActions () {
			boolean evalFinished = root==null || stack.empty();
			stepAction.setEnabled(!evalFinished && state==State.PAUSED);
			runAction.setEnabled(!evalFinished && state==State.PAUSED);
			pauseAction.setEnabled(state != State.PAUSED);
			stepSizeCombo.setEnabled(!evalFinished);
			pruneGraphCombo.setEnabled(currentSnapshot==snapshots.size()-1 && root!=null);
		}
		
		
		void openInputStream (InputStream inputStream) throws IOException, JSONException {
			Document document = Document.readFile(inputStream);
			inputPanel.setSettings(document.settings);
			Panel.this.root = null;
			Panel.this.stack.clear();
			Panel.this.snapshots.clear();
			Panel.this.snapshots.addAll(Arrays.asList(document.snapshots));
			Panel.this.settings = document.settings;					
			setCurrentSnapshot(0);
			drawGraph(true);
			//scaleToFitCheckBox.setSelected(false);
			tabbedPane.setSelectedComponent(graphTab);
			setState(State.PAUSED);
		}

		Action openAction = new AbstractAction ("Open...") {
			public void actionPerformed(ActionEvent e) {
				try {
					int dialogResult = fileChooser.showOpenDialog(null);
					if (dialogResult != JFileChooser.APPROVE_OPTION)
						return;
					File file = fileChooser.getSelectedFile();
					setStatus ("opening: "+file.toString());
					InputStream inputStream = new FileInputStream(file);
					openInputStream (inputStream);
				} catch (Exception exc) {
					setStatus("open failed: "+exc.getMessage());
				}				
			}			
		};
		
		Action saveAction = new AbstractAction ("Save...") {
			public void actionPerformed(ActionEvent ae) {
				try {
					int dialogResult = fileChooser.showSaveDialog(null);
					if (dialogResult != JFileChooser.APPROVE_OPTION)
						return;
					File file = fileChooser.getSelectedFile();
					setStatus ("saving to: "+file.toString());
					Document document = new Document (settings, snapshots);
					Document.writeFile(document, new FileOutputStream(file));
				} catch (Exception exc) {
					setStatus ("save failed: "+exc.getMessage());
				}
			}
		};
		
		public void graphViewMoved () {
			scaleToFitCheckBox.setSelected(false);			
		}
		public void graphViewScaled () {
			scaleToFitCheckBox.setSelected(false);			
		}


		void showStep () {
			status.setText(String.format("Step: %s, Beta: %s, Delta: %s, Subst: %s", getCurrentStep(), betaCount, deltaCount, substCount));
		}
		
		void drawGraph (boolean scaleToFit) {
			att.grappa.Node node = graph.findNodeByName("root");
			att.grappa.GrappaPoint oldRootPos=null, newRootPos;
			if (node != null) {
				oldRootPos = node.getCenterPoint();
			}
			if (snapshots.size()==0)
				return;
			if (currentSnapshot >= snapshots.size() || currentSnapshot < 0)
				currentSnapshot=0;
			if (snapshots.size() != 0) {
				Snapshot snapshot = snapshots.get(currentSnapshot);
				status.setText(String.format("Step: %s, Beta: %s, Delta: %s, Subst: %s", snapshot.step, snapshot.beta, snapshot.delta, snapshot.subst));
				//System.out.println(snapshot.graphStr);
				att.grappa.Parser parser = new att.grappa.Parser (new StringReader(snapshot.graphStr), null, graph);
				try {
					parser.parse();
				}
				catch (Exception exc) {
					setStatus ("Graphviz parse error: "+exc.getMessage());
					return;
				}
			}
			else {
				graph.reset();
			}
			
			att.grappa.Node rootNode = graph.findNodeByName("root");
			if (oldRootPos != null && rootNode !=null) {
				newRootPos = rootNode.getCenterPoint();
				graphPanel.translate(oldRootPos, newRootPos);
			}

			if (scaleToFit)
				graphPanel.scaleToFit();
			graphPanel.repaint();
		}
		
		class PostponedRedraw implements Runnable {
			boolean stale=true;
			void redraw () {
				stale=true;
				SwingUtilities.invokeLater(this);
			}
			public void run () {
				if (stale) {
					drawGraph (scaleToFitCheckBox.isSelected());
					stale = false;
				}
			}
		}

		public void instantiate (InputPanel.Settings settings) { 
			//test permissions
			try {
				if (System.getSecurityManager()!=null)
					System.getSecurityManager().checkExec("dot");
			}
			catch (SecurityException exc) {
				status.setText("Unable to instantiate graph, cannot run Graphviz 'dot' on local machine from within Java sandbox.");
				return;
			}
			//parse defns + expr
			env = new Env ();
			SExp [] defnSexps;
			SExp exprSexp;
			try {
				defnSexps = SExp.parseAll (settings.defns);
			} catch (SExp.ParseError exc) {
				setStatus(String.format("parse error: %s", exc.msg));
				inputPanel.setDefnsCaretPosition(exc.pos);
				//defns.requestFocusInWindow();
				return;
			} catch (IOException exc) {
				setStatus("unexpected IOException parsing sexp");
				return;
			}

			try {
				exprSexp = SExp.parseOne (settings.expr);
			}
			catch (SExp.ParseError exc) {
				setStatus(String.format("parse error: %s", exc.msg));
				inputPanel.setExprCaretPosition(exc.pos);
				return;
			} catch (IOException exc) {
				setStatus("unexpected IOException parsing sexp");
				return;
			}

			Strategy funcStrategy = settings.funcStrategy;
			Strategy argStrategy = settings.argStrategy;

			try {
				// instantiate defns
				for (SExp sexp : defnSexps) {
					if (!Instantiate.isDefinition(sexp)) {
						setStatus (String.format("syntax error: unexpected expression, expected definition"));
						inputPanel.setDefnsCaretPosition(sexp.pos);
						return;						
					}
					Instantiate.arityCheck(sexp, 2);
					Addr hole = new Addr (null);
					String name = sexp.get(1).symbol();
					SExp defn = sexp.get(2);
					env.bind(name, hole);
					Addr addr = Instantiate.instantiate(builtins, funcStrategy, argStrategy, 0, Context.emptyContext, env, defn);
					hole.link(addr);
				}
			}
			catch (Instantiate.SyntaxError exc) {
				setStatus (String.format("syntax error: %s", exc.msg));
				inputPanel.setDefnsCaretPosition(exc.pos);
				return;
			}

			try {
				// instantiate expr
				root = Instantiate.instantiate(builtins, funcStrategy, argStrategy, 0, Context.emptyContext, env, exprSexp);
			}
			catch (Instantiate.SyntaxError exc) {
				setStatus (String.format("syntax error: %s", exc.msg));
				inputPanel.setExprCaretPosition(exc.pos);
				return;
			}
			catch (Heap.BlackHole exc) {
				setStatus ("black hole in graph");
				root = null;
				stack.clear();
				setState(State.PAUSED);
				return;
			}

			Panel.this.settings = settings;

			// reset reductions tab
			stack.clear();
			stack.push(root, Eval.Form.HYPER_STRICT, 0);
			try {
				Eval.findNextRedex(stack);
			}
			catch (Heap.BlackHole exc) {
				setStatus ("black hole in graph");
				root = null;
				stack.clear();
				setState(State.PAUSED);
				return;
			}
			
			currentSnapshot = 0;
			betaCount = deltaCount = substCount = 0;

			snapshots.clear();

			try {
				snapshots.add (takeSnapshot());
				lastSnapshotStep = 0;
			}
			catch (IOException exc) {
				setStatus ("Error rendering graph: "+exc.getMessage());
				return;
			}

			setCurrentSnapshot (0);
			setState(State.PAUSED);
			drawGraph (true);
			graphPanel.scaleToFit();

			// switch to reductions tab
			tabbedPane.setSelectedComponent(graphTab);
		}
		
		JComponent aboutPanel = new JEditorPane () {
			StringBuffer b = new StringBuffer ();
			void showProperty (String property) {
				String value;
				try {
					value = System.getProperty(property);
				} catch (SecurityException exc) {
					value = "permission denied";
				}
				b.append(String.format("<tr><td>%s<td>%s</tr>", property, value));
			}
			{
				JEditorPane pane = this;
				pane.setEditable(false);
				pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
				pane.setContentType("text/html");
				b.append("<h1>Lambda Animator</h1>");
				b.append("by Mike Thyer<p>7 July 2007");
				b.append("<p>Please send any comments, questions, suggestions or bugs to: <tt> mike@thyer.name</tt>");
				b.append("<p><hr><p><table>");
				showProperty ("java.version");
				showProperty ("java.vendor");
				showProperty ("java.vm.name");
				showProperty ("java.vm.version");
				showProperty ("java.vm.vendor");
				//showProperty ("java.vm.info");
				//showProperty ("java.runtime.version");
				//showProperty ("java.runtime.name");
				showProperty ("os.name");
				showProperty ("os.version");
				showProperty ("os.arch");
				
//				Enumeration propEnumerator = System.getProperties().keys();
//				ArrayList<String> properties = new ArrayList<String>();
//				b.append("</table>");
//				b.append("<p><hr><p><table>");
//				while (propEnumerator.hasMoreElements()) {
//					properties.add((String)(propEnumerator.nextElement()));
//				}
//				Collections.sort(properties);
//				for (String property : properties) {
//					showProperty (property);
//				}
//				b.append("</table>");
				
				pane.setText(b.toString());
				this.setCaretPosition(0);
			}			
		};
	}
}
