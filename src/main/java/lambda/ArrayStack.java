package lambda;

import java.util.ArrayList;

public class ArrayStack <T> extends ArrayList <T> {
	void push (T elem) {
		add(elem);
	}
	T pop () {
		return remove (size()-1);
	}
	T peek () {
		return get(size()-1);
	}
}
