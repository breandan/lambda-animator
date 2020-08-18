package lambda;

import javax.swing.SwingUtilities;

public class Applet extends javax.swing.JApplet {
	public void init () {
		final String resources = "http://thyer.name/lambda-animator/resources";
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
	            public void run() {
	        		add (new Gui.Panel(resources));
	            }
	        });	
		}
		catch (Exception e) {
			throw new Error (e);
		}
	}
}
