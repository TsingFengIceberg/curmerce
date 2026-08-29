package cn.iocoder.yudao.curmerce.auction;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Collections;

@RestController
public class AuctionProxyController {
    private final AuctionCoreProxy coreProxy;

    public AuctionProxyController(AuctionCoreProxy coreProxy) {
        this.coreProxy = coreProxy;
    }

    @RequestMapping({"/app-api/commerce/auction/**", "/admin-api/commerce/auction/**"})
    public ResponseEntity<byte[]> forward(HttpServletRequest request)
            throws IOException {
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        HttpHeaders headers = new HttpHeaders();
        Collections.list(request.getHeaderNames()).forEach(name -> headers.add(name, request.getHeader(name)));
        return coreProxy.forward(method, request.getRequestURI(), request.getQueryString(),
                headers, request.getInputStream().readAllBytes());
    }
}
