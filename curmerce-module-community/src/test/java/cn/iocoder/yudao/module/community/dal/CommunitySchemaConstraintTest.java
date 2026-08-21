package cn.iocoder.yudao.module.community.dal;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CommunitySchemaConstraintTest {
    @Test
    void rejectsInvalidStatusesAndDuplicateInteractions() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:community_constraints;MODE=MySQL;DB_CLOSE_DELAY=-1"); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE community_post (id BIGINT AUTO_INCREMENT PRIMARY KEY, status TINYINT NOT NULL, CONSTRAINT chk_post_status CHECK (status IN (0,1,2)))");
            statement.execute("CREATE TABLE community_post_reaction (id BIGINT AUTO_INCREMENT PRIMARY KEY, post_id BIGINT NOT NULL, user_id BIGINT NOT NULL, type TINYINT NOT NULL, deleted BOOLEAN DEFAULT FALSE, CONSTRAINT chk_reaction_type CHECK (type IN (1,2)), CONSTRAINT uk_reaction UNIQUE (post_id,user_id,type,deleted))");
            statement.execute("CREATE TABLE community_follow (id BIGINT AUTO_INCREMENT PRIMARY KEY, follower_user_id BIGINT NOT NULL, followed_user_id BIGINT NOT NULL, CONSTRAINT chk_follow_distinct CHECK (follower_user_id <> followed_user_id), CONSTRAINT uk_follow UNIQUE (follower_user_id,followed_user_id))");
            assertThrows(SQLException.class, () -> statement.execute("INSERT INTO community_post(status) VALUES (9)"));
            statement.execute("INSERT INTO community_post(status) VALUES (0)");
            statement.execute("INSERT INTO community_post_reaction(post_id,user_id,type) VALUES (1,7,1)");
            assertThrows(SQLException.class, () -> statement.execute("INSERT INTO community_post_reaction(post_id,user_id,type) VALUES (1,7,1)"));
            assertThrows(SQLException.class, () -> statement.execute("INSERT INTO community_post_reaction(post_id,user_id,type) VALUES (1,7,9)"));
            assertThrows(SQLException.class, () -> statement.execute("INSERT INTO community_follow(follower_user_id,followed_user_id) VALUES (7,7)"));
        }
    }
}
