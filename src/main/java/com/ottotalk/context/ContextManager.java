package com.ottotalk.context;

import com.ottotalk.OttoTalkClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ContextManager {
    private final ConcurrentLinkedQueue<ContextMessage> contextMessages = new ConcurrentLinkedQueue<>();
    
    public void addToContext(String message, String sender) {
        ContextMessage contextMsg = new ContextMessage(message, sender, System.currentTimeMillis());
        contextMessages.offer(contextMsg);
        
        // nur die neuesten messages behalten, anzahl kommt aus der config
        int maxMessages = OttoTalkClient.getConfig().maxContextMessages;
        while (contextMessages.size() > maxMessages) {
            contextMessages.poll();
        }
        
    }
    
    public void removeFromContext(String message) {
        contextMessages.removeIf(msg -> msg.getMessage().equals(message));
    }
    
    public String getContextAsString() {
        StringBuilder context = new StringBuilder();
        for (ContextMessage msg : contextMessages) {
            context.append(msg.getSender()).append(": ").append(msg.getMessage()).append("\n");
        }
        return context.toString().trim();
    }
    
    public List<ContextMessage> getContextMessages() {
        return new ArrayList<>(contextMessages);
    }
    
    public void clearContext() {
        contextMessages.clear();
    }
    
    public boolean isEmpty() {
        return contextMessages.isEmpty();
    }
    
    public int size() {
        return contextMessages.size();
    }
    
    public static class ContextMessage {
        private final String message;
        private final String sender;
        private final long timestamp;
        
        public ContextMessage(String message, String sender, long timestamp) {
            this.message = message;
            this.sender = sender;
            this.timestamp = timestamp;
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getSender() {
            return sender;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        @Override
        public String toString() {
            return sender + ": " + message;
        }
    }
}
