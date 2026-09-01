package cn.iocoder.yudao.curmerce.search;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/app-api/search")
public class SearchController {
    private final SearchProjectionService projectionService;
    private final SearchProperties properties;

    public SearchController(SearchProjectionService projectionService, SearchProperties properties) {
        this.projectionService = projectionService;
        this.properties = properties;
    }

    @GetMapping("/products")
    public CommonResult<ElasticsearchIndexClient.SearchPage> products(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return success(projectionService.searchProducts(keyword, pageNo, pageSize));
    }

    @GetMapping("/posts")
    public CommonResult<ElasticsearchIndexClient.SearchPage> posts(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return success(projectionService.searchPosts(keyword, pageNo, pageSize));
    }

    @PostMapping("/reconcile")
    public CommonResult<SearchProjectionService.ProjectionReconciliationReport> reconcile(
            @RequestHeader(value = "X-Curmerce-Search-Token", required = false) String token,
            HttpServletRequest request) {
        authorizeRebuild(token, request);
        return success(projectionService.reconcile());
    }

    @PostMapping("/rebuild/all")
    public CommonResult<SearchProjectionService.RebuildReport> rebuildAll(
            @RequestHeader(value = "X-Curmerce-Search-Token", required = false) String token,
            HttpServletRequest request) {
        authorizeRebuild(token, request);
        return success(projectionService.rebuildAll());
    }

    @PostMapping("/rebuild/products")
    public CommonResult<SearchProjectionService.RebuildReport> rebuildProducts(
            @RequestHeader(value = "X-Curmerce-Search-Token", required = false) String token,
            HttpServletRequest request) {
        authorizeRebuild(token, request);
        return success(projectionService.rebuildProducts());
    }

    @PostMapping("/rebuild/posts")
    public CommonResult<SearchProjectionService.RebuildReport> rebuildPosts(
            @RequestHeader(value = "X-Curmerce-Search-Token", required = false) String token,
            HttpServletRequest request) {
        authorizeRebuild(token, request);
        return success(projectionService.rebuildPosts());
    }

    private void authorizeRebuild(String token, HttpServletRequest request) {
        String expected = properties.rebuildToken();
        if (expected.isBlank() && (request.getRemoteAddr() == null ||
                !(request.getRemoteAddr().equals("127.0.0.1") || request.getRemoteAddr().equals("0:0:0:0:0:0:0:1")))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Search rebuild is local-only");
        }
        if (!expected.isBlank() && (token == null || !java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                token.getBytes(java.nio.charset.StandardCharsets.UTF_8)))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid search rebuild token");
        }
    }
}
