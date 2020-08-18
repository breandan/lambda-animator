package lambda;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import lambda.Eval.Form;
import lambda.Eval.Strategy;

public class InputPanel extends JPanel {
	interface Callback {
		void instantiate (Settings settings);
	}
	
	static class Settings {
		final String defns, expr;
		final Eval.Strategy funcStrategy, argStrategy;
		final Eval.Form reduceToValue;
		final boolean copyArgs;
		public Settings(final String defns, final String expr, final Strategy funcStrategy, final Strategy argStrategy, final Form reduceToValue, final boolean copyArgs) {
			super();
			this.defns = defns;
			this.expr = expr;
			this.funcStrategy = funcStrategy;
			this.argStrategy = argStrategy;
			this.reduceToValue = reduceToValue;
			this.copyArgs = copyArgs;
		}
	}
	
	final Callback callback;
	
	int gridy=0;
	
	GridBagConstraints c = new GridBagConstraints();
	JTextArea defns = new JTextArea ();
	JTextField expr = new JTextField ();
	JComboBox argStrategyCombo = new JComboBox ();
	JComboBox funcStrategyCombo = new JComboBox ();
	JComboBox valueFormCombo = new JComboBox ();
	JComboBox copyArgsCombo = new JComboBox ();
	JPanel argPanel = new JPanel ();

	InputPanel (Callback callback) {
		this.callback = callback;
		setLayout(new GridBagLayout());

		argStrategyCombo.addItem("call by name");
		argStrategyCombo.addItem("call by value (hs)");
		argStrategyCombo.addItem("call by value (whnf)");
		argStrategyCombo.addItem("call by value (wnf)");
		argStrategyCombo.addItem("call by value (hnf)");
		argStrategyCombo.addItem("call by value (nf)");
		argStrategyCombo.addItem("call by need");

		copyArgsCombo.addItem("copy arguments");
		copyArgsCombo.addItem("share arguments");

		argPanel.add(argStrategyCombo);
		argPanel.add(new JLabel("   "));
		argPanel.add(copyArgsCombo);
		argPanel.setLayout(new GridBagLayout());

		funcStrategyCombo.addItem("substitute by name");
		funcStrategyCombo.addItem("substitute by value (hnf)");
		funcStrategyCombo.addItem("substitute by value (nf)");
		funcStrategyCombo.addItem("substitute by need");

		valueFormCombo.addItem("hyper-strict");
		valueFormCombo.addItem("weak head normal form");
		valueFormCombo.addItem("weak normal form");
		valueFormCombo.addItem("head normal form");
		valueFormCombo.addItem("normal form");

		beginInputFields();
		addInputField ("Definitions", new JScrollPane (defns), true, true);
		addInputField ("Expression", expr, true, false);
		addInputField ("Function Strategy", funcStrategyCombo, false, false);
		addInputField ("Argument Strategy", argPanel, false, false);
		addInputField ("Reduce To", valueFormCombo, false, false);
		addInputField ("", new JButton(instantiateAction), false, false);
		endInputFields();

		Font textFont = new Font ("Monospaced", Font.PLAIN, 12);
		defns.setFont(textFont);
		expr.setFont(textFont);

		SettingsChangeListener settingsChangeListener = new SettingsChangeListener ();
		argStrategyCombo.addActionListener(settingsChangeListener);
		funcStrategyCombo.addActionListener(settingsChangeListener);
		settingsChangeListener.actionPerformed(null);
	}
	void beginInputFields () {
		c.insets = new Insets(5,5,5,5);
	}
	void addInputField(String name, JComponent component, boolean stretchWidth, boolean stretchHeight) {
		c.anchor = GridBagConstraints.NORTHWEST;
		c.gridx = 0;
		c.gridy = gridy ++;
		c.weightx = 0.0;
		c.weighty = 0.0;
		c.fill = GridBagConstraints.NONE;
		add(new JLabel(name), c);
		c.gridx = 1;
		if (stretchWidth) {
			c.weightx = 1.0;
			c.fill = GridBagConstraints.HORIZONTAL;
		}
		if (stretchHeight) {
			c.weighty = 1.0;
			c.fill = GridBagConstraints.VERTICAL;
		}
		if (stretchWidth && stretchHeight)
			c.fill = GridBagConstraints.BOTH;
		add(component, c);				
	}
	void endInputFields () {
		c.insets = new Insets(0,0,0,0);
		c.gridx = 0;
		c.gridy = gridy;
		c.fill = GridBagConstraints.BOTH;
		c.gridwidth = 3;
		c.weightx = 1.0;
		c.weighty = 0.0;
		add(new JPanel(), c);
	}

	Eval.Strategy getArgStrategy () {
		switch (argStrategyCombo.getSelectedIndex()) {
		case 0: return new Eval.Strategy(Eval.SubStrategy.NAME);
		case 1: return new Eval.Strategy(Eval.Form.HYPER_STRICT);
		case 2: return new Eval.Strategy(Eval.Form.WHNF);
		case 3: return new Eval.Strategy(Eval.Form.WNF);
		case 4: return new Eval.Strategy(Eval.Form.HNF);
		case 5: return new Eval.Strategy(Eval.Form.NF);
		case 6: return new Eval.Strategy(Eval.SubStrategy.NEED);
		default: throw new Error();
		}
	}
	Eval.Strategy getFuncStrategy () {
		switch (funcStrategyCombo.getSelectedIndex()) {
		case 0: return new Eval.Strategy(Eval.SubStrategy.NAME);
		case 1: return new Eval.Strategy(Eval.Form.HNF);
		case 2: return new Eval.Strategy(Eval.Form.NF);
		case 3: return new Eval.Strategy(Eval.SubStrategy.NEED);
		default: throw new Error();
		}
	}

	Eval.Form getValueForm () {
		switch (valueFormCombo.getSelectedIndex()) {
		case 0: return Eval.Form.HYPER_STRICT;
		case 1: return Eval.Form.WHNF;
		case 2: return Eval.Form.WNF;
		case 3: return Eval.Form.HNF;
		case 4: return Eval.Form.NF;
		default: throw new Error ();
		}
	}
	boolean getCopyArgs () {
		switch (copyArgsCombo.getSelectedIndex()) {
		case 0: return true;
		case 1: return false;
		default: throw new Error ();
		}
	}

	Settings getSettings () {
		return new Settings (defns.getText(), expr.getText(), getFuncStrategy(), getArgStrategy(), getValueForm(), getCopyArgs());
	}
	void setSettings (Settings settings) {
		defns.setText(settings.defns);
		defns.setCaretPosition(0);
		expr.setText(settings.expr);
		expr.setCaretPosition(0);
		switch (settings.argStrategy.subStrategy) {
		case NAME:  argStrategyCombo.setSelectedIndex(0); break;
		case VALUE: 
			switch (settings.argStrategy.form) {
			case HYPER_STRICT: argStrategyCombo.setSelectedIndex(1); break;
			case WHNF: argStrategyCombo.setSelectedIndex(2); break;
			case WNF: argStrategyCombo.setSelectedIndex(3); break;
			case HNF: argStrategyCombo.setSelectedIndex(4); break;
			case NF: argStrategyCombo.setSelectedIndex(5); break;
			default: throw new Error ();
			}
			break;
		case NEED:  argStrategyCombo.setSelectedIndex(6); break;
		default: throw new Error ();
		}
		switch (settings.funcStrategy.subStrategy) {
		case NAME:  funcStrategyCombo.setSelectedIndex(0); break;
		case VALUE: 
			switch (settings.funcStrategy.form) {
			case HNF: funcStrategyCombo.setSelectedIndex(1); break;
			case NF: funcStrategyCombo.setSelectedIndex(2); break;
			default: throw new Error ();
			}
			break;
		case NEED:  funcStrategyCombo.setSelectedIndex(3); break;
		default: throw new Error ();
		}
		switch (settings.reduceToValue) {
		case HYPER_STRICT: valueFormCombo.setSelectedIndex(0); break;
		case WHNF: valueFormCombo.setSelectedIndex(1); break;
		case WNF:  valueFormCombo.setSelectedIndex(2); break;
		case HNF:  valueFormCombo.setSelectedIndex(3); break;
		case NF:   valueFormCombo.setSelectedIndex(4); break;
		default: throw new Error ();
		}
		if (settings.copyArgs)
			copyArgsCombo.setSelectedIndex(0);
		else
			copyArgsCombo.setSelectedIndex(1);
	}

	class SettingsChangeListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			switch (argStrategyCombo.getSelectedIndex()) {
			case 0: // call-by-name
			copyArgsCombo.setSelectedIndex(0);
			copyArgsCombo.setEnabled(false);
			break;
			case 1: // call-by-value
			case 2: // call-by-value
			case 3: // call-by-value
			case 4: // call-by-value
			case 5: // call-by-value
				copyArgsCombo.setEnabled(true);
				break;
			case 6: // call-by-need
				copyArgsCombo.setSelectedIndex(1);
				copyArgsCombo.setEnabled(false);
				break;
			default: throw new Error ();
			}
		}
	}
	
	Action instantiateAction = new AbstractAction ("Instantiate") {
		public void actionPerformed(ActionEvent e) {
			callback.instantiate(getSettings());
		}
	};
	
	void setDefnsCaretPosition (int pos) {
		defns.setCaretPosition(pos);
		defns.requestFocusInWindow();
	}
	void setExprCaretPosition (int pos) {
		expr.setCaretPosition(pos);
		expr.requestFocusInWindow();
	}

}

