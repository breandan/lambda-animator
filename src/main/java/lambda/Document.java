package lambda;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import lambda.Eval.Strategy;
import lambda.Eval.SubStrategy;
import lambda.Gui.Panel.Snapshot;
import lambda.InputPanel.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class Document {
	final InputPanel.Settings settings;
	final Snapshot [] snapshots;
	
	public Document(Settings settings, Snapshot[] snapshots) {
		this.settings = settings;
		this.snapshots = snapshots;
	}
	public Document(Settings settings, List<Snapshot> snapshots) {
		this.settings = settings;
		this.snapshots = new Snapshot [snapshots.size()];
		snapshots.toArray(this.snapshots);
	}
	
	static Document readFile (InputStream inputStream) throws IOException, JSONException {
		String fileContents = readAll (
			                  new BufferedReader (
		                      new InputStreamReader (
		                      new GZIPInputStream (
		                      inputStream ) ) ) );
		
			JSONObject docJ = new JSONObject (new JSONTokener(fileContents));
			String expr = docJ.getString("expression");
			String defns = docJ.getString("definitions");
			ArrayList<Snapshot> snapshots = new ArrayList<Snapshot>();
			JSONArray snapshotsJ = docJ.getJSONArray("snapshots");
			for (int i=0; i != snapshotsJ.length(); i++) {
				JSONObject sj = snapshotsJ.getJSONObject(i);
				int step = sj.getInt("step");
				//ArrayList<Integer> stack = new ArrayList<Integer>();
				//JSONArray stepsJ = sj.getJSONArray("stack");
				//for (int j=0; j != stepsJ.length(); j++) {
				//	stack.add(stepsJ.getInt(j));
				//}
				String graph = sj.getString("graph");
				snapshots.add( new Snapshot ( step, /*stack,*/ graph, 
						sj.optInt("beta"), sj.optInt("delta"), sj.optInt("subst")));
			}
			JSONObject optJ = docJ.getJSONObject("evalOptions");
			Eval.Strategy func = Eval.Strategy.fromString(optJ.getString("funcStrategy"), Strategy.from(SubStrategy.NAME));
			Eval.Strategy arg = Eval.Strategy.fromString(optJ.getString("argStrategy"), Strategy.from(SubStrategy.NEED));
			Eval.Form value = Eval.Form.valueOf(optJ.getString("valueForm"));
			boolean copyArgs = optJ.getBoolean("copyArgs");
			InputPanel.Settings settings = new InputPanel.Settings (defns, expr, func, arg, value, copyArgs);

			return new Document (settings, snapshots);
	}
	
	
	static String readAll (Reader reader) throws IOException {
		Writer writer = new StringWriter ();
		int ch;
		while ((ch = reader.read()) != -1) {
			writer.write(ch);
		}
		return writer.toString();
	}
	
	static void writeFile (Document document, OutputStream outputStream) 
		throws IOException, JSONException
	{
		JSONArray snapshotsJSON = new JSONArray ();
		for (Snapshot s : document.snapshots) {
			JSONObject o = new JSONObject ();
			o.put("step", s.step);
			//o.put("stack", new JSONArray (s.stack));
			o.put("graph", s.graphStr);
			o.put("beta", s.beta);
			o.put("delta", s.delta);
			o.put("subst", s.subst);
			snapshotsJSON.put(o);
		}
		
		InputPanel.Settings settings = document.settings;
		
		JSONObject optJ = new JSONObject ();
		optJ.put("argStrategy", settings.argStrategy.toString());
		optJ.put("funcStrategy", settings.funcStrategy.toString());
		optJ.put("valueForm", settings.reduceToValue.toString());
		optJ.put("copyArgs", settings.copyArgs);
		
		JSONObject docJSON = new JSONObject ();
		docJSON.put("definitions", settings.defns);
		docJSON.put("expression", settings.expr);
		docJSON.put("evalOptions", optJ);
		docJSON.put("snapshots", snapshotsJSON);

		Writer writer = new BufferedWriter (
				        new OutputStreamWriter (
				        new GZIPOutputStream (
				        outputStream ) ) );
		docJSON.write(writer);
		writer.close();
	}
}
