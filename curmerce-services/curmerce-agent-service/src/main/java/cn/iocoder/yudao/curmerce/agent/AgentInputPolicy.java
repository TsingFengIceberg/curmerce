package cn.iocoder.yudao.curmerce.agent;

import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

@Component
public class AgentInputPolicy {
    private static final Pattern INJECTION = Pattern.compile("(?i)(ignore|disregard)\\s+(all\\s+)?previous|system\\s+prompt|developer\\s+message|reveal\\s+(the\\s+)?token|bypass\\s+(the\\s+)?confirm|reveal\\s+(the\\s+)?password|忽略(所有)?(之前|上面|系统)指令|无视(之前|上面)的?指令|泄露(系统)?(提示词|令牌|密码)|显示(系统)?提示词|开发者消息|绕过(安全|用户)?确认");
    private static final Pattern STRUCTURED_ATTACK = Pattern.compile("(?i)(;\\s*(drop|delete|update|insert|alter|truncate)\\b|\\bunion\\s+select\\b|--\\s*$)");
    private static final Pattern CROSS_USER_ORDER = Pattern.compile("(?i)(other\\s+(users?|people|person)['’]?s?\\s+orders?|someone\\s+else['’]?s?\\s+orders?|其他人.*订单|别人.*订单|他人.*订单)");
    private static final Pattern CONFIRMATION_BYPASS = Pattern.compile("(?i)(绕过.*确认|跳过.*确认|无需.*确认|bypass.*confirm|不要确认直接(退款|操作)|直接执行退款)");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(Bearer\\s+|authorization\\s*[:=]\\s*|api[_-]?key\\s*[:=]\\s*|password\\s*[:=]\\s*|secret\\s*[:=]\\s*|token\\s*[:=]\\s*)[^\\s,;]+"
    );
    public String sanitize(String query) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("Agent 查询不能为空");
        String normalized = query.trim();
        if (normalized.length() > 8000) throw new IllegalArgumentException("Agent 查询过长");
        if (INJECTION.matcher(normalized).find() || STRUCTURED_ATTACK.matcher(normalized).find()
                || CROSS_USER_ORDER.matcher(normalized).find() || CONFIRMATION_BYPASS.matcher(normalized).find()) {
            throw new IllegalArgumentException("查询包含不允许的指令");
        }
        return redactSecrets(normalized);
    }

    /** Redacts values that must never enter model context, audit, or feedback. */
    public static String redactSecrets(String value) {
        if (value == null || value.isBlank()) return value == null ? "" : value;
        return SECRET.matcher(value).replaceAll("$1[REDACTED]");
    }
}
