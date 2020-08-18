package lambda;

import java.util.Arrays;
import java.util.TreeMap;

import lambda.Heap.Addr;
import lambda.Heap.Apply;
import lambda.Heap.Context;
import lambda.Heap.Delta;
import lambda.Heap.Node;
import lambda.Heap.Pair;
import lambda.Heap.Primitive;
import lambda.Heap.Value;


public class Primitives {

	enum Strict {NON_STRICT, STRICT, HYPER_STRICT};

	static abstract class BuiltIn {
		final int arity;
		BuiltIn (int arity) {
			this.arity = arity;
		}
		public abstract Addr instantiate (Context context, Addr [] args);
	}
	
	static class PrimitiveBuiltIn extends BuiltIn {
		Primitive prim;
		PrimitiveBuiltIn (Primitive prim) {
			super(prim.args.length);
			this.prim = prim;
		}
		public Addr instantiate (Context context, Addr [] args) {
			if (args.length!=arity)
				throw new InternalError("argument/arity mismatch");
			return new Addr (new Delta (context, prim, args));
		}
	}
	
	static class ConsBuiltIn extends BuiltIn {
		ConsBuiltIn () {
			super(2);
		}
		public Addr instantiate (Context context, Addr [] args) {
			assert(args.length==2);
			return new Addr (new Pair (context, args[0], args[1]));
		}		
	}
	
	static class PrimHead extends Primitive {
		PrimHead () {
			super ("head", Strict.STRICT);
		}
		public Addr reduce (Context context, Addr [] args) {
			return args[0].deref().asPair().head;
		}
		public boolean isReducible (Context context, Addr [] args) {
			return args[0].deref().isPair(); 
		}
	}
	
	static class PrimTail extends Primitive {
		PrimTail () {
			super ("tail", Strict.STRICT);
		}
		public boolean isReducible (Context context, Addr [] args) {
			return args[0].deref().isPair();
		}
		public Addr reduce (Context context, Addr [] args) {
			return args[0].deref().asPair().tail;
		}
	}
	
	static class PrimIf extends Primitive {
		PrimIf () {
			super ("if", Strict.STRICT,Strict.NON_STRICT,Strict.NON_STRICT);
		}
		public boolean isReducible (Context context, Addr [] args) {
			Node arg1 = args[0].deref();
			return arg1.isValue() && arg1.asValue().value instanceof Boolean;
		}
		public Addr reduce (Context context, Addr [] args) {
			return ((Boolean)(args[0].deref().asValue().value))
			       ? args[1]
			       : args[2];
		}
	}
	
	static boolean isWhnf (Addr addr) {
		return isWhnf(addr.deref());
	}
	static boolean isWhnf (Node node) {
		switch (node.tag) {
		case PAIR:
		case VALUE:
		case LAMBDA:
			return true;
		case APPLY:
		case VAR:
		case DELTA:
		case SUBST:
			return false;
		default:
			throw new Error();
		}
	}
	
	static class PrimIsPair extends Primitive {
		PrimIsPair () {
			super ("pair?", Strict.STRICT);
		}
		public boolean isReducible (Context context, Addr [] args) {
			return isWhnf (args[0]);
		}
		public Addr reduce (Context context, Addr [] args) {
			Node arg = args[0].deref();
			switch (arg.tag) {
			case PAIR:
				return new Addr (new Value(context, Boolean.TRUE));
			case VALUE:
			case LAMBDA:
				return new Addr (new Value(context, Boolean.FALSE));
			default:
				throw new Error();
			}
		}
	};
	
	static class PrimIsNull extends Primitive {
		PrimIsNull () {
			super ("null?", Strict.STRICT);
		}
		public boolean isReducible (Context context, Addr [] args) {
			return isWhnf (args[0]);
		}
		public Addr reduce (Context context, Addr [] args) {
			Node arg = args[0].deref();
			switch (arg.tag) {
			case VALUE:
				Value value = arg.asValue();
				if (value.value instanceof lambda.SExp && ((lambda.SExp)(value.value)).isNil())
					return new Addr (new Value(context, Boolean.TRUE));
				else
					return new Addr (new Value(context, Boolean.FALSE));
			case PAIR:
			case LAMBDA:
				return new Addr (new Value(context, Boolean.FALSE));
			default:
				throw new Error();
			}
		}
	};
	
	static class PrimIsNumber extends Primitive {
		PrimIsNumber () {
			super ("number?", Strict.STRICT);
		}
		public boolean isReducible (Context context, Addr [] args) {
			return isWhnf (args[0]);
		}
		public Addr reduce (Context context, Addr [] args) {
			Value arg = args[0].deref().asValue();
			boolean result = arg.isValue() && arg.value instanceof Integer;
			return new Addr (new Value(context, Boolean.valueOf(result)));
		}
	}
	
	static class PrimIsSymbol extends Primitive {
		PrimIsSymbol () {
			super ("symbol?", Strict.STRICT);
		}
		public boolean isReducible (Context context, Addr [] args) {
			return isWhnf (args[0]);
		}
		public Addr reduce (Context context, Addr [] args) {
			Value arg = args[0].deref().asValue();
			boolean result = arg.isValue() && arg.value instanceof SExp.Symbol;
			return new Addr (new Value(context, Boolean.valueOf(result)));
		}
	}
	
	static class PrimUndefined extends Primitive {
		PrimUndefined () {
			super ("undefined");
		}
		public boolean isReducible (Context context, Addr [] args) {
			return false;
		}
		public Addr reduce (Context context, Addr [] args) {
			throw new Error ();
		}
	}
	
	static class PrimIsEqual extends Primitive {
		PrimIsEqual () {
			super ("equal?", Strict.STRICT, Strict.STRICT);
		}
		public boolean isReducible (Context context, Addr [] args) {
			Node node1 = args[0].deref();
			Node node2 = args[1].deref();
			return isWhnf(node1)
			    && isWhnf(node2)
			    && ( node1.tag==Heap.Node.Tag.VALUE
			      || node2.tag==Heap.Node.Tag.VALUE );
		}
		public Addr reduce (Context context, Addr [] args) {
			Node node1 = args[0].deref();
			Node node2 = args[1].deref();
			boolean result;
			if (node1.isValue() && node2.isValue())
				result = node1.asValue().value.equals(node2.asValue().value);
			else if (node1.isValue() || node2.isValue())
				result=false;
			else 
				throw new Error ();
			//System.out.println(String.format("(= %s %s) -> %s", node1, node2, result));
			return new Addr (new Value(context, Boolean.valueOf(result)));
		}
	}
	
	static boolean isInteger (Addr addr) {
		Node node = addr.deref();
		return node.isValue() && node.asValue().value instanceof Integer;
	}
	
	static int toInt (Addr addr) {
		return ((Integer)addr.deref().asValue().value).intValue();
	}
	
	static abstract class PrimBinaryMath extends Primitive {
		PrimBinaryMath (String name) {
			super (name, Strict.STRICT, Strict.STRICT);
		}
		public boolean isReducible(Context context, Addr [] args) {
			return isInteger(args[0]) && isInteger(args[1]);
		}
		public Addr reduce(Context context, Addr [] args) {
			int arg0 = toInt(args[0]);
			int arg1 = toInt(args[1]);
			Integer result = new Integer (oper (arg0, arg1));
			//System.out.println(String.format("(%s %s %s) -> %s", name, arg0, arg1, result));
			return new Addr (new Value (context, result));
		}
		abstract int oper (int lhs, int rhs);
	}
	
	static class PrimAdd extends PrimBinaryMath {
		PrimAdd () {
			super ("+");
		}
		int oper (int lhs, int rhs) {
			return lhs+rhs;
		}
	}
	
	static class PrimSub extends PrimBinaryMath {
		PrimSub () {
			super ("-");
		}
		int oper (int lhs, int rhs) {
			return lhs-rhs;
		}
	}
	
	static class PrimMult extends PrimBinaryMath {
		PrimMult () {
			super ("*");
		}
		int oper (int lhs, int rhs) {
			return lhs*rhs;
		}
	}
	
	static abstract class PrimBinaryMathCompare extends Primitive {
		PrimBinaryMathCompare (String name) {
			super (name, Strict.STRICT, Strict.STRICT);
		}
		public boolean isReducible(Context context, Addr [] args) {
			return isInteger(args[0]) && isInteger(args[1]);
		}
		public Addr reduce(Context context, Addr [] args) {
			return new Addr (new Value (context, new Boolean (oper (toInt(args[0]), toInt(args[1])))));
		}
		abstract boolean oper (int lhs, int rhs);
	}
	
	static class PrimEquals extends PrimBinaryMathCompare {
		PrimEquals () {
			super ("=");
		}
		boolean oper (int lhs, int rhs) {
			return lhs==rhs;
		}
	}
	
	static class PrimLessThan extends PrimBinaryMathCompare {
		PrimLessThan () {
			super ("<");
		}
		boolean oper (int lhs, int rhs) {
			return lhs<rhs;
		}
	}
	
	static Primitive primResid = new Primitive ("resid", new Strict [] {Strict.NON_STRICT}) {
		public boolean isReducible(Context context, Addr [] args) {
			return context.depth==0;
		}
		public Addr reduce(Context context, Addr [] args) {
			return args[0];
		}
	};
	
	static Primitive primResidUntil = new Primitive ("resid-until", new Strict [] {Strict.STRICT, Strict.NON_STRICT}) {
		public boolean isReducible(Context context, Addr [] args) {
//			if (args.get(0).deref().depth==0)
//				System.out.println(String.format("%s %s", args.get(0).deref().depth, args.get(0).deref().toString()));
			return args[0].deref().context.depth==0;
//			switch (args.get(0).deref().tag) {
//			case VALUE:
//			case PAIR:
//			case LAMBDA:
//				return args.get(0).deref().depth==0;
//				//return true;
//			default:
//				return args.get(0).deref().depth==0;
//				//return false;
//			}
		}
		public Addr reduce(Context context, Addr [] args) {
			//System.out.println(String.format("%s %s %s", depth, args.get(0).deref().depth, args.get(1).deref().depth));
			return args[1];
		}
	};
	
	static Primitive primSeq = new Primitive ("seq", new Strict [] {Strict.STRICT, Strict.NON_STRICT}) {
		public boolean isReducible(Context context, Addr [] args) {
			switch (args[0].deref().tag) {
			case VALUE:
			case PAIR:
			case LAMBDA:
				return true;
			default:
				return false;
			}
		}
		public Addr reduce(Context context, Addr [] args) {
			return args[1];
		}
	};
	
	static boolean isHyperStrictValue (Addr addr) {
		Node node = addr.deref();
		switch (node.tag) {
		case VALUE:
		case LAMBDA:
			return true;
		case PAIR:
			Pair pair = node.asPair();
			return isHyperStrictValue (pair.head) && isHyperStrictValue (pair.tail);
		default:
			return false;
		}
	}
	
	static Primitive primHyperSeq = new Primitive ("hyper-seq", new Strict [] {Strict.HYPER_STRICT, Strict.NON_STRICT}) {
		public boolean isReducible(Context context, Addr [] args) {
			return isHyperStrictValue(args[0]);
		}
		public Addr reduce(Context context, Addr [] args) {
			return args[1];
		}
	};
	
	static class BuiltInFunctions {
		TreeMap <String ,BuiltIn> builtins = new TreeMap<String,BuiltIn>();
		void bind (String name, BuiltIn builtin) {
			builtins.put(name, builtin);
		}
		void bind (String name, Primitive primitive) {
			builtins.put(name, new PrimitiveBuiltIn(primitive));
		}
		void bind (Primitive primitive) {
			builtins.put(primitive.name, new PrimitiveBuiltIn(primitive));
		}
		BuiltIn lookup (String name) {
			return builtins.get(name);
		}
	}
	
	static class BuiltInLiteral extends BuiltIn {
		Object value;
		BuiltInLiteral (Object value) {
			super(0);
			this.value = value;
		}
		public Addr instantiate(Context context, Addr[] args) {
			return new Addr (new Value (context, value));
		}
	}
	
	static BuiltIn literalTrue = new BuiltInLiteral (Boolean.TRUE);
	static BuiltIn literalFalse = new BuiltInLiteral (Boolean.FALSE);
	
	static Eval.Strategy mkStrategy (String symbol) {
		if (symbol.equals("name"))
			return new Eval.Strategy(Eval.SubStrategy.NAME);
		if (symbol.equals("hs"))
			return new Eval.Strategy(Eval.Form.HYPER_STRICT);
		if (symbol.equals("whnf"))
			return new Eval.Strategy(Eval.Form.WHNF);
		if (symbol.equals("wnf"))
			return new Eval.Strategy(Eval.Form.WNF);
		if (symbol.equals("hnf"))
			return new Eval.Strategy(Eval.Form.HNF);
		if (symbol.equals("nf"))
			return new Eval.Strategy(Eval.Form.NF);
		if (symbol.equals("need"))
			return new Eval.Strategy(Eval.SubStrategy.NEED);
		throw new Error ("unknown strategy: " + symbol);
	}

	static class ApplyStrategy {
		static final String [] strategies = {"name", "hyper-strict", "whnf", "wnf", "hnf", "nf", "need"};
		static {
			Arrays.sort(strategies);
		}
		static boolean isStrategy (Addr addr) {
			Node node = addr.deref();
			if (!node.isValue())
				return false;
			Object value = node.asValue().value;
			if ( ! (value instanceof SExp.Symbol) )
				return false;
			SExp.Symbol symbol = (SExp.Symbol) value;
			return Arrays.binarySearch (strategies, symbol.toString()) >= 0;
		}
		static Eval.Strategy getStrategy (Addr addr) {
			if (isStrategy(addr)) {
				SExp.Symbol symbol = (SExp.Symbol) addr.deref().asValue().value;
				Eval.Strategy strategy = Eval.Strategy.fromString(symbol.toString(), null);
				return strategy;
			}
			else
				throw new InternalError ("not a strategy");
		}
	}
	
	static BuiltIn builtInApplyStrategy = new BuiltIn (4) {
		public Addr instantiate(Context context, Addr[] args) {
			if (ApplyStrategy.isStrategy(args[0]) && ApplyStrategy.isStrategy(args[1])) {
				Eval.Strategy funcStrategy = ApplyStrategy.getStrategy(args[0]);
				Eval.Strategy argStrategy = ApplyStrategy.getStrategy(args[1]);
				return new Addr (new Apply (context, args[2], args[3], funcStrategy, argStrategy));				
			}
			else {
				return new Addr (new Delta (context, primApplyStrategy, args));
			}
		}
	};

	static Primitive primApplyStrategy = 
		new Primitive ("apply-strategy", 
				Strict.STRICT, Strict.STRICT, Strict.NON_STRICT, Strict.NON_STRICT) 
	{
		public boolean isReducible(Context context, Addr[] args) {
			return ApplyStrategy.isStrategy(args[0]) && ApplyStrategy.isStrategy(args[1]);
		}
		public Addr reduce(Context context, Addr[] args) {
			Eval.Strategy funcStrategy = ApplyStrategy.getStrategy(args[0]);
			Eval.Strategy argStrategy = ApplyStrategy.getStrategy(args[1]);
			return new Addr (new Apply (context, args[2], args[3], funcStrategy, argStrategy));
		}
	};
	
	
	static public BuiltInFunctions makeBuiltIns () {
		BuiltInFunctions builtins = new BuiltInFunctions();
		builtins.bind("cons", new ConsBuiltIn());
		builtins.bind("head", new PrimHead());
		builtins.bind("tail", new PrimTail());
		builtins.bind("hd",   new PrimHead());
		builtins.bind("tl",   new PrimTail());
		builtins.bind("car",  new PrimHead());
		builtins.bind("cdr",  new PrimTail());
		builtins.bind("if",   new PrimIf());
		builtins.bind("pair?",new PrimIsPair());
		builtins.bind("null?",new PrimIsNull());
		builtins.bind("equal?",new PrimIsEqual());
		builtins.bind("number?",new PrimIsNumber());
		builtins.bind("symbol?",new PrimIsSymbol());
		builtins.bind("+",    new PrimAdd());
		builtins.bind("-",    new PrimSub());
		builtins.bind("*",    new PrimMult());
		builtins.bind("=",    new PrimEquals());
		builtins.bind("<",    new PrimLessThan());
		builtins.bind("undefined", new PrimUndefined());
		builtins.bind("true", literalTrue);
		builtins.bind("false", literalFalse);
		
		builtins.bind("apply-strategy",  builtInApplyStrategy);
		builtins.bind("resid",           primResid);
		builtins.bind("resid-until",     primResidUntil);
		builtins.bind("seq",             primSeq);
		builtins.bind("hyper-seq",       primHyperSeq);
		
		return builtins;
	}
}
