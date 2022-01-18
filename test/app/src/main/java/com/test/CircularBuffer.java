package com.test;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;

/**
 * Created by yedek_pc on 15.08.2017.
 */

public class CircularBuffer<T> {

    private T[] buffer;

    private int tail;

    private int head;

    @SuppressWarnings("unchecked")
    public CircularBuffer(int n) {
        buffer = (T[]) new Object[n];
        tail = 0;
        head = 0;
    }

    public void add(T toAdd) {
        if (head != (tail - 1)) {
            buffer[head++] = toAdd;
        } else {
            throw new BufferOverflowException();
        }
        head = head % buffer.length;
    }

    public T get() {
        T t = null;
        int adjTail = tail > head ? tail - buffer.length : tail;
        if (adjTail < head) {
            t = (T) buffer[tail++];
            tail = tail % buffer.length;
        } else {
            throw new BufferUnderflowException();
        }
        return t;
    }

    public String toString() {
        return "CircularBuffer(size=" + buffer.length + ", head=" + head + ", tail=" + tail + ")";
    }


}