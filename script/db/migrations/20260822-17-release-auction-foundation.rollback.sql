SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM system_role_menu WHERE menu_id IN (SELECT id FROM system_menu WHERE permission IN ('commerce:release:create','commerce:release:query','commerce:release:update','commerce:auction:create','commerce:auction:query','commerce:auction:update'));
DELETE FROM system_menu WHERE permission IN ('commerce:release:create','commerce:release:query','commerce:release:update','commerce:auction:create','commerce:auction:query','commerce:auction:update');
DROP TABLE IF EXISTS commerce_auction_bid;
DROP TABLE IF EXISTS commerce_auction_session;
DROP TABLE IF EXISTS commerce_release_purchase;
DROP TABLE IF EXISTS commerce_release_item;
DROP TABLE IF EXISTS commerce_release_campaign;
SET FOREIGN_KEY_CHECKS = 1;
