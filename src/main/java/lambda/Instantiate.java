package lambda;

import java.util.TreeMap;

import lambda.Heap.Addr;
import lambda.Heap.Apply;
import lambda.Heap.Context;
import lambda.Heap.Lambda;
import lambda.Heap.Pair;
import lambda.Heap.Value;
import lambda.Heap.Var;
import lambda.Primitives.BuiltIn;
import lambda.Primitives.BuiltInFunctions;


public class Instantiate {
	public static Addr instantiate 
			( BuiltInFunctions builtins, 
			  Eval.Strategy funcStrategy, Eval.Strategy argStrategy, 
			  int depth, Heap.Context context, Env env, SExp sexp) throws SyntaxError 
	{
		Instantiate inst = new Instantiate (builtins, funcStrategy, argStrategy, depth, context, env);
		return inst.instExpr(sexp);
	}
	
	public Addr instantiate (int depth, Heap.Context context, Env env, SExp sexp) throws SyntaxError {
		Instantiate inst = new Instantiate (builtins, funcStrategy, argStrategy, depth, context, env);
		return inst.instExpr(sexp);
	}
	
    final BuiltInFunctions builtins;
    final Eval.Strategy funcStrategy;
    final Eval.Strategy argStrategy;
    final int depth;
    final Heap.Context context;
    final Env env;
    
    Instantiate ( BuiltInFunctions builtins_, 
    		      Eval.Strategy funcStrategy, Eval.Strategy argStrategy, 
    		      int depth_, Heap.Context context, Env env_) 
    {
    	builtins=builtins_;
    	depth = depth_;
    	env = new Env (env_);
    	this.funcStrategy = funcStrategy;
    	this.argStrategy = argStrategy;
    	this.context = context;
    	if (context==null && depth!=0 || context!=null && depth != context.depth)
    		throw new Error ("bad context");
    }
    
    Addr instLambda (SExp sexp) throws SyntaxError  {
    	SExp vars = sexp.get(1);
    	SExp body = sexp.get(2);
    	if (!vars.isList())
    		throw new SyntaxError (vars.pos, "variable list expected");
    	int numVars = vars.length();
    	Env newEnv = new Env (env);
    	int newDepth=depth;
    	Heap.Context newContext = context;
    	Addr [] lambdas = new Addr [vars.length()];
    	for (int i=0; i!=vars.length(); i++) {
    		lambdas[i] = new Addr (null);
    	}
    	for (int i=0; i!=vars.length(); i++) {
    		String name = vars.get(i).symbol();
        	newDepth++;
        	newContext = new Context (newDepth, lambdas[i], newContext);
        	Addr var = new Addr (new Var (newContext));
        	newEnv.bind (name, var);
    	}
    	Addr result = instantiate (newDepth, newContext, newEnv, body);
    	for (int i=numVars-1; i != -1; i--) {
    		newDepth--;
        	newContext = newContext.context;
    		result = new Addr (new Lambda (newContext, result));
    		lambdas[i].link(result);
    	}
    	return lambdas[0];
    }    
    Addr instApply (SExp sexp) throws SyntaxError {
    	SExp func = sexp.get(0);
    	Addr result = instExpr (func);
    	for (int i=1; i!=sexp.length(); i++) {
    		Addr arg = instExpr (sexp.get(i));
    		result = new Addr (new Apply (context, result, arg, funcStrategy, argStrategy));
    	}
    	return result;
    }
    Addr instQuote (SExp arg) throws SyntaxError {
        if (arg.isList()) {
        	Addr result = new Addr (new Heap.Value(context, SExp.nil));
        	for (int i=arg.length()-1; i!=~0; i--) {
        		result = new Addr (new Pair (context, instQuote (arg.get(i)), result));
        	}
        	return result;
        }
        else if (arg.isNil()) {
            return new Addr(new Value(context, SExp.nil));
        }
        else if (arg.isSymbol()) {
            return new Addr(new Value(context, arg));
        }
        else if (arg.isNumber()) {
            return new Addr(new Value(context, new Integer (arg.number())));
        }
        else if (arg.isValue()) {
        	return new Addr(new Value(context, arg.asValue().value));
        }
        else throw new SyntaxError (arg, "unknown SExp: "+arg.toString());
    }
    Addr instLet (SExp sexp) throws SyntaxError  {
        SExp decls = sexp.get(1);
        SExp expr  = sexp.get(2);
        Env newEnv = instDecls (decls);
        return instantiate (depth, context, newEnv, expr);
    }
    Env instDecls  (SExp decls) throws SyntaxError  {
        Env newEnv = new Env (env);
        TreeMap <Addr, SExp> todo = new TreeMap <Addr,SExp> ();
        for (int i=0; i!=decls.length(); i++) {
        	SExp decl = decls.get(i);
        	if (!decl.isList() || decl.length()!=2)
        		throw new SyntaxError (decl, "declaration error, expected two element list (name defn)");
            Addr hole = new Addr(null);
            newEnv.bind(decl.get(0).symbol(), hole);
            todo.put(hole, decl.get(1));
        }
        for (Addr addr : todo.keySet()) {
            SExp expr = todo.get(addr);
            Addr addr2 = instantiate (depth, context, newEnv, expr);
            addr.link(addr2);
        }
        return newEnv;
    }
    
    // instantiate partially/fully/overly - applied builtins
    Addr instBuiltIn (SExp sexp) throws SyntaxError {
    	BuiltIn builtin = builtins.lookup(sexp.get(0).symbol());
    	int numArgs = sexp.length()-1;
    	int arity = builtin.arity;
    	
    	// numDeltaArgs + numMissingArgs === arity
    	int numDeltaArgs = numArgs > arity ? arity : numArgs;
    	int numMissingArgs = arity > numArgs ? arity - numArgs : 0;

    	Addr args [] = new Addr [arity];
    	Addr [] lambdas = new Addr [numMissingArgs];
    	int newDepth = depth;
    	Context newContext = context;
    	
    	for (int i=0; i!=numMissingArgs; i++) {
    		lambdas[i] = new Addr (null);
    	}
    	for (int i=0; i!=numDeltaArgs; i++) {
    		args[i] = instExpr(sexp.get(i+1));
    	}
    	for (int i=numDeltaArgs; i!=arity; i++) {
    		newDepth++;
    		newContext = new Context (newDepth, lambdas[i-numDeltaArgs], newContext);
    		args[i] = new Addr (new Var (newContext));
    	}
    	
    	Addr addr = builtin.instantiate(newContext, args);
    	
    	for (int i=0; i!=numMissingArgs; i++) {
    		newDepth--;
    		newContext = newContext.context;
    		addr = new Addr (new Lambda (newContext, addr));
    		lambdas[i].link(addr);
    	}
    	for (int i=arity; i<numArgs; i++) {
    		addr = new Addr (new Apply (context, addr, instExpr(sexp.get(i+1)), funcStrategy, argStrategy));
    	}
    	return addr;
    }
    Addr instExpr (SExp sexp) throws SyntaxError {
    	if (sexp.isSymbol())
    	{
    		String symbol = sexp.symbol();
    		Addr addr =  env.lookup(symbol);
    		if (addr!=null) {
    			if (addr.deref()!=null && addr.deref().isVar() /*&& multipleVars*/)
    				return new Addr (addr.deref());
   				else
   					return addr;
    		}
    		BuiltIn builtin = builtins.lookup(symbol);
    		if (builtin!=null)
    			return instBuiltIn (new SExp.List(sexp));
   			throw new SyntaxError(sexp, "unknown identifier: "+symbol);
    	}
    	else if (sexp.isNumber()) {
    		return new Addr (new Value (context, new Integer (sexp.number())));
    	}
    	else if (sexp.isValue()) {
    		return new Addr (new Value (context, sexp.asValue().value));
    	}
    	else if (sexp.isList())
    	{
    		if (sexp.get(0).isSymbol())
    		{
    			String tag = sexp.get(0).symbol();
    			if (tag.equals("lambda")) {
    				arityCheck(sexp, 2);
    				return instLambda (sexp);
    			}
    			else if (tag.equals("letrec")) {
    				arityCheck (sexp, 2);
    				return instLet (sexp);
    			}
    			else if (tag.equals("quote")) {
    				arityCheck (sexp, 1);
    				return instQuote (sexp.get(1));
    			}
    			else if (tag.equals("define"))
    				throw new SyntaxError (sexp, "define only permitted at top level");
    			else if (env.lookup(tag)!=null)
    				return instApply (sexp);
    			else if (builtins.lookup(tag)!=null)
    				return instBuiltIn(sexp);
    			else 
    				throw new SyntaxError(sexp, "unknown identifier: "+tag);
    		}
    		else 
    			return instApply (sexp);
    	}
    	else throw new SyntaxError (sexp, "unknown SExp: "+sexp.toString());
    }
    
    static void arityCheck (SExp sexp, int arity) throws SyntaxError {
    	int numArgs = sexp.length()-1; 
    	if (numArgs > arity)
    		throw new SyntaxError (sexp.pos, String.format("too many arguments to %s, expected %s, found %s", sexp.get(0).asSymbol().value, arity, numArgs));
    	if (numArgs < arity)
    		throw new SyntaxError (sexp.pos, String.format("too few arguments to %s, expected %s, found %s", sexp.get(0).asSymbol().value, arity, numArgs));
    }
    
    static class SyntaxError extends Exception {
    	int pos;
    	String msg;
    	SyntaxError(int pos, String msg) {
    		this.pos = pos;
    		this.msg = msg;
    	}
    	SyntaxError(SExp sexp, String msg) {
    		pos = sexp.pos;
    		this.msg = msg;
    	}
		public java.lang.String getMessage () {
			return msg;
		}
    }

    static boolean isDefinition (SExp sexp) {
    	return (sexp.isList() && sexp.get(0).isSymbol() && sexp.get(0).symbol().equals("define"));
    }
    
}
