package lambda;

import java.util.ArrayList;
import java.util.HashMap;

import lambda.Heap.Addr;
import lambda.Heap.Apply;
import lambda.Heap.Context;
import lambda.Heap.Delta;
import lambda.Heap.Lambda;
import lambda.Heap.Node;
import lambda.Heap.Pair;
import lambda.Heap.Primitive;
import lambda.Heap.SubstCommon;
import lambda.Heap.Value;
import lambda.Heap.Var;


public class Eval {
	
	static void eval (Addr addr, boolean copyArgs/*, Options opt*/)
	{
	    int reductionNum=0;
	    Stack stack = new Stack ();
	    stack.push(addr, Form.HYPER_STRICT, 0);

	    findNextRedex (stack/*, opt*/);
//        if (opt.log != null)
//        	opt.log.log (addr);
	    while ( ! stack.empty() ) {
		    Addr nextRedex = stack.peekAddr();
	        //System.out.print(reductionNum+": ");
	        //System.out.print(stack);
	        //System.out.println(PrettyPrint.prettyprint (addr));
	    	//System.out.println(nextRedex);
	        reduce (nextRedex, copyArgs);
	        reductionNum ++;
//	        if (opt.log != null)
//	        	opt.log.log (addr);
	    	findNextRedex(stack/*, opt*/);
	    }
//	    Ast.Node result = UnInstantiate.unInstantiate(addr);
//	    SExp result2 = result.toSExp();
//	    System.out.println(result2.toString());
	    System.out.println(String.format("[steps: %d]", reductionNum));
	}
	
	enum SubStrategy {NAME, VALUE, NEED};
	enum Form {WHNF, WNF, HNF, NF, HYPER_STRICT};
	//enum Strategy {NAME, HS, WHNF, WNF, HNF, NF, NEED};

	static class Strategy {
		final SubStrategy subStrategy;
		final Form form;
		static final Strategy NAME = new Strategy (SubStrategy.NAME);
		static final Strategy HS = new Strategy (Form.HYPER_STRICT);
		static final Strategy WHNF = new Strategy (Form.WHNF);
		static final Strategy WNF = new Strategy (Form.WNF);
		static final Strategy HNF = new Strategy (Form.HNF);
		static final Strategy NF = new Strategy (Form.NF);
		static final Strategy NEED = new Strategy (SubStrategy.NEED);
		
		public Strategy(final Form form) {
			this.subStrategy = SubStrategy.VALUE;
			this.form = form;
		}
		public Strategy(final SubStrategy subStrategy) {
			if (subStrategy==SubStrategy.VALUE)
				throw new Error ();
			this.subStrategy = subStrategy;
			this.form = Form.WHNF;
		}
		
		static Strategy from (Form form) {
			switch (form) {
			case HYPER_STRICT: return HS;
			case WHNF: return WHNF;
			case WNF: return WNF;
			case HNF: return HNF;
			case NF: return NF;
			}
			throw new Error ();
		}		
		static Strategy from (SubStrategy subStrategy) {
			switch (subStrategy) {
			case NAME: return NAME;
			case NEED: return NEED;
			}
			throw new Error ();
		}
		
		public String toString () {
			switch (subStrategy) {
			case NAME: return "name";
			case VALUE:
				switch (form) {
				case HYPER_STRICT: return "hs";
				case WHNF: return "whnf";
				case WNF:  return "wnf";
				case HNF:  return "hnf";
				case NF:   return "nf";
				}
			case NEED: return "need";
			}
			throw new Error ();
		}
		static Strategy fromString (String s1, Strategy defaultResult) {
			String s = s1.toLowerCase();
			if (s.equals("name")) return NAME;
			if (s.equals("hs"))   return HS;
			if (s.equals("whnf")) return WHNF;
			if (s.equals("wnf"))  return WNF;
			if (s.equals("hnf"))  return HNF;
			if (s.equals("nf"))   return NF;
			if (s.equals("need")) return NEED;
			return defaultResult;
		}
	}
	
	//Weak Normal Form - non-Head Normal Form - reduce args
	//Head Normal Form - non-Weak Normal Form - reduce under lambdas
	//Strategy - apply / arg+apply / body+apply / arg+body+apply
	//Arg - duplicated / shared / pre-evaluated to {W,}{H,}NF
	//Body - duplicated / shared / pre-evaluated to {W,}{H,}NF
	// name / need / value-{W,}{H,}NF
	
	//HYPER_STRICT is part-way between WNF and WHNF, it is strict in constructor arguments (cons A B),
	//but not strict in arguments of other irreducible applications (lambda (v) (v A B)).

	
	static boolean isReduced (Addr addr) {
		Node node = addr.deref();
		boolean result;
		if (node.isReduced())
			return true;
		switch (node.tag) {
		case VAR:
		case LAMBDA:
		case VALUE:
		case PAIR:
			result = true;
		case APPLY:
			Apply apply = node.asApply();
			if (apply.func.deref().isLambda())
				return false;
			result = isReduced(node.asApply().func);
			break;
		case DELTA:
			Delta delta = node.asDelta();
			Primitive prim = delta.prim;
			result = true;
			for (int i=0; i != prim.args.length; i++) {
				switch (prim.args[i]) {
				case NON_STRICT:
					break;
				case STRICT:
					result &= isReduced(delta.arg(i));
					break;
				case HYPER_STRICT:
					result &= delta.arg(i).deref().isReduced(Form.HYPER_STRICT);
					break;
				default: throw new Error ();
				}
			}
			result &= ! prim.isReducible(node.context, delta.args);
			break;
		case SUBST:
			return false;
		default: throw new Error ();
		}
		if (result) {
			node.setReduced();			
		}
		return result;
	}

	// substitute only those nodes which are reduced, build Subst nodes when non-reduced nodes are found.
	static Addr substSome (Context context, Addr body, SubstCommon sc, boolean copyArgs, int extent) {
		Node node = body.deref();
    	if (node.tag==Node.Tag.VAR && node.context.depth==sc.bind) {
    		if (copyArgs)
    			//return Eval.substAll (sc.arg, 0, null, 0, copyArgs);
    			return Eval.substSome (context, sc.arg, new SubstCommon (0, null, 0, null, false), copyArgs, substLimit);
    			//throw new Error ("copy args temporarily out of service");
    		else
    			return sc.arg;
    	}
		if (node.context.depth < sc.bind )
			return body;
		Addr memoResult = sc.memo.get(body);
    	if (memoResult!=null) {
    		return memoResult;
    	}
    	int newDepth = node.context.depth+sc.shift;
    	context = context.getContextAtDepth(newDepth);
		if ( extent==0 || sc.specializing && ! isReduced (body) || ! sc.specializing && node.isSubst()) {
			return new Addr (new Heap.Subst (context, body, sc));
			//return mkSubst (sc, newDepth, body);
		}
    	Addr hole = new Addr(null);
    	sc.memo.put(body, hole);
    	Addr result;
    	switch (node.tag) {
    	case APPLY:
    		Apply apply = node.asApply();
    		Addr applyFunc = substSome(context, apply.func,sc,copyArgs,extent-1);
    		Addr applyArg  = substSome(context, apply.arg, sc,copyArgs,extent-1);
    		result = new Addr (new Apply (context, applyFunc, applyArg, apply.funcStrategy, apply.argStrategy));
    		break;
    	case PAIR:
    		Pair pair = node.asPair();
    		Addr head = substSome(context, pair.head,sc,copyArgs,extent-1);
    		Addr tail = substSome(context, pair.tail,sc,copyArgs,extent-1);
    		result = new Addr (new Pair (context, head, tail));
    		break;
    	case DELTA:
    		Delta delta = node.asDelta();
    		ArrayList<Addr> args = new ArrayList<Addr>(delta.args.length);
    		for (Addr deltaArg : delta.args)
    		{
    			args.add(substSome(context,deltaArg,sc,copyArgs,extent-1));
    		}
    		result = new Addr (new Delta(context, delta.prim, args));
    		break;
    	case LAMBDA:
    		Lambda lambda = node.asLambda();
    		Context newContext = new Heap.Context (newDepth+1,hole,context);
    		Addr newLambdaBody = substSome (newContext,lambda.body,sc,copyArgs,extent-1);
    		result = new Addr (new Lambda(context, newLambdaBody));
    		break;
    	case VAR:
    		result = new Addr (new Var(context));
    		break;
    	case VALUE:
    		result = new Addr (new Value (context, node.asValue().value));
    		break;
    	default:
    		throw new Error ();
    	}
    	hole.link(result);
    	return result;
	}

    static boolean isStackCandidate (Stack stack, Addr addr, Form form) {
    	if (addr.deref().isReduced(form))
    		return false;
    	if (addr.deref().isInStack(form))
    		return false;
    	if (stack.isTried(addr, form))
    		return false;
    	return true;
    }
    
    static void setInProgress (HashMap<Addr, Integer> inProgress, Addr addr, Form form) {
    	if (inProgress.containsKey(addr))
    		inProgress.put(addr, inProgress.get(addr) | form.ordinal());
    	else 
    		inProgress.put(addr, form.ordinal());
    }
    
    static boolean isInProgress (HashMap<Addr, Integer> inProgress, Addr addr, Form form) {
    	if (inProgress.containsKey(addr))
    		return (inProgress.get(addr) & form.ordinal()) != 0;
    	else
    		return false;
    }
    
	
    // find the next redex, using the 'stack' to remember our place from the last call.
	static void findNextRedex (Stack stack) {
		stack.clearTried();
		stack.setNextChild(0);
		start:
			while ( ! stack.empty() ) {
				Stack.Entry se = stack.peek();
				Addr addr = se.addr;
				Node node = addr.deref();
				Form form = se.form;
				if (node.isReduced(form)) {
					stack.pop();
					continue start;
				}
				//System.out.println(stack);
				//int childIndex = stack.getNextChild();
				switch (node.tag) {
				case APPLY:
					Apply apply = node.asApply();
					if (se.reducible) {
						switch (stack.getNextChild()) {
						case 0:
							Form funcForm = apply.funcStrategy.subStrategy==SubStrategy.VALUE 
							? apply.funcStrategy.form : Form.WHNF;
							if ( isStackCandidate(stack, apply.func, funcForm)) {
								stack.push(apply.func, funcForm, 0);
								continue start;
							}
						case 1:
							if ( apply.argStrategy.subStrategy==SubStrategy.VALUE 
									&& isStackCandidate(stack, apply.arg,apply.argStrategy.form) )
							{
								stack.push(apply.arg, apply.argStrategy.form, 1);
								continue start;
							}
						}
						if (apply.func.deref().isLambda()) {
							return;
						}
						else {
							stack.setIrreducible();
						}
					}
					switch (stack.getNextChild()) {
					case 0:
						if ((form==Form.NF || form==Form.HNF) && isStackCandidate(stack, apply.func, form)) {
							stack.push(apply.func, form, 0);
							continue start;
						}
					case 1:
						if ((form==Form.NF || form==Form.WNF) && isStackCandidate(stack, apply.arg, form)) {
							stack.push(apply.arg, form, 1);
							continue start;
						}
					}
					boolean argReduced  = apply.arg.deref().isReduced(form)  || !(form==Form.NF || form==Form.WNF);
					boolean funcReduced = apply.func.deref().isReduced(form) || !(form==Form.NF || form==Form.HNF);
					if (funcReduced && argReduced)
						node.setReduced(form);
					stack.pop();
					continue start;
				case DELTA:
					Delta delta = node.asDelta();
					Primitive prim = delta.prim;
					Addr [] children = delta.args;
					if (se.reducible) {
						for (int i=stack.getNextChild(); i!=children.length; i++) {
							Addr arg = children[i];
							switch (prim.getArgStrictness(i)) {
							case NON_STRICT:
								break;
							case STRICT:
								if ( isStackCandidate(stack, arg, Form.WHNF) ) {
									stack.push(arg, Form.WHNF, i);
									continue start;
								}
								break;
							case HYPER_STRICT:
								if ( isStackCandidate(stack, arg, Form.HYPER_STRICT) ) {
									stack.push(arg, Form.HYPER_STRICT, i);
									continue start;
								}
								break;
							default: throw new InternalError ("missing case");			
							}
						}
						if (prim.isReducible(node.context, delta.args))
							return;
						else
							stack.setIrreducible();
					}
					if (form==Form.NF || form==Form.WNF) {
						for (int i=stack.getNextChild(); i!=children.length; i++) {
							Addr arg = delta.arg(i);
							if ( isStackCandidate(stack, arg, form) ) {
								stack.push(arg, form, i);
								continue start;
							}
						}
						boolean allArgsReduced=true;
						for (int i=0; i!=children.length; i++) {
							if (!children[i].deref().isReduced(form))
								allArgsReduced=false;
						}
						if (allArgsReduced)
							delta.setReduced(form);
						stack.pop();
						continue start;
					}
					node.setReduced(form);
					continue start;
				case PAIR:
					Pair pair = node.asPair();
					if (form==Form.WHNF || form==Form.HNF) {
						node.setReduced(form);
						continue start;
					}
					switch (stack.getNextChild()) {
					case 0:
						if ( isStackCandidate(stack, pair.head, form) ) {
							stack.push(pair.head, form, 0);
							continue start;
						}
					case 1:
						if ( isStackCandidate(stack, pair.tail, form) ) {
							stack.push(pair.tail, form, 1);
							continue start;
						}
					}
					if (pair.head.deref().isReduced(form) && pair.tail.deref().isReduced(form))
						node.setReduced(form);
					stack.pop();
					continue start;
				case SUBST:
					Heap.Subst subst = node.asSubst();
					Addr substBody = subst.body;
					if (subst.common.specializing) {
						if (isStackCandidate(stack, substBody, Form.WHNF)) {
							stack.push(substBody, Form.WHNF, 0);
							continue start;
						}
						return;
					}
					else {
						if (substBody.deref().isSubst()) {
							stack.push(substBody, Form.WHNF, 0);
							continue start;
						}
						else 
							return;
					}
				case LAMBDA:
					Lambda lambda = node.asLambda();
					switch (stack.getNextChild())
					{
					case 0:
						if (form==Form.WHNF || form==Form.WNF || form==Form.HYPER_STRICT) {
							node.setReduced(form);
							continue start;
						} else if (isStackCandidate(stack, lambda.body, form)) {
							stack.push(lambda.body, form, 0);
							continue start;
						}
					}
					if (lambda.body.deref().isReduced(form))
						lambda.setReduced(form);
					stack.pop();
					continue start;
				case VAR:
				case VALUE:
					node.setReduced(form);
					continue start;
				default: throw new Error ("missing case");
				}
			}
	}
	
	
	
	// perform a single reduction step to reduce the redex at he top of the 'stack', 
	// using the reduction strategy 'opt'
	static boolean reduce (Addr addr, boolean copyArgs/*, Options opt*/) {
		Node node = addr.deref();
		switch (node.tag) {
		case APPLY:
			Apply apply = node.asApply();
			Node func = apply.func.deref();
			if (func.isLambda()) {
				Lambda lambda = func.asLambda();
				Addr body = lambda.body;
				int bind = lambda.context.depth+1;
				Addr arg = apply.arg;
				int shift = node.context.depth-func.context.depth-1;
				Heap.Context context = apply.context;
				switch (apply.funcStrategy.subStrategy) {
				case NAME:
					SubstCommon sc = new Heap.SubstCommon (bind, arg, shift, apply.func, false);
					addr.link ( substSome (context, body, sc, copyArgs, substLimit));
					return true;
				case VALUE:
					if (true || func.isReduced(apply.funcStrategy.form)) {
						sc = new Heap.SubstCommon (bind, arg, shift, apply.func, false);
						addr.link ( substSome (context, body, sc, copyArgs, substLimit));
						return true;
					}
					else
						return false;
				case NEED:
					sc = new Heap.SubstCommon (bind, arg, shift, apply.func, true);
					addr.link ( substSome (context, body, sc, copyArgs, substLimit));
					return true;
				default:
					throw new Error ();
				}
			}
			else
				return false;
		case DELTA:
			Delta delta = node.asDelta();
	    	Primitive prim = delta.prim;
	    	Addr [] args = delta.args;
	    	if (prim.isReducible(node.context, args)) {
	    		//System.out.println(prim.name);
	    		addr.link(prim.reduce(node.context, args));
	    		return true;
	    	}
	    	else
	    		return false;
		case SUBST:
			//substOne (addr, copyArgs);
			Heap.Subst subst = node.asSubst();
			addr.link(substSome (subst.context, subst.body, subst.common, copyArgs, substLimit));
			return true;
		case VAR:
		case LAMBDA:
		case VALUE:
		case PAIR:
			return false;
		default:
			throw new Error ("missing case");
			
		}
	}
	
	// this limit controls how much graph is substituted in one go.
	// n = -1  => as much as possible
	// n >  0  => n deep, arity^(substLimit-1) nodes
	// n =  1  => a single node
	// changing this has no effect on how many beta and delta reductions are performed.
	final private static int substLimit = 10;
	
}
