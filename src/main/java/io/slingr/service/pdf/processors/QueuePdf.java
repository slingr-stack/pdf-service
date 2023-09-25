package io.slingr.service.pdf.processors;

import io.slingr.services.ws.exchange.FunctionRequest;

import java.util.LinkedList;
import java.util.Queue;

public class QueuePdf {

    static final Queue<FunctionRequest> queue = new LinkedList<>();
    private static QueuePdf queueInstance = null;

    public static QueuePdf getStreamInstance() {
        if (queueInstance == null) {
            queueInstance = new QueuePdf();
        }
        return queueInstance;
    }

    public Queue<FunctionRequest> get() {
        return queue;
    }

    // Inserts the specified element into this queue if it is possible to do so
    // immediately without violating capacity restrictions
    public void add(FunctionRequest value) {
        synchronized (queue) {
            queue.add(value);
        }
    }

    // Retrieves and removes the head of this queue, or returns null if this
    // queue is empty.
    public FunctionRequest poll() {
        return queue.poll();
    }

    // Returns true if this collection contains no elements
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    // Returns the number of elements in this collection. If this collection
    // contains more than Integer.MAX_VALUE elements, returns Integer.MAX_VALUE
    public int getTotalSize() {
        return queue.size();
    }
}
