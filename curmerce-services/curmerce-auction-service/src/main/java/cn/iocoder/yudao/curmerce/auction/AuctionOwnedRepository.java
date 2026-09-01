package cn.iocoder.yudao.curmerce.auction;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@ConditionalOnProperty(prefix = "curmerce.auction", name = "local-store-enabled", havingValue = "true")
public class AuctionOwnedRepository {
    private final JdbcTemplate jdbc;

    public AuctionOwnedRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String COLUMNS = "id, merchant_id, store_id, product_id, sku_id, name, status, starting_price, min_increment, start_time, end_time, winner_user_id, winning_bid_id, settlement_order_id, settlement_failed_time, settlement_failure_reason, product_name, product_image_url, sku_label, original_price";

    public AuctionSessionRow findSession(Long id, boolean forUpdate) {
        List<AuctionSessionRow> rows = jdbc.query("SELECT " + COLUMNS + " FROM auction_session WHERE id=? AND deleted=0" + (forUpdate ? " FOR UPDATE" : ""), this::mapSession, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<AuctionSessionRow> page(String keyword, int offset, int limit, boolean ownerOnly, Long merchantId) {
        StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS).append(" FROM auction_session WHERE deleted=0");
        List<Object> args = new ArrayList<>();
        if (ownerOnly) { sql.append(" AND merchant_id=?"); args.add(merchantId); }
        else { sql.append(" AND status IN (10,20,30,50) AND start_time <= CURRENT_TIMESTAMP AND end_time >= CURRENT_TIMESTAMP"); }
        if (keyword != null && !keyword.isBlank()) { sql.append(" AND name LIKE ?"); args.add("%" + keyword.trim() + "%"); }
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?"); args.add(limit); args.add(offset);
        return jdbc.query(sql.toString(), args.toArray(), this::mapSession);
    }

    public long count(String keyword, boolean ownerOnly, Long merchantId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM auction_session WHERE deleted=0");
        List<Object> args = new ArrayList<>();
        if (ownerOnly) { sql.append(" AND merchant_id=?"); args.add(merchantId); }
        else { sql.append(" AND status IN (10,20,30,50) AND start_time <= CURRENT_TIMESTAMP AND end_time >= CURRENT_TIMESTAMP"); }
        if (keyword != null && !keyword.isBlank()) { sql.append(" AND name LIKE ?"); args.add("%" + keyword.trim() + "%"); }
        Long value = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return value == null ? 0 : value;
    }

    public long insertSession(AuctionCreateCommand command) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO auction_session (merchant_id,store_id,product_id,sku_id,name,status,starting_price,min_increment,start_time,end_time,product_name,product_image_url,sku_label,original_price,creator,updater) VALUES (?,?,?,?,?,0,?,?,?,?,?,?,?,?, 'auction-service','auction-service')", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, command.merchantId()); ps.setLong(2, command.storeId()); ps.setLong(3, command.productId()); ps.setLong(4, command.skuId()); ps.setString(5, command.name());
            ps.setLong(6, command.startingPrice()); ps.setLong(7, command.minIncrement()); ps.setTimestamp(8, Timestamp.valueOf(command.startTime())); ps.setTimestamp(9, Timestamp.valueOf(command.endTime()));
            ps.setString(10, command.productName()); ps.setString(11, command.productImageUrl()); ps.setString(12, command.skuLabel());
            if (command.originalPrice() == null) ps.setNull(13, java.sql.Types.BIGINT); else ps.setLong(13, command.originalPrice());
            return ps;
        }, keys);
        if (keys.getKey() == null) throw new IllegalStateException("Auction id was not generated");
        return keys.getKey().longValue();
    }

    public int updateDraft(Long id, Long merchantId, AuctionCreateCommand c) {
        return jdbc.update("UPDATE auction_session SET product_id=?,sku_id=?,name=?,starting_price=?,min_increment=?,start_time=?,end_time=?,product_name=?,product_image_url=?,sku_label=?,original_price=?,updater='auction-service' WHERE id=? AND merchant_id=? AND status=0 AND deleted=0", c.productId(), c.skuId(), c.name(), c.startingPrice(), c.minIncrement(), Timestamp.valueOf(c.startTime()), Timestamp.valueOf(c.endTime()), c.productName(), c.productImageUrl(), c.skuLabel(), c.originalPrice(), id, merchantId);
    }

    public int updateStatus(Long id, Long merchantId, int expected, int target) {
        return jdbc.update("UPDATE auction_session SET status=?,updater='auction-service' WHERE id=? AND merchant_id=? AND status=? AND deleted=0", target, id, merchantId, expected);
    }

    public int markRunning(Long id) { return jdbc.update("UPDATE auction_session SET status=20 WHERE id=? AND status=10 AND deleted=0", id); }
    public int promoteScheduled(LocalDateTime now) { return jdbc.update("UPDATE auction_session SET status=20 WHERE status=10 AND start_time<=? AND end_time>? AND deleted=0", Timestamp.valueOf(now), Timestamp.valueOf(now)); }
    public List<Long> selectExpiredIds(LocalDateTime now, int limit) { return jdbc.queryForList("SELECT id FROM auction_session WHERE status IN (10,20) AND end_time<=? AND deleted=0 ORDER BY id LIMIT ? FOR UPDATE", Long.class, Timestamp.valueOf(now), limit); }
    public int markEnded(Long id, Long winner, Long winningBid) { return jdbc.update("UPDATE auction_session SET status=30,winner_user_id=?,winning_bid_id=? WHERE id=? AND status IN (10,20) AND deleted=0", winner, winningBid, id); }
    public int setSettlementOrder(Long id, Long orderId) { return jdbc.update("UPDATE auction_session SET settlement_order_id=? WHERE id=? AND settlement_order_id IS NULL AND deleted=0", orderId, id); }

    public AuctionBidRow highestBid(Long sessionId) {
        List<AuctionBidRow> rows = jdbc.query("SELECT id,session_id,bidder_user_id,amount,idempotency_key,create_time FROM auction_bid WHERE session_id=? AND deleted=0 ORDER BY amount DESC,id ASC LIMIT 1", this::mapBid, sessionId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<AuctionBidRow> bidPage(Long sessionId, int offset, int limit) {
        return jdbc.query("SELECT id,session_id,bidder_user_id,amount,idempotency_key,create_time FROM auction_bid WHERE session_id=? AND deleted=0 ORDER BY amount DESC,id ASC LIMIT ? OFFSET ?", this::mapBid, sessionId, limit, offset);
    }

    public long bidCount(Long sessionId) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM auction_bid WHERE session_id=? AND deleted=0", Long.class, sessionId);
        return value == null ? 0 : value;
    }

    public AuctionBidRow findBidByKey(Long sessionId, String idempotencyKey) {
        List<AuctionBidRow> rows = jdbc.query("SELECT id,session_id,bidder_user_id,amount,idempotency_key,create_time FROM auction_bid WHERE session_id=? AND idempotency_key=? AND deleted=0 LIMIT 1", this::mapBid, sessionId, idempotencyKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long insertBid(Long sessionId, Long bidderUserId, Long amount, String idempotencyKey) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO auction_bid (session_id,bidder_user_id,amount,idempotency_key,creator,updater) VALUES (?,?,?,?,'auction-service','auction-service')", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, sessionId); ps.setLong(2, bidderUserId); ps.setLong(3, amount); ps.setString(4, idempotencyKey);
            return ps;
        }, keys);
        if (keys.getKey() == null) throw new IllegalStateException("Auction bid id was not generated");
        return keys.getKey().longValue();
    }

    private AuctionSessionRow mapSession(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new AuctionSessionRow(rs.getLong("id"), rs.getLong("merchant_id"), rs.getLong("store_id"), rs.getLong("product_id"), rs.getLong("sku_id"), rs.getString("name"), rs.getInt("status"), rs.getLong("starting_price"), rs.getLong("min_increment"), time(rs.getTimestamp("start_time")), time(rs.getTimestamp("end_time")), nullable(rs.getObject("winner_user_id")), nullable(rs.getObject("winning_bid_id")), nullable(rs.getObject("settlement_order_id")), time(rs.getTimestamp("settlement_failed_time")), rs.getString("settlement_failure_reason"), rs.getString("product_name"), rs.getString("product_image_url"), rs.getString("sku_label"), nullable(rs.getObject("original_price")));
    }
    private AuctionBidRow mapBid(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new AuctionBidRow(rs.getLong("id"), rs.getLong("session_id"), rs.getLong("bidder_user_id"), rs.getLong("amount"), rs.getString("idempotency_key"), time(rs.getTimestamp("create_time")));
    }
    private static LocalDateTime time(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
    private static Long nullable(Object value) { return value == null ? null : ((Number) value).longValue(); }

    public record AuctionCreateCommand(Long merchantId, Long storeId, Long productId, Long skuId, String name, Long startingPrice, Long minIncrement, LocalDateTime startTime, LocalDateTime endTime, String productName, String productImageUrl, String skuLabel, Long originalPrice) {}
    public record AuctionSessionRow(Long id, Long merchantId, Long storeId, Long productId, Long skuId, String name, Integer status, Long startingPrice, Long minIncrement, LocalDateTime startTime, LocalDateTime endTime, Long winnerUserId, Long winningBidId, Long settlementOrderId, LocalDateTime settlementFailedTime, String settlementFailureReason, String productName, String productImageUrl, String skuLabel, Long originalPrice) {}
    public record AuctionBidRow(Long id, Long sessionId, Long bidderUserId, Long amount, String idempotencyKey, LocalDateTime createTime) {}
}
