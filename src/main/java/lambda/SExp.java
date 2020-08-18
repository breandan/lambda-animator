package lambda;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;

public abstract class SExp {
	final int pos;
	protected SExp () {
		this.pos = 0;
	}
	protected SExp (int pos) {
		this.pos = pos;
	}

	abstract void append (StringBuffer  buf);
	
	public java.lang.String toString () {
		StringBuffer buf = new StringBuffer();
		append (buf);
		return buf.toString();
	}

	static class Symbol extends SExp {
		java.lang.String value;
		public Symbol(java.lang.String value) {
			this.value = value;
		}
		public Symbol(int pos, java.lang.String value) {
			super(pos);
			this.value = value;
		}
		public void append (StringBuffer buf) { buf.append(value); }
		public boolean equals (Object obj) {
			return obj instanceof Symbol && value.equals(((Symbol)obj).value);
		}
	}
//	static class String extends SExp {
//		java.lang.String value;
//		public String(java.lang.String value) {
//			this.value = value;
//		}
//		public void append (StringBuffer buf) { buf.append(value); }
//	}
	static class Number extends SExp {
		int value;
		public Number(int value) {
			this.value = value;
		}
		public Number(int pos, int value) {
			super(pos);
			this.value = value;
		}
		public void append (StringBuffer buf) { buf.append(value); }
	}
	static class Value extends SExp {
		Object value;
		public Value (int pos, Object value) {
			super(pos);
			this.value = value;
		}
		public Value (Object value) {
			this.value = value;
		}
		public void append (StringBuffer buf) {
			buf.append(value.toString()); 
		}
	}
	static class List extends SExp {
		SExp [] children;
		public List (SExp...sexps) {
			this.children = sexps;
		}
		public List (int pos, SExp...sexps) {
			super(pos);
			this.children = sexps;
		}
		public void append (StringBuffer buf) {
			buf.append("(");
			for (int i=0; i!=children.length; i++) {
				if (i!=0) 
					buf.append(" ");
				buf.append(children[i].toString());
			}
			buf.append(")");
		}
	}

	boolean isList () {
		return this instanceof List; 
	}
	boolean isNil () {
		return isList() && length()==0;
	}
	boolean isAtom () {
		return ( ! isList() ) || isNil();
	}
	boolean isNumber () {
		return this instanceof Number;
	}
	boolean isValue () {
		return this instanceof Value;
	}
	boolean isSymbol () {
		return this instanceof Symbol;
	}

	List asList () {
		return (List) this;
	}
	Symbol asSymbol () {
		return (Symbol) this;
	}
	Number asNumber () {
		return (Number) this;
	}
	Value asValue () {
		return (Value) this;
	}

	int length () {
		return asList().children.length;
	}
	SExp get (int i) {
		return asList().children[i];
	}

	java.lang.String symbol () {
		return asSymbol().value;
	}

	int number () {
		return asNumber().value;
	}

	static Symbol symbol (java.lang.String value) {
		return new Symbol (value);
	}

	static Number number (int value) {
		return new Number (value);
	}

	static String string (java.lang.String value) {
		return new String (value);
	}

	static final List nil = new List (new SExp [] {});

	static List nil () {
		return nil;
	}

	
	static SExp parseOne (java.lang.String str) throws IOException {
		Parser parser = new Parser (new StringReader(str));
		SExp result = parser.parseOne();
		parser.skipWhitespace();
		if (!parser.isEOF())
			throw new ParseError (parser.pos, "unexpected characters at end of input");
		return result;
	}

	static SExp [] parseAll (java.lang.String str) throws IOException {
		Parser parser = new Parser (new StringReader(str));
		ArrayList<SExp> results = new ArrayList<SExp>();
		parser.skipWhitespace();
		while (!parser.isEOF()) {
			results.add(parser.parseOne());
			parser.skipWhitespace();
		}
		SExp [] array = new SExp [results.size()];
		return results.toArray(array);
	}

	static class Parser {
		final static char [] symbolChars;
		static {
			symbolChars = "!$%&*+-./:<=>?@^_~".toCharArray();
			Arrays.sort(symbolChars);
		}
		
		final Reader reader;
		
		int pos;
		int nextChar;
		StringBuffer tokenBuffer = new StringBuffer();

		Parser (Reader reader) throws IOException {
			this.reader = reader;
			readChar();
			pos=0;
		}
		char readChar() throws IOException {
			int result = nextChar;
			try {
				nextChar = reader.read();
			} catch (IOException exc) {
				throw new Error (exc);
			}
			pos++;
			if (result==-1)
				throw new InternalError ("attempt to read past end of input");
			return (char) result;
		}
		char peekChar () {
			if (nextChar==-1)
				throw new InternalError ("attempt to peek past end of input");
			return (char)nextChar;			
		}
		void skipChar (char ch) throws IOException {
			if (readChar()!=ch)
				throw new InternalError ("skipChar failed");
		}
		boolean isEOF() {
			return nextChar==-1;
		}

		static boolean isSymbolChar (char ch)
		{
			if (Character.isLetterOrDigit(ch))
				return true;
			if (Arrays.binarySearch(symbolChars, ch) >= 0)
				return true;
			return false;
		}
		static boolean isWhitespace (char ch) {
			return Character.isWhitespace(ch);		
		}
		void skipWhitespace () throws IOException {
			do {
				while (!isEOF() && isWhitespace(peekChar()))
					readChar();
				if (!isEOF() && peekChar()==';')
					while (!isEOF() && peekChar()!='\n')
						readChar();
				else
					break;
			}
			while (true);
		}
		SExp parseSymbol () throws IOException {
			int startPos = pos;
			tokenBuffer.setLength(0);
			do {
				tokenBuffer.append(readChar());
			}
			while (!isEOF() && isSymbolChar(peekChar()));
			java.lang.String atom = tokenBuffer.toString();
			try {
				int num = Integer.parseInt(atom);
				return new Number (startPos, num);
			}
			catch (NumberFormatException exc) {
				return new Symbol (startPos, atom);
			}
		}
		SExp parseHash () throws IOException {
			skipChar('#');
			int startPos=pos;
			tokenBuffer.setLength(0);
			while (!isEOF() && isSymbolChar(peekChar())) {
				tokenBuffer.append(readChar());
			}
			java.lang.String token = tokenBuffer.toString();
			if (token.equals("t"))
				return new Value(Boolean.TRUE);
			if (token.equals("f"))
				return new Value(Boolean.TRUE);
			throw new ParseError (startPos, "unexpected symbol after #, expected 't' or 'f', found: '"+token+"'");
		}
		SExp parseString () throws IOException {
			int startPos = pos;
			skipChar('"');
			tokenBuffer.setLength(0);
			while (!isEOF() && peekChar()!='"') {
				tokenBuffer.append(readChar());
			}
			if (isEOF())
				throw new ParseError (startPos, "unexpected end of input, expected closing double quote");
			skipChar('"');
			return new Value (tokenBuffer.toString());
		}
		SExp parseList () throws IOException {
			ArrayList<SExp> children = new ArrayList<SExp>();
			int startPos = pos;
			skipChar('(');
			skipWhitespace();
			while (!isEOF() && peekChar()!=')') {
				children.add(parseOne());
				skipWhitespace();
			}
			if (isEOF())
				throw new ParseError (startPos, "unexpected end of input, open parentheses not matched");
			skipChar(')');
			SExp [] array = new SExp [children.size()];
			return new List (startPos, children.toArray(array));
		}
		SExp parseQuote () throws IOException {
			int startPos = pos;
			skipChar('\'');
			SExp sexp = parseOne();
			return new List (startPos, new Symbol (startPos, "quote"), sexp);
		}
		public SExp parseOne () throws IOException {
			skipWhitespace();
			if (isEOF())
				throw new ParseError (pos, "unexpected end of input, expected s-expression");
			char ch=peekChar();
			if (isSymbolChar(ch))
				return parseSymbol();
			switch (ch) {
			case '#':
				return parseHash();
			case '"':
				return parseString();
			case '\'':
				return parseQuote();
			case '(':
				return parseList();
			case ')':
				throw new ParseError (pos, "unexpected close parentheses");
			default : 
				throw new ParseError (pos, "invalid char: "+ch);
			}
		}
	}

	static class ParseError extends Error {
		final int pos;
		final java.lang.String msg;
		public ParseError(int pos, java.lang.String msg) {
			this.pos = pos;
			this.msg = msg;
		}
		public java.lang.String getMessage () {
			return msg;
		}
	}
}
