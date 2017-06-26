package axoloti.mvc.array;

import axoloti.mvc.AbstractModel;
import axoloti.mvc.IModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 *
 * @author jtaelman
 */
public class ArrayModel<T extends IModel> extends AbstractModel implements List<T>, Iterable<T> {

    ArrayList<T> array = new ArrayList<>();

    public ArrayList<T> getArray() {
        return array;
    }

    public void setArray(ArrayList<T> array) {
        ArrayList<T> old_array = this.array;
        this.array = array;
        firePropertyChange(
                ArrayController.ARRAY,
                old_array, array);
    }

    @Override
    public int size() {
        return array.size();
    }

    @Override
    public boolean isEmpty() {
        return array.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return array.contains(o);
    }

    @Override
    public Iterator iterator() {
        return array.iterator();
    }

    @Override
    public Object[] toArray() {
        return array.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean add(T e) {
        if (array.contains(e)) {
            System.out.println("model already added : " + e.toString());
            return false;
        }
        ArrayList<T> old_array = this.array;
        array = (ArrayList<T>) array.clone();
        boolean r = array.add(e);
        firePropertyChange(
                ArrayController.ARRAY,
                old_array, array);
        return r;
    }

    @Override
    public boolean remove(Object o) {
        ArrayList<T> old_array = this.array;
        array = (ArrayList<T>) array.clone();
        boolean r = array.remove(o);
        firePropertyChange(
                ArrayController.ARRAY,
                old_array, array);
        return r;
    }

    @Override
    public boolean containsAll(Collection c) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean addAll(Collection c) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean addAll(int index, Collection c) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean removeAll(Collection c) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean retainAll(Collection c) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void clear() {
        array.clear();
    }

    @Override
    public T get(int index) {
        return array.get(index);
    }

    @Override
    public T set(int index, T element) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void add(int index, T element) {
        array.add(index, element);
    }

    @Override
    public T remove(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int indexOf(Object o) {
        return array.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ListIterator listIterator() {
        return array.listIterator();
    }

    @Override
    public ListIterator listIterator(int index) {
        return array.listIterator(index);
    }

    @Override
    public List subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}