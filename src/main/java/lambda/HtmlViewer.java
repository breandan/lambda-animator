package lambda;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URL;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JEditorPane;
import javax.swing.KeyStroke;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.Caret;
import javax.swing.text.Document;

public class HtmlViewer extends JEditorPane implements HyperlinkListener {
	interface Callback {
		void reportStatus (String text);
		boolean hyperlinkClickFilter (URL url);
	}
	final Callback callback;
	String url;
	public HtmlViewer (Callback callback) {
		this.callback = callback;
		getActionMap().put("reload", reloadAction);
		getInputMap().put(KeyStroke.getKeyStroke("control R"), "reload");
		getCaret().addChangeListener(caretListener);
		setEditable(false);
		addHyperlinkListener(this);
		putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
	}
	
	void reload () {
//		URL currentUrl = getPage();
//		if (currentUrl != null)
//			url = currentUrl.toString();
		try {
			// this is the Sun work-around to bug id 4492274
			getDocument().putProperty(Document.StreamDescriptionProperty, null);
			super.setPage(url);
			callback.reportStatus("Page reloaded");
		} catch (Exception exc) {
			callback.reportStatus("Error reloading page: "+exc.getMessage());
		}
	}
	Action reloadAction = new AbstractAction () {
		public void actionPerformed(ActionEvent arg0) {
			reload();
		}
	};
	
	boolean openPage (String url) {
		this.url = url;
		try {
			super.setPage(url);
		}
		catch (Exception exc) {
			setText("Error opening page: "+exc.getMessage()+"<br>Press Control-R to retry");
			getCaret().setVisible(false);
			callback.reportStatus ("Error opening page: "+exc.getMessage());
			return false;
		}
		return true;
	}
	
	// cursor key induced scrolling behaviour appears sluggish unless you can see the caret
	// but don't want to see it unless user has moved it.
	// todo: should really work out how to implement browser-like scrolling without a caret
	ChangeListener caretListener = new ChangeListener () {
		public void stateChanged(ChangeEvent e) {
			((Caret)(e.getSource())).setVisible(true);
		}
	};
	
	public void hyperlinkUpdate(HyperlinkEvent e) {
		if (e.getEventType() == HyperlinkEvent.EventType.ENTERED)
			callback.reportStatus(e.getURL().toString());
		else if (e.getEventType() == HyperlinkEvent.EventType.EXITED)
			callback.reportStatus(null);
		else if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
			activated(e);
	}
	void activated(HyperlinkEvent e) {
		URL newUrl = e.getURL();
		if (!callback.hyperlinkClickFilter(newUrl))
			return;
		String ref = e.getURL().getRef();
		if (ref != null && ref.length()!=0) {
			scrollToReference(e.getURL().getRef());					
		}
		//todo: follow hyperlink to new pages.
	}
	
}
