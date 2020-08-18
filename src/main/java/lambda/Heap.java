package lambda;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import lambda.Eval.Form;

public class Heap {
    Addr newHole () { return new Addr (null); }
    void show (java.io.PrintStream out) {}
    
    static class BlackHole extends Error {
    	
    }

    public static class Addr implements Comparable <Addr> {
    	static int nextId = 0;
    	final int id;
    	private Addr indirect;
    	private Node node;
    	Addr (Node node) {
    		id = nextId++;
    		this.node = node;
    	}
    	
    	Addr (int id, Node node) {
    		this.id = id;
    		this.node = node;
    		if (id >= nextId)
    			nextId = id+1;
    	}
    	
    	public int compareTo (Addr addr) { 
    		return Integer.signum(id-addr.id); 
    	}
    	public int hashCode () { 
    		return id; 
    	}
    	public boolean equals (Object o) { 
    		return o==null ? false : id==((Addr)o).id;
    	}    	
    	public String toString () { 
    		return Integer.toString(id); 
    	}
    	
    	public Addr ref() {
    		if (indirect==null)
    			return this;
    		if (indirect.indirect==null)
    			return indirect;
    		if (indirect.indirect.indirect==null) {
    			indirect = indirect.indirect;
    			return indirect;
    		}
    		return refAux ();
    	}
    	
    	public Addr refAux () {
    		HashSet<Addr> done = new HashSet<Addr>();
    		Addr current = this;
    		while (current.indirect!=null) {
    			done.add(current);
    			current = current.indirect;
    			if (done.contains(current))
    				throw new BlackHole();
    		}
    		return current;
    	}
    	public Node deref() { 
    		return ref().node; 
    	}
    	public void update (Node newNode) { 
    		ref().node = newNode;
    	}
    	public void linkAux (Addr newAddr) { 
    		node=null;
    		if (indirect!=null)
    			indirect.link(newAddr);
    		indirect=newAddr;
    	}
    	public void link (Addr newAddr) {
    		Node oldNode = deref();
    		Node newNode = newAddr.deref();
    		if (oldNode != null)
    			newNode.inStack |= oldNode.inStack;
    		linkAux(newAddr);
    	}
    }
    
    public abstract static class Node {
    	final Context context;
    	public enum Tag {VAR, LAMBDA, APPLY, SUBST, PAIR, VALUE, DELTA};
    	final Tag tag;
    	
        Node (Context context, Tag tag) {
        	this.context = context;
        	this.tag = tag;
        }

        int reduced=0;
        boolean isReduced (Eval.Form form) {
        	//if (tag==Tag.LAMBDA && (reduced & 4)==0)
        	//	return (reduced & (1 << form.ordinal())) != 0;
        	return (reduced & (1 << form.ordinal())) != 0;
        }
        void setReduced (Eval.Form form) {
        	reduced |= (1 << form.ordinal());
        	//if (tag==Tag.LAMBDA && form==Form.HNF)
        	//	return;
        }

        int inStack=0;
        boolean isInStack (Eval.Form form) {
        	return (inStack & (1 << form.ordinal())) != 0;
        }
        boolean isInStack () {
        	return inStack != 0;
        }
        void setInStack (Eval.Form form) {
        	inStack |= (1 << form.ordinal());
        }
        void clearInStack (Eval.Form form) {
        	inStack &= ~(1 << form.ordinal());
        }
        
        void setReduced () { 
        	setReduced(Eval.Form.WHNF); 
        }
        public boolean isReduced() { 
        	return isReduced(Eval.Form.WHNF); 
        }
        
    	final static Addr [] noChildren = new Addr [0];
        public Addr [] children () {
        	return noChildren;
        }
        
        boolean isVar    () { return tag==Tag.VAR; }
        boolean isLambda () { return tag==Tag.LAMBDA; }
        boolean isApply  () { return tag==Tag.APPLY; }
        boolean isSubst  () { return tag==Tag.SUBST; }
        boolean isPair   () { return tag==Tag.PAIR; }
        boolean isValue  () { return tag==Tag.VALUE; }
        boolean isDelta  () { return tag==Tag.DELTA; }
        
        Var    asVar    () { return (Var)    this; }
        Lambda asLambda () { return (Lambda) this; }
        Apply  asApply  () { return (Apply)  this; }
        Subst  asSubst  () { return (Subst)  this; }
        Pair   asPair   () { return (Pair)   this; }
        Value  asValue  () { return (Value)  this; }
        Delta  asDelta  () { return (Delta)  this; }
    }
    
    static <T> T [] mkArray (T... args) {
    	return args;
    }
    
    public static class Var extends Node {
    	Var (Context context) {
    		super(context, Tag.VAR);
    		setReduced (Form.WHNF);
    		setReduced (Form.WNF);
    		setReduced (Form.HNF);
    		setReduced (Form.NF);
    	}
    	public String toString () {
    		return "var";
    	}
    }
    
    public static class Lambda extends Node {
    	final Addr body;
    	Lambda (Context context, Addr body) {
    		super(context, Tag.LAMBDA);
    		setReduced (Form.WHNF);
    		setReduced (Form.WNF);
    		this.body = body;
    	}
    	public Addr [] children () { return mkArray(body); }
    	public String toString () {
    		return "lambda "+body.toString();
    	}
    }
    
    public static class Apply extends Node {
    	final Addr func;
    	final Addr arg;
    	final Eval.Strategy funcStrategy;
    	final Eval.Strategy argStrategy;
    	
    	Apply (Context context, Addr func, Addr arg, Eval.Strategy funcStrategy, Eval.Strategy argStrategy) {
    		super(context, Tag.APPLY);
    		this.func = func;
    		this.arg = arg;
    		this.funcStrategy = funcStrategy;
    		this.argStrategy = argStrategy;;
    	}
    	public Addr [] children () { return mkArray(func, arg); }
    	public String toString () {
    		return "apply "+func.toString() + " " + arg.toString();
    	}
    }
    public static class Value extends Node {
    	final Object value;
    	Value (Context context, Object value) {
    		super(context, Tag.VALUE);
    		this.value = value;
    		setReduced (Form.WHNF);
    		setReduced (Form.WNF);
    		setReduced (Form.HNF);
    		setReduced (Form.NF);
    	}
    	public String toString () {
    		return "value "+value.toString();
    	}
    }
    public static class Pair extends Node {
    	final Addr head;
    	final Addr tail;
    	Pair (Context context, Addr head, Addr tail) {
    		super(context, Tag.PAIR);
    		this.head = head;
    		this.tail = tail;
    		setReduced (Form.WHNF);
    		setReduced (Form.HNF);
    	}
    	public Addr [] children () { return mkArray(head, tail); }
    	public String toString () {
    		return "pair "+head.toString()+" "+tail.toString();
    	}
    }
    public static class Delta extends Node {
    	final Primitive prim;
    	final Addr [] args;
    	Delta (Context context, Primitive prim, Addr [] args) {
    		super(context, Tag.DELTA);
    		this.prim = prim;
    		this.args = args.clone();
    	}
    	Delta (Context context, Primitive prim, ArrayList <Addr> args) {
    		super(context, Tag.DELTA);
    		this.prim = prim;
    		this.args = new Addr [args.size()];
    		args.toArray(this.args);
    	}
    	public Addr [] children () { 
    		return args.clone();
    	}
    	public String toString () {
    		StringBuffer str = new StringBuffer();
    		str.append ("delta "+prim.name);
    		for (Addr arg : args) {
    			str.append(" "+arg.toString());
    		}
    		return str.toString();
    	}
    	Addr arg(int i) { 
    		return args[i]; }    
    	}
    
    public static class Subst extends Node {
    	final Addr body;
    	final SubstCommon common;
    	Subst (Context context, Addr body_, SubstCommon common_) {
    		super(context, Tag.SUBST);
    		body = body_;
    		common = common_;
    	}
    	public Addr [] children () {
    		if (common.bind==0)
        		return mkArray(body);
    		else
    			return mkArray(body, common.arg); 
    	}
    	public String toString () {
    		return "subst "+body.toString() + " " + common.bind + " " + common.arg.toString() + " " + common.shift;
    	}
    }
    
    static class SubstCommon {
    	final int bind;
    	final Addr arg;
    	final int shift;
    	final MemoTable memo;
    	final Addr lambda;
    	final boolean specializing;
    	SubstCommon (int bind_, Addr arg_, int shift_, Addr lambda_, boolean spec) {
    		bind = bind_;
    		arg = arg_;
    		shift = shift_;
    		memo = new MemoTable();
    		lambda = lambda_;
    		specializing = spec;
    	}    	
    }

    static class MemoTable {
    	HashMap<Addr,Addr> memo = new HashMap<Addr,Addr>();
    	void put (Addr from, Addr to) {
    		if (memo.containsKey(from.ref()))
    			throw new Error ("impossible");
    		memo.put(from.ref(), to);
    	}
    	Addr get (Addr from) {
    		return memo.get(from.ref());
    	}
    }
    
    public abstract static class Primitive
    {
    	final String name;
    	final Primitives.Strict [] args;
    	Primitive (String name, Primitives.Strict ... args) {
    		this.name = name;
    		this.args = args;
    	}
    	public abstract boolean isReducible (Context context, Addr [] args);
    	public abstract Addr reduce (Context context, Addr [] args);
    	Primitives.Strict getArgStrictness (int i) {
    		return args[i];
    	}
    };

    // context is used purely for display purposes
    // without context it's difficult to draw nodes within one function together in the same box.
	static class Context {
		final int depth;
		final Addr lambda;
		final Context context;
		Context (int depth_, Addr lambda_, Context context_) {
			depth = depth_;
			lambda = lambda_==null ? null : lambda_.ref();
			context = context_;
			if ( depth==0 && (lambda!=null || context!=null)
			||   depth!=0 && ( lambda==null || context==null 
   			                   || lambda.deref()!=null && lambda.deref().context.depth != context.depth
					           || lambda.deref()!=null && depth != lambda.deref().context.depth+1 ) )
			{
				throw new Error ("badly formed context");
			}
		}
    	Context getContextAtDepth (int depth) {
    		Context context = this;
	    	while (context.depth != depth) {
	    		context = context.context;
	    	}
	    	return context;
    	}
    	static final Context emptyContext = new Context (0, null, null);
	}
}

    