package damjay.floating.projects.models;

import java.util.HashMap;
import java.util.Map;

public class HttpPacket {
    private String method;
    private String url;
    private Map<String, String> headers;
    private String body;
    private long timestamp;

    public HttpPacket() {
        this.headers = new HashMap<>();
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and setters
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    public Map<String, String> getHeaders() { return headers; }
    public void addHeader(String key, String value) { headers.put(key, value); }
    
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    
    public long getTimestamp() { return timestamp; }

    public String toCurl() {
        StringBuilder curl = new StringBuilder("curl");
        
        // Add method if not GET
        if (!"GET".equals(method)) {
            curl.append(" -X ").append(method);
        }
        
        // Add headers
        for (Map.Entry<String, String> header : headers.entrySet()) {
            curl.append(" -H '").append(header.getKey())
                .append(": ").append(header.getValue()).append("'");
        }
        
        // Add body for POST/PUT/PATCH
        if (body != null && !body.isEmpty() && 
            ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))) {
            curl.append(" -d '").append(body).append("'");
        }
        
        // Add URL
        curl.append(" '").append(url).append("'");
        
        return curl.toString();
    }
} 