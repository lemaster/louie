/* 
 * Copyright 2015 Rhythm & Hues Studios.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.rhythm.louie.util;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.slf4j.LoggerFactory;

/**
 *
 * @author cjohnson
 * @param <E>
 */
public class FutureList<E> implements Collection<E>, List<E> {
    private final List<ListenableFuture<E>> futures;
    
    public FutureList() {
        futures = new ArrayList<>();
    }
    
    public FutureList(List<? extends ListenableFuture<E>> futures) {
        this();
        this.futures.addAll(futures);
    }

    @Override
    public int size() {
        return futures.size();
    }

    @Override
    public boolean isEmpty() {
        return futures.isEmpty();
    }

    @Override
    public E get(int index) {
        try {
            return futures.get(index).get();
        } catch (InterruptedException | ExecutionException ex) {
            LoggerFactory.getLogger(FutureList.class).error("Error getting item", ex);
        }
        return null;
    }
    
    @Override
    public E remove(int index) {
        Future<E> current = futures.remove(index);
        if (current!=null&&current.isDone()) {
            try {
                return current.get();
            } catch (InterruptedException | ExecutionException e) {}
        }
        return null;
    }
    
    @Override
    public Iterator<E> iterator() {
        return new CompletedItr();
    }

    /**
     * An iterator that returns the items in their original order, but blocks if necessary
     */
    private class CompletedItr implements Iterator<E> {
        Iterator<ListenableFuture<E>> futureIter;
        public CompletedItr() {
            futureIter = Futures.inCompletionOrder(futures).iterator();
        }
        
        @Override
        public boolean hasNext() {
            return futureIter.hasNext();
        }

        @Override
        public E next() {
            try {
                return futureIter.next().get();
            } catch (InterruptedException | ExecutionException ex) {
                LoggerFactory.getLogger(FutureList.class).error("Error iterating", ex);
            }
            return null;
        }

        @Override
        public void remove() {
            futureIter.remove();
        }
    }
    
    /**
     * Returns items that are completed first
     */
    private class ExpermimentalCompletedItr implements Iterator<E> {
        LinkedList<Future<E>> processing;
        public ExpermimentalCompletedItr() {
            processing = new LinkedList<Future<E>>(futures);
        }
        
        @Override
        public boolean hasNext() {
            return !processing.isEmpty();
        }

        @Override
        public E next() {
            if (processing.isEmpty()) {
                throw new NoSuchElementException();
            }
            try {
                while (true) {
                    Iterator<Future<E>> pIter = processing.iterator();
                    while (pIter.hasNext()) {
                        Future<E> item = pIter.next();
                        if (item.isDone()) {
                            pIter.remove();
                            return item.get();
                        }
                    }
                    Thread.sleep(10);
                }
            } catch (InterruptedException | ExecutionException ex) {
                LoggerFactory.getLogger(FutureList.class).error("Error iterating", ex);
            }
            return null;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Remove items from a FutureList is not supported");
        }
    }

    @Override
    public boolean add(E e) {
        return futures.add(Futures.immediateFuture(e));
    }
    
    public boolean addFuture(ListenableFuture<E> futureItem) {
        return futures.add(futureItem);
    }

    @Override
    public E set(int index, E element) {
        futures.set(index, Futures.immediateFuture(element));
        // not sure what to return here
        return element;
    }

    @Override
    public void add(int index, E element) {
        futures.add(index, Futures.immediateFuture(element));
    }
    
    @Override
    public boolean addAll(Collection<? extends E> c) {
        if (c.isEmpty()) {
            return false;
        }
        for (E item : c) {
            add(item);
        }
        return true;
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        if (index < 0 || index > size())
            throw new IndexOutOfBoundsException("Index "+index+" is out of bounds");
        if (c.isEmpty()) {
            return false;
        }
        for (E e : c) {
            add(index++, e);
        }
        return true;
    }

    @Override
    public void clear() {
        futures.clear();
    }
    
    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> T[] toArray(T[] a) {
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
    public boolean remove(Object o) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean containsAll(Collection c) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    @Override
    public boolean equals(Object o) {
        return this==o;
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int indexOf(Object o) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int lastIndexOf(Object o) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ListIterator<E> listIterator() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
  
}
