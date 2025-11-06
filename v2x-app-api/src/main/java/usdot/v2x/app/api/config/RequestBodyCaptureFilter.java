package usdot.v2x.app.api.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;

@Component
@Order(1)
@Slf4j
public class RequestBodyCaptureFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;

            // Only capture body for POST/PUT requests to API endpoints
            if (isApiRequest(httpRequest) && isPostOrPutRequest(httpRequest)) {
                CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(httpRequest);
                chain.doFilter(cachedRequest, response);
            } else {
                chain.doFilter(request, response);
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        return requestPath != null && (requestPath.startsWith("/prd/") ||
                requestPath.startsWith("/api/") ||
                requestPath.startsWith("/auth/"));
    }

    private boolean isPostOrPutRequest(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method);
    }

    public static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private byte[] cachedBody;

        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            // Cache the request body
            InputStream inputStream = request.getInputStream();
            this.cachedBody = inputStream.readAllBytes();
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new ServletInputStream() {
                private final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cachedBody);

                @Override
                public boolean isFinished() {
                    return byteArrayInputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // Not implemented for this use case
                }

                @Override
                public int read() throws IOException {
                    return byteArrayInputStream.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        public String getRequestBody() {
            if (cachedBody != null) {
                return new String(cachedBody, StandardCharsets.UTF_8);
            }
            return null;
        }
    }

    /**
     * Static method to get request body from any HttpServletRequest
     */
    public static String getRequestBody(HttpServletRequest request) {
        if (request instanceof CachedBodyHttpServletRequest) {
            return ((CachedBodyHttpServletRequest) request).getRequestBody();
        }
        return null;
    }
}
