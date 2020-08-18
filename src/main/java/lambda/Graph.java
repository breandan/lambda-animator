package lambda;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import lambda.Heap.Addr;
import lambda.Heap.Context;
import att.grappa.Edge;
import att.grappa.GrappaSupport;
import att.grappa.Node;
import att.grappa.Subgraph;

public class Graph {
	
	static class HeapEdge {
		final Addr from;
		final int route;
		final Addr to;
		public HeapEdge(final Addr from, final int route, final Addr to) {
			super();
			this.from = from;
			this.route = route;
			this.to = to;
		}
		public boolean equals(Object obj) {
			HeapEdge lhs = this;
			HeapEdge rhs = (HeapEdge) obj;
			return lhs.from.ref().id == rhs.from.ref().id
				   && lhs.to.ref().id == rhs.to.ref().id
				   && lhs.route == rhs.route;
		}
		public int hashCode() {
			int hash = 0;
			hash += from==null ? 0 : from.ref().id;
			hash *= 31;
			hash += to==null ? 0 : to.ref().id;
			hash *= 31;
			hash += route;
			return hash;
		}
	}
	
	static String showStrategy (Eval.Strategy strategy) {
		switch (strategy.subStrategy) {
		case NAME: return "name";
		case VALUE:
			switch (strategy.form) {
			case HYPER_STRICT: return "hs";
			case WHNF: return "whnf";
			case WNF: return "wnf";
			case HNF: return "hnf";
			case NF: return "nf";
			}
		case NEED: return "need";
		}
	throw new Error ();
	}
	
	static String showApply (Heap.Apply apply) {
		return String.format("%s @ %s", showStrategy(apply.funcStrategy), showStrategy(apply.argStrategy));
	}

	static String showValue (Object v) {
		if (v instanceof SExp)
			return "'"+v.toString();
//		else if (v instanceof Boolean) {
//			if ((Boolean)v) 
//				return "#t";
//			else
//				return "#f";
//		}
		else if (v instanceof String) {
			return String.format("\"%s\"", v.toString());
		}
		else
			return v.toString();
	}
	
	static String showNode (Heap.Node node) {
		int depth = node.context.depth;
		switch (node.tag) {
		case VAR: return String.format("_%d", depth);
		case LAMBDA: return String.format("\\%d", depth+1);
		case APPLY: return showApply (node.asApply());
		case PAIR: return "cons";
		case VALUE: return showValue(node.asValue().value);
		case DELTA: return node.asDelta().prim.name;
		//case SUBST: return String.format("%d->, %d",node.asSubst().common.bind,node.asSubst().common.shift);
		case SUBST: return String.format("%d->",node.asSubst().common.bind);
		default: throw new Error();
		}
	}
	
	
	
	static void buildGraph (att.grappa.Graph graph, Addr root, Stack stack, int maxNodes) throws IOException {
		graph.reset();
		BuildGraph bg = new BuildGraph (graph, root, stack);
		bg.build(maxNodes);
		bg.layoutGraph();
//		try {
//			Writer writer = new FileWriter ("graph.dot");
//			graph.printGraph(writer);
//			writer.close();
//		} catch (IOException exc) {
//
//		}
	}
	
	static class BuildGraph {
		att.grappa.Graph graph;
		HashMap <Addr,Node> nodes = new HashMap <Addr,Node>();
		HashMap <HeapEdge, Edge> edges = new HashMap <HeapEdge,att.grappa.Edge>();
		HashMap <Addr,Integer> ranks = new HashMap <Addr,Integer> ();
		HashMap <Addr,att.grappa.Subgraph> subgraphs = new HashMap <Addr,att.grappa.Subgraph> ();
		HashSet <Addr> keptNodes = new HashSet <Addr> ();
		HashSet <Addr> fringeNodes = new HashSet <Addr> ();
		
		final Addr root;
		final Stack stack;
		private int uniqueNodeId=0;
		
		BuildGraph (att.grappa.Graph graph, Addr root, Stack stack) {
			this.root = root;
			this.stack = stack;
			this.graph = graph;
		}
		
		void build (int maxNodes) {
			pruneGraph (root, stack, maxNodes, keptNodes, fringeNodes);
			ranks = calcNodeRanks (root);
			
			Node rootNode = new Node(graph, "root");
			rootNode.setAttribute("label", showNode(root.deref()));
			nodes.put(root.ref(), rootNode);
			
			buildFromRoot();
			buildDisjoint();
			
			highlightStack(stack);
		}
		
		Node getNode (Addr parent, Addr addr) {
			Context parentContext = parent==null ? null : parent.deref().context;
			addr = addr.ref();
			Node gNode = nodes.get(addr);
			if (gNode != null)
				return gNode;
			Heap.Node hNode = addr.deref();
			if (fringeNodes.contains(addr)) {
				Subgraph subgraph = getSubgraph (parentContext);
				uniqueNodeId++;
				gNode = new Node (subgraph, "u"+uniqueNodeId);
				gNode.setAttribute("shape", "point");
				gNode.setAttribute("label", "");
			}
			else if (hNode.isVar() && parent!=null && !parent.deref().isLambda()) {
				Subgraph subgraph = getSubgraph (parentContext);
				uniqueNodeId++;
				gNode = new Node (subgraph, "u"+uniqueNodeId);
				gNode.setAttribute("label", showNode(hNode));
				
			}
			else {
				Subgraph subgraph = getSubgraph (hNode.context);
				gNode = new Node (subgraph, "a"+addr.id);
				gNode.setAttribute("label", showNode(hNode));
				nodes.put(addr, gNode);
			}
			return gNode;
		}
		
		Subgraph getSubgraph (Context context) {
			if (context.depth == 0)
				return graph;
			Subgraph subgraph = subgraphs.get(context.lambda);
			if (subgraph != null)
				return subgraph;
			String name = String.format("cluster%d", context.lambda.id);
			subgraph = new Subgraph (getSubgraph(context.context), name);
			//subgraph.setAttribute("label", Integer.toString(context.depth+1));
			subgraph.setAttribute("pad", "1,1");
			subgraph.setAttribute("margin", "1,1");
			subgraph.setAttribute("style", "setlinewidth(3)");
			subgraphs.put(context.lambda, subgraph);
			return subgraph;
		}
		
		boolean isBackEdge (Addr a, Addr b) {
			Integer rankA = ranks.get(a.ref());
			Integer rankB = ranks.get(b.ref());
			if (rankA==null && rankB!=null)
				return true;
			if (rankA==null || rankB==null)
				return false;
			return rankA > rankB;
		}
		
		String getOutputPort (Heap.Node node, int arity, int i) {
			if ((arity==2 || arity==3) && i==0)
				return "sw";
			if ((arity==2 || arity==3) && i==arity-1)
				return "se";
			if ((arity==3 && i==1) || arity==1)
				return "s";
			if (arity==4) 
				switch (i) {
				case 0: return "w";
				case 1: return "sw";
				case 2: return "s";
				case 3: return "se";
				}
			return null;
		}
		
		void buildDisjoint () {
			for (Addr addr : keptNodes) {
				addr = addr.ref();
				if (nodes.containsKey(addr) || addr.deref().isVar())
					continue;
				Node node = getNode (null, addr);
				node.setAttribute("color", "red");
				Addr [] children = addr.deref().children();
				for (int i=0; i!=children.length; i++) {
					Addr child = children[i];
					Node childNode = getNode (addr, child);
					Edge edge = new Edge (graph, node, childNode);
					edges.put(new HeapEdge (addr, i, child), edge);
				}
			}			
		}
		
		void buildFromRoot () {
			ArrayQueue <Addr> todo = new ArrayQueue <Addr> ();
			HashSet <Addr> done = new HashSet <Addr> ();
			todo.add(root);
			while (!todo.isEmpty()) {
				Addr addr = todo.remove().ref();
				if (fringeNodes.contains(addr) || done.contains(addr))
					continue;
				done.add(addr);
				Heap.Node hNode = addr.deref();
				Addr [] children = hNode.children();
				int numChildren= children.length;
				if (numChildren==0)
					continue;
				Node gNode = getNode (null, addr);
				for (int i=0; i!=numChildren; i++) {
					Addr child = children[i];
					todo.add(child);
					Node gChild = getNode (addr, child);
					Edge edge;
					String port;
					if (isBackEdge (addr, child)) {
						edge = new Edge (graph, gChild, gNode);
						edge.setAttribute("dir", "back");
						//edge.setAttribute("color","red");
						port = "headport";
					}
					else {
						edge = new Edge (graph, gNode, gChild);
						port = "tailport";
					}
					String portDir = getOutputPort (hNode, numChildren, i);
					if (portDir != null) {
						edge.setAttribute(port, portDir);
					}
					edges.put(new HeapEdge(addr, i, child), edge);
				}
			}
		}
		
		static void pruneGraph ( Addr root, Stack stack, int maxNodes, // inputs
				HashSet<Addr> keep, HashSet<Addr> fringe // outputs
		) 
		{
			keep.clear();
			fringe.clear();

			ArrayQueue<Addr> todo = new ArrayQueue<Addr>();

			todo.add(root.ref());
			//for (Stack.Entry se : stack.stack) {
			for (int i=0; i!=stack.size(); i++) {
				Stack.Entry se = stack.get(i);
				todo.add(se.addr.ref());
			}

			while (todo.size()!=0 && keep.size() < maxNodes) {
				Addr addr = todo.remove().ref();
				if (keep.contains(addr))
					continue;
				keep.add(addr);
				for (Addr child : addr.deref().children()) {
					todo.add(child.ref());
				}
			}

			fringe.addAll(todo);
			fringe.removeAll(keep);
		}
		
		void rankNodes (HashMap<Addr,Integer> ranks, int rankLimit, HashSet <Addr> stack, int rank, Addr addr) {
			addr = addr.ref();
			if (rank > rankLimit || fringeNodes.contains(addr) || stack.contains(addr)) 
				return;
			
			stack.add(addr);
			if (!ranks.containsKey(addr) || rank > ranks.get(addr))
				ranks.put(addr, rank);
			for (Addr child : addr.deref().children()) {
				rankNodes (ranks, rankLimit, stack, rank+1, child);
			}
			stack.remove(addr);
		}
		
		void unrankPartiallyRankedNodes (HashMap<Addr,Integer> ranks, int rankLimit, HashSet <Addr> stack, int rank, Addr addr) {
			addr = addr.ref();
			if (fringeNodes.contains(addr) || stack.contains(addr)) 
				return;
			
			if (rank > rankLimit) {
				unrankNodes (ranks, addr);
			}
			
			stack.add(addr);
			for (Addr child : addr.deref().children()) {
				unrankPartiallyRankedNodes (ranks, rankLimit, stack, rank+1, child);
			}
			stack.remove(addr);
		}

		void unrankNodes (HashMap<Addr,Integer> ranks, Addr addr) {
			addr = addr.ref();
			if (!ranks.containsKey(addr)) 
				return;
			
			ranks.remove(addr);
			
			for (Addr child : addr.deref().children()) {
				unrankNodes (ranks, child);
			}
		}
		
		HashMap <Addr,Integer> calcNodeRanks (Addr addr) {
			HashMap <Addr,Integer> ranks = new HashMap <Addr,Integer> ();
			HashSet <Addr> stack = new HashSet <Addr> ();
			rankNodes (ranks, 10, stack, 0, addr);
			unrankPartiallyRankedNodes (ranks, 10, stack, 0, addr);
			return ranks;
		}
		
		void highlightStack (Stack stack) {
			Addr prev=null;
			//for (Stack.Entry se : stack.stack) {
			for (int i=0; i!=stack.size(); i++) {
				Stack.Entry se = stack.get(i);
				Node node = nodes.get(se.addr.ref());
				if (node==null)
					continue;
				node.setAttribute("style", "setlinewidth(3)");
				if (prev!=null) {
					Edge edge = edges.get(new HeapEdge (prev, se.route, se.addr));
					if (edge!=null)
						edge.setAttribute("style", "setlinewidth(3)");
				}
				prev = se.addr;
			}
		}
		
		void layoutGraph () throws IOException {
			graph.setNodeAttribute("fontname", "Helvetica");
			//String [] dotArgs = {"dot", "-Nfontname=Helvetica"};
			String [] dotArgs = {"dot"};
			Process dotProcess = Runtime.getRuntime().exec(dotArgs, null, null);
			GrappaSupport.filterGraph(graph, dotProcess);
			dotProcess.getOutputStream().close();
			
			InputStream errorStream = dotProcess.getErrorStream();
			int ch;
			while ((ch = errorStream.read()) != -1) {
				System.err.write(ch);
			}
			errorStream.close();

//			URLConnection urlConn = (new URL("http://www.research.att.com/~john/cgi-bin/format-graph")).openConnection();
//			urlConn.setDoInput(true);
//			urlConn.setDoOutput(true);
//			urlConn.setUseCaches(false);
//			urlConn.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
//			GrappaSupport.filterGraph(graph, urlConn);

		}
	}
}
