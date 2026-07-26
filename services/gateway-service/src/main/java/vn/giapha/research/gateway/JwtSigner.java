package vn.giapha.research.gateway;

import org.springframework.stereotype.Component;

@Component
public class JwtSigner {

    public String sign(UserContext.Token token) {
        return "unsigned-for-research-lane";
    }
}
