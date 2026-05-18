package com.example.phm.sensor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class SensorBuffer {

    private final int capacity;
    private final ArrayDeque<SensorFrame> deque;

    public SensorBuffer(int capacity) {
        this.capacity = capacity;
        this.deque = new ArrayDeque<>(capacity);
    }

    public synchronized void push(SensorFrame frame) {
        if (deque.size() >= capacity) {
            deque.pollFirst();
        }
        deque.addLast(frame);
    }

    public synchronized List<SensorFrame> snapshot() {
        return new ArrayList<>(deque);
    }

    public synchronized SensorFrame latest() {
        return deque.peekLast();
    }

    public synchronized int size() {
        return deque.size();
    }

    public int capacity() {
        return capacity;
    }
}
