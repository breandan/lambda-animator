package lambda;

import java.util.HashMap;

import lambda.Heap.Addr;


public class Env {
	public HashMap <String,Addr> map;
	public void bind (String name, Addr addr) {
		map.put(name, addr);
	}
	public Addr lookup (String name) {
		return map.get(name);
	}
	Env (Env env) {
		map = new HashMap <String,Addr> (env.map);
		
	}
	Env () {
		map = new HashMap <String,Addr> ();
	}
}
