package cn.iocoder.yudao.curmerce.agent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Flags factual-looking model claims that are absent from retrieved evidence. */
@Component
public class AgentGroundingValidator {
    private static final Pattern MONEY = Pattern.compile("(?:¥|￥)\\s?\\d+(?:\\.\\d{1,2})?");
    private static final Pattern ORDER_ID = Pattern.compile("订单(?:号|编号|ID)?\\s*[:：#]?\\s*\\d+");
    private static final Pattern INVENTORY = Pattern.compile("(?:库存|剩余|可售)(?:数量)?\\s*(?:为|是|[:：])?\\s*\\d+\\s*(?:件|个|份)?");

    public List<String> validate(String answer, String context) {
        if (answer == null || answer.isBlank()) return List.of("模型没有返回可验证的回答");
        String safeContext = context == null ? "" : context;
        List<String> warnings = new ArrayList<>();
        var money = MONEY.matcher(answer);
        while (money.find() && !safeContext.contains(money.group())) warnings.add("回答中的金额未在检索上下文中找到");
        var order = ORDER_ID.matcher(answer);
        while (order.find() && !safeContext.contains(order.group())) warnings.add("回答中的订单编号未在工具结果中找到");
        var inventory = INVENTORY.matcher(answer);
        while (inventory.find() && !normalizesToContext(inventory.group(), safeContext)) {
            warnings.add("回答中的库存或可售数量未在检索上下文中找到");
        }
        return warnings.stream().distinct().toList();
    }

    private static boolean normalizesToContext(String claim, String context) {
        String normalizedClaim = claim.replaceAll("\\s+", "").replaceAll("(?:为|是|[:：])", "");
        String normalizedContext = context.replaceAll("\\s+", "").replaceAll("(?:为|是|[:：])", "");
        if (normalizedContext.contains(normalizedClaim)) return true;
        java.util.regex.Matcher number = Pattern.compile("\\d+").matcher(normalizedClaim);
        if (!number.find()) return false;
        String quantity = number.group();
        return (normalizedClaim.startsWith("库存") && normalizedContext.matches("(?s).*库存(?:数量)?" + quantity + "(?:件|个|份)?.*"))
                || (normalizedClaim.startsWith("剩余") && normalizedContext.matches("(?s).*剩余" + quantity + "(?:件|个|份)?.*"))
                || (normalizedClaim.startsWith("可售") && normalizedContext.matches("(?s).*可售(?:数量)?" + quantity + "(?:件|个|份)?.*"));
    }
}
