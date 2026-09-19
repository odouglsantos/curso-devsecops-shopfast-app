INSERT INTO products (name, category, price, stock_quantity, description) VALUES
    ('Fone Bluetooth ShopFast X1', 'eletronicos', 249.90, 40, 'Fone over-ear com cancelamento de ruido'),
    ('Teclado Mecanico RGB', 'eletronicos', 389.00, 15, 'Switch azul, layout ABNT2'),
    ('Cafeteira Expresso Compacta', 'casa', 599.00, 8, 'Compativel com capsulas padrao'),
    ('Tenis de Corrida Trail', 'esporte', 459.90, 22, 'Solado com tracao para terreno irregular'),
    ('Mochila Antifurto 25L', 'acessorios', 199.90, 60, 'Compartimento acolchoado para notebook');

-- Hashes BCrypt (custo 12), com sal por senha: o que antes era MD5 sem sal e
-- caia em rainbow table em segundos. Senhas de laboratorio, para uso no curso:
-- admin123 / Joana@2026 / Carlos@2026.
INSERT INTO users (username, password_hash, email, role, credit_card_number) VALUES
    ('admin', '$2a$12$4U0gRfdfdRBdLNDDYsOmderAH6.LBN28Npm.Y9xcD2Fowjhoe91qe', 'admin@shopfast.io', 'ADMIN', NULL),
    ('joana', '$2a$12$xKQxpgh0s6kByyEX1Ljf5u8HcvFlkp0pC/QOLQ4cmyC9C3lJU5Sme', 'joana@example.com', 'CUSTOMER', '4111111111111111'),
    ('carlos', '$2a$12$AN0RdqXjRN3S.E3p02sHQ.UBK0BWeLkqn5plNfUSzlcC.Wd2Z.qSi', 'carlos@example.com', 'CUSTOMER', '5500005555555559');

INSERT INTO orders (user_id, status, total, coupon_code, created_at) VALUES
    (2, 'PAID', 449.80, 'SHOP000042', CURRENT_TIMESTAMP),
    (3, 'PENDING', 599.00, NULL, CURRENT_TIMESTAMP);
