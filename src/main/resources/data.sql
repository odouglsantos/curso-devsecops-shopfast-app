INSERT INTO products (name, category, price, stock_quantity, description) VALUES
    ('Fone Bluetooth ShopFast X1', 'eletronicos', 249.90, 40, 'Fone over-ear com cancelamento de ruido'),
    ('Teclado Mecanico RGB', 'eletronicos', 389.00, 15, 'Switch azul, layout ABNT2'),
    ('Cafeteira Expresso Compacta', 'casa', 599.00, 8, 'Compativel com capsulas padrao'),
    ('Tenis de Corrida Trail', 'esporte', 459.90, 22, 'Solado com tracao para terreno irregular'),
    ('Mochila Antifurto 25L', 'acessorios', 199.90, 60, 'Compartimento acolchoado para notebook');

-- VULN (didatica): hashes MD5 sem sal, quebraveis em segundos por rainbow table.
-- Senhas em claro, versionadas junto: admin123 / Joana@2026 / Carlos@2026.
INSERT INTO users (username, password_hash, email, role, credit_card_number) VALUES
    ('admin', '0192023a7bbd73250516f069df18b500', 'admin@shopfast.io', 'ADMIN', NULL),
    ('joana', '60b3c2379ca5dc2cd1c1c03797936fcb', 'joana@example.com', 'CUSTOMER', '4111111111111111'),
    ('carlos', '491311f3666b1e036365b78ec5973db7', 'carlos@example.com', 'CUSTOMER', '5500005555555559');

INSERT INTO orders (user_id, status, total, coupon_code, created_at) VALUES
    (2, 'PAID', 449.80, 'SHOP000042', CURRENT_TIMESTAMP),
    (3, 'PENDING', 599.00, NULL, CURRENT_TIMESTAMP);
