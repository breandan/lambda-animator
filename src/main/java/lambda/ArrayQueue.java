package lambda;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class ArrayQueue <E> extends AbstractQueue <E> {
	E [] data = (E[]) new Object [10];
	int offset = 0;
	int size = 0;
	
	public boolean offer(E elem) {
		if (elem==null)
			throw new NullPointerException();
		if (size+offset==data.length) {
			if (offset >= data.length-offset) {
				for (int i=0; i!=size; i++) {
					data[i] = data[i+offset];
				}
				offset = 0;
			}
			else {
				E [] newArray = (E[]) new Object [2*data.length];
				for (int i=0; i!=size; i++) {
					newArray[i] = data[i+offset];
				}
				offset = 0;
				data = newArray;
			}
		}
		data[size+offset] = elem;
		size ++;
		return true;
	}
	public E peek() {
		if (size==0) 
			return null;
		E elem = data [offset];
		return elem;
	}
	public E poll() {
		if (size == 0)
			return null;
		E elem = data [offset];
		offset ++;
		size --;
		return elem;
	}
	public Iterator<E> iterator() {
		return new ArrayQueueIterator ();
	}

	public int size () {
		return size;
	}
	
	class ArrayQueueIterator implements Iterator <E> {
		int pos=0;
		public boolean hasNext() {
			return pos < size;
		}
		public E next() {
			if (pos < size) {
				E result = data[pos + offset];
				pos ++;
				return result;
			}
			else
				throw new NoSuchElementException ();
		}
		public void remove() {
			throw new UnsupportedOperationException ();
		}
	}
	


//	public void add (T elem) {
//	if (size+offset==data.length) {
//		if (offset >= data.length-offset) {
//			for (int i=0; i!=size; i++) {
//				data[i] = data[i+offset];
//			}
//			offset = 0;
//		}
//		else {
//			T [] newArray = (T[]) new Object [2*data.length];
//			for (int i=0; i!=size; i++) {
//				newArray[i] = data[i+offset];
//			}
//			offset = 0;
//			data = newArray;
//		}
//	}
//	data[size+offset] = elem;
//	size ++;
//}
//public T remove () {
//	if (size == 0) {
//		throw new NoSuchElementException ();
//	}
//	T elem = data [offset];
//	offset ++;
//	size --;
//	return elem;
//}
//public boolean isEmpty () {
//	return size==0;
//}

}
