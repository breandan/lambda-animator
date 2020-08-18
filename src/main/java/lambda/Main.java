package lambda;


import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringBufferInputStream;
import java.util.ArrayList;

import lambda.Heap.Addr;
import lambda.Heap.Context;
import lambda.Primitives.BuiltInFunctions;


public class Main {

	static class Option {
		enum Tag {INPUT_FILE, INPUT_STRING, LOG_FILE, 
			      REDUCE_TO_FORM, FUNC_STRATEGY, ARG_STRATEGY};
		Tag tag;
		String arg;
		Option (Tag tag_, String arg_) {
			tag=tag_;
			arg=arg_;
		}
		Option (Tag tag_) {
			tag=tag_;
		}
	}
	
	static ArrayList<Option> options = new ArrayList<Option>();
	
	public static void main(String[] args) {
		Env env = new Env ();
		BuiltInFunctions builtins = Primitives.makeBuiltIns();
		try {
		for (int i=0; i!=args.length; i++) {
			String arg = args[i];
			if (arg.charAt(0)=='-') {
				if (arg.equals("-l")) {
					i++;
					if (i==args.length)
						commandLineError ("filename expected after -l");
//					logFilename = args[i];
//					options.add(new Option(Option.Tag.LOG_FILE, args[i]));
				}
				else if (arg.equals("-c")) {
					i++;
					if (i==args.length)
						commandLineError ("expression expected after -c");
					options.add(new Option(Option.Tag.INPUT_STRING, args[i]));
				}
				else if (arg.equals("-form")) {
					i++;
					options.add(new Option (Option.Tag.REDUCE_TO_FORM, args[i]));
				}
				else if (arg.equals("-func")) {
					i++;
					options.add(new Option (Option.Tag.FUNC_STRATEGY, args[i]));
				}
				else if (arg.equals("-arg")) {
					i++;
					options.add(new Option (Option.Tag.ARG_STRATEGY, args[i]));
				}
				else
					commandLineError ("unknown option: " + arg);
			}
			else {
				options.add(new Option(Option.Tag.INPUT_FILE, args[i]));
			}
		}
		}
		catch (ArrayIndexOutOfBoundsException exc) {
			commandLineError ("unexpected end of command line, missing argument");
		}
		
		try {
			//log.logFile = new BufferedOutputStream (new FileOutputStream (logFilename));
			
			InputStream inputStream;
			String filename;
			Eval.Strategy argStrategy = new Eval.Strategy (Eval.SubStrategy.NEED);
			Eval.Strategy funcStrategy = new Eval.Strategy (Eval.SubStrategy.NAME);
			Eval.Form valueForm = Eval.Form.WHNF;
			boolean copyArgs = false;
			//Eval.Options opt;
			for (int i=0; i!=options.size(); i++) {
				filename = options.get(i).arg; 
				switch (options.get(i).tag) {
				case INPUT_FILE:
					if (filename.equals("-")) {
						filename = "<stdin>";
						inputStream = new BufferedInputStream (System.in);
					}
					else {
						inputStream = new BufferedInputStream (new FileInputStream (filename));
					}
					//opt = new Eval.Options (log, funcStrategy, argStrategy, valueForm, copyArgs);
					try {
						processFile (builtins, env, filename, funcStrategy, argStrategy, copyArgs, inputStream);
					}
					catch (SExp.ParseError exc) {
						System.err.println(exc.msg);
						System.exit(1);
					}
					catch (Instantiate.SyntaxError exc) {
						System.err.println(exc.msg);
						System.exit(1);
					}
					catch (IOException exc) {
						System.err.println(exc.getMessage());
						System.exit(1);
					}
					break;
				case INPUT_STRING:
					inputStream = new StringBufferInputStream (options.get(i).arg);
					//opt = new Eval.Options (null, funcStrategy, argStrategy, valueForm, copyArgs);
					try {
						processFile (builtins, env, filename, funcStrategy, argStrategy, copyArgs, inputStream);
					}
					catch (SExp.ParseError exc) {
						System.err.println(exc.msg);
						System.exit(1);
					}
					catch (Instantiate.SyntaxError exc) {
						System.err.println(exc.msg);
						System.exit(1);
					}
					catch (IOException exc) {
						System.err.println(exc.getMessage());
						System.exit(1);
					}
					break;
				case FUNC_STRATEGY:
					funcStrategy = readStrategy(options.get(i).arg);
					break;
				case ARG_STRATEGY:
					argStrategy = readStrategy(options.get(i).arg);
					break;
				case REDUCE_TO_FORM:
					valueForm = readForm(options.get(i).arg);
					break;
				}
			}
		}
		catch (FileNotFoundException exc) {
			Error ("file not found: "+exc.getMessage());
		}		
	}

	static Eval.Strategy readStrategy (String s) {
		if (s.equals("name"))
			return new Eval.Strategy(Eval.SubStrategy.NAME);
		if (s.equals("hs"))
			return new Eval.Strategy(Eval.Form.HYPER_STRICT);
		if (s.equals("whnf"))
			return new Eval.Strategy(Eval.Form.WHNF);
		if (s.equals("wnf"))
			return new Eval.Strategy(Eval.Form.WNF);
		if (s.equals("hnf"))
			return new Eval.Strategy(Eval.Form.HNF);
		if (s.equals("nf"))
			return new Eval.Strategy(Eval.Form.NF);
		if (s.equals("need"))
			return new Eval.Strategy(Eval.SubStrategy.NEED);
		throw new Error ("unknown strategy: "+s);
	}
	
	static Eval.Form readForm (String s) {
		if (s.equals("hs"))
			return Eval.Form.HYPER_STRICT;
		if (s.equals("whnf"))
			return Eval.Form.WHNF;
		if (s.equals("wnf"))
			return Eval.Form.WNF;
		if (s.equals("hnf"))
			return Eval.Form.HNF;
		if (s.equals("nf"))
			return Eval.Form.NF;
		throw new Error ("unknown form: "+s);
	}
	
	static void commandLineError (String error) {
		System.err.println(error);
		System.exit(1);
	}
	
	static void Error (String error) {
		System.err.println(error);
		System.exit(1);
	}
	
	public static void processFile ( BuiltInFunctions builtins, Env env, String filename, 
			Eval.Strategy funcStrategy, Eval.Strategy argStrategy, boolean copyArgs,
			InputStream inputStream) throws SExp.ParseError, Instantiate.SyntaxError, IOException
	{
		SExp.Parser parser = new SExp.Parser (new InputStreamReader(inputStream));
		SExp sexp;
		parser.skipWhitespace();
		while(!parser.isEOF()) {
			sexp = parser.parseOne();
			if (sexp != null) {
				if (sexp.isList() && sexp.get(0).isSymbol() && sexp.get(0).symbol().equals("define")) {
					Addr hole = new Addr (null);
					String name = sexp.get(1).symbol();
					SExp defn = sexp.get(2);
					env.bind(name, hole);
					Addr addr = Instantiate.instantiate(builtins, funcStrategy, argStrategy, 0, Context.emptyContext, env, defn);
					hole.link(addr);
				}
				else {
					Addr addr = Instantiate.instantiate(builtins, funcStrategy, argStrategy, 0, Context.emptyContext, env, sexp);
					Eval.eval(addr, copyArgs);
					//log.logFile.flush();
				}
			}
			parser.skipWhitespace();			
		}
	}
}
