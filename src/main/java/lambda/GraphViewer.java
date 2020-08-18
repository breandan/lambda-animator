package lambda;

import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import javax.swing.JPanel;


public class GraphViewer extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener {
	interface Callback {
		void graphViewMoved ();
		void graphViewScaled ();
	}
	Callback callback;
	GraphViewer graphPanel = this;
	att.grappa.Graph g;
	att.grappa.GrappaPanel gp;
	double offsetX, offsetY, zoom=1.0;
	boolean firstPaint = true;
	GraphViewer (Callback callback, att.grappa.Graph g) {
		this.callback = callback;
		this.g = g;
		
		gp = new att.grappa.GrappaPanel(g);
		add(gp);
		repaint();
		
		graphPanel.addMouseListener(this);
		graphPanel.addMouseMotionListener(this);
		graphPanel.addMouseWheelListener(this);		
	}
	public void paint(Graphics g1) {
		Graphics2D g2d = (Graphics2D) g1;
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		AffineTransform transform = new AffineTransform();
		transform.translate((int)(-offsetX), (int)(-offsetY));
		transform.scale(zoom, zoom);
		Rectangle box = this.getVisibleRect();
		Point2D corner1, corner2;
		try {
			corner1 = transform.inverseTransform (new Point2D.Double(box.getMinX(), box.getMinY()), null);
			corner2 = transform.inverseTransform (new Point2D.Double(box.getMaxX(), box.getMaxY()), null);
		} catch (Throwable exc) {
			throw new Error (exc);
		}
		Shape clipper = new Rectangle2D.Double (corner1.getX(), corner1.getY(), corner2.getX()-corner1.getX(), corner2.getY()-corner1.getY());
		g2d.transform(transform);
		gp.paintGraph(g2d, clipper);
	}
	public void translate (double x, double y) {
		offsetX -= x;
		offsetY -= y;
	}
	public void translate (att.grappa.GrappaPoint oldPos, att.grappa.GrappaPoint newPos) {
		double x = oldPos.x - newPos.x;
		double y = oldPos.y - newPos.y;
		translate (x*zoom, y*zoom);
	}
	public void zoom (double scale) {
		zoom (scale, getX()+getWidth()/2, getY()+getHeight()/2);
	}
	public void zoom (double scale, double centerX, double centerY) {
		zoom *= scale;
		offsetX = (offsetX+centerX)*scale - centerX;
		offsetY = (offsetY+centerY)*scale - centerY;
	}
	public void scaleToFit () {
		//Rectangle2D bbox = g.getBoundingBox();
		Rectangle2D bbox = (Rectangle2D)g.getAttributeValue("bb");
		double zoomX = (double) getWidth() / bbox.getWidth();
		double zoomY = (double) getHeight() / bbox.getHeight();
		zoom = zoomX < zoomY ? zoomX : zoomY;
		offsetX = bbox.getX() * zoom + (bbox.getWidth()*zoom - getWidth()) / 2;
		offsetY = bbox.getY() * zoom;
	}

	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mouseMoved(MouseEvent e) {}

	int initX, initY;
	int prevX, prevY;

	public void mousePressed(MouseEvent mev) {
		initX = mev.getX();
		initY = mev.getY();
		prevX = mev.getX();
		prevY = mev.getY();
		if ((mev.getModifiers() & InputEvent.BUTTON1_MASK) != 0) {
			setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
			mev.consume();
			//graphTab.requestFocusInWindow();
		}
	}
	public void mouseDragged(MouseEvent mev) {
		int x = mev.getX();
		int y = mev.getY();

		if ((mev.getModifiers() & InputEvent.BUTTON1_MASK) != 0) {
			graphPanel.translate(x-prevX, y-prevY);
			graphPanel.repaint();
			mev.consume();
			//scaleToFitCheckBox.setSelected(false);
			callback.graphViewMoved();
		}
		if ((mev.getModifiers() & InputEvent.BUTTON3_MASK) != 0) {
			//double zoomFactor = Math.pow(1.1, Math.signum(mev.getY()-prevY));
			double zoomFactor = mev.getY()-prevY > 0 ? 1.1 : 1/1.1;
			graphPanel.zoom(zoomFactor, initX, initY);
			graphPanel.repaint();
			mev.consume();
			//scaleToFitCheckBox.setSelected(false);
			callback.graphViewScaled();
		}
		prevX = x;
		prevY = y;
	}
	public void mouseReleased(MouseEvent mev) {
		if ((mev.getModifiers() & InputEvent.BUTTON1_MASK) != 0) {
			setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			mev.consume();
		}
	}
	public void mouseWheelMoved(MouseWheelEvent e) {
		double zoomFactor = Math.pow(1.1, Math.signum(e.getWheelRotation()));
		graphPanel.zoom(zoomFactor, e.getX(), e.getY());
		graphPanel.repaint();
		e.consume();
		//scaleToFitCheckBox.setSelected(false);
		callback.graphViewScaled();
	}
}

