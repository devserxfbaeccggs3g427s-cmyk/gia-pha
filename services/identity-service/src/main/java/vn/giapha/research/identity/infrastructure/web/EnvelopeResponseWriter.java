package vn.giapha.research.identity.infrastructure.web;

import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import vn.giapha.research.identity.infrastructure.kernel.web.ApiFailure;

@Component
public class EnvelopeResponseWriter {
    private final JsonMapper json;

    public EnvelopeResponseWriter(JsonMapper json) {
        this.json = json;
    }

    public void write(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.resetBuffer();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        response.getWriter().write(json.writeValueAsString(ApiFailure.of(code, message)));
    }
}
