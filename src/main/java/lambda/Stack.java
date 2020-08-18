package lambda;

import java.util.HashSet;

import lambda.Eval.Form;
import lambda.Heap.Addr;

public class Stack {
	public static class Entry
	{
		final Addr addr;
		final Form form;
		final int route;
		final boolean reducible;
		Addr addr () { return addr; }
		Entry (Addr addr, Form form, int route, boolean reducible) {
			this.addr = addr;
			this.form = form;
			this.route = route;
			this.reducible = reducible;
		}
	}

	private ArrayStack<Entry> stack = new ArrayStack<Entry>();
	private int nextChild;
	private HashSet<Addr> [] tried = new HashSet [Eval.Form.values().length];
	{
		for (int i=0; i!=Form.values().length; i++) {
			tried[i] = new HashSet<Addr> ();
		}
	}
	
	public void push (Entry entry) {
		stack.push(entry); 
		entry.addr.deref().setInStack(entry.form);
		nextChild=0;
		setTried (entry.addr, entry.form);
	}
	public void push (Addr addr, Form form, int route) {
		push(new Entry(addr, form, route, true)); 
	}
	Entry pop () { 
		Entry se = stack.pop(); 
		se.addr.deref().clearInStack(se.form);
		nextChild=se.route+1;
		return se;
	}
	Addr peekAddr () { return stack.peek().addr(); }
	Form form () { return stack.peek().form; }
	Entry peek () { return stack.peek(); }
	boolean empty () { return stack.isEmpty(); }
	Addr root () { return stack.get(0).addr(); }
	void clear () { stack.clear(); nextChild=0; }
	int size () { return stack.size(); }
	Entry get (int i) { return stack.get(i); }
	int getNextChild () { return nextChild; }
	void setNextChild (int index) { nextChild=index; }
	void resetToRoot () { 
		Entry root = get(0); 
		while (!empty())
			pop();
		push(root); 
	}
	void setIrreducible () {
		Entry se = pop();
		push (new Entry (se.addr, se.form, se.route, false));
	}
	
	boolean isTried (Addr addr, Form form) {
		return tried[form.ordinal()].contains(addr.ref());
	}
	void setTried  (Addr addr, Form form) {
		tried[form.ordinal()].add(addr.ref());
	}
	void clearTried () {
		for (int i=0; i!=Form.values().length; i++) {
			tried[i].clear();
		}
	}
	
//	ArrayList<Integer> getAddrIds () {
//		ArrayList<Integer> result = new ArrayList<Integer>();
//		for (Entry se : stack) {
//			result.add(se.addr().id);
//		}
//		return result;
//	}
	
	public String toString () {
		StringBuffer str = new StringBuffer();
		str.append('{');
		for (int i=0; i != stack.size(); i++) {
			if (i!=0)
				str.append(',');
			str.append(stack.get(i).addr());
		}
		str.append('}');
		return str.toString();    		
	}
}

